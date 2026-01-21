package com.mobileanalysis.engine;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.engine.service.HeartbeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractAnalysisEngineTest {

    @Mock
    private HeartbeatService heartbeatService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private Acknowledgment acknowledgment;

    @Captor
    private ArgumentCaptor<TaskResponseEvent> responseCaptor;

    private TestEngine engine;
    private TaskEvent taskEvent;

    @BeforeEach
    void setUp() {
        engine = new TestEngine(heartbeatService, kafkaTemplate);

        taskEvent = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath("/path/to/file.apk")
            .engineType("TEST_ENGINE")
            .idempotencyKey(UUID.randomUUID())
            .timeoutSeconds(300)
            .build();
    }

    @Test
    void handleTaskEvent_success_sendsSuccessResponse() throws Exception {
        // Given
        engine.setProcessResult("/path/to/output.json");

        // When
        engine.handleTaskEvent(taskEvent, acknowledgment);

        // Then
        verify(heartbeatService).startHeartbeat(1L, taskEvent.getAnalysisId(), "TEST_ENGINE");
        verify(heartbeatService).stopHeartbeat();

        verify(kafkaTemplate).send(
            eq("orchestrator-responses"),
            eq(taskEvent.getAnalysisId().toString()),
            responseCaptor.capture()
        );

        TaskResponseEvent response = responseCaptor.getValue();
        assertThat(response.getTaskId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.getOutputPath()).isEqualTo("/path/to/output.json");
        assertThat(response.getErrorMessage()).isNull();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskEvent_processingFailure_sendsFailureResponse() {
        // Given
        engine.setProcessException(new RuntimeException("Processing error"));

        // When
        engine.handleTaskEvent(taskEvent, acknowledgment);

        // Then
        verify(heartbeatService).stopHeartbeat();

        verify(kafkaTemplate).send(
            eq("orchestrator-responses"),
            anyString(),
            responseCaptor.capture()
        );

        TaskResponseEvent response = responseCaptor.getValue();
        assertThat(response.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(response.getErrorMessage()).contains("Processing error");
        assertThat(response.getOutputPath()).isNull();

        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskEvent_validationFailure_doesNotAcknowledge() {
        // Given
        taskEvent = TaskEvent.builder()
            .taskId(1L)
            .analysisId(null) // Invalid: null analysisId
            .filePath("/path/to/file.apk")
            .build();

        // When/Then
        assertThatThrownBy(() -> engine.handleTaskEvent(taskEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleTaskEvent_responseFailure_doesNotAcknowledge() throws Exception {
        // Given
        engine.setProcessResult("/output.json");
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("Kafka error"));

        // When/Then
        assertThatThrownBy(() -> engine.handleTaskEvent(taskEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void validateTask_missingRequiredFields_throwsException() {
        // Given
        TaskEvent invalidEvent = TaskEvent.builder()
            .taskId(null) // Missing task ID
            .analysisId(UUID.randomUUID())
            .filePath("/path/to/file.apk")
            .build();

        // When/Then
        assertThatThrownBy(() -> engine.handleTaskEvent(invalidEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class);
    }

    // Test implementation of AbstractAnalysisEngine
    private static class TestEngine extends AbstractAnalysisEngine {
        private String processResult;
        private Exception processException;

        public TestEngine(HeartbeatService heartbeatService, KafkaTemplate<String, Object> kafkaTemplate) {
            super(heartbeatService, kafkaTemplate);
        }

        public void setProcessResult(String result) {
            this.processResult = result;
        }

        public void setProcessException(Exception exception) {
            this.processException = exception;
        }

        @Override
        protected String processTask(TaskEvent event) throws Exception {
            if (processException != null) {
                throw processException;
            }
            return processResult;
        }

        @Override
        protected String getEngineType() {
            return "TEST_ENGINE";
        }
    }
}
