# 📋 FEATURE PLAN: Complete Phase 1 Core Orchestrator Foundation

## Feature Description

Complete the Mobile Analysis Platform Phase 1 by implementing the missing core services, repositories, and messaging components that enable the end-to-end workflow: FileEvent ingestion → Analysis creation with tasks → Outbox-based event publishing → Engine task dispatch.

## User Story

As a **Development Team**,
I want to **complete the Phase 1 core orchestrator implementation**,
So that **the system can ingest file analysis requests and orchestrate multi-stage analysis workflows with fault tolerance and automatic retry mechanisms**.

## Problem Statement

The Phase 1 foundation is 70% complete with database schemas, domain models, and infrastructure. However, the critical business logic layer (repositories, services, messaging consumers, and scheduled outbox processor) remains unimplemented. Without these components, the system cannot:
- Accept and process file analysis requests
- Create analysis records with all required tasks
- Manage task dependencies and ready-state detection
- Publish events to analysis engines
- Achieve end-to-end workflow orchestration

## Solution Statement

Implement 5 key components in dependency order:
1. **JPA Repositories** - Data access layer with specialized queries
2. **Shared Configurations** - Kafka manual commit setup and Redis serialization
3. **Core Services** - Business logic (ConfigurationService + AnalysisOrchestrator)
4. **Messaging Layer** - Event consumer and scheduled outbox poller
5. **Integration Tests** - End-to-end validation with Testcontainers

Each component follows enterprise patterns documented in CLAUDE.md: transactional outbox, idempotency, manual Kafka commits, DB-first/Redis-second pattern, and correlation ID tracing.

## Feature Metadata

**Feature Type**: Enhancement (complete scaffold into working system)  
**Estimated Complexity**: Medium (5-7 hours, straightforward patterns)  
**Primary Systems Affected**:
- common/config/ (new shared configs)
- orchestrator-service/domain/ (use existing JPA entities)
- orchestrator-service/repository/ (new JPA repositories)
- orchestrator-service/service/ (new business logic)
- orchestrator-service/messaging/ (new Kafka integration)
- orchestrator-service/outbox/ (new scheduled processor)
- orchestrator-service/src/test/ (new integration tests)

**Dependencies**: 
- PostgreSQL 16, Redis 7, Kafka 3.8 (via Docker Compose - already running)
- Spring Boot 3.3.0 (already configured)
- Testcontainers (already in pom.xml)

---

## CONTEXT REFERENCES

### Mandatory Codebase Files to Read BEFORE Implementation

**Database & Schema**:
- `orchestrator-service/src/main/resources/db/migration/V001__create_config_tables.sql` - Config table structure with sample data
- `orchestrator-service/src/main/resources/db/migration/V002__create_runtime_tables.sql` - Runtime tables with indexes
- `orchestrator-service/src/main/resources/db/migration/V003__create_outbox_table.sql` - Outbox schema

**Existing JPA Entities** (reference for patterns):
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/AnalysisEntity.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/AnalysisTaskEntity.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/OutboxEventEntity.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/AnalysisConfigEntity.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/TaskConfigEntity.java`

**Reference Implementations**:
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisRepository.java` - Existing repository pattern to mirror
- `CLAUDE.md` (lines 200-400) - Kafka consumer/producer patterns with manual commit
- `CLAUDE.md` (lines 400-500) - Service patterns and @Transactional usage
- `CLAUDE.md` (lines 500-600) - Outbox pattern implementation
- `CLAUDE.md` (lines 900-1100) - Testing patterns with Testcontainers

**Configuration Reference**:
- `orchestrator-service/src/main/resources/application.yml` - All settings needed for Kafka/Redis
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/OrchestratorServiceApplication.java` - Main application class

**Events & DTOs**:
- `common/src/main/java/com/mobileanalysis/common/events/FileEvent.java` - Input event structure
- `common/src/main/java/com/mobileanalysis/common/events/TaskEvent.java` - Output event structure

### New Files to Create

**Repositories** (4 files, ~10 min):
- `common/src/main/java/com/mobileanalysis/common/repository/BaseRepository.java` - Optional base interface (if pattern calls for it)
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisTaskRepository.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisConfigRepository.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/TaskConfigRepository.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/OutboxRepository.java`

**Shared Config** (2 files, ~15 min):
- `common/src/main/java/com/mobileanalysis/common/config/KafkaConfig.java` - KafkaTemplate + listener factory
- `common/src/main/java/com/mobileanalysis/common/config/RedisConfig.java` - RedisTemplate + JSON serialization

