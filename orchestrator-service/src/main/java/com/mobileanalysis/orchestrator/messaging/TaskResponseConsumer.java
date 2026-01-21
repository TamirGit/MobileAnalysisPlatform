package com.mobileanalysis.orchestrator.messaging;

import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    // DependencyResolver and AnalysisCompletionService will be added in subsequent tasks
    
    /**
     * Listen for task response events from orchestrator-responses topic.
     * Manual acknowledgment ensures we only commit after successful processing.
     * 
     * @param event Task response event from engine
     * @param acknowledgment Kafka acknowledgment for manual commit
     */
    @KafkaListener(
        topics = "${app.kafka.topics.orchestrator-responses:orchestrator-responses}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleTaskResponse(@Payload TaskResponseEvent event, Acknowledgment acknowledgment) {
        // Set MDC correlation IDs for logging
        MDC.put("analysisId", event.getAnalysisId().toString());
        MDC.put("taskId", event.getTaskId().toString());
        
        try {
            log.info("Received task response: analysisId={}, taskId={}, status={}", 
                event.getAnalysisId(), event.getTaskId(), event.getStatus());
            
            // Process the task response (update DB, resolve dependencies, check completion)
            processTaskResponse(event);
            
            // Commit offset ONLY after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.info("Task response processed successfully and committed: taskId={}", event.getTaskId());
            }
            
        } catch (Exception e) {
            log.error("Failed to process task response: taskId={}, error={}", 
                event.getTaskId(), e.getMessage(), e);
            // Don't acknowledge - Kafka will redeliver
            throw new RuntimeException("Task response processing failed", e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Process task response in transaction:
     * 1. Update task status, output path, error message
     * 2. Resolve and dispatch dependent tasks (via outbox)
     * 3. Check if analysis is complete
     * 4. Update Redis cache
     * 
     * Will be fully implemented after DependencyResolver and AnalysisCompletionService are created.
     * 
     * @param event Task response event
     */
    @Transactional
    public void processTaskResponse(TaskResponseEvent event) {
        // TODO: Implement in Task 6 after DependencyResolver and AnalysisCompletionService are created
        log.info("Processing task response (placeholder implementation): taskId={}", event.getTaskId());
    }
}
