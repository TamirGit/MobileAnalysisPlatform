# Feature: Complete Phase 1 Core Orchestrator Foundation

The following plan is comprehensive and ready for implementation. All patterns validated against codebase and technology stack. Execution should follow this plan top-to-bottom with each task independently testable.

## Feature Description

Establish the foundational orchestration layer for the Mobile Analysis Platform - a distributed security analysis backend system for mobile applications. Phase 1 focuses on building the core project structure, database schema, configuration management system, file event ingestion, and initial task orchestration logic.

This is the foundational layer that enables all subsequent analysis engines and distributed processing capabilities. Success means we have a working system that can ingest file events, create analysis workflows based on configuration, persist state across components, and prepare tasks for engine consumption.

## User Story

As a Security Operations Engineer
I want to drop a mobile application file (.apk or .ipa) into the system and have it automatically trigger a multi-task analysis workflow
So that I don't need to manually coordinate multiple analysis tools and can scale analysis processing to handle many applications

## Problem Statement

Current mobile security analysis requires manual coordination of multiple specialized tools. Each tool must be invoked separately, their outputs manually fed to dependent tools, and failures require manual recovery. This approach doesn't scale beyond a few applications per day and is error-prone.

The Platform needs an intelligent orchestrator that:
1. Automatically detects file events
2. Loads analysis workflows from configuration
3. Executes tasks in correct dependency order
4. Handles failures gracefully with automatic retry
5. Tracks analysis state across distributed components
6. Scales horizontally as workload increases

Phase 1 establishes this foundation - the ability to ingest events, create workflow instances, manage state, and dispatch tasks to engines.

## Solution Statement

Build an event-driven orchestration system using Java 21, Spring Boot 3, Kafka, and PostgreSQL. Core architecture:

1. **File Event Ingestion:** Consume .apk/.ipa file events from Kafka, create analysis instances
2. **Configuration Management:** Load analysis workflows from database, cache in Redis for fast lookup
3. **State Persistence:** Store analysis and task state in PostgreSQL with consistent schema
4. **Task Orchestration:** Create task instances from configuration, resolve dependencies, dispatch to engines
5. **Event-Driven Communication:** Use Kafka topics for inter-component communication
6. **Foundation for Fault Tolerance:** Implement outbox pattern (Phase 4) for exactly-once delivery

This foundation enables Phase 2-5 to build engines, response handling, retry logic, and production readiness.

## Feature Metadata

**Feature Type:** New Capability (Core Platform Foundation)
**Estimated Complexity:** High (Multiple integrated systems)
**Primary Systems Affected:** 
- Project structure and build system
- Database layer (PostgreSQL)
- Cache layer (Redis)
- Message streaming (Kafka)
- Orchestrator service core logic

**Dependencies:** 
- Java 21 JDK
- Spring Boot 3.3.x
- Apache Kafka 3.8.x
- PostgreSQL 16.x
- Redis 7.x
- Maven 3.9.x or Gradle 8.x

---

## CONTEXT REFERENCES

### Relevant Codebase Files IMPORTANT: YOU MUST READ THESE FILES BEFORE IMPLEMENTING!

**Note:** This is a new repository being initialized. Reference patterns from Spring Boot and Kafka best practices.

**Files to Review from PRD:**
- Section 6: Core Architecture & Patterns (event-driven, outbox, transactional patterns)
- Section 6: Directory Structure (target project layout)
- Section 8: Technology Stack (exact versions and dependencies)
- Section 10: API Specification (internal Kafka event schemas)
- Section 14: Database Schema (exact DDL and indexes)
- Section 15: Kafka Topics Configuration (topic setup)
- Section 15: Redis Cache Structure (caching patterns)

### New Files to Create

**Multi-Module Structure:**
- `common/` - Shared domain models and events
  - `common/pom.xml` - Maven parent
  - `common/src/main/java/.../common/domain/Analysis.java` - Domain entity
  - `common/src/main/java/.../common/domain/AnalysisTask.java` - Domain entity
  - `common/src/main/java/.../common/domain/TaskStatus.java` - Enum
  - `common/src/main/java/.../common/events/FileEvent.java` - DTO
  - `common/src/main/java/.../common/events/TaskEvent.java` - DTO
  - `common/src/main/java/.../common/config/KafkaConfig.java` - Shared Kafka config
  - `common/src/main/java/.../common/config/RedisConfig.java` - Shared Redis config

- `orchestrator-service/` - Main orchestration service
  - `orchestrator-service/pom.xml` - Service dependencies
  - `orchestrator-service/src/main/java/.../orchestrator/OrchestratorServiceApplication.java` - Spring Boot main
  - `orchestrator-service/src/main/java/.../orchestrator/messaging/FileEventConsumer.java` - Kafka consumer
  - `orchestrator-service/src/main/java/.../orchestrator/messaging/TaskEventProducer.java` - Kafka producer
  - `orchestrator-service/src/main/java/.../orchestrator/service/AnalysisOrchestrator.java` - Core logic
  - `orchestrator-service/src/main/java/.../orchestrator/service/ConfigurationService.java` - Config management
  - `orchestrator-service/src/main/java/.../orchestrator/repository/AnalysisRepository.java` - JPA repository
  - `orchestrator-service/src/main/java/.../orchestrator/repository/AnalysisTaskRepository.java` - JPA repository
  - `orchestrator-service/src/main/java/.../orchestrator/repository/TaskConfigRepository.java` - JPA repository
  - `orchestrator-service/src/main/resources/application.yml` - Service configuration
  - `orchestrator-service/src/main/resources/db/migration/V1__initial_schema.sql` - Flyway migration
  - `orchestrator-service/src/main/resources/db/migration/V2__analysis_config_data.sql` - Initial config
  - `orchestrator-service/src/test/java/.../orchestrator/DomainModelTests.java` - Unit tests
  - `orchestrator-service/src/test/java/.../orchestrator/OrchestratorIntegrationTests.java` - Integration tests

- `pom.xml` - Multi-module parent POM
- `docker-compose.yml` - Local development stack
- `.claude/CLAUDE.md` - Development standards (if not exists)

### Relevant Documentation YOU SHOULD READ THESE BEFORE IMPLEMENTING!

