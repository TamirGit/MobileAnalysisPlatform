package com.mobileanalysis.common.config;

import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared Kafka configuration for all services.
 * 
 * Features:
 * - ErrorHandlingDeserializer wraps JsonDeserializer to catch malformed messages
 * - DefaultErrorHandler with DeadLetterPublishingRecoverer sends malformed messages to DLQ
 * - Manual acknowledgment for exactly-once semantics
 * - Separate factories for FileEvent, TaskResponseEvent, and String messages
 */
@Configuration
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    // ========== PRODUCER CONFIGURATIONS ==========

    // Producer configuration for String messages
    @Bean
    public Map<String, Object> stringProducerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, String> stringProducerFactory() {
        return new DefaultKafkaProducerFactory<>(stringProducerConfigs());
    }

    @Bean(name = "stringKafkaTemplate")
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        return new KafkaTemplate<>(stringProducerFactory());
    }

    // Producer configuration for JSON messages (for outbox pattern)
    @Bean
    public Map<String, Object> objectProducerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false); // Don't add __TypeId__ header
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> objectProducerFactory() {
        return new DefaultKafkaProducerFactory<>(objectProducerConfigs());
    }

    @Bean(name = "kafkaTemplate") // Primary bean for Object serialization
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(objectProducerFactory());
    }

    // ========== CONSUMER CONFIGURATIONS ==========

    // Consumer configuration for FileEvent (JSON deserialization with error handling)
    @Bean
    public Map<String, Object> fileEventConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Use ErrorHandlingDeserializer to wrap JsonDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // Delegate to these deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        
        // JsonDeserializer configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobileanalysis.common.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, FileEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Don't require __TypeId__ header
        
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

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, FileEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FileEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fileEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(createErrorHandler());
        return factory;
    }

    // Consumer configuration for TaskResponseEvent (JSON deserialization with error handling)
    @Bean
    public Map<String, Object> taskResponseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        
        // Use ErrorHandlingDeserializer to wrap JsonDeserializer
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        
        // Delegate to these deserializers
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        
        // JsonDeserializer configuration
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobileanalysis.common.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaskResponseEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false); // Don't require __TypeId__ header
        
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

    @Bean(name = "taskResponseKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> taskResponseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(taskResponseConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(createErrorHandler());
        return factory;
    }

    // Consumer configuration for String messages
    @Bean
    public Map<String, Object> stringConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
        return props;
    }

    @Bean
    public ConsumerFactory<String, String> stringConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(stringConsumerConfigs());
    }

    @Bean(name = "stringKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> stringKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(stringConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    // ========== DLQ CONFIGURATION ==========

    /**
     * DeadLetterPublishingRecoverer routes malformed messages to DLQ topics.
     * Each original topic gets a corresponding .DLQ suffix topic.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> kafkaTemplate) {
        return new DeadLetterPublishingRecoverer(kafkaTemplate,
            (consumerRecord, exception) -> {
                String originalTopic = consumerRecord.topic();
                String dlqTopic = originalTopic + ".DLQ";
                
                log.warn("Routing message to DLQ. Original topic: {}, DLQ topic: {}, Partition: {}, Offset: {}, Error: {}",
                    originalTopic, dlqTopic, consumerRecord.partition(), consumerRecord.offset(), exception.getMessage());
                
                // All DLQ topics use partition 0 for simplicity
                return new TopicPartition(dlqTopic, 0);
            });
    }

    // ========== ERROR HANDLING ==========

    /**
     * Create error handler that:
     * 1. Sends malformed messages to DLQ via DeadLetterPublishingRecoverer
     * 2. Commits the offset (don't retry malformed messages forever)
     * 3. Allows consumer to continue processing next messages
     * 
     * This prevents infinite retry loops for malformed/unparseable messages.
     */
    private DefaultErrorHandler createErrorHandler() {
        // Create recoverer that sends to DLQ
        DeadLetterPublishingRecoverer recoverer = deadLetterPublishingRecoverer(kafkaTemplate());
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            recoverer,
            new FixedBackOff(0L, 0L) // No retries - fail immediately and send to DLQ
        );
        
        // Don't retry these exception types - send directly to DLQ
        errorHandler.addNotRetryableExceptions(
            org.springframework.kafka.support.serializer.DeserializationException.class,
            org.springframework.messaging.converter.MessageConversionException.class,
            org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException.class,
            com.fasterxml.jackson.core.JsonProcessingException.class
        );
        
        return errorHandler;
    }
}