**Core Services** (3 files, ~45 min):
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/ConfigurationService.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/AnalysisOrchestrator.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/TaskDependencyResolver.java` (helper)

**Messaging** (2 files, ~30 min):
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/FileEventConsumer.java`
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/outbox/OutboxPoller.java`

**Tests** (1 file, ~30 min):
- `orchestrator-service/src/test/java/com/mobileanalysis/orchestrator/integration/AnalysisOrchestratorIntegrationTest.java`

### Relevant Documentation YOU MUST READ

- [Spring Data JPA - Query Methods](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/#jpa.query-methods)
  - Specific section: Derived query methods and @Query annotation
  - Why: Building repository queries for idempotency, dependencies, etc.

- [Spring Kafka Manual Offset Management](https://docs.spring.io/spring-kafka/docs/current/reference/html/#committing-offsets)
  - Specific section: Manual acknowledgment pattern
  - Why: Critical for exactly-once semantics with database transactions

- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
  - Full article (5-10 min read)
  - Why: Understanding reliability and event ordering guarantees

- [Spring @Scheduled Documentation](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Scheduled.html)
  - Specific section: fixedDelay parameter and scheduling behavior
  - Why: Implementing outbox poller with fixed delay polling

- [PostgreSQL DISTINCT ON](https://www.postgresql.org/docs/current/sql-select.html#SQL-DISTINCT)
  - Specific section: DISTINCT ON for efficient batch queries
  - Why: Optimizing unprocessed event batch fetching

### Patterns to Follow

**Query Method Naming** (from Spring Data JPA):
```java
// Pattern: findBy{Property}[Operator]
List<AnalysisTask> findByAnalysisIdAndStatus(UUID analysisId, TaskStatus status);
List<AnalysisTask> findByIdempotencyKeyAndStatus(String idempotencyKey, TaskStatus status);
List<OutboxEvent> findByProcessedFalseOrderByCreatedAtAsc(Pageable pageable);

// Pattern: Custom @Query when derived is insufficient
@Query("SELECT t FROM AnalysisTask t WHERE t.lastHeartbeatAt < ?1 AND t.status = 'RUNNING'")
List<AnalysisTask> findStaleHeartbeats(Instant threshold);
```

**Service Method Pattern** (from CLAUDE.md):
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalysisOrchestrator {
    private final AnalysisRepository analysisRepository;
    private final AnalysisTaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    
    @Transactional  // CRITICAL: DB and outbox in single transaction
    public UUID createAnalysis(FileEvent fileEvent) {
        String correlationId = fileEvent.getAnalysisId();
        MDC.put("correlationId", correlationId);
        
        try {
            // Implementation
            log.info("Analysis created. correlationId={}", correlationId);
            return analysisId;
        } catch (Exception e) {
            log.error("Analysis creation failed. correlationId={}", correlationId, e);
            throw new AnalysisCreationException(correlationId, e);
        } finally {
            MDC.clear();
        }
    }
}
```

**Kafka Listener Pattern** (from CLAUDE.md):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class FileEventConsumer {
    private final AnalysisOrchestrator orchestrator;
    
    @KafkaListener(
        topics = "${app.kafka.topics.file-events}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"  // Manual commit factory
    )
    public void consume(FileEvent event, Acknowledgment acknowledgment) {
        String correlationId = event.getAnalysisId();
        MDC.put("correlationId", correlationId);
        
        try {
            orchestrator.createAnalysis(event);
            acknowledgment.acknowledge();  // Commit AFTER success
        } catch (Exception e) {
            log.error("Processing failed. correlationId={}", correlationId, e);
            // No acknowledge - Kafka will redeliver
        } finally {
            MDC.clear();
        }
    }
}
```

**Scheduled Outbox Poller Pattern** (from CLAUDE.md):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void pollOutbox() {
        List<OutboxEvent> events = outboxRepository.findUnprocessedBatch(50);
        
        for (OutboxEvent event : events) {
            MDC.put("correlationId", event.getPartitionKey());
            
            try {
                ProducerRecord<String, String> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getPartitionKey(),  // analysisId for ordering
                    event.getPayload()
                );
                kafkaTemplate.send(record).get();  // Synchronous send
                
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                outboxRepository.save(event);
                
                log.debug("Event published. eventId={}, topic={}", event.getId(), event.getTopic());
            } catch (Exception e) {
                log.warn("Failed to publish event, will retry. eventId={}", event.getId(), e);
                // Leave processed=false, will retry on next poll
            } finally {
                MDC.clear();
            }
        }
    }
}
```

**Redis Template Pattern** (for ConfigurationService caching):
```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {
    private final AnalysisConfigRepository configRepository;
    private final RedisTemplate<String, AnalysisConfig> redisTemplate;
    private static final int CACHE_TTL_SECONDS = 3600;
    
    public AnalysisConfig getConfiguration(FileType fileType) {
        String cacheKey = "analysis-config:" + fileType;
        
        // Try cache first
        AnalysisConfig cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Config retrieved from cache. fileType={}", fileType);
            return cached;
        }
        
        // Cache miss - read from DB
        AnalysisConfig config = configRepository.findByFileType(fileType)
            .orElseThrow(() -> new ConfigurationNotFoundException(fileType));
        
        // Populate cache (best-effort)
        try {
            redisTemplate.opsForValue().set(cacheKey, config, Duration.ofSeconds(CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("Cache population failed. fileType={}", fileType, e);
            // Continue - DB is source of truth
        }
        
        return config;
    }
}
```

**Testing Pattern with Testcontainers** (from CLAUDE.md):
```java
@SpringBootTest
@Testcontainers
class AnalysisOrchestratorIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
        .withDatabaseName("test_db");
    
    @Container
    static KafkaContainer kafka = new KafkaContainer(
        DockerImageName.parse("confluentinc/cp-kafka:7.6.0")
    );
    
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7")
        .withExposedPorts(6379);
    
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    }
    
    @Test
    void shouldCreateAnalysisWithAllTasks_whenValidFileEvent() {
        // Arrange
        FileEvent event = FileEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .analysisId(UUID.randomUUID().toString())
            .filePath("/storage/test.apk")
            .fileType("APK")
            .timestamp(Instant.now())
            .build();
        
        // Act
        UUID analysisId = orchestrator.createAnalysis(event);
        
        // Assert
        assertThat(analysisId).isNotNull();
        AnalysisEntity analysis = analysisRepository.findById(analysisId).orElseThrow();
        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.RUNNING);
        assertThat(analysis.getTasks()).hasSize(4);  // APK has 4 tasks
        
        List<OutboxEvent> outboxEvents = outboxRepository.findAll();
        assertThat(outboxEvents).hasSize(2);  // 2 tasks without dependencies
    }
}
```

---

## IMPLEMENTATION PLAN

### Phase 1: Foundation - Repositories

Implement JPA repository interfaces with specialized query methods. These are the data access layer foundation.

**Deliverables**: 5 repository interfaces with all required query methods

### Phase 2: Infrastructure - Shared Configurations

Implement Kafka and Redis configurations with proper Spring integration. These enable messaging and caching.

**Deliverables**: KafkaConfig with manual commit, RedisConfig with JSON serialization

### Phase 3: Business Logic - Core Services

Implement the orchestration logic and configuration management. These form the heart of Phase 1.

**Deliverables**: ConfigurationService with caching, AnalysisOrchestrator with createAnalysis method

### Phase 4: Messaging Layer - Event Consumers & Publishers

Implement Kafka consumer and scheduled outbox poller. These connect the workflow.

**Deliverables**: FileEventConsumer, OutboxPoller (working end-to-end)

### Phase 5: Testing & Validation

Implement comprehensive integration tests. These validate the entire workflow.

**Deliverables**: Integration test with 100% pass rate, manual testing verified

---

## STEP-BY-STEP TASKS

### TASK 1: CREATE AnalysisTaskRepository.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisTaskRepository.java`

**IMPLEMENT**: JPA repository with specialized queries for:
- Finding tasks by analysis ID and status (for ready-state detection)
- Finding tasks by idempotency key (for duplicate detection)
- Finding tasks with no dependencies (for initial dispatch)
- Finding stale heartbeats (for health monitoring)

**PATTERN**: Mirror from `AnalysisRepository.java` (extends JpaRepository, uses @Query for complex queries)

**IMPORTS**:
```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
```

**GOTCHA**: 
- Use `@Query` with JPQL (JPA QL, not native SQL) to stay portable
- Remember `Long` for task ID vs `UUID` for analysis ID
- `analysisId` is UUID, not String

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
# Check for compilation errors in repository
```

---

### TASK 2: CREATE AnalysisConfigRepository.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisConfigRepository.java`

**IMPLEMENT**: JPA repository for loading analysis configurations by file type

**PATTERN**: Mirror from `AnalysisRepository` pattern

**KEY QUERY**: `findByFileType(FileType fileType)` returning Optional

**IMPORTS**: Same as Task 1

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 3: CREATE TaskConfigRepository.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/TaskConfigRepository.java`

**IMPLEMENT**: JPA repository for loading task configs under an analysis config

**PATTERN**: Mirror pattern

**KEY QUERY**: `findByAnalysisConfigId(Long analysisConfigId)` returning List

**IMPORTS**: Same as Task 1

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 4: CREATE OutboxRepository.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/OutboxRepository.java`

**IMPLEMENT**: JPA repository for transactional outbox pattern

**CRITICAL QUERIES**:
- `findUnprocessedBatch(int batchSize)` - Load 50 unprocessed events ordered by creation time
- `findByProcessedFalseOrderByCreatedAtAscLimitN(int n)` using Pageable

**PATTERN**: Reference from CLAUDE.md outbox pattern section

**KEY DETAIL**: Use `Pageable` for batch limiting, order by `createdAt` ASC for FIFO

**IMPORTS**: 
```java
import org.springframework.data.domain.Pageable;
// plus standard imports
```

**GOTCHA**: 
- Outbox must be processed in FIFO order for event ordering
- Use `LIMIT` via Pageable, not SQL LIMIT string

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 5: CREATE KafkaConfig.java

**File**: `common/src/main/java/com/mobileanalysis/common/config/KafkaConfig.java`

