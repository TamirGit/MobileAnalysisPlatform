package com.mobileanalysis.engine.config;

import com.mobileanalysis.common.events.TaskEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka configuration for analysis engines.
 * Sets up:
 * - Consumer for TaskEvent messages with manual acknowledgment
 * - Producer (KafkaTemplate) for sending responses and heartbeats
 * - Scheduling support for HeartbeatService
 */
@Configuration
@EnableKafka
@EnableScheduling
public class EngineConfiguration {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    /**
     * Kafka consumer factory for TaskEvent messages.
     * Uses manual acknowledgment to ensure at-least-once processing.
     */
    @Bean
    public ConsumerFactory<String, TaskEvent> taskEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class.getName());
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, TaskEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.mobileanalysis.common.events");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual acknowledgment
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Kafka listener container factory for TaskEvent consumers.
     * Configured for manual acknowledgment.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TaskEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, TaskEvent> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, TaskEvent> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
    
    /**
     * Kafka producer factory for sending events (responses, heartbeats).
     */
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(org.apache.kafka.clients.producer.ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, 
            org.apache.kafka.common.serialization.StringSerializer.class);
        props.put(org.apache.kafka.clients.producer.ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, 
            org.springframework.kafka.support.serializer.JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }
    
    /**
     * KafkaTemplate for publishing events to Kafka.
     * Used by engines to send TaskResponseEvent and HeartbeatEvent.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}
