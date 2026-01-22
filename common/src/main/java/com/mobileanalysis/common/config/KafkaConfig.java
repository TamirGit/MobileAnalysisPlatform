package com.mobileanalysis.common.config;

import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
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

    // Consumer configuration for FileEvent (JSON deserialization)
    @Bean
    public Map<String, Object> fileEventConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
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
            new JsonDeserializer<>(FileEvent.class, false)
        );
    }

    @Bean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, FileEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, FileEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(fileEventConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // Manual commit
        return factory;
    }

    // Consumer configuration for TaskResponseEvent (JSON deserialization)
    @Bean
    public Map<String, Object> taskResponseConsumerConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for reliability
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
            new JsonDeserializer<>(TaskResponseEvent.class, false)
        );
    }

    @Bean(name = "taskResponseKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> taskResponseKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, TaskResponseEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(taskResponseConsumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // Manual commit
        return factory;
    }

    // Consumer configuration for String messages (for other use cases)
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
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL); // Manual commit
        return factory;
    }
}