**IMPLEMENT**: Spring Kafka configuration with:
- KafkaTemplate bean for producers
- ConcurrentKafkaListenerContainerFactory with manual commit enabled
- Consumer configuration for manual offset management

**PATTERN**: Reference CLAUDE.md lines 200-300 for exact patterns

**CRITICAL DETAIL**: `enable-auto-commit: false` and `AckMode.MANUAL` are MANDATORY for exactly-once semantics

**IMPORTS**:
```java
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
// etc.
```

**GOTCHA**: 
- Spring auto-configures KafkaTemplate, but we need custom container factory
- Container factory must be named `"kafkaListenerContainerFactory"` (default name)
- Manual commit requires `AckMode.MANUAL` and `enable-auto-commit: false` in app config

**VALIDATE**:
```bash
./mvnw compile -pl common
```

---

### TASK 6: CREATE RedisConfig.java

**File**: `common/src/main/java/com/mobileanalysis/common/config/RedisConfig.java`

**IMPLEMENT**: Spring Data Redis configuration with:
- RedisTemplate bean with Jackson2 JSON serialization
- Proper serialization for AnalysisConfig and other domain objects

**PATTERN**: Standard Spring Redis template configuration

**IMPORTS**:
```java
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serialization.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serialization.StringRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
```

**GOTCHA**: 
- Default RedisTemplate uses JDK serialization (binary, not human-readable)
- Must explicitly set Jackson2JsonRedisSerializer for JSON format
- Key serializer should be StringRedisSerializer for readable keys

**VALIDATE**:
```bash
./mvnw compile -pl common
```

---

### TASK 7: CREATE ConfigurationService.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/ConfigurationService.java`

