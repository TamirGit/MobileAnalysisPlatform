package com.mobileanalysis.dynamicanalysis;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DynamicAnalysisConsumer.
 */
@ExtendWith(MockitoExtension.class)
class DynamicAnalysisConsumerTest {

    @Mock
    private DynamicAnalyzer analyzer;

    @InjectMocks
    private DynamicAnalysisConsumer consumer;

    @TempDir
    Path tempDir;

    private TaskEvent taskEvent;

    @BeforeEach
    void setUp() {
        // Set storage path to temp directory
        ReflectionTestUtils.setField(consumer, "storagePath", tempDir.toString());

        // Create test task event
        taskEvent = TaskEvent.builder()
            .taskId(123L)
            .analysisId(UUID.randomUUID())
            .engineType("DYNAMIC_ANALYSIS")
            .filePath("/storage/test.apk")
            .idempotencyKey(UUID.randomUUID())
            .timeoutSeconds(1800)
            .build();
    }

    @Test
    void processTask_success_returnsCompletedResponse() {
        // Given
        Map<String, Object> analysisResults = new HashMap<>();
        analysisResults.put("status", "COMPLETED");
        analysisResults.put("findings", "Test findings");
        
        when(analyzer.analyze(anyString())).thenReturn(analysisResults);

        // When
        TaskResponseEvent response = consumer.processTask(taskEvent);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo(123L);
        assertThat(response.getAnalysisId()).isEqualTo(taskEvent.getAnalysisId());
        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.getOutputPath()).isNotNull();
        assertThat(response.getOutputPath()).contains("task_123_output.json");
    }

    @Test
    void processTask_analysisFailure_throwsException() {
        // Given
        when(analyzer.analyze(anyString()))
            .thenThrow(new RuntimeException("Analysis failed"));

        // When/Then
        assertThatThrownBy(() -> consumer.processTask(taskEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Dynamic analysis failed");
    }

    @Test
    void processTask_writesResultsToFilesystem() {
        // Given
        Map<String, Object> analysisResults = new HashMap<>();
        analysisResults.put("networkConnections", 42);
        analysisResults.put("behaviorsDetected", 15);
        
        when(analyzer.analyze(anyString())).thenReturn(analysisResults);

        // When
        TaskResponseEvent response = consumer.processTask(taskEvent);

        // Then
        Path outputPath = Path.of(response.getOutputPath());
        assertThat(outputPath).exists();
        assertThat(outputPath.getFileName().toString()).isEqualTo("task_123_output.json");
    }

    @Test
    void processTask_interruptedException_throwsRuntimeException() {
        // Given
        when(analyzer.analyze(anyString()))
            .thenThrow(new RuntimeException("Dynamic analysis interrupted", 
                new InterruptedException("Interrupted")));

        // When/Then
        assertThatThrownBy(() -> consumer.processTask(taskEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Dynamic analysis failed");
    }
}
