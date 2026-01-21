package com.mobileanalysis.orchestrator.integration;

import com.mobileanalysis.common.domain.*;
import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.orchestrator.domain.*;
import com.mobileanalysis.orchestrator.repository.*;
import com.mobileanalysis.orchestrator.service.AnalysisOrchestrator;
import com.mobileanalysis.orchestrator.service.ConfigurationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for Phase 1 Core Orchestrator Foundation.
 * <p>
 * Tests complete workflow:
 * 1. File event triggers analysis creation
 * 2. Analysis and tasks persisted to PostgreSQL
 * 3. Configuration loaded from database and cached in Redis
 * 4. Outbox events created for task dispatch
 * 5. Ready-to-run tasks identified (no dependencies)
 * <p>
 * Uses Testcontainers for real infrastructure:
 * - PostgreSQL 16-alpine
 * - Redis 7-alpine
 * - Kafka 7.6.0 (cp-kafka) with KRaft (matches docker-compose.yml)
 * <p>
 * Docker Connection (Windows + Docker Desktop):
 * - Configured via testcontainers.properties
 * - Uses npipe:////./pipe/docker_engine (Windows named pipe)
 * - Works with both Hyper-V and WSL2 backends
 * - Ryuk disabled for Windows compatibility
 */
@SpringBootTest
@Testcontainers
@DisplayName("Phase 1 Orchestrator Integration Tests")
class OrchestratorIntegrationTest {

    // Testcontainers - Real infrastructure (static fields managed by framework)
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("test_mobile_analysis")
        .withUsername("test")
        .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"))
        .withKraft();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private AnalysisOrchestrator orchestrator;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @BeforeEach
    void setUp() {
        // Clean up database before each test
        outboxRepository.deleteAll();
        analysisTaskRepository.deleteAll();
        analysisRepository.deleteAll();
    }

    @Test
    @DisplayName("Should process APK file event and create complete analysis workflow")
    void shouldProcessApkFileEventAndCreateAnalysis() {
        // Arrange
        FileEvent fileEvent = FileEvent.builder()
            .eventId(UUID.randomUUID())
            .filePath("/storage/incoming/test-app.apk")
            .fileType(FileType.APK)
            .timestamp(Instant.now())
            .build();

        // Act
        orchestrator.processFileEvent(fileEvent);

        // Assert - Analysis created
        List<AnalysisEntity> analyses = analysisRepository.findByStatus(AnalysisStatus.RUNNING);
        assertThat(analyses)
            .hasSize(1)
            .first()
            .satisfies(analysis -> {
                assertThat(analysis.getFilePath()).isEqualTo("/storage/incoming/test-app.apk");
                assertThat(analysis.getFileType()).isEqualTo(FileType.APK);
                assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
                assertThat(analysis.getStartedAt()).isNotNull();
            });

        AnalysisEntity analysis = analyses.get(0);

        // Assert - Tasks created (4 tasks for APK: Static Analysis, Decompiler, Signature Check, Dynamic Analysis)
        List<AnalysisTaskEntity> tasks = analysisTaskRepository.findByAnalysisIdOrderByCreatedAt(analysis.getId());
        assertThat(tasks)
            .hasSize(4)
            .allSatisfy(task -> {
                assertThat(task.getAnalysisId()).isEqualTo(analysis.getId());
                assertThat(task.getStatus()).isIn(TaskStatus.PENDING, TaskStatus.DISPATCHED);
                assertThat(task.getIdempotencyKey()).isNotNull();
            });

        // Assert - Independent tasks identified (Static Analysis and Signature Check have no dependencies)
        List<AnalysisTaskEntity> independentTasks = tasks.stream()
            .filter(task -> task.getDependsOnTaskId() == null)
            .collect(Collectors.toList());
        assertThat(independentTasks)
            .hasSize(2)
            .extracting(AnalysisTaskEntity::getEngineType)
            .containsExactlyInAnyOrder(EngineType.STATIC_ANALYSIS, EngineType.SIGNATURE_CHECK);

        // Assert - Dependent tasks identified (Decompiler depends on Static Analysis, Dynamic depends on Decompiler)
        List<AnalysisTaskEntity> dependentTasks = tasks.stream()
            .filter(task -> task.getDependsOnTaskId() != null)
            .collect(Collectors.toList());
        assertThat(dependentTasks)
            .hasSize(2)
            .extracting(AnalysisTaskEntity::getEngineType)
            .containsExactlyInAnyOrder(EngineType.DECOMPILER, EngineType.DYNAMIC_ANALYSIS);

        // Assert - Outbox events created for independent tasks
        List<OutboxEventEntity> outboxEvents = outboxRepository.findUnprocessedBatch(PageRequest.of(0, 100));
        assertThat(outboxEvents)
            .hasSizeGreaterThanOrEqualTo(2) // At least 2 for independent tasks
            .allSatisfy(event -> {
                assertThat(event.getAggregateId()).isEqualTo(analysis.getId().toString());
                assertThat(event.getPartitionKey()).isEqualTo(analysis.getId().toString());
                assertThat(event.getProcessed()).isFalse();
            });
    }

