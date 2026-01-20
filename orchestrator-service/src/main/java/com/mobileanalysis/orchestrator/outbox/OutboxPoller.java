package com.mobileanalysis.orchestrator.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.repository.OutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Outbox Poller - Scheduled task for transactional outbox pattern.
 * 
 * Polls unprocessed events from the outbox table and publishes them to Kafka.
 * This ensures exactly-once semantics by writing events to the database
 * in the same transaction as domain changes, then asynchronously publishing.
 * 
 * Pattern: Transactional Outbox (Microservices.io)
 * Polling Interval: 1 second (configurable)
 * Batch Size: 50 events per poll (configurable)
 */
@Component
@Slf4j
public class OutboxPoller {

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${app.outbox.batch-size:50}")
    private int batchSize;

    /**
     * Poll outbox table and publish unprocessed events to Kafka.
     * 
     * Runs every app.outbox.poll-interval-ms (default 1000ms).
     * Processes up to app.outbox.batch-size events per execution.
     * 
     * For each event:
     * 1. Publish to Kafka topic with partition key from outbox
     * 2. Mark as processed with timestamp
     * 3. Continue on individual failures (log warning)
     */
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void pollAndPublish() {
        try {
            // Fetch batch of unprocessed events
            List<OutboxEventEntity> events = outboxRepository.findUnprocessedBatch(batchSize);
            
            if (events.isEmpty()) {
                return; // No events to process
            }

            log.debug("Polling outbox: found {} unprocessed events", events.size());

            // Process each event
            for (OutboxEventEntity event : events) {
                processOutboxEvent(event);
            }

            log.info("Outbox poll complete: processed {} events", events.size());

        } catch (Exception e) {
            log.error("Outbox polling failed", e);
            // Don't throw - allow next poll to retry
        }
    }

    /**
     * Process single outbox event - publish to Kafka and mark as processed.
     * 
     * @param event Outbox event to process
     */
    @Transactional
    protected void processOutboxEvent(OutboxEventEntity event) {
        try {
            // Create Kafka producer record with partition key
            ProducerRecord<String, Object> record = new ProducerRecord<>(
                event.getTopic(),
                event.getPartitionKey(), // analysisId for task ordering
                parsePayload(event.getPayload())
            );

            // Publish to Kafka (synchronous for reliability)
            kafkaTemplate.send(record).get(); // Blocks until success or failure

            // Mark as processed
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            outboxRepository.save(event);

            log.info("Outbox event published: id={}, topic={}, partitionKey={}", 
                event.getId(), event.getTopic(), event.getPartitionKey());

        } catch (Exception e) {
            log.warn("Failed to publish outbox event, will retry next poll. eventId={}, topic={}, error={}", 
                event.getId(), event.getTopic(), e.getMessage());
            // Don't mark as processed - will retry next poll
            // Don't throw - continue processing other events
        }
    }

    /**
     * Parse JSON payload from outbox to Object for Kafka serialization.
     * 
     * @param payload JSON string payload
     * @return Parsed object
     */
    private Object parsePayload(String payload) {
        try {
            // Parse as generic object - Kafka serializer will handle
            return objectMapper.readValue(payload, Object.class);
        } catch (Exception e) {
            log.error("Failed to parse outbox payload", e);
            throw new RuntimeException("Invalid outbox payload", e);
        }
    }
}
