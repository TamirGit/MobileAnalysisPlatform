package com.mobileanalysis.decompiler;

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

@ExtendWith(MockitoExtension.class)
class DecompilerConsumerTest {

    @Mock
    private Decompiler decompiler;

    @InjectMocks
    private DecompilerConsumer consumer;

    @TempDir
    Path tempDir;

    private TaskEvent taskEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(consumer, "storagePath", tempDir.toString());

        taskEvent = TaskEvent.builder()
            .taskId(456L)
            .analysisId(UUID.randomUUID())
            .engineType("DECOMPILER")
            .filePath("/storage/test.apk")
            .idempotencyKey(UUID.randomUUID())
            .timeoutSeconds(900)
            .build();
    }

    @Test
    void processTask_success_returnsCompletedResponse() {
        Map<String, Object> decompileResults = new HashMap<>();
        decompileResults.put("status", "COMPLETED");
        decompileResults.put("classesDecompiled", 250);
        
        when(decompiler.decompile(anyString())).thenReturn(decompileResults);

        TaskResponseEvent response = consumer.processTask(taskEvent);

        assertThat(response).isNotNull();
        assertThat(response.getTaskId()).isEqualTo(456L);
        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.getOutputPath()).contains("task_456_output.json");
    }

    @Test
    void processTask_decompilationFailure_throwsException() {
        when(decompiler.decompile(anyString()))
            .thenThrow(new RuntimeException("Decompilation failed"));

        assertThatThrownBy(() -> consumer.processTask(taskEvent))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Decompilation failed");
    }
}