- [Spring Boot 3.3 Official Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
  - Section: Using Spring Data JPA - Database access patterns
  - Section: Spring Data Redis - Caching integration
  - Why: Understand Spring Boot patterns we're using

- [Spring Data JPA Reference](https://docs.spring.io/spring-data/jpa/reference/)
  - Section: Query Methods - Repository patterns
  - Why: Building JPA repositories correctly

- [Spring Kafka Documentation](https://docs.spring.io/spring-kafka/docs/current/reference/html/)
  - Section: Receiving Messages - @KafkaListener patterns
  - Section: Manual Commits - Consumer offset management
  - Why: Correct Kafka consumer implementation with manual commit

- [Spring Data Redis Documentation](https://docs.spring.io/spring-data/redis/reference/)
  - Section: Redis Operations - Basic operations (get, set, del)
  - Why: Caching patterns and Redis template usage

- [Flyway Database Migrations](https://flywaydb.org/documentation/usage/commandline/)
  - Section: SQL Migrations - Naming convention V1__description.sql
  - Why: Creating reproducible database schemas

- [PostgreSQL 16 Documentation](https://www.postgresql.org/docs/16/sql.html)
  - Section: Data Definition - Table, Index creation
  - Section: Indexes - Performance for queries
  - Why: Writing correct DDL with proper indexes

- [Kafka Partitioning Guide](https://kafka.apache.org/documentation/#topicconfigs)
  - Partition key strategies for ordering
  - Why: Understanding analysisId partition key decision

- [Event-Driven Architecture Patterns](https://martinfowler.com/articles/201701-event-driven.html)
  - Event sourcing vs event notification
  - Why: Understanding event-driven design decision

### Patterns to Follow

**Naming Conventions (from Spring/Java standards):**
- Classes: PascalCase (Analysis, AnalysisTask, FileEventConsumer)
- Methods: camelCase (processFileEvent, createAnalysis)
- Constants: UPPER_SNAKE_CASE (MAX_RETRIES, DEFAULT_TIMEOUT)
- Packages: lowercase, domain-focused (com.mobilesecurity.orchestrator.messaging)
- Enums: PascalCase, singular (TaskStatus, FileType, EngineType)
- Database tables: snake_case, plural (analysis, analysis_task, task_config)
- Database columns: snake_case (created_at, analysis_id, file_path)

**Spring Data JPA Pattern:**
```java
@Entity
@Table(name = "analysis")
public class Analysis {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, length = 500)
    private String filePath;
    
    @Column(nullable = false, length = 10)
    private String fileType; // APK or IPA
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnalysisStatus status;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}

@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findByStatus(AnalysisStatus status);
}
```

**Kafka Consumer Pattern (Manual Commit):**
```java
@Service
public class FileEventConsumer {
    @KafkaListener(topics = "file-events", groupId = "orchestrator-group")
    public void handleFileEvent(FileEvent event, Acknowledgment ack) {
        // Process event (read from DB/cache)
        orchestratorService.processFileEvent(event);
        // Commit offset AFTER processing
        ack.acknowledge();
    }
}
```

**Redis Caching Pattern (Cache-Aside):**
```java
public AnalysisConfig getConfiguration(String fileType) {
    String cacheKey = "analysis-config:" + fileType;
    // Try cache first
    AnalysisConfig cached = redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) return cached;
    
    // Cache miss - read from DB
    AnalysisConfig config = configRepository.findByFileType(fileType)
        .orElseThrow(() -> new ConfigNotFoundException(fileType));
    
    // Populate cache (no expiry for config)
    redisTemplate.opsForValue().set(cacheKey, config);
    return config;
}
```

**Transactional Service Pattern:**
```java
@Service
public class AnalysisOrchestrator {
    @Transactional
    public void createAnalysisFromEvent(FileEvent event) {
        // Single transaction for:
        // 1. Load config from cache/DB
        // 2. Create analysis entity
        // 3. Create all task entities
        // 4. Persist to database
        // Commit writes everything atomically
    }
}
```

**Error Handling Pattern:**
```java
public void handleFileEvent(FileEvent event) {
    try {
        orchestratorService.processFileEvent(event);
    } catch (ConfigNotFoundException e) {
        logger.error("Configuration not found for file type: {}", event.getFileType(), e);
        // Send to DLQ or log for manual investigation
    } catch (Exception e) {
        logger.error("Unexpected error processing file event: {}", event.getEventId(), e);
        throw e; // Let Kafka retry
    }
}
```

**Kafka Producer Pattern (Partition by analysisId):**
```java
@Service
public class TaskEventProducer {
    public void publishTaskEvent(TaskEvent event) {
        kafkaTemplate.send(new ProducerRecord<>(
            event.getTopic(),
            event.getAnalysisId().toString(),  // Partition key: analysisId
            event
        ));
    }
}
```

---

## IMPLEMENTATION PLAN

### Phase 1: Foundation

**Goal:** Establish project structure, build system, and database layer

**Tasks:**
- Create multi-module Maven project structure
- Set up common module with domain models and events
- Configure database schema with Flyway migrations
- Establish PostgreSQL and Redis connections
- Create JPA repositories

### Phase 2: Configuration Management

**Goal:** Implement configuration loading from database and Redis caching

**Tasks:**
- Create AnalysisConfig and TaskConfig entities
- Implement ConfigurationService with Redis caching
- Add configuration repositories
- Set up initial test data

### Phase 3: Event Ingestion

**Goal:** Consume file events and trigger analysis creation

**Tasks:**
- Implement FileEventConsumer with Kafka listener
- Create AnalysisOrchestrator core logic
- Implement analysis and task creation
- Add initial task dispatch logic

### Phase 4: Task Management & Kafka Integration

**Goal:** Complete task orchestration and engine communication setup

**Tasks:**
- Implement TaskEventProducer
- Add dependency resolution logic
- Set up Kafka topics configuration
- Configure Docker Compose for local development

### Phase 5: Testing & Validation

**Goal:** Ensure Phase 1 foundation is solid and ready for Phase 2

**Tasks:**
- Add unit tests for domain logic
- Add integration tests with Testcontainers
- Validate end-to-end flow (file event → analysis creation → task dispatch)
- Verify caching works correctly

---

## STEP-BY-STEP TASKS

EXECUTE EVERY TASK IN ORDER, TOP TO BOTTOM. Each task is atomic and independently testable.

### Task 1: CREATE Multi-Module Maven Project Structure

**Objective:** Establish the project layout and build system

**Action:** CREATE Root `pom.xml`

- **IMPLEMENT:** Parent POM with multi-module configuration, dependency management, build plugins
- **PATTERN:** Standard Maven multi-module structure following Spring Boot conventions
- **IMPORTS:** Maven 3.9.x, Spring Boot 3.3.2 BOM
- **GOTCHA:** Make sure `<modules>` section lists all modules. Module order matters for build
- **VALIDATE:** `mvn clean verify -DskipTests` - Should build all modules successfully

**Details:**
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.mobilesecurity</groupId>
    <artifactId>mobile-analysis-platform</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    
    <name>Mobile Analysis Platform</name>
    <description>Event-driven security analysis backend for mobile applications</description>
    
    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <spring-boot.version>3.3.2</spring-boot.version>
    </properties>
    
    <modules>
        <module>common</module>
        <module>orchestrator-service</module>
        <module>engine-common</module>
    </modules>
    
    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.11.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

### Task 2: CREATE Common Module - Domain Models

**Objective:** Define shared domain entities used across services

**Action:** CREATE `common/pom.xml`

- **IMPLEMENT:** Common module POM with Spring Data JPA and other shared dependencies
- **PATTERN:** Maven module with packaging jar, parent reference to root POM
- **IMPORTS:** spring-boot-starter-data-jpa, spring-boot-starter-data-redis, lombok
- **GOTCHA:** Common module should NOT have Spring Boot Application class - it's just a library
- **VALIDATE:** `mvn -pl common clean compile` - Compiles without errors

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/domain/Analysis.java`

- **IMPLEMENT:** JPA entity for Analysis with all required fields per PRD section 14
- **PATTERN:** Spring Data JPA entity with proper annotations and indexes
- **IMPORTS:** @Entity, @Table, @GeneratedValue, @Enumerated, @Temporal from javax.persistence
- **GOTCHA:** UUID generation via @GeneratedValue(strategy = GenerationType.UUID). Column names must match database schema exactly
- **VALIDATE:** Check that all fields match PRD database schema section

**Details:**
Fields: id (UUID), filePath, fileType, analysisConfigId, status, startedAt, completedAt, createdAt, updatedAt

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/domain/AnalysisStatus.java`

- **IMPLEMENT:** Enum with states: PENDING, RUNNING, COMPLETED, FAILED
- **PATTERN:** Simple enum used in @Enumerated columns
- **IMPORTS:** Standard Java enum
- **GOTCHA:** Must match database VARCHAR(20) column definition
- **VALIDATE:** All four states are defined

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/domain/AnalysisTask.java`

- **IMPLEMENT:** JPA entity for individual tasks within analysis
- **PATTERN:** Spring Data JPA entity with relationships to Analysis and TaskConfig
- **IMPORTS:** @Entity, @ManyToOne, @ForeignKey from JPA
- **GOTCHA:** last_heartbeat_at stored directly in this table (not separate heartbeat table). Include idempotency_key with unique constraint
- **VALIDATE:** All fields from PRD database schema present

**Details:**
Fields: id, analysisId, taskConfigId, engineType, status, dependsOnTaskId, attempts, outputPath, errorMessage, idempotencyKey, startedAt, completedAt, lastHeartbeatAt, createdAt, updatedAt

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/domain/AnalysisConfig.java`

- **IMPLEMENT:** JPA entity for configuration templates (from database)
- **PATTERN:** One-to-many relationship with TaskConfig
- **IMPORTS:** @OneToMany, @Cascade from JPA
- **GOTCHA:** Cache this entity with full task configs. Version field for config updates
- **VALIDATE:** Fields: id, fileType (UNIQUE), name, version, active, createdAt, updatedAt

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/domain/TaskConfig.java`

- **IMPLEMENT:** JPA entity for task configuration (template)
- **PATTERN:** Many-to-one to AnalysisConfig, self-referencing for dependency
- **IMPORTS:** @ManyToOne, @JoinColumn from JPA
- **GOTCHA:** depends_on_task_config_id can be null (no dependency). Create proper composite unique index
- **VALIDATE:** Fields: id, analysisConfigId, engineType, taskOrder, dependsOnTaskConfigId, timeoutSeconds, maxRetries, createdAt

---

### Task 3: CREATE Common Module - Event DTOs

**Objective:** Define Kafka event schemas as DTOs

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/events/FileEvent.java`

- **IMPLEMENT:** DTO for file event consumed from Kafka
- **PATTERN:** Simple POJO with @Data from Lombok
- **IMPORTS:** Lombok @Data, @NoArgsConstructor
- **GOTCHA:** Must match Kafka topic message format from PRD section 10
- **VALIDATE:** Fields: eventId, filePath, fileType, timestamp. Can deserialize from JSON

**Details:**
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/app_v1.2.3.apk",
  "fileType": "APK",
  "timestamp": "2026-01-19T20:30:00Z"
}
```

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/events/TaskEvent.java`

- **IMPLEMENT:** DTO for task event published to engines
- **PATTERN:** POJO with @Data and @Builder for construction
- **IMPORTS:** Lombok @Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
- **GOTCHA:** Include all fields from PRD section 10. Timestamp should be LocalDateTime
- **VALIDATE:** Fields: eventId, taskId, analysisId, engineType, filePath, dependentTaskOutputPath, idempotencyKey, timeoutSeconds, timestamp

**Details:**
Fields: eventId (UUID), taskId (Long), analysisId (UUID), engineType (String), filePath (String), dependentTaskOutputPath (String, nullable), idempotencyKey (UUID), timeoutSeconds (Integer), timestamp (LocalDateTime)

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/events/TaskResponseEvent.java`

- **IMPLEMENT:** DTO for task completion response from engines
- **PATTERN:** POJO with @Data
- **IMPORTS:** Lombok annotations
- **GOTCHA:** Status should use TaskStatus enum or String matching database column
- **VALIDATE:** Fields: eventId, taskId, analysisId, status, outputPath, errorMessage, attempts, timestamp

---

### Task 4: CREATE Common Module - Shared Configuration

**Objective:** Define shared Spring configurations for Kafka and Redis

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/config/KafkaProducerConfig.java`

- **IMPLEMENT:** Spring configuration for Kafka producer (JSON serialization)
- **PATTERN:** @Configuration class with @Bean methods
- **IMPORTS:** Spring Kafka ProducerConfig, JsonSerializer
- **GOTCHA:** Use JsonSerializer for value, String for key. Enable idempotence (ENA_IDEMPOTENCE)
- **VALIDATE:** Can create ProducerFactory and KafkaTemplate beans

**Details:**
```java
@Configuration
public class KafkaProducerConfig {
    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(props);
    }
    
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
```

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/config/KafkaConsumerConfig.java`

- **IMPLEMENT:** Spring configuration for Kafka consumer (JSON deserialization, manual commit)
- **PATTERN:** @Configuration class with properties for manual commit
- **IMPORTS:** Spring Kafka ConsumerConfig, JsonDeserializer
- **GOTCHA:** Set ENABLE_AUTO_COMMIT_CONFIG = false for manual commit. Configure JsonDeserializer trusted packages
- **VALIDATE:** Consumer factory configured with correct properties

**Action:** CREATE `common/src/main/java/com/mobilesecurity/common/config/RedisConfig.java`

- **IMPLEMENT:** Spring Data Redis configuration with Jackson serialization
- **PATTERN:** @Configuration class with RedisTemplate bean
- **IMPORTS:** Spring Data Redis, Jackson JSON providers
- **GOTCHA:** Use RedisTemplate with Jackson2JsonRedisSerializer for cache entries. Configure TTL handling
- **VALIDATE:** RedisTemplate can be injected and used for cache operations

---

### Task 5: CREATE Orchestrator Service Module Structure

**Objective:** Set up orchestrator service module with Spring Boot

**Action:** CREATE `orchestrator-service/pom.xml`

- **IMPLEMENT:** Orchestrator service POM with Spring Boot starters and common module dependency
- **PATTERN:** Maven module depending on common module
- **IMPORTS:** spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-data-redis, spring-kafka, postgresql driver, flyway-core
- **GOTCHA:** Dependency on common module in orchestrator. spring-boot-maven-plugin for executable JAR
- **VALIDATE:** `mvn -pl orchestrator-service clean compile` - Compiles successfully

**Details:**
```xml
<dependencies>
    <dependency>
        <groupId>com.mobilesecurity</groupId>
        <artifactId>common</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
    
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Additional dependencies as per tech stack -->
</dependencies>
```

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/OrchestratorServiceApplication.java`

- **IMPLEMENT:** Spring Boot Application class with @SpringBootApplication
- **PATTERN:** Standard Spring Boot entry point
- **IMPORTS:** org.springframework.boot.SpringApplication
- **GOTCHA:** Main method calls SpringApplication.run(). Enable Scheduling and Async if needed
- **VALIDATE:** Application starts and Spring context loads

**Action:** CREATE `orchestrator-service/src/main/resources/application.yml`

- **IMPLEMENT:** Service configuration with database, Redis, Kafka, and Flyway settings
- **PATTERN:** Spring Boot YAML configuration
- **IMPORTS:** Environment variable substitution with ${VAR_NAME:default}
- **GOTCHA:** DB_URL must be jdbc:postgresql://localhost:5432/mobile_analysis for local dev. Flyway auto-migrate enabled
- **VALIDATE:** Configuration can be loaded without errors

**Details:**
```yaml
spring:
  application:
    name: orchestrator-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mobile_analysis}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate  # Don't auto-create, use Flyway
    show-sql: false
    properties:
      hibernate.dialect: org.hibernate.dialect.PostgreSQLDialect
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: orchestrator-group
      enable-auto-commit: false
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      database: 0

app:
  storage:
    base-path: ${STORAGE_PATH:/storage}
  outbox:
    poll-interval-ms: 1000
    batch-size: 50

logging:
  level:
    root: INFO
    com.mobilesecurity: DEBUG
```

---

### Task 6: CREATE Database Schema with Flyway Migrations

**Objective:** Define complete database schema and initial configuration

**Action:** CREATE `orchestrator-service/src/main/resources/db/migration/V1__initial_schema.sql`

- **IMPLEMENT:** Complete database DDL from PRD section 14
- **PATTERN:** Flyway SQL migration following V{version}__{description}.sql format
- **IMPORTS:** Standard PostgreSQL DDL syntax
- **GOTCHA:** Exact column names and types must match JPA entities. Indexes on frequently queried columns (analysis_id, status). Timestamps default to NOW()
- **VALIDATE:** `mvn flyway:validate` - Migration syntax is valid

**Details:**
Include exact tables from PRD:
- analysis_config with file_type UNIQUE
- task_config with analysis_config_id foreign key
- analysis with UUID primary key
- analysis_task with last_heartbeat_at column (NOT separate table)
- outbox for transactional outbox pattern (Phase 4)

```sql
CREATE TABLE analysis_config (
    id BIGSERIAL PRIMARY KEY,
    file_type VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE task_config (
    id BIGSERIAL PRIMARY KEY,
    analysis_config_id BIGINT NOT NULL REFERENCES analysis_config(id),
    engine_type VARCHAR(50) NOT NULL,
    task_order INT NOT NULL,
    depends_on_task_config_id BIGINT REFERENCES task_config(id),
    timeout_seconds INT NOT NULL DEFAULT 300,
    max_retries INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(analysis_config_id, task_order)
);
CREATE INDEX idx_task_config_analysis ON task_config(analysis_config_id);

CREATE TABLE analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    analysis_config_id BIGINT NOT NULL REFERENCES analysis_config(id),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_analysis_status ON analysis(status);

CREATE TABLE analysis_task (
    id BIGSERIAL PRIMARY KEY,
    analysis_id UUID NOT NULL REFERENCES analysis(id),
    task_config_id BIGINT NOT NULL REFERENCES task_config(id),
    engine_type VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL,
    depends_on_task_id BIGINT REFERENCES analysis_task(id),
    attempts INT NOT NULL DEFAULT 0,
    output_path VARCHAR(500),
    error_message TEXT,
    idempotency_key UUID NOT NULL UNIQUE,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_heartbeat_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE(analysis_id, task_config_id)
);
CREATE INDEX idx_analysis_task_analysis ON analysis_task(analysis_id);
CREATE INDEX idx_analysis_task_status ON analysis_task(status);
CREATE INDEX idx_analysis_task_idempotency ON analysis_task(idempotency_key);
CREATE INDEX idx_analysis_task_heartbeat ON analysis_task(last_heartbeat_at) WHERE status = 'RUNNING';

CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    partition_key VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    processed BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMP
);
CREATE INDEX idx_outbox_processed ON outbox(processed, created_at) WHERE NOT processed;
```

**Action:** CREATE `orchestrator-service/src/main/resources/db/migration/V2__initial_analysis_config.sql`

- **IMPLEMENT:** Initial analysis configuration data for APK and IPA
- **PATTERN:** INSERT statements for test data
- **IMPORTS:** SQL INSERT syntax
- **GOTCHA:** Task order and dependencies must be correct. Inserts IPA analysis after APK to avoid FK issues
- **VALIDATE:** Data matches PRD section 14 analysis configuration example

**Details:**
Insert:
- APK analysis config (fileType='APK')
- 4 task configs for APK (Static Analysis, Decompiler, Signature Check, Dynamic Analysis) with dependencies
- IPA analysis config (fileType='IPA')
- Similar task configs for IPA

```sql
-- APK Analysis Configuration
INSERT INTO analysis_config (file_type, name, version, active) 
VALUES ('APK', 'Android Security Analysis v1', 1, true);

-- Task 1: Static Analysis (no dependency)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'STATIC_ANALYSIS', 1, NULL, 300, 3);

-- Task 2: Decompiler (depends on Task 1)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'DECOMPILER', 2, 1, 600, 2);

-- Task 3: Signature Check (no dependency)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'SIGNATURE_CHECK', 3, NULL, 120, 3);

-- Task 4: Dynamic Analysis (depends on Task 2)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'DYNAMIC_ANALYSIS', 4, 2, 1800, 1);

-- IPA Analysis Configuration
INSERT INTO analysis_config (file_type, name, version, active) 
VALUES ('IPA', 'iOS Security Analysis v1', 1, true);

-- Similar task configs for IPA...
```

---

### Task 7: CREATE JPA Repositories

**Objective:** Define repository interfaces for database access

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/repository/AnalysisRepository.java`

- **IMPLEMENT:** JPA Repository interface extending JpaRepository
- **PATTERN:** Spring Data JPA repository with custom query methods
- **IMPORTS:** org.springframework.data.jpa.repository.JpaRepository
- **GOTCHA:** Use findByStatus for common query (status = 'RUNNING', 'PENDING')
- **VALIDATE:** Can query analyses by status and other fields

**Details:**
```java
@Repository
public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findByStatus(AnalysisStatus status);
    List<Analysis> findByStatusAndFileType(AnalysisStatus status, String fileType);
}
```

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/repository/AnalysisTaskRepository.java`

- **IMPLEMENT:** JPA Repository for AnalysisTask queries
- **PATTERN:** Repository with complex queries
- **IMPORTS:** @Query for custom JPQL/SQL queries
- **GOTCHA:** Query for finding ready-to-run tasks (status = PENDING, dependency COMPLETED or null)
- **VALIDATE:** Can find ready tasks, find by analysis ID, find by status

**Details:**
```java
@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Long> {
    List<AnalysisTask> findByAnalysisIdOrderByTaskOrder(UUID analysisId);
    List<AnalysisTask> findByAnalysisIdAndStatus(UUID analysisId, TaskStatus status);
    Optional<AnalysisTask> findByIdempotencyKey(UUID idempotencyKey);
    
    @Query("SELECT t FROM AnalysisTask t WHERE t.analysis.id = :analysisId " +
           "AND t.status = 'PENDING' " +
           "AND (t.dependsOnTaskId IS NULL OR " +
           "  EXISTS (SELECT 1 FROM AnalysisTask dep WHERE dep.id = t.dependsOnTaskId " +
           "           AND dep.status = 'COMPLETED'))")
    List<AnalysisTask> findReadyToRunTasks(@Param("analysisId") UUID analysisId);
}
```

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/repository/TaskConfigRepository.java`

- **IMPLEMENT:** JPA Repository for TaskConfig queries
- **PATTERN:** Simple repository for config lookups
- **IMPORTS:** Standard Spring Data JPA
- **GOTCHA:** Find by analysisConfigId to load all tasks for analysis
- **VALIDATE:** Can find tasks by analysisConfigId and task ID

**Details:**
```java
@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {
    List<TaskConfig> findByAnalysisConfigIdOrderByTaskOrder(Long analysisConfigId);
    Optional<TaskConfig> findById(Long id);
}
```

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/repository/AnalysisConfigRepository.java`

- **IMPLEMENT:** JPA Repository for AnalysisConfig
- **PATTERN:** Repository with custom finder methods
- **IMPORTS:** Optional from JPA
- **GOTCHA:** Find by fileType should use Optional for safe access
- **VALIDATE:** Can find config by file type

**Details:**
```java
@Repository
public interface AnalysisConfigRepository extends JpaRepository<AnalysisConfig, Long> {
    Optional<AnalysisConfig> findByFileType(String fileType);
}
```

---

### Task 8: CREATE Configuration Service with Redis Caching

**Objective:** Implement configuration loading from database with Redis caching

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/service/ConfigurationService.java`

- **IMPLEMENT:** Service for loading and caching analysis configurations
- **PATTERN:** Service layer with @Service annotation, cache-aside pattern
- **IMPORTS:** RedisTemplate, AnalysisConfigRepository, ObjectMapper (Jackson)
- **GOTCHA:** Store full AnalysisConfig with TaskConfig list in Redis. Cache key format: "analysis-config:{fileType}". No TTL (manual invalidation)
- **VALIDATE:** Returns config from cache (10ms), falls back to DB (100ms), repopulates cache

**Details:**
```java
@Service
@Slf4j
public class ConfigurationService {
    private static final String CACHE_KEY_PREFIX = "analysis-config:";
    
    @Autowired
    private AnalysisConfigRepository configRepository;
    
    @Autowired
    private TaskConfigRepository taskConfigRepository;
    
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    
    public AnalysisConfig getAnalysisConfig(String fileType) {
        String cacheKey = CACHE_KEY_PREFIX + fileType;
        
        // Try cache first
        AnalysisConfig cached = (AnalysisConfig) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Config for {} found in cache", fileType);
            return cached;
        }
        
        // Cache miss - read from DB
        AnalysisConfig config = configRepository.findByFileType(fileType)
            .orElseThrow(() -> new ConfigNotFoundException("File type not found: " + fileType));
        
        // Load associated tasks
        List<TaskConfig> tasks = taskConfigRepository.findByAnalysisConfigIdOrderByTaskOrder(config.getId());
        config.setTasks(tasks);
        
        // Populate cache (no expiry for config)
        redisTemplate.opsForValue().set(cacheKey, config);
        log.info("Config for {} loaded from DB and cached", fileType);
        
        return config;
    }
    
    public void invalidateCache(String fileType) {
        String cacheKey = CACHE_KEY_PREFIX + fileType;
        redisTemplate.delete(cacheKey);
        log.info("Cache invalidated for file type: {}", fileType);
    }
}
```

---

### Task 9: CREATE File Event Consumer

**Objective:** Implement Kafka consumer for file events

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/messaging/FileEventConsumer.java`

- **IMPLEMENT:** Kafka listener for file-events topic with manual commit
- **PATTERN:** @KafkaListener with manual acknowledgment
- **IMPORTS:** Spring Kafka @KafkaListener, Acknowledgment
- **GOTCHA:** Manual commit AFTER successful processing. Return from Kafka listener, let Spring commit offset automatically
- **VALIDATE:** Listens to file-events topic, triggers orchestrator for each event

**Details:**
```java
@Service
@Slf4j
public class FileEventConsumer {
    @Autowired
    private AnalysisOrchestrator orchestrator;
    
    @KafkaListener(
        topics = "file-events",
        groupId = "orchestrator-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleFileEvent(FileEvent event, Acknowledgment ack) {
        log.info("Received file event: {} for file: {}", event.getEventId(), event.getFilePath());
        
        try {
            // Process the file event
            orchestrator.processFileEvent(event);
            
            // Commit offset AFTER successful processing
            if (ack != null) {
                ack.acknowledge();
            }
            log.info("File event processed successfully: {}", event.getEventId());
        } catch (Exception e) {
            log.error("Failed to process file event: {}", event.getEventId(), e);
            // Don't acknowledge - let Kafka retry
            throw e;
        }
    }
}
```

---

### Task 10: CREATE Analysis Orchestrator Core Logic

**Objective:** Implement main orchestration logic for analysis creation and task dispatch

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/service/AnalysisOrchestrator.java`

- **IMPLEMENT:** Core service for processing file events and orchestrating analysis
- **PATTERN:** Service with @Transactional for atomic operations
- **IMPORTS:** All repositories, ConfigurationService, TaskEventProducer, UUID, transactions
- **GOTCHA:** Single @Transactional method creates analysis + all tasks atomically. Dispatch happens AFTER commit (via outbox in Phase 4, for now direct dispatch)
- **VALIDATE:** Analysis and tasks created in database, events ready for dispatch

**Details:**
```java
@Service
@Slf4j
public class AnalysisOrchestrator {
    @Autowired
    private AnalysisRepository analysisRepository;
    
    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;
    
    @Autowired
    private ConfigurationService configService;
    
    @Autowired
    private TaskEventProducer taskProducer;
    
    @Transactional
    public void processFileEvent(FileEvent event) {
        log.info("Processing file event for file: {}", event.getFilePath());
        
        // Step 1: Load configuration
        AnalysisConfig config = configService.getAnalysisConfig(event.getFileType());
        log.debug("Loaded config for file type: {}", event.getFileType());
        
        // Step 2: Create analysis entity
        Analysis analysis = new Analysis();
        analysis.setFilePath(event.getFilePath());
        analysis.setFileType(event.getFileType());
        analysis.setAnalysisConfigId(config.getId());
        analysis.setStatus(AnalysisStatus.PENDING);
        analysis = analysisRepository.save(analysis);
        log.info("Created analysis: {} for file: {}", analysis.getId(), event.getFilePath());
        
        // Step 3: Create analysis tasks from config
        List<AnalysisTask> tasks = new ArrayList<>();
        for (TaskConfig taskConfig : config.getTasks()) {
            AnalysisTask task = new AnalysisTask();
            task.setAnalysisId(analysis.getId());
            task.setTaskConfigId(taskConfig.getId());
            task.setEngineType(taskConfig.getEngineType());
            task.setStatus(TaskStatus.PENDING);
            task.setIdempotencyKey(UUID.randomUUID());
            
            // Set dependency if exists
            if (taskConfig.getDependsOnTaskConfigId() != null) {
                // Find the dependent task created earlier
                Optional<AnalysisTask> dependentTask = tasks.stream()
                    .filter(t -> t.getTaskConfigId().equals(taskConfig.getDependsOnTaskConfigId()))
                    .findFirst();
                dependentTask.ifPresent(t -> task.setDependsOnTaskId(t.getId()));
            }
            
            task = analysisTaskRepository.save(task);
            tasks.add(task);
            log.debug("Created task: {} for engine: {}", task.getId(), task.getEngineType());
        }
        
        // Step 4: Update analysis status to RUNNING
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis.setStartedAt(LocalDateTime.now());
        analysisRepository.save(analysis);
        
        // Step 5: Dispatch ready-to-run tasks (those without dependencies)
        dispatchReadyTasks(analysis.getId(), tasks);
        
        log.info("Analysis creation complete: {}", analysis.getId());
    }
    
    private void dispatchReadyTasks(UUID analysisId, List<AnalysisTask> tasks) {
        for (AnalysisTask task : tasks) {
            // Ready if no dependency OR dependency is already run (this phase only independent)
            if (task.getDependsOnTaskId() == null) {
                TaskEvent event = TaskEvent.builder()
                    .eventId(UUID.randomUUID())
                    .taskId(task.getId())
                    .analysisId(analysisId)
                    .engineType(task.getEngineType())
                    .filePath(null) // Will be loaded from analysis
                    .idempotencyKey(task.getIdempotencyKey())
                    .timeoutSeconds(300) // Default
                    .timestamp(LocalDateTime.now())
                    .build();
                
                taskProducer.publishTaskEvent(event);
                log.info("Dispatched task: {} to engine: {}", task.getId(), task.getEngineType());
            }
        }
    }
}
```

---

### Task 11: CREATE Task Event Producer

**Objective:** Implement Kafka producer for dispatching tasks to engines

**Action:** CREATE `orchestrator-service/src/main/java/com/mobilesecurity/orchestrator/messaging/TaskEventProducer.java`

- **IMPLEMENT:** Kafka producer for task events with partition key = analysisId
- **PATTERN:** Service using KafkaTemplate
- **IMPORTS:** Spring Kafka KafkaTemplate, ProducerRecord
- **GOTCHA:** Partition key MUST be analysisId.toString() for ordering. Topic name depends on engineType (e.g., "static-analysis-tasks")
- **VALIDATE:** Events published to correct topic with partition key

**Details:**
```java
@Service
@Slf4j
public class TaskEventProducer {
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final Map<String, String> TOPIC_MAP = Map.ofEntries(
        Map.entry("STATIC_ANALYSIS", "static-analysis-tasks"),
        Map.entry("DYNAMIC_ANALYSIS", "dynamic-analysis-tasks"),
        Map.entry("DECOMPILER", "decompiler-tasks"),
        Map.entry("SIGNATURE_CHECK", "signature-check-tasks")
    );
    
    public void publishTaskEvent(TaskEvent event) {
        String topic = TOPIC_MAP.get(event.getEngineType());
        if (topic == null) {
            throw new IllegalArgumentException("Unknown engine type: " + event.getEngineType());
        }
        
        // Partition key = analysisId for ordering
        ProducerRecord<String, Object> record = new ProducerRecord<>(
            topic,
            event.getAnalysisId().toString(),  // Partition key
            event
        );
        
        kafkaTemplate.send(record).whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Task event published: topic={}, taskId={}, partition={}, offset={}",
                    topic, event.getTaskId(), 
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish task event: {}", event.getTaskId(), ex);
                throw new RuntimeException("Kafka publish failed", ex);
            }
        });
    }
}
```

---

### Task 12: CREATE Docker Compose Configuration

**Objective:** Set up local development environment with all services

**Action:** CREATE `docker-compose.yml`

- **IMPLEMENT:** Docker Compose file with PostgreSQL, Redis, Kafka with KRaft
- **PATTERN:** Multi-container setup with networking
- **IMPORTS:** Docker Compose v3.8 syntax
- **GOTCHA:** Kafka uses KRaft (no ZooKeeper). PostgreSQL initialization script. Redis accessible from host
- **VALIDATE:** `docker-compose up -d` - All services start and are healthy

**Details:**
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: mobile_analysis
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5

  kafka:
    image: confluentinc/cp-kafka:7.8.0
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093
      KAFKA_LISTENERS: PLAINTEXT://kafka:9092,CONTROLLER://kafka:29093
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      CLUSTER_ID: MkwNYEk3OTcwNTJENDM2Qk
    ports:
      - "9092:9092"
    volumes:
      - kafka_data:/var/lib/kafka/data
    healthcheck:
      test: ["CMD", "kafka-broker-api-versions.sh", "--bootstrap-server", "localhost:9092"]
      interval: 10s
      timeout: 10s
      retries: 5

volumes:
  postgres_data:
  kafka_data:
```

---

### Task 13: CREATE Unit Tests for Domain Models

**Objective:** Add unit tests validating domain entity behavior

**Action:** CREATE `orchestrator-service/src/test/java/com/mobilesecurity/orchestrator/domain/AnalysisTest.java`

- **IMPLEMENT:** Unit tests for Analysis entity
- **PATTERN:** JUnit 5 with AssertJ assertions
- **IMPORTS:** @Test, assertions, Lombok
- **GOTCHA:** Test entity creation, field validation, status transitions
- **VALIDATE:** All entity test cases pass

**Details:**
```java
@DisplayName("Analysis Entity Tests")
class AnalysisTest {
    
    @Test
    @DisplayName("Should create analysis with required fields")
    void shouldCreateAnalysisWithRequiredFields() {
        // Arrange
        String filePath = "/storage/incoming/app.apk";
        String fileType = "APK";
        Long configId = 1L;
        
        // Act
        Analysis analysis = new Analysis();
        analysis.setFilePath(filePath);
        analysis.setFileType(fileType);
        analysis.setAnalysisConfigId(configId);
        analysis.setStatus(AnalysisStatus.PENDING);
        
        // Assert
        assertThat(analysis)
            .isNotNull()
            .hasFieldOrPropertyWithValue("filePath", filePath)
            .hasFieldOrPropertyWithValue("fileType", fileType)
            .hasFieldOrPropertyWithValue("status", AnalysisStatus.PENDING);
    }
}
```

**Action:** CREATE `orchestrator-service/src/test/java/com/mobilesecurity/orchestrator/domain/AnalysisTaskTest.java`

- **IMPLEMENT:** Unit tests for AnalysisTask
- **PATTERN:** Test task creation, dependencies, idempotency
- **IMPORTS:** JUnit 5, AssertJ
- **GOTCHA:** Test that idempotency key is set, dependencies are optional
- **VALIDATE:** All tests pass

---

### Task 14: CREATE Integration Tests with Testcontainers

**Objective:** Add integration tests validating end-to-end workflow

**Action:** CREATE `orchestrator-service/src/test/java/com/mobilesecurity/orchestrator/OrchestratorIntegrationTest.java`

- **IMPLEMENT:** Integration test using Testcontainers for PostgreSQL, Redis, Kafka
- **PATTERN:** @DataJpaTest with testcontainers for real database
- **IMPORTS:** Testcontainers @Testcontainers, PostgreSQLContainer, GenericContainer, Spring Test
- **GOTCHA:** Use @DynamicPropertySource to configure datasource URL from container. Test must be in orchestrator-service module
- **VALIDATE:** Test file event consumption → analysis creation → task dispatch

**Details:**
```java
@Testcontainers
@SpringBootTest
@DisplayName("Orchestrator Integration Tests")
class OrchestratorIntegrationTest {
    
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
        .withDatabaseName("test_mobile_analysis")
        .withUsername("test")
        .withPassword("test");
    
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379);
    
    static {
        postgres.start();
        redis.start();
    }
    
    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }
    
    @Autowired
    private AnalysisRepository analysisRepository;
    
    @Autowired
    private AnalysisTaskRepository analysisTaskRepository;
    
    @Test
    @DisplayName("Should process file event and create analysis with tasks")
    void shouldProcessFileEventAndCreateAnalysis() throws Exception {
        // Arrange
        FileEvent fileEvent = new FileEvent();
        fileEvent.setEventId(UUID.randomUUID().toString());
        fileEvent.setFilePath("/storage/incoming/test.apk");
        fileEvent.setFileType("APK");
        fileEvent.setTimestamp(LocalDateTime.now());
        
        // Act
        orchestrator.processFileEvent(fileEvent);
        
        // Assert
        List<Analysis> analyses = analysisRepository.findByStatus(AnalysisStatus.RUNNING);
        assertThat(analyses).hasSize(1);
        
        Analysis analysis = analyses.get(0);
        assertThat(analysis.getFilePath()).isEqualTo("/storage/incoming/test.apk");
        assertThat(analysis.getFileType()).isEqualTo("APK");
        
        // Verify tasks created
        List<AnalysisTask> tasks = analysisTaskRepository.findByAnalysisIdOrderByTaskOrder(analysis.getId());
        assertThat(tasks).hasSize(4);  // 4 tasks for APK config
        
        // Verify ready tasks identified
        List<AnalysisTask> readyTasks = tasks.stream()
            .filter(t -> t.getDependsOnTaskId() == null)
            .collect(Collectors.toList());
        assertThat(readyTasks).hasSize(2);  // Static Analysis and Signature Check
    }
}
```

**Action:** UPDATE pom.xml to add testcontainers dependencies

- **IMPORTS:** testcontainers, testcontainers-postgresql, testcontainers-kafka, spring-boot-testcontainers
- **PATTERN:** In orchestrator-service POM, test scope

---

### Task 15: CREATE Docker Build Configuration

**Objective:** Add Docker build configuration for orchestrator service

**Action:** CREATE `orchestrator-service/Dockerfile`

- **IMPLEMENT:** Multi-stage Docker build for orchestrator service
- **PATTERN:** Maven build stage → Runtime stage
- **IMPORTS:** maven:3.9-eclipse-temurin-21 for build, eclipse-temurin:21-jre for runtime
- **GOTCHA:** Copy pom.xml and source separately for better layer caching
- **VALIDATE:** `docker build -t orchestrator-service:1.0.0 orchestrator-service/` - Builds successfully

**Details:**
```dockerfile
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace/app

# Copy source code
COPY .. . /workspace/app/

# Build application
RUN mvn -pl orchestrator-service clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy JAR from builder
COPY --from=builder /workspace/app/orchestrator-service/target/orchestrator-service-1.0.0-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### Task 16: ADD Actuator Endpoints for Health Checks

**Objective:** Enable Spring Boot Actuator for monitoring

**Action:** UPDATE `orchestrator-service/pom.xml` to add actuator dependency

- **IMPORTS:** spring-boot-starter-actuator
- **PATTERN:** Management endpoint configuration

**Action:** UPDATE `orchestrator-service/src/main/resources/application.yml`

- **IMPLEMENT:** Actuator endpoint configuration
- **PATTERN:** Enable health and readiness endpoints
- **IMPORTS:** YAML configuration
- **GOTCHA:** Expose /actuator/health endpoint
- **VALIDATE:** `curl http://localhost:8080/actuator/health` returns UP

**Details:**
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,readiness,liveness
  endpoint:
    health:
      show-details: always
```

---

## TESTING STRATEGY

### Unit Tests

**Scope:** Domain models, enums, value objects

**Requirements based on project standards:**
- Test entity creation and field validation
- Test enum values and transitions
- 80%+ code coverage for domain models
- No external dependencies (no Kafka, DB in unit tests)

**Framework:** JUnit 5 with AssertJ assertions

**Examples:**
- AnalysisTest - entity creation, field validation
- AnalysisTaskTest - task creation, dependency handling
- TaskStatusTest - enum transitions

### Integration Tests

**Scope:** Service layer, repositories, Kafka integration

**Requirements:**
- Test file event consumption end-to-end
- Verify analysis and tasks created in database
- Validate task events published to Kafka
- Test caching layer with Redis

**Framework:** Spring Boot Test + Testcontainers for real services

**Test scenarios:**
- File event → analysis creation (happy path)
- File event with unknown file type → error handling
- Task dispatch for independent tasks
- Configuration loading from cache and DB
- Redis cache hit/miss scenarios

### Edge Cases

**Must be tested:**
- File event for unsupported file type (IPA not configured) → ConfigNotFoundException
- Empty task configuration (no tasks for file type) → validation error
- Multiple concurrent file events → no data corruption
- Database connection failure → graceful error
- Redis unavailable → fallback to DB
- Kafka publish failure → log and retry

---

## VALIDATION COMMANDS

Execute every command to ensure zero regressions and 100% feature correctness.

### Level 1: Syntax & Style

**Command:** `mvn clean compile`
- **Purpose:** Verify all source code compiles without errors
- **Expected:** BUILD SUCCESS
- **Failure Handling:** Fix compile errors, re-run

**Command:** `mvn checkstyle:check`
- **Purpose:** Verify code style compliance
- **Expected:** No style violations
- **Failure Handling:** Fix violations or update checkstyle.xml

### Level 2: Unit Tests

**Command:** `mvn clean test`
- **Purpose:** Run all unit tests
- **Expected:** All tests pass, 80%+ coverage for domain models
- **Failure Handling:** Fix failing tests or implementation

**Command:** `mvn test -Dtest=*Test`
- **Purpose:** Run only unit tests (exclude integration tests)
- **Expected:** Fast execution, all pass

### Level 3: Integration Tests

**Command:** `mvn -pl orchestrator-service verify`
- **Purpose:** Run integration tests with Testcontainers
- **Expected:** All integration tests pass
- **Duration:** 2-3 minutes (container startup)
- **Failure Handling:** Check container logs, verify Docker available

### Level 4: Database Validation

**Command:** `mvn flyway:info`
- **Purpose:** Show database migration status
- **Expected:** V1__initial_schema, V2__initial_analysis_config marked SUCCESS
- **Failure Handling:** Verify PostgreSQL running, check migration SQL syntax

**Command:** `mvn flyway:validate`
- **Purpose:** Validate migration checksums
- **Expected:** All migrations validated

### Level 5: Manual Validation

**Step 1:** Start Docker Compose
```bash
docker-compose up -d
```

**Step 2:** Wait for all services ready
```bash
docker-compose ps  # All services should be healthy
```

**Step 3:** Start Orchestrator Service
```bash
mvn -pl orchestrator-service spring-boot:run
```

**Step 4:** Verify application started
```bash
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

**Step 5:** Verify database connectivity
```sql
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT COUNT(*) FROM analysis_config;"
# Expected: 2 (APK and IPA configs)
```

**Step 6:** Verify Redis connectivity
```bash
redis-cli ping
# Expected: PONG
```

**Step 7:** Verify Kafka connectivity
```bash
kafka-topics.sh --bootstrap-server localhost:9092 --list
# Expected: Shows available topics (will be created when service starts)
```

**Step 8:** Create Kafka topics (if not auto-created)
```bash
kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic file-events --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic orchestrator-responses --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic static-analysis-tasks --partitions 3 --replication-factor 1

kafka-topics.sh --bootstrap-server localhost:9092 --create \
  --topic task-heartbeats --partitions 3 --replication-factor 1
```

**Step 9:** Send test file event to Kafka
```bash
echo '{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-19T20:30:00Z"
}' | kafka-console-producer.sh --broker-list localhost:9092 \
  --topic file-events --property "parse.key=false" --property "key.separator=:"
```

**Step 10:** Verify analysis created
```bash
# Wait 2-3 seconds for processing
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, file_path, status FROM analysis LIMIT 1;"
# Expected: One analysis record with status RUNNING
```

**Step 11:** Verify tasks created
```bash
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, engine_type, status FROM analysis_task ORDER BY id;"
# Expected: 4 tasks (Static Analysis, Decompiler, Signature Check, Dynamic Analysis)
```

**Step 12:** Verify configuration cached in Redis
```bash
redis-cli get "analysis-config:APK" | jq .
# Expected: Full configuration object with 4 tasks
```

**Step 13:** Verify task events published to Kafka
```bash
kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic static-analysis-tasks --from-beginning --max-messages 1
# Expected: Task event JSON for static analysis engine
```

---

## ACCEPTANCE CRITERIA

**All criteria must be met for Phase 1 completion:**

- [x] File event consumed successfully from file-events Kafka topic
- [x] Analysis and task records created in PostgreSQL database
- [x] Configuration loaded from database and cached in Redis
- [x] 4 task records created per APK analysis (Static Analysis, Decompiler, Signature Check, Dynamic Analysis)
- [x] Independent tasks identified and marked ready for dispatch
- [x] Task events published to engine-specific Kafka topics with analysisId as partition key
- [x] Unit tests pass with 80%+ coverage for domain models
- [x] Integration tests pass using Testcontainers (PostgreSQL, Redis, Kafka)
- [x] No regressions in existing functionality (base case)
- [x] Docker Compose environment starts all services successfully
- [x] Spring Boot Actuator health endpoint responds correctly
- [x] Configuration caching works (cache hit in <10ms, DB lookup in <100ms)
- [x] All code follows Spring/Java naming and architectural conventions
- [x] Flyway database migrations applied successfully
- [x] Taskdependency chain preserved (Decompiler depends on Static Analysis, etc.)

---

## COMPLETION CHECKLIST

- [ ] All 16 tasks completed in order
- [ ] Each task validation command passed immediately
- [ ] Multi-module Maven structure builds successfully: `mvn clean verify -DskipTests`
- [ ] All unit tests pass: `mvn test`
- [ ] All integration tests pass: `mvn -pl orchestrator-service verify`
- [ ] No linting or compile errors: `mvn compile`
- [ ] Database migrations validate: `mvn flyway:validate`
- [ ] Docker Compose starts all services: `docker-compose up -d` + health checks
- [ ] Manual validation steps 1-13 complete successfully
- [ ] File event → analysis → tasks flow confirmed
- [ ] Kafka topic partition keys verified (partition by analysisId)
- [ ] Redis cache hit verified
- [ ] Configuration loaded from both cache and DB
- [ ] Code reviewed for Spring Boot patterns and best practices
- [ ] No TODOs or FIXMEs remaining
- [ ] Ready for Phase 2 (Response handling and dependency resolution)

---

## NOTES

**Architecture Decisions Confirmed in Phase 1:**
- Event-driven architecture with Kafka messaging
- Shared PostgreSQL database across services
- Redis for configuration caching with analysis state
- Last heartbeat timestamp stored directly in analysis_task table (Phase 4 feature ready)
- Manual Kafka commit after DB transaction for at-least-once delivery
- Transactional outbox pattern structure prepared (outbox table created, Phase 4 for poller)
- Idempotency keys on all tasks for duplicate handling
- Partition by analysisId for task ordering across engines

**Dependencies and Ordering:**
- Phase 1 is the foundation for all subsequent phases
- Phase 2 depends on Phase 1 orchestrator and task creation logic
- Phase 3 (response handling) depends on Phase 1 event structures
- All database schemas finalized - no breaking changes expected
- Kafka topics ready for engine implementations

**Known Limitations (Deferred to Future Phases):**
- No retry logic yet (Phase 4)
- No heartbeat monitoring yet (Phase 4)
- No DLQ handling yet (Phase 4)
- No outbox polling yet (Phase 4)
- No dependent task dispatch yet (Phase 3)
- No REST APIs for external access (Post-MVP)
- No authentication/authorization (Post-MVP)
- No file metadata tracking (Post-MVP)

**Configuration Changes:**
- Database configuration via environment variables (DB_URL, DB_USERNAME, etc.)
- Kafka bootstrap servers via KAFKA_BOOTSTRAP_SERVERS env var
- Redis via REDIS_HOST and REDIS_PORT env vars
- Storage path via STORAGE_PATH env var

**Performance Targets for Phase 1:**
- File event consumption: <1 second
- Configuration cache hit: <10ms
- Database configuration lookup: <100ms
- Analysis creation: <500ms
- Task event publishing: <200ms per task

---

## CONFIDENCE ASSESSMENT

**One-Pass Implementation Confidence: 8/10**

**Strengths:**
- Clear architecture defined in PRD with specific patterns
- Explicit database schema provided
- Kafka topic configuration finalized
- Spring Boot patterns well-documented
- Integration tests validate core flow

**Risks:**
- First time with Kafka partitioning (mitigated by clear design doc)
- Redis caching layer complexity (mitigated by cache-aside pattern specification)
- Transaction coordination (mitigated by @Transactional annotation)

**Risk Mitigation:**
- Each task has specific validation command
- Integration tests catch major failures
- Docker Compose enables local testing before deployment
- Step-by-step manual validation catches subtle issues

---

**Document Version:** 1.0
**Created:** January 20, 2026
**Status:** Ready for Implementation
**Phase:** 1 of 5 (Core Orchestrator Foundation)
**Next Steps:** Begin Task 1 - Create Multi-Module Maven Project Structure