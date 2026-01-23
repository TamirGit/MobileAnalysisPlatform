package com.mobileanalysis.common.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service responsible for publishing malformed messages to Dead Letter Queue (DLQ) topics.
 * Each original topic has a corresponding DLQ topic with .DLQ suffix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DLQPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Publishes a malformed message to the appropriate DLQ topic.
     * 
     * @param record The original consumer record that failed processing
     * @param exception The exception that caused the failure
     */
    public void publishToDLQ(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            String originalTopic = record.topic();
            String dlqTopic = originalTopic + ".DLQ";
            
            // Build DLQ message with metadata
            DLQMessage dlqMessage = DLQMessage.builder()
                    .originalTopic(originalTopic)
                    .originalPartition(record.partition())
                    .originalOffset(record.offset())
                    .errorMessage(exception.getMessage())
                    .errorTimestamp(Instant.now())
                    .messagePayload(record.value() != null ? record.value().toString() : "null")
                    .build();
            
            // Publish to DLQ topic
            kafkaTemplate.send(dlqTopic, record.key() != null ? record.key().toString() : null, dlqMessage);
            
            log.warn("Published malformed message to DLQ. Original topic: {}, DLQ topic: {}, Partition: {}, Offset: {}, Error: {}",
                    originalTopic, dlqTopic, record.partition(), record.offset(), exception.getMessage());
            
        } catch (Exception dlqException) {
            // Log but don't throw - we don't want DLQ publishing failures to crash the consumer
            log.error("Failed to publish message to DLQ. Original topic: {}, Partition: {}, Offset: {}. DLQ Error: {}",
                    record.topic(), record.partition(), record.offset(), dlqException.getMessage(), dlqException);
        }
    }
}
