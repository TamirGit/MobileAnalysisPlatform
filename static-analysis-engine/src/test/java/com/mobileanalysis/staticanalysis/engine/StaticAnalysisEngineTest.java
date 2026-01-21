package com.mobileanalysis.staticanalysis.engine;

import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.engine.service.HeartbeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class StaticAnalysisEngineTest {

    @Mock
    private HeartbeatService heartbeatService;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private StaticAnalysisEngine engine;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        engine = new StaticAnalysisEngine(heartbeatService, kafkaTemplate);
    }

    @Test
    void getEngineType_returnsStaticAnalysis() {
        // When/Then
        assertThat(engine.getEngineType()).isEqualTo("STATIC_ANALYSIS");
    }

    @Test
    void validateTask_apkFile_passes() throws IOException {
        // Given
        Path apkFile = tempDir.resolve("test.apk");
        Files.createFile(apkFile);

        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath(apkFile.toString())
            .build();

        // When/Then - should not throw
        engine.validateTask(event);
    }

    @Test
    void validateTask_ipaFile_passes() throws IOException {
        // Given
        Path ipaFile = tempDir.resolve("test.ipa");
        Files.createFile(ipaFile);

        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath(ipaFile.toString())
            .build();

        // When/Then - should not throw
        engine.validateTask(event);
    }

    @Test
    void validateTask_invalidFileType_throwsException() {
        // Given
        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath("/path/to/file.txt")
            .build();

        // When/Then
        assertThatThrownBy(() -> engine.validateTask(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Static analysis only supports APK and IPA files");
    }

    @Test
    void processTask_validApk_generatesOutput() throws Exception {
        // Given
        Path apkFile = tempDir.resolve("test.apk");
        Files.writeString(apkFile, "APK content");

        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath(apkFile.toString())
            .build();

        // When
        String outputPath = engine.processTask(event);

        // Then
        assertThat(outputPath).isNotNull();
        assertThat(outputPath).contains(event.getAnalysisId().toString());
        assertThat(outputPath).endsWith(".json");

        // Verify output file was created
        Path outputFile = Path.of(outputPath);
        assertThat(Files.exists(outputFile)).isTrue();
        assertThat(Files.readString(outputFile)).contains("Static analysis completed");
    }

    @Test
    void processTask_fileNotFound_throwsException() {
        // Given
        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath("/nonexistent/file.apk")
            .build();

        // When/Then
        assertThatThrownBy(() -> engine.processTask(event))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Input file not found");
    }

    @Test
    void processTask_emptyFile_processesSuccessfully() throws Exception {
        // Given
        Path apkFile = tempDir.resolve("empty.apk");
        Files.createFile(apkFile);

        TaskEvent event = TaskEvent.builder()
            .taskId(1L)
            .analysisId(UUID.randomUUID())
            .filePath(apkFile.toString())
            .build();

        // When
        String outputPath = engine.processTask(event);

        // Then
        assertThat(outputPath).isNotNull();
        assertThat(Files.exists(Path.of(outputPath))).isTrue();
    }
}
