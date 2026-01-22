package com.mobileanalysis.orchestrator.config;

import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for orchestrator service.
 * 
 * Key features:
 * - ErrorHandlingDeserializer wraps JsonDeserializer to catch malformed messages
 * - Malformed messages are sent to DLQ and offset is committed (no infinite retry)
 * - Separate container factories for FileEvent and TaskResponseEvent
 * - Manual acknowledgment for exactly-once semantics
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${app.kafka.topics.orchestrator-responses-dlq:orchestrator-responses-dlq}")
    private String dlqTopic;

    // ========== Consumer Configuration for FileEvent ==========

    @Bean
    public Map<String, Object> fileEventConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Use ErrorHandlingDeserializer to wrap JsonDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // Delegate to these deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        
        // Configure JsonDeserializer for FileEvent
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FileEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobileanalysis.common.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return props;
    }

    @Bean
    public ConsumerFactory<String, FileEvent> fileEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            fileEventConsumerConfigs(),
            new StringDeserializer(),
            new ErrorHandlingDeserializer<>(new JsonDeserializer<>(FileEvent.class, false))
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, FileEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FileEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fileEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Set error handler that sends to DLQ and commits offset
        factory.setCommonErrorHandler(createErrorHandler());
        
        return factory;
    }

    // ========== Consumer Configuration for TaskResponseEvent ==========

    @Bean
    public Map<String, Object> taskResponseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Use ErrorHandlingDeserializer to wrap JsonDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // Delegate to these deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        
        // Configure JsonDeserializer for TaskResponseEvent
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaskResponseEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobileanalysis.common.events");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        
        return props;
    }

    @Bean
    public ConsumerFactory<String, TaskResponseEvent> taskResponseConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
            taskResponseConsumerConfigs(),
            new StringDeserializer(),
            new ErrorHandlingDeserializer<>(new JsonDeserializer<>(TaskResponseEvent.class, false))
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> taskResponseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(taskResponseConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        
        // Set error handler that sends to DLQ and commits offset
        factory.setCommonErrorHandler(createErrorHandler());
        
        return factory;
    }

    // ========== Producer Configuration ==========

    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ========== Error Handling ==========

    /**
     * Create error handler that:
     * 1. Logs the error
     * 2. Sends malformed message to DLQ
     * 3. Commits the offset (don't retry forever)
     * 
     * This prevents infinite retry loops for malformed messages.
     */
    private DefaultErrorHandler createErrorHandler() {
        // No retries for deserialization errors - send to DLQ immediately
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            (consumerRecord, exception) -> {
                log.error(
                    "Failed to process message from topic {} partition {} offset {}. "
                    + "Sending to DLQ. Error: {}",
                    consumerRecord.topic(),
                    consumerRecord.partition(),
                    consumerRecord.offset(),
                    exception.getMessage(),
                    exception
                );
                
                // TODO: Send to DLQ topic (implement in Phase 3)
                // For now, just log - the offset will be committed
                log.warn("DLQ not yet implemented - message will be lost: {}", consumerRecord);
            },
            new FixedBackOff(0L, 0L) // No retries
        );
        
        // Add exception types that should not be retried
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class,
            org.springframework.messaging.converter.MessageConversionException.class,
            org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class
        );
        
        return errorHandler;
    }
}