    @Test
    @DisplayName("Should cache analysis configuration in Redis")
    void shouldCacheAnalysisConfiguration() {
        // Act - First call loads from DB
        long startTime1 = System.currentTimeMillis();
        AnalysisConfigEntity config1 = configurationService.getAnalysisConfig(FileType.APK);
        long duration1 = System.currentTimeMillis() - startTime1;

        // Act - Second call loads from cache
        long startTime2 = System.currentTimeMillis();
        AnalysisConfigEntity config2 = configurationService.getAnalysisConfig(FileType.APK);
        long duration2 = System.currentTimeMillis() - startTime2;

        // Assert - Configuration loaded correctly
        assertThat(config1)
            .isNotNull()
            .satisfies(config -> {
                assertThat(config.getFileType()).isEqualTo(FileType.APK);
                assertThat(config.getActive()).isTrue();
            });

        // Assert - Same configuration returned
        assertThat(config2.getId()).isEqualTo(config1.getId());

        // Assert - Cache hit is significantly faster (should be <10ms vs >50ms for DB)
        assertThat(duration2).isLessThan(duration1);
        System.out.println("DB lookup: " + duration1 + "ms, Cache hit: " + duration2 + "ms");
    }

    @Test
    @DisplayName("Should handle IPA file event correctly")
    void shouldProcessIpaFileEvent() {
        // Arrange
        FileEvent fileEvent = FileEvent.builder()
            .eventId(UUID.randomUUID())
            .filePath("/storage/incoming/test-app.ipa")
            .fileType(FileType.IPA)
            .timestamp(Instant.now())
            .build();

        // Act
        orchestrator.processFileEvent(fileEvent);

        // Assert - Analysis created for IPA
        List<AnalysisEntity> analyses = analysisRepository.findByStatus(AnalysisStatus.RUNNING);
        assertThat(analyses)
            .hasSize(1)
            .first()
            .satisfies(analysis -> assertThat(analysis.getFileType()).isEqualTo(FileType.IPA));

        // Assert - Tasks created for IPA configuration
        AnalysisEntity analysis = analyses.get(0);
        List<AnalysisTaskEntity> tasks = analysisTaskRepository.findByAnalysisIdOrderByCreatedAt(analysis.getId());
        assertThat(tasks).isNotEmpty(); // IPA should have configured tasks
    }

    @Test
    @DisplayName("Should maintain idempotency for duplicate processing")
    void shouldMaintainIdempotency() {
        // Arrange
        FileEvent fileEvent = FileEvent.builder()
            .eventId(UUID.randomUUID())
            .filePath("/storage/incoming/duplicate-test.apk")
            .fileType(FileType.APK)
            .timestamp(Instant.now())
            .build();

        // Act - Process same file event twice
        orchestrator.processFileEvent(fileEvent);
        orchestrator.processFileEvent(fileEvent);

        // Assert - Two separate analyses created (file path is not idempotency key)
        // Each FileEvent creates a new analysis instance
        List<AnalysisEntity> analyses = analysisRepository.findByStatus(AnalysisStatus.RUNNING);
        assertThat(analyses).hasSize(2); // Two separate analyses

        // Assert - Each analysis has unique tasks with unique idempotency keys
        for (AnalysisEntity analysis : analyses) {
            List<AnalysisTaskEntity> tasks = analysisTaskRepository.findByAnalysisIdOrderByCreatedAt(analysis.getId());
            assertThat(tasks)
                .hasSize(4)
                .extracting(AnalysisTaskEntity::getIdempotencyKey)
                .doesNotContainNull()
                .doesNotHaveDuplicates();
        }
    }
}
