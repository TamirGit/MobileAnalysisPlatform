package com.mobileanalysis.orchestrator.messaging;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.OutboxRepository;
import com.mobileanalysis.orchestrator.service.AnalysisCompletionService;
import com.mobileanalysis.orchestrator.service.DependencyResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Kafka consumer for task response events from analysis engines.
 * Implements manual commit pattern:
 * 1. Consume task response from Kafka
 * 2. Update task status in database
 * 3. Resolve and dispatch dependent tasks
 * 4. Check for analysis completion
 * 5. Commit offset ONLY after successful database transaction
 * <p>
 * If processing fails, offset is not committed and Kafka will redeliver.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskResponseConsumer {
    
    private final AnalysisTaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final DependencyResolver dependencyResolver;
    private final AnalysisCompletionService completionService;
    
    /**
     * Listen for task response events from orchestrator-responses topic.
     * Process response in transaction:
     * 1. Find and update task status, output path, error message, completed timestamp
     * 2. Save task to database
     * 3. If COMPLETED: Resolve dependencies and create outbox events for ready dependent tasks
     * 4. Save outbox events (transactional with task update)
     * 5. Check if analysis is complete (all tasks done)
     * <p>
     * Manual acknowledgment ensures we only commit after successful transaction.
     * All operations are in a single transaction - succeed together or rollback together.
     * 
     * @param event Task response event from engine
     * @param acknowledgment Kafka acknowledgment for manual commit
     */
    @KafkaListener(
        topics = "${app.kafka.topics.orchestrator-responses:orchestrator-responses}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "taskResponseKafkaListenerContainerFactory"
    )
    @Transactional // Transaction starts here (entry point from outside)
    public void handleTaskResponse(@Payload TaskResponseEvent event, Acknowledgment acknowledgment) {
        // Set MDC correlation IDs for logging
        MDC.put("analysisId", event.getAnalysisId().toString());
        MDC.put("taskId", event.getTaskId().toString());
        
        try {
            log.info("Received task response: analysisId={}, taskId={}, status={}, attempts={}", 
                event.getAnalysisId(), event.getTaskId(), event.getStatus(), event.getAttempts());
            
            // Step 1: Find task by ID
            AnalysisTaskEntity task = taskRepository.findById(event.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Task not found: " + event.getTaskId()));
            
            log.debug("Processing task response: current status={}, new status={}", 
                task.getStatus(), event.getStatus());
            
            // Step 2: Update task with response data
            task.setStatus(event.getStatus());
            task.setOutputPath(event.getOutputPath());
            task.setErrorMessage(event.getErrorMessage());
            task.setAttempts(event.getAttempts());
            task.setCompletedAt(event.getTimestamp());
            
            taskRepository.save(task);
            
            log.info("Task {} updated: status={}, outputPath={}, attempts={}", 
                task.getId(), task.getStatus(), task.getOutputPath(), task.getAttempts());
            
            // Step 3: If task completed successfully, resolve dependencies
            if (event.getStatus() == TaskStatus.COMPLETED) {
                log.info("Task {} completed successfully, resolving dependencies", task.getId());
                
                List<OutboxEventEntity> outboxEvents = dependencyResolver.resolveAndDispatch(task);
                
                if (!outboxEvents.isEmpty()) {
                    // Step 4: Save outbox events (same transaction)
                    outboxRepository.saveAll(outboxEvents);
                    log.info("Created {} outbox events for dependent tasks of task {}", 
                        outboxEvents.size(), task.getId());
                }
            } else if (event.getStatus() == TaskStatus.FAILED) {
                log.warn("Task {} failed: {} (attempts: {})", 
                    task.getId(), event.getErrorMessage(), event.getAttempts());
            }
            
            // Step 5: Check if analysis is complete
            completionService.checkAndMarkCompletion(event.getAnalysisId());
            
            // Note: Redis cache update would go here in future (best-effort, after commit)
            // For Phase 2, we focus on core functionality
            
            // Commit offset ONLY after successful transaction
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.info("Task response processed successfully and committed: taskId={}, status={}", 
                    event.getTaskId(), event.getStatus());
            }
            
        } catch (Exception e) {
            log.error("Failed to process task response: taskId={}, error={}", 
                event.getTaskId(), e.getMessage(), e);
            // Don't acknowledge - Kafka will redeliver
            // Transaction will be rolled back automatically
            throw new RuntimeException("Task response processing failed", e);
        } finally {
            MDC.clear();
        }
    }
}