**IMPLEMENT**: Service for loading analysis configurations with Redis caching
- Method: `getConfiguration(FileType fileType)` → AnalysisConfig
- Try Redis cache first
- On miss, query database
- On database hit, populate cache (best-effort, don't block on failure)

**PATTERN**: CLAUDE.md lines 400-450 "State Management Pattern (DB-First, Redis-Second)"

**KEY METHODS**:
- `public AnalysisConfig getConfiguration(FileType fileType)` - Load config with caching
- `public List<TaskConfig> getTasksForConfig(AnalysisConfig config)` - Get associated tasks

**IMPORTS**:
```java
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
```

**GOTCHA**: 
- Redis failures must NOT block analysis creation
- Log warnings on cache failures, but continue with DB value
- Cache TTL should be configurable (use application.yml `app.cache.ttl-seconds`)

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
# Test compilation only at this stage
```

---

### TASK 8: CREATE TaskDependencyResolver.java (Helper)

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/TaskDependencyResolver.java`

**IMPLEMENT**: Helper class for resolving task dependencies from config-level to runtime-level
- Input: AnalysisConfig (template) + created AnalysisTask records
- Output: Mapping of which tasks depend on which other tasks
- Identify ready tasks (no dependencies)

**PATTERN**: Domain logic helper, can be static utility or service dependency

**KEY METHOD**:
```java
public Set<Long> getReadyTasks(List<AnalysisTask> allTasks)
public Map<Long, Long> getDependencies(List<AnalysisTask> allTasks)
```

**IMPORTS**: Standard Java collections

**GOTCHA**: 
- Config defines dependencies as task config references
- Runtime must map these to actual task IDs
- Don't add circular dependency checks (config should validate this)

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 9: CREATE AnalysisOrchestrator.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/AnalysisOrchestrator.java`

**IMPLEMENT**: Core orchestration service with `createAnalysis` method implementing the full workflow:

1. Load configuration via ConfigurationService
2. Create AnalysisEntity with PENDING status
3. Create AnalysisTaskEntity records for all tasks
4. Resolve dependencies (config-level → runtime-level)
5. Identify ready tasks (no dependencies)
6. Create OutboxEvent records for ready tasks
7. Update Analysis status to RUNNING
8. Return analysis ID

**PATTERN**: CLAUDE.md lines 400-500 "Transactional Outbox Pattern"

**CRITICAL**: Everything in single `@Transactional` method

**IMPORTS**:
```java
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
```

**METHOD SIGNATURE**:
```java
@Transactional
public UUID createAnalysis(FileEvent fileEvent)
```

**GOTCHA**: 
- Must NOT call kafkaTemplate directly (use Outbox)
- Must handle idempotency (check idempotencyKey before creating)
- MDC setup/clear is essential for correlation ID tracing
- analysisId from FileEvent becomes analysisId in all records

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 10: CREATE FileEventConsumer.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/FileEventConsumer.java`

**IMPLEMENT**: Kafka consumer for file-events topic
- Listen to configured topic (from application.yml)
- Parse FileEvent
- Delegate to AnalysisOrchestrator.createAnalysis()
- Manual commit AFTER database transaction succeeds
- Don't commit on exception (let Kafka redeliver)
- MDC setup/clear for correlation IDs

**PATTERN**: CLAUDE.md lines 150-180 "Kafka Consumers with Manual Commit"

**IMPORTS**:
```java
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
```

**CRITICAL DETAILS**:
- `@KafkaListener` must reference `containerFactory = "kafkaListenerContainerFactory"` (manual commit factory)
- topics and groupId from `application.yml` properties
- Acknowledgment.acknowledge() called AFTER successful orchestrator call
- No acknowledge on exception - exception is re-thrown implicitly

**GOTCHA**: 
- FileEvent.getAnalysisId() is the correlation ID (must be UUID string)
- Don't swallow exceptions - let Spring Kafka handle retries
- MDC.clear() in finally block

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 11: CREATE OutboxPoller.java

**File**: `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/outbox/OutboxPoller.java`

**IMPLEMENT**: Scheduled component that polls outbox and publishes to Kafka
- `@Scheduled` with fixed delay (from application.yml `app.outbox.poll-interval-ms`)
- Query 50 unprocessed events
- For each event:
  - Create ProducerRecord with topic and partitionKey from outbox
  - Send synchronously to Kafka
  - Mark as processed with timestamp
  - Save back to DB
- On failure for individual event, log warning and continue (don't block whole batch)

**PATTERN**: CLAUDE.md lines 450-500 "Outbox Poller"

**IMPORTS**:
```java
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import java.time.Instant;
```

**CRITICAL DETAILS**:
- MDC.put("correlationId", event.getPartitionKey()) - partitionKey IS analysisId
- kafkaTemplate.send(record).get() - synchronous send (blocks until confirmed)
- Save event with processed=true and processedAt=Instant.now()
- On exception, log warn and continue (don't save, will retry)

**GOTCHA**: 
- Synchronous send (.get()) ensures Kafka confirms before marking processed
- If Kafka send fails, event stays unprocessed and will retry next poll
- partitionKey must be analysisId (String) from outbox record

**VALIDATE**:
```bash
./mvnw compile -pl orchestrator-service
```

---

### TASK 12: CREATE AnalysisOrchestratorIntegrationTest.java

**File**: `orchestrator-service/src/test/java/com/mobileanalysis/orchestrator/integration/AnalysisOrchestratorIntegrationTest.java`

**IMPLEMENT**: Integration test with Testcontainers validating end-to-end workflow
- Setup PostgreSQL, Kafka, Redis containers
- Test: FileEvent input → Analysis created → Tasks created → Outbox populated
- Test: Ready tasks identified correctly
- Test: Outbox poller publishes to engine topic
- Assert: Kafka message received with correct topic and partition key

**PATTERN**: CLAUDE.md lines 1100-1150 "Testcontainers Setup"

**KEY TESTS**:
1. `shouldCreateAnalysisWithAllTasks_whenValidFileEvent()`
2. `shouldDispatchReadyTasks_toOutbox()`
3. `shouldPublishOutboxEventsToKafka()`
4. `shouldHandleIdempotentEvents()`

**IMPORTS**:
```java
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
```

**GOTCHA**: 
- DynamicPropertySource must be static method
- Containers start automatically with @Container
- Test needs to wait for async outbox poller (use Thread.sleep or CountDownLatch)
- Kafka consumer in test needs small poll timeout

**VALIDATE**:
```bash
./mvnw verify -pl orchestrator-service
# Integration tests will run and should pass
```

---

### TASK 13: UPDATE pom.xml (if needed)

**File**: `pom.xml` (root) and `orchestrator-service/pom.xml`

**VERIFY**: All required dependencies are present
- spring-kafka
- spring-data-redis
- testcontainers
- testcontainers-kafka

**PATTERN**: Check existing pom.xml structure

**ACTION**: Only add if missing. Likely already present from scaffolding.

**VALIDATE**:
```bash
./mvnw dependency:tree | grep kafka
./mvnw dependency:tree | grep redis
```

---

### TASK 14: VERIFY application.yml Configuration

**File**: `orchestrator-service/src/main/resources/application.yml`

**VERIFY**: All required properties present:
```yaml
spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      enable-auto-commit: false  # CRITICAL
      group-id: ${KAFKA_GROUP_ID:orchestrator-group}
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  kafka:
    topics:
      file-events: file-events
      static-analysis-tasks: static-analysis-tasks
      dynamic-analysis-tasks: dynamic-analysis-tasks
      decompiler-tasks: decompiler-tasks
      signature-check-tasks: signature-check-tasks
  outbox:
    poll-interval-ms: 1000
    batch-size: 50
  cache:
    ttl-seconds: 3600
```

**GOTCHA**: 
- `enable-auto-commit: false` must be false
- topic names must match Kafka auto-created topics in docker-compose.yml

**VALIDATE**:
```bash
grep "enable-auto-commit" orchestrator-service/src/main/resources/application.yml
# Should show: false
```

---

### TASK 15: FULL BUILD & COMPILE CHECK

**ACTION**: Clean build to ensure all code compiles

**VALIDATE**:
```bash
./mvnw clean install -DskipTests
# Should complete with BUILD SUCCESS
```

---

### TASK 16: START DOCKER INFRASTRUCTURE

**ACTION**: Ensure Docker Compose is running for integration tests

**VALIDATE**:
```bash
docker-compose up -d
docker-compose ps
# All services should show "running" and "healthy"
```

---

### TASK 17: RUN INTEGRATION TESTS

**ACTION**: Execute integration tests with Testcontainers

**VALIDATE**:
```bash
./mvnw verify -pl orchestrator-service
# All tests should pass:
# - AnalysisOrchestratorIntegrationTest.shouldCreateAnalysisWithAllTasks_whenValidFileEvent
# - Other test cases defined above
```

---

### TASK 18: RUN ORCHESTRATOR SERVICE

**ACTION**: Start orchestrator service locally

**VALIDATE**:
```bash
./mvnw spring-boot:run -pl orchestrator-service
# Service should start without errors
# Logs should show:
# - Flyway migrations applied
# - Kafka broker connected
# - Redis connected
# - OutboxPoller @Scheduled started
```

---

### TASK 19: MANUAL TEST - SEND FILE EVENT

**ACTION**: Publish FileEvent to Kafka and verify workflow

**VALIDATE**:
```bash
# In another terminal, send FileEvent:
docker exec -it $(docker ps | grep kafka | awk '{print $1}') \
  kafka-console-producer --bootstrap-server localhost:9092 --topic file-events

# Paste this JSON (then Ctrl+D):
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "analysisId": "550e8400-e29b-41d4-a716-446655440001",
  "filePath": "/storage/incoming/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-20T20:30:00Z"
}

# In orchestrator logs, should see:
# - "Analysis created. correlationId=550e8400-e29b-41d4-a716-446655440001"
# - "Analysis tasks created" with task count
```

---

### TASK 20: MANUAL TEST - VERIFY DATABASE

**ACTION**: Query PostgreSQL to verify analysis and tasks created

**VALIDATE**:
```bash
psql -h localhost -U postgres -d mobile_analysis

# Check analysis created:
SELECT id, file_type, status, started_at FROM analysis;
# Should show 1 record with status='RUNNING'

# Check tasks created:
SELECT id, analysis_id, engine_type, status, depends_on_task_id FROM analysis_task;
# Should show 4 records (APK has 4 tasks)

# Check outbox populated:
SELECT id, event_type, topic, processed FROM outbox;
# Should show 2 records (2 tasks without dependencies)

# Exit:
\q
```

---

### TASK 21: MANUAL TEST - VERIFY KAFKA OUTPUT

**ACTION**: Consume from engine task topics to verify outbox poller published events

**VALIDATE**:
```bash
# Wait ~2 seconds for outbox poller to run (fixed delay 1s)

# Check static-analysis-tasks topic:
docker exec -it $(docker ps | grep kafka | awk '{print $1}') \
  kafka-console-consumer --bootstrap-server localhost:9092 \
  --topic static-analysis-tasks --from-beginning --max-messages 1

# Should see TaskEvent JSON with analysisId matching the file event

# Check topic partition:
docker exec -it $(docker ps | grep kafka | awk '{print $1}') \
  kafka-topics --bootstrap-server localhost:9092 --describe --topic static-analysis-tasks
# Should have 4 partitions (matches config)
```

---

### TASK 22: VERIFICATION - LOGS WITH CORRELATION IDS

**ACTION**: Verify logs contain correlation IDs throughout workflow

**VALIDATE**:
```bash
# In orchestrator service logs, grep for correlation ID:
grep -i "correlationId\|550e8400" orchestrator-service.log
# Should show pattern like:
# [correlationId=550e8400-e29b-41d4-a716-446655440001] Analysis created...
# [correlationId=550e8400-e29b-41d4-a716-446655440001] Analysis tasks created...
```

---

## TESTING STRATEGY

### Unit Tests

**Scope**: ConfigurationService, TaskDependencyResolver, domain logic in AnalysisOrchestrator

**Pattern**: Mock repositories, test business logic in isolation

**Framework**: JUnit 5 + Mockito

**Examples**:
- ConfigurationService: Test cache hit/miss scenarios, fallback to DB
- TaskDependencyResolver: Test dependency mapping correctness
- AnalysisOrchestrator: Test transaction behavior (mock repositories)

### Integration Tests

**Scope**: Full workflow with Testcontainers (PostgreSQL, Redis, Kafka)

**Pattern**: Real Spring context, real containers, verify end-to-end

**Tests**:
- FileEvent consumed → Analysis created with all tasks
- Task dependencies resolved correctly
- Ready tasks written to outbox
- Outbox poller publishes to Kafka
- Idempotent event handling (duplicate events create no duplicates)

### Edge Cases

- Duplicate FileEvents (same eventId/analysisId)
- Missing configuration for file type
- Kafka publisher failure (outbox retry)
- Redis cache failure (fallback to DB)
- Task dependency cycles (config should prevent, test assumes valid config)
- Large number of tasks (verify batch processing)

---

## VALIDATION COMMANDS

Execute every command in sequence. All must pass with zero errors.

### Level 1: Syntax & Compilation

```bash
# Compile all modules
./mvnw clean compile

# Check for compilation errors
echo "✓ Compilation check passed"
```

### Level 2: Code Style & Dependencies

```bash
# Verify no missing dependencies
./mvnw dependency:tree | head -50

# Verify required Kafka dependency
./mvnw dependency:tree | grep "org.springframework.kafka" | head -3

# Verify required Redis dependency
./mvnw dependency:tree | grep "org.springframework.data:spring-data-redis" | head -3

echo "✓ Dependencies verified"
```

### Level 3: Build with Tests

```bash
# Build and run all tests
./mvnw clean install

# Check test summary
echo "✓ Full build with tests passed"
```

### Level 4: Integration Tests Only

```bash
# Ensure Docker Compose running
docker-compose ps

# Run integration tests with real containers
./mvnw verify -pl orchestrator-service -Dgroups=integration

echo "✓ Integration tests passed"
```

### Level 5: Service Startup Validation

```bash
# Start orchestrator service in background
timeout 30 ./mvnw spring-boot:run -pl orchestrator-service &
SERVICE_PID=$!

# Wait for startup
sleep 10

# Check if running
if ps -p $SERVICE_PID > /dev/null; then
  echo "✓ Orchestrator service started successfully"
  kill $SERVICE_PID
else
  echo "✗ Service failed to start"
  exit 1
fi
```

### Level 6: Manual Workflow Validation

```bash
# Start Docker infrastructure
docker-compose up -d

# Start orchestrator service (keep running in separate terminal)
./mvnw spring-boot:run -pl orchestrator-service &

# Send test FileEvent
docker exec mobile-analysis-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic file-events <<EOF
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "analysisId": "550e8400-e29b-41d4-a716-446655440001",
  "filePath": "/storage/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-20T20:30:00Z"
}
EOF

# Wait for outbox poller
sleep 3

# Verify database
psql -h localhost -U postgres -d mobile_analysis -c "SELECT COUNT(*) FROM analysis;"
# Should return: 1

psql -h localhost -U postgres -d mobile_analysis -c "SELECT COUNT(*) FROM analysis_task;"
# Should return: 4 (APK has 4 tasks)

echo "✓ Manual workflow test passed"
```

---

## ACCEPTANCE CRITERIA

- [ ] All 5 repositories created with required query methods
- [ ] KafkaConfig implemented with manual commit factory
- [ ] RedisConfig implemented with JSON serialization
- [ ] ConfigurationService loads configs with Redis caching
- [ ] AnalysisOrchestrator creates analyses with full task graph
- [ ] TaskDependencyResolver correctly maps dependencies
- [ ] FileEventConsumer processes FileEvents with correlation IDs
- [ ] OutboxPoller publishes events to Kafka with correct partition keys
- [ ] Integration test passes with Testcontainers (real containers)
- [ ] Manual test: FileEvent → Analysis + Tasks + Outbox → Kafka output
- [ ] All logs include correlation IDs (analysisId)
- [ ] Redis cache populates and is used correctly
- [ ] Idempotency: Duplicate FileEvents create no duplicates
- [ ] Error handling: Failures are logged with context, retried appropriately
- [ ] No compiler warnings or errors
- [ ] All dependencies resolved correctly
- [ ] `./mvnw verify` passes with BUILD SUCCESS
- [ ] Service starts without errors and outbox poller runs

---

## COMPLETION CHECKLIST

- [ ] TASK 1: AnalysisTaskRepository created and compiled
- [ ] TASK 2: AnalysisConfigRepository created and compiled
- [ ] TASK 3: TaskConfigRepository created and compiled
- [ ] TASK 4: OutboxRepository created and compiled
- [ ] TASK 5: KafkaConfig created and compiled
- [ ] TASK 6: RedisConfig created and compiled
- [ ] TASK 7: ConfigurationService created and compiled
- [ ] TASK 8: TaskDependencyResolver created and compiled
- [ ] TASK 9: AnalysisOrchestrator created and compiled
- [ ] TASK 10: FileEventConsumer created and compiled
- [ ] TASK 11: OutboxPoller created and compiled
- [ ] TASK 12: AnalysisOrchestratorIntegrationTest created and passes
- [ ] TASK 13: pom.xml dependencies verified
- [ ] TASK 14: application.yml verified with all required properties
- [ ] TASK 15: Full build passes with ./mvnw clean install
- [ ] TASK 16: Docker Compose running and healthy
- [ ] TASK 17: Integration tests pass with ./mvnw verify
- [ ] TASK 18: Orchestrator service starts without errors
- [ ] TASK 19: Manual test: FileEvent published successfully
- [ ] TASK 20: Manual test: Database shows analysis and 4 tasks created
- [ ] TASK 21: Manual test: Kafka shows published TaskEvents
- [ ] TASK 22: Manual test: Logs show correlation IDs throughout
- [ ] All acceptance criteria met
- [ ] Zero compilation errors
- [ ] Zero test failures
- [ ] Zero regressions (existing functionality unchanged)
- [ ] Code follows project conventions (CLAUDE.md)
- [ ] All patterns from codebase are mirrored correctly

---

## NOTES

### Design Decisions

1. **Separate TaskDependencyResolver** - Helper class keeps AnalysisOrchestrator focused on orchestration, dependency resolution logic testable independently

2. **Redis Best-Effort Caching** - Cache failures don't block analysis creation; DB is always source of truth. This maintains resilience.

3. **Synchronous Outbox Publishing** - `.get()` on KafkaTemplate ensures Kafka confirms before marking processed. Prevents event loss.

4. **Fixed-Delay Polling** - OutboxPoller uses fixed delay (not fixed rate) to avoid pile-up if publishing is slow.

5. **Spring Data JPA JPQL** - Using JPQL (not native SQL) makes queries database-agnostic and maintainable.

### Implementation Risks & Mitigations

**Risk**: Kafka connection failure during outbox polling
- **Mitigation**: Catch exceptions per-event, don't block batch, retry on next poll

**Risk**: Redis connection failure during config loading
- **Mitigation**: Catch cache errors, fall back to database, log warning

**Risk**: Task dependency resolution complexity
- **Mitigation**: Keep logic in separate resolver class, extensively test edge cases

**Risk**: Duplicate FileEvents creating multiple analyses
- **Mitigation**: Verify idempotency key uniqueness in database, add unique constraint

**Risk**: Outbox events published out of order
- **Mitigation**: Ensure FIFO polling (order by createdAt ASC), use analysisId as partition key

### What to Read Before Implementation

**MUST READ** (before writing any code):
1. CLAUDE.md lines 150-200 (Kafka consumer patterns)
2. CLAUDE.md lines 250-350 (Transactional Outbox pattern)
3. CLAUDE.md lines 400-500 (Service patterns)
4. PHASE1_STATUS.md (current state and what's missing)
5. Database migrations (V001, V002, V003)

**SHOULD READ** (for deeper understanding):
6. Existing AnalysisRepository.java (pattern reference)
7. application.yml (configuration to use)
8. PRD.md sections 3-5 (architecture details)

**REFERENCE** (while implementing):
9. Microservices.io Transactional Outbox article
10. Spring Kafka Manual Commit documentation
11. Spring Data JPA Query Methods guide

### Estimated Timeline

- **Repositories** (Tasks 1-4): 10 minutes
- **Config** (Tasks 5-6): 15 minutes
- **Services** (Tasks 7-9): 45 minutes
- **Messaging** (Tasks 10-11): 30 minutes
- **Tests & Manual** (Tasks 12-22): 60 minutes
- **Total Remaining**: ~2.5 hours

### One-Pass Implementation Confidence

**Confidence Score**: 9/10

**Reasoning**:
- ✅ Clear patterns documented in CLAUDE.md
- ✅ Database schema complete and correct
- ✅ Build system fully configured
- ✅ Docker infrastructure working
- ✅ All external dependencies specified
- ✅ Comprehensive task breakdown with validation commands
- ⚠️ Slight risk in Kafka manual commit setup (but well-documented)
- ⚠️ Redis cache integration (but well-established Spring pattern)

**What Could Go Wrong**:
1. Kafka listener container factory not found → Check Spring Kafka docs
2. Redis serialization failure → Check RedisConfig JSON setup
3. Testcontainers port conflicts → Check Docker environment
4. Task dependency resolution logic → Carefully follow PHASE1_STATUS examples

All risks mitigated by clear patterns and comprehensive documentation.

---

## SUCCESS METRICS

**Phase 1 Complete When**:
1. ✅ `./mvnw verify` passes with BUILD SUCCESS
2. ✅ Integration test passes with all assertions
3. ✅ Manual test: FileEvent ingested → Analysis created with 4 tasks
4. ✅ Manual test: Outbox poller publishes to static-analysis-tasks topic
5. ✅ Logs show correlation IDs throughout workflow
6. ✅ No compilation errors, warnings, or test failures
7. ✅ All acceptance criteria met

**Phase 1 Ready to Merge When**:
8. ✅ All tasks completed in order
9. ✅ Every validation command passes
10. ✅ Code review confirms patterns followed
11. ✅ No regressions to existing functionality
12. ✅ PR description references this plan

---

**Plan Created**: January 20, 2026, 8:28 PM IST  
**Feature Branch**: `feature/foundation/phase1-core-orchestrator`  
**Estimated Duration**: 2.5 hours  
**Implementation Status**: Ready for execution  
**Next Step**: Begin with Task 1 (AnalysisTaskRepository)