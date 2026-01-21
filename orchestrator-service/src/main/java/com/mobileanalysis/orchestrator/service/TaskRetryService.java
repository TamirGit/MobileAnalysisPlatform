package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisRepository;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.TaskConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Service responsible for retrying failed tasks.
 *
 * Retry rules:
 * - maxRetries represents TOTAL attempts (original + retries), not additional retries
 * - If task.attempts < maxRetries, task is retried by dispatching a new TaskEvent
 * - If task.attempts >= maxRetries, task stays FAILED (retry budget exhausted)
 * - Each retry increments the attempts counter
 * 
 * Example with maxRetries=3:
 * - Attempt 1 (attempts=1): Original execution fails
 * - Attempt 2 (attempts=2): First retry (eligible because 2 <= 3)
 * - Attempt 3 (attempts=3): Second retry (eligible because 3 <= 3)
 * - After attempt 3 fails: No more retries (3 >= 3, budget exhausted)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TaskRetryService {

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisRepository analysisRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final com.mobileanalysis.orchestrator.repository.OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void retryIfPossible(Long taskId, String failureReason) {
        AnalysisTaskEntity task = analysisTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        retryIfPossible(task, failureReason);
    }

    /**
     * Attempt to retry a task if it has remaining retry budget.
     * 
     * Note: maxRetries is the total number of attempts allowed (including original).
     * The current attempts counter will be incremented for the retry.
     * 
     * @param task Task to potentially retry
     * @param failureReason Reason why the task failed
     */
    @Transactional
    public void retryIfPossible(AnalysisTaskEntity task, String failureReason) {
        TaskConfigEntity taskConfig = taskConfigRepository.findById(task.getTaskConfigId())
                .orElseThrow(() -> new IllegalStateException("Task config not found: " + task.getTaskConfigId()));

        int maxRetries = taskConfig.getMaxRetries(); // Total attempts allowed
        int currentAttempts = task.getAttempts();
        int nextAttempts = currentAttempts + 1;

        // Check if we've exhausted the retry budget
        // maxRetries is TOTAL attempts, so if nextAttempts > maxRetries, we're done
        if (nextAttempts > maxRetries) {
            log.error("Retry budget exhausted for task {} (engine={}, currentAttempts={}, maxTotalAttempts={}). "
                    + "Reason: {}",
                    task.getId(), task.getEngineType(), currentAttempts, maxRetries, failureReason);
            // Keep FAILED; a dedicated DLQ flow can be added later.
            return;
        }

        // We have retry budget remaining - prepare retry
        int retriesUsed = currentAttempts; // Original attempt doesn't count as a retry
        int retriesRemaining = maxRetries - nextAttempts;
        
        log.info("Retry eligible for task {} (engine={}, attempts={}/{}, retriesUsed={}, retriesRemaining={})",
                task.getId(), task.getEngineType(), currentAttempts, maxRetries, retriesUsed, retriesRemaining);

        AnalysisEntity analysis = analysisRepository.findById(task.getAnalysisId())
                .orElseThrow(() -> new IllegalStateException("Analysis not found: " + task.getAnalysisId()));

        String dependentOutputPath = resolveDependentOutputPath(task);

        TaskEvent taskEvent = TaskEvent.builder()
                .eventId(UUID.randomUUID())
                .taskId(task.getId())
                .analysisId(task.getAnalysisId())
                .engineType(task.getEngineType())
                .filePath(analysis.getFilePath())
                .dependentTaskOutputPath(dependentOutputPath)
                .idempotencyKey(task.getIdempotencyKey())
                .timeoutSeconds(taskConfig.getTimeoutSeconds())
                .timestamp(Instant.now())
                .build();

        String topic = getTopicForEngineType(task.getEngineType().name());

        try {
            String payload = objectMapper.writeValueAsString(taskEvent);

            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                    .aggregateType("AnalysisTask")
                    .aggregateId(task.getId().toString())
                    .eventType("TASK_RETRY")
                    .topic(topic)
                    .partitionKey(task.getAnalysisId().toString())
                    .payload(payload)
                    .processed(false)
                    .build();

            outboxRepository.save(outboxEvent);

            // Update task state for retry
            task.setAttempts(nextAttempts);
            task.setStatus(TaskStatus.PENDING);
            task.setErrorMessage(failureReason);
            task.setCompletedAt(null);
            analysisTaskRepository.save(task);

            log.warn("Retry scheduled for task {} (engine={}, nextAttempts={}, maxTotalAttempts={})",
                    task.getId(), task.getEngineType(), nextAttempts, maxRetries);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize retry TaskEvent for task {}", task.getId(), e);
            throw new RuntimeException("Failed to enqueue retry event", e);
        }
    }

    private String resolveDependentOutputPath(AnalysisTaskEntity task) {
        if (task.getDependsOnTaskId() == null) {
            return null;
        }

        Optional<AnalysisTaskEntity> parent = analysisTaskRepository.findById(task.getDependsOnTaskId());
        return parent.map(AnalysisTaskEntity::getOutputPath).orElse(null);
    }

    private String getTopicForEngineType(String engineType) {
        return switch (engineType) {
            case "STATIC_ANALYSIS" -> "static-analysis-tasks";
            case "DYNAMIC_ANALYSIS" -> "dynamic-analysis-tasks";
            case "DECOMPILER" -> "decompiler-tasks";
            case "SIGNATURE_CHECK" -> "signature-check-tasks";
            default -> throw new IllegalArgumentException("Unknown engine type: " + engineType);
        };
    }
}
