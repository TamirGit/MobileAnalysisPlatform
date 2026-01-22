package com.mobileanalysis.orchestrator.messaging;

import com.mobileanalysis.common.events.HeartbeatEvent;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kafka consumer for heartbeat events from analysis engines.
 * Heartbeats are sent every 30 seconds by running engines to indicate task health.
 * This consumer updates the last_heartbeat_at timestamp in the database.
 * <p>
 * The HeartbeatMonitor scheduled job uses these timestamps to detect stale tasks
 * (no heartbeat for 2+ minutes) and mark them as failed.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HeartbeatConsumer {
    
    private final AnalysisTaskRepository taskRepository;
    
    /**
     * Listen for heartbeat events from task-heartbeats topic.
     * Updates last_heartbeat_at timestamp for the task in a transaction.
     * 
     * @param event Heartbeat event from engine
     * @param acknowledgment Kafka acknowledgment for manual commit (never null with manual ack mode)
     */
    @KafkaListener(
        topics = "${app.kafka.topics.task-heartbeats:task-heartbeats}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional // Transaction starts here (entry point from outside)
    public void handleHeartbeat(@Payload HeartbeatEvent event, Acknowledgment acknowledgment) {
        // Set MDC correlation IDs for logging
        MDC.put("analysisId", event.getAnalysisId().toString());
        MDC.put("taskId", event.getTaskId().toString());
        
        try {
            log.debug("Received heartbeat: analysisId={}, taskId={}, engineType={}", 
                event.getAnalysisId(), event.getTaskId(), event.getEngineType());
            
            // Find task
            AnalysisTaskEntity task = taskRepository.findById(event.getTaskId())
                .orElseThrow(() -> new IllegalArgumentException(
                    "Task not found: " + event.getTaskId()));
            
            // Update last heartbeat timestamp
            task.setLastHeartbeatAt(event.getTimestamp());
            taskRepository.save(task);
            
            log.debug("Updated heartbeat for task: {}, timestamp: {}", 
                event.getTaskId(), event.getTimestamp());
            
            // Commit offset ONLY after successful transaction
            // Note: Acknowledgment is never null when using manual ack mode with @KafkaListener
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process heartbeat: taskId={}, error={}", 
                event.getTaskId(), e.getMessage(), e);
            // Don't acknowledge - Kafka will redeliver
            // Transaction will be rolled back automatically
            throw new RuntimeException("Heartbeat processing failed", e);
        } finally {
            MDC.clear();
        }
    }
}
