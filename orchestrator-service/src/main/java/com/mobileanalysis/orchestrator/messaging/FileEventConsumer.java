package com.mobileanalysis.orchestrator.messaging;

import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.orchestrator.service.AnalysisOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for file events.
 * Implements manual commit pattern:
 * 1. Consume message from Kafka
 * 2. Process (create analysis in database)
 * 3. Commit offset ONLY after successful database transaction
 * <p>
 * If processing fails, offset is not committed and Kafka will redeliver.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileEventConsumer {
    
    private final AnalysisOrchestrator orchestrator;
    
    /**
     * Listen for file events from file-events topic.
     * Manual acknowledgment ensures we only commit after successful processing.
     * 
     * @param fileEvent File event containing path and type
     * @param acknowledgment Kafka acknowledgment for manual commit
     */
    @KafkaListener(
        topics = "${app.kafka.topics.file-events:file-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFileEvent(@Payload FileEvent fileEvent, Acknowledgment acknowledgment) {
        String eventId = fileEvent.getEventId() != null ? fileEvent.getEventId().toString() : "unknown";
        
        // Set MDC for logging (will be overridden by orchestrator with analysisId)
        MDC.put("eventId", eventId);
        MDC.put("fileType", fileEvent.getFileType().toString());
        
        try {
            log.info("Received file event: eventId={}, filePath={}, fileType={}", 
                eventId, fileEvent.getFilePath(), fileEvent.getFileType());
            
            // Process the file event (creates analysis + tasks in transaction)
            orchestrator.processFileEvent(fileEvent);
            
            // Commit offset ONLY after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.info("File event processed successfully and committed: eventId={}", eventId);
            }
            
        } catch (Exception e) {
            log.error("Failed to process file event: eventId={}, error={}", eventId, e.getMessage(), e);
            // Don't acknowledge - Kafka will redeliver
            // In production, consider DLQ after max retries
            throw new RuntimeException("File event processing failed", e);
        } finally {
            MDC.clear();
        }
    }
}
