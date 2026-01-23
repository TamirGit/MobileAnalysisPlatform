package com.mobileanalysis.common.dlq;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Data class representing a message sent to the Dead Letter Queue (DLQ).
 * Contains original message metadata and error information for debugging and recovery.
 */
@Data
@Builder
public class DLQMessage {
    
    /**
     * The original Kafka topic the message was sent to
     */
    private String originalTopic;
    
    /**
     * The partition of the original topic
     */
    private int originalPartition;
    
    /**
     * The offset within the partition
     */
    private long originalOffset;
    
    /**
     * Error message explaining why the message was sent to DLQ
     */
    private String errorMessage;
    
    /**
     * Timestamp when the error occurred
     */
    private Instant errorTimestamp;
    
    /**
     * The original message payload as a string (may be malformed JSON)
     */
    private String messagePayload;
}
