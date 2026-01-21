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
import com.mobileanalysis.orchestrator.util.EngineTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for resolving task dependencies and dispatching ready dependent tasks.
 * When a task completes, this service:
 * 1. Finds all tasks that depend on the completed task
 * 2. Checks if their dependencies are satisfied
 * 3. Creates outbox events for tasks that are ready to run
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DependencyResolver {

    private final AnalysisTaskRepository analysisTaskRepository;
    private final AnalysisRepository analysisRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<OutboxEventEntity> resolveAndDispatch(AnalysisTaskEntity completedTask) {
        List<OutboxEventEntity> outboxEvents = new ArrayList<>();

        log.debug("Resolving dependencies for completed task: taskId={}, analysisId={}",
                completedTask.getId(), completedTask.getAnalysisId());

        List<AnalysisTaskEntity> dependentTasks = analysisTaskRepository
                .findByDependsOnTaskId(completedTask.getId());

        if (dependentTasks.isEmpty()) {
            log.debug("No dependent tasks found for task: {}", completedTask.getId());
            return outboxEvents;
        }

        log.info("Found {} dependent tasks for completed task: {}",
                dependentTasks.size(), completedTask.getId());

        for (AnalysisTaskEntity dependentTask : dependentTasks) {
            if (areAllDependenciesMet(dependentTask)) {
                log.info("All dependencies met for task: {}, dispatching", dependentTask.getId());

                // Mark task as dispatched and increment attempts
                dependentTask.setStatus(TaskStatus.DISPATCHED);
                dependentTask.setAttempts(1); // First attempt for this task
                analysisTaskRepository.save(dependentTask);

                OutboxEventEntity outboxEvent = createTaskDispatchEvent(dependentTask, completedTask);
                outboxEvents.add(outboxEvent);

                log.info("Task {} dispatched to outbox for engine: {}",
                        dependentTask.getId(), dependentTask.getEngineType());
            } else {
                log.debug("Task {} still has unmet dependencies", dependentTask.getId());
            }
        }

        return outboxEvents;
    }

    private boolean areAllDependenciesMet(AnalysisTaskEntity task) {
        if (task.getDependsOnTaskId() == null) {
            return true;
        }

        AnalysisTaskEntity parentTask = analysisTaskRepository
                .findById(task.getDependsOnTaskId())
                .orElse(null);

        if (parentTask == null) {
            log.error("Parent task not found: {} for dependent task: {}",
                    task.getDependsOnTaskId(), task.getId());
            return false;
        }

        boolean isReady = parentTask.getStatus() == TaskStatus.COMPLETED;
        log.debug("Dependency check for task {}: parent task {} status is {}, ready={}",
                task.getId(), parentTask.getId(), parentTask.getStatus(), isReady);

        return isReady;
    }

    private OutboxEventEntity createTaskDispatchEvent(AnalysisTaskEntity task,
                                                     AnalysisTaskEntity parentTask) {
        try {
            TaskConfigEntity taskConfig = taskConfigRepository
                    .findById(task.getTaskConfigId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Task config not found: " + task.getTaskConfigId()));

            AnalysisEntity analysis = analysisRepository.findById(task.getAnalysisId())
                    .orElseThrow(() -> new IllegalStateException("Analysis not found: " + task.getAnalysisId()));

            TaskEvent taskEvent = TaskEvent.builder()
                    .eventId(UUID.randomUUID())
                    .taskId(task.getId())
                    .analysisId(task.getAnalysisId())
                    .engineType(task.getEngineType())
                    .filePath(analysis.getFilePath())
                    .dependentTaskOutputPath(parentTask.getOutputPath())
                    .idempotencyKey(task.getIdempotencyKey())
                    .timeoutSeconds(taskConfig.getTimeoutSeconds())
                    .attempts(task.getAttempts()) // Pass current attempts (should be 1 for first dispatch)
                    .timestamp(Instant.now())
                    .build();

            String topic = EngineTopicMapper.getTopicForEngineType(task.getEngineType());
            String payload = objectMapper.writeValueAsString(taskEvent);

            return OutboxEventEntity.builder()
                    .aggregateType("AnalysisTask")
                    .aggregateId(task.getId().toString())
                    .eventType("TASK_DISPATCHED")
                    .topic(topic)
                    .partitionKey(task.getAnalysisId().toString())
                    .payload(payload)
                    .processed(false)
                    .build();

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task event: taskId={}", task.getId(), e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }
}
