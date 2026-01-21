package com.mobileanalysis.staticanalysis.messaging;

import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.staticanalysis.engine.StaticAnalysisEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for static analysis task events.
 * Delegates to StaticAnalysisEngine for processing.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskConsumer {
    
    private final StaticAnalysisEngine engine;
    
    @KafkaListener(
        topics = "${app.kafka.topics.static-analysis-tasks:static-analysis-tasks}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeTask(@Payload TaskEvent event, Acknowledgment acknowledgment) {
        log.info("Received static analysis task: taskId={}, analysisId={}", 
            event.getTaskId(), event.getAnalysisId());
        
        engine.handleTaskEvent(event, acknowledgment);
    }
}
