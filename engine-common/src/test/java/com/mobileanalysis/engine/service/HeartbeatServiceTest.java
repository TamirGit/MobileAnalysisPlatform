package com.mobileanalysis.engine.service;

import com.mobileanalysis.common.events.HeartbeatEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Captor
    private ArgumentCaptor<HeartbeatEvent> eventCaptor;

    private HeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        heartbeatService = new HeartbeatService(kafkaTemplate);
        ReflectionTestUtils.setField(heartbeatService, "heartbeatTopic", "task-heartbeats");
    }

    @Test
    void startHeartbeat_setsCurrentTask() {
        // Given
        Long taskId = 1L;
        UUID analysisId = UUID.randomUUID();
        String engineType = "STATIC_ANALYSIS";

        // When
        heartbeatService.startHeartbeat(taskId, analysisId, engineType);

        // Then - verify heartbeat can be sent
        heartbeatService.sendHeartbeat();
        verify(kafkaTemplate).send(eq("task-heartbeats"), eq(analysisId.toString()), any(HeartbeatEvent.class));
    }

    @Test
    void stopHeartbeat_clearsCurrentTask() {
        // Given
        heartbeatService.startHeartbeat(1L, UUID.randomUUID(), "STATIC_ANALYSIS");

        // When
        heartbeatService.stopHeartbeat();

        // Then - no heartbeat should be sent
        heartbeatService.sendHeartbeat();
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void sendHeartbeat_noActiveTask_doesNotSend() {
        // When
        heartbeatService.sendHeartbeat();

        // Then
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void sendHeartbeat_activeTask_sendsHeartbeatEvent() {
        // Given
        Long taskId = 1L;
        UUID analysisId = UUID.randomUUID();
        String engineType = "STATIC_ANALYSIS";

        heartbeatService.startHeartbeat(taskId, analysisId, engineType);

        // When
        heartbeatService.sendHeartbeat();

        // Then
        verify(kafkaTemplate).send(
            eq("task-heartbeats"),
            eq(analysisId.toString()),
            eventCaptor.capture()
        );

        HeartbeatEvent event = eventCaptor.getValue();
        assertThat(event.getTaskId()).isEqualTo(taskId);
        assertThat(event.getAnalysisId()).isEqualTo(analysisId);
        assertThat(event.getEngineType()).isEqualTo(engineType);
        assertThat(event.getTimestamp()).isNotNull();
    }

    @Test
    void sendHeartbeat_kafkaFailure_doesNotThrow() {
        // Given
        heartbeatService.startHeartbeat(1L, UUID.randomUUID(), "STATIC_ANALYSIS");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Kafka error"));

        // When/Then - should not throw
        heartbeatService.sendHeartbeat();

        // Heartbeat remains active for next attempt
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void startHeartbeat_multipleTimes_replacesCurrentTask() {
        // Given
        UUID analysisId1 = UUID.randomUUID();
        UUID analysisId2 = UUID.randomUUID();

        // When
        heartbeatService.startHeartbeat(1L, analysisId1, "STATIC_ANALYSIS");
        heartbeatService.startHeartbeat(2L, analysisId2, "DYNAMIC_ANALYSIS");

        // Then - only sends heartbeat for second task
        heartbeatService.sendHeartbeat();

        verify(kafkaTemplate).send(
            eq("task-heartbeats"),
            eq(analysisId2.toString()),
            argThat(event -> ((HeartbeatEvent) event).getTaskId().equals(2L))
        );
    }
}
