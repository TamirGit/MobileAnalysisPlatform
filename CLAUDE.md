# Mobile Analysis Platform

A scalable, event-driven security backend for analyzing mobile applications (.apk/.ipa) through orchestrated multi-stage workflows with fault tolerance and automatic retry mechanisms.

## Tech Stack

- **Backend**: Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Kafka, Spring Data Redis
- **Message Streaming**: Apache Kafka 3.8.x with KRaft (no ZooKeeper)
- **Databases**: PostgreSQL 16.x (primary), Redis 7.x (cache)
- **Build Tool**: Maven 3.9.x or Gradle 8.x (multi-module)
- **Database Migrations**: Flyway 10.x
- **Testing**: JUnit 5, Mockito, Testcontainers, Spring Boot Test, AssertJ
- **Containerization**: Docker, Docker Compose
- **Utilities**: Lombok, Jackson, SLF4J + Logback

## Project Structure

```
MobileAnalysisPlatform/
├── common/                              # Shared code across services
│   ├── domain/                          # Domain models (Analysis, AnalysisTask, etc.)
│   ├── events/                          # Event DTOs (FileEvent, TaskEvent, etc.)
│   └── config/                          # Shared Spring configs
├── orchestrator-service/                # Main orchestration service
│   ├── src/main/java/.../orchestrator/
│   │   ├── messaging/                   # Kafka consumers/producers
│   │   ├── service/                     # Business logic
│   │   ├── repository/                  # JPA repositories
│   │   └── outbox/                      # Outbox pattern implementation
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/                # Flyway migration scripts
├── engine-common/                       # Shared engine base classes
│   ├── AbstractAnalysisEngine.java      # Base for all engines
│   ├── EngineConfiguration.java
│   └── HeartbeatService.java
├── static-analysis-engine/              # Static analysis engine
├── dynamic-analysis-engine/             # Dynamic analysis engine
├── decompiler-engine/                   # Decompiler engine
├── signature-check-engine/              # Signature verification engine
├── docker-compose.yml                   # Local dev environment
├── pom.xml / build.gradle              # Multi-module build config
└── .claude/
    ├── PRD.md                           # Product Requirements Document
    └── CLAUDE.md                        # This file
```

## Commands

```bash
# Start local infrastructure (PostgreSQL, Redis, Kafka)
docker-compose up -d

# Build all modules
./mvnw clean install
# OR
./gradlew build

# Run orchestrator service
./mvnw spring-boot:run -pl orchestrator-service
# OR
./gradlew :orchestrator-service:bootRun

# Run specific engine
./mvnw spring-boot:run -pl static-analysis-engine
# OR
./gradlew :static-analysis-engine:bootRun

# Run unit tests
./mvnw test
# OR
./gradlew test

# Run integration tests (with Testcontainers)
./mvnw verify
# OR
./gradlew integrationTest

# Stop local infrastructure
docker-compose down

# View Kafka topics
docker exec -it mobile-analysis-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Tail Kafka topic
docker exec -it mobile-analysis-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic file-events --from-beginning
```

## Reference Documentation

| Document | When to Read |
|----------|--------------|  
| `.claude/PRD.md` | Understanding architecture, requirements, features, API specs, implementation phases |
| [Spring Boot 3.x Docs](https://docs.spring.io/spring-boot/docs/current/reference/html/) | Spring framework features, configuration, best practices |
| [Apache Kafka Docs](https://kafka.apache.org/documentation/) | Kafka concepts, consumer/producer configs, stream processing |
| [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html) | Understanding outbox implementation |
| [PostgreSQL 16 Docs](https://www.postgresql.org/docs/16/) | Database features, indexing, performance tuning |

## Code Conventions

### General Principles
- **Resilience First**: Handle failures gracefully with retry mechanisms
- **Idempotency**: All operations must be idempotent (safe to retry)
- **Correlation IDs**: Include analysisId as correlation ID in all logs and events
- **Fail Fast**: Validate early, throw meaningful exceptions
- **Immutability**: Prefer immutable objects where possible
- **Database is Source of Truth**: PostgreSQL first, Redis cache second (best-effort)

### Backend (Java/Spring Boot)

**Package Structure:**
```
com.mobileanalysis.[service-name]/
├── api/           # REST controllers (future)
├── messaging/     # Kafka consumers & producers
├── service/       # Business logic
├── repository/    # Data access
├── domain/        # Domain entities
├── config/        # Spring configurations
└── exception/     # Custom exceptions
```

**Naming Conventions:**
- **Classes**: PascalCase (e.g., `AnalysisOrchestrator`, `TaskEventConsumer`)
- **Methods**: camelCase (e.g., `processFileEvent`, `updateTaskStatus`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_RETRIES`, `DEFAULT_TIMEOUT`)
- **Packages**: lowercase (e.g., `messaging`, `service`)

**Spring Dependency Injection Best Practices:**

**✅ RECOMMENDED: Constructor Injection**
```java
@Service
@Slf4j
public class AnalysisOrchestrator {
    private final AnalysisRepository analysisRepository;
    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    // Constructor injection - recommended
    public AnalysisOrchestrator(
            AnalysisRepository analysisRepository,
            TaskRepository taskRepository,
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.taskRepository = taskRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }
    
    // Business methods...
}
```

**With Lombok @RequiredArgsConstructor (even better):**
```java
@Service
@Slf4j
@RequiredArgsConstructor  // Lombok generates constructor for all 'final' fields
public class AnalysisOrchestrator {
    private final AnalysisRepository analysisRepository;
    private final TaskRepository taskRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    // Business methods...
}
```

**❌ DISCOURAGED: Field Injection**
```java
@Service
@Slf4j
public class AnalysisOrchestrator {
    @Autowired  // ❌ Avoid this pattern
    private AnalysisRepository analysisRepository;
    
    @Autowired  // ❌ Avoid this pattern
    private TaskRepository taskRepository;
    
    // Business methods...
}
```

**Why Constructor Injection?**
1. **Immutability**: Fields can be `final`, ensuring they're never null or changed
2. **Testability**: Easy to create instances in tests without Spring context
3. **Explicit Dependencies**: Constructor shows all dependencies clearly
4. **Null Safety**: Spring ensures all dependencies are provided at construction time
5. **Circular Dependency Detection**: Fails fast if circular dependencies exist

**Testing with Constructor Injection:**
```java
@Test
void shouldProcessFileEvent() {
    // Easy to create with mocks - no Spring context needed
    AnalysisRepository mockRepo = mock(AnalysisRepository.class);
    TaskRepository mockTaskRepo = mock(TaskRepository.class);
    
    AnalysisOrchestrator orchestrator = new AnalysisOrchestrator(
        mockRepo, mockTaskRepo, mockOutbox, objectMapper
    );
    
    // Test...
}
```

**Key Patterns:**
- Use `@Service` for business logic classes
- Use `@Repository` for data access classes
- Use `@KafkaListener` with **manual commit** for consumers
- Use `@Transactional` for operations modifying multiple entities
- Use `@Scheduled(fixedDelay = ...)` for periodic tasks (e.g., outbox polling)
- Use `@Slf4j` (Lombok) for logging
- Use **constructor injection** (preferably with Lombok `@RequiredArgsConstructor`)

**Error Handling:**
```java
// Always log with correlation ID (analysisId)
log.error("Failed to process task. correlationId={}, taskId={}, error={}", 
    correlationId, taskId, e.getMessage(), e);

// Throw custom exceptions with context
throw new TaskProcessingException(
    String.format("Task %d failed after %d attempts", taskId, attempts), e);
```

**Kafka Consumers with Manual Commit:**
```java
@KafkaListener(
    topics = "${app.kafka.topics.file-events}",
    groupId = "${spring.kafka.consumer.group-id}",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consume(FileEvent event, Acknowledgment acknowledgment) {
    String correlationId = event.getAnalysisId(); // Use analysisId as correlation ID
    MDC.put("correlationId", correlationId);
    
    try {
        orchestrator.processFileEvent(event); // DB transaction inside
        acknowledgment.acknowledge(); // Commit offset AFTER DB success
    } catch (Exception e) {
        log.error("Processing failed. correlationId={}", correlationId, e);
        // Don't acknowledge - Kafka will redeliver
    } finally {
        MDC.clear();
    }
}
```

**Kafka Producers with Partition Key:**
```java
// Always use analysisId as partition key for task ordering
ProducerRecord<String, TaskEvent> record = new ProducerRecord<>(
    topic,
    event.getAnalysisId().toString(), // Partition key = analysisId
    taskEvent
);
kafkaTemplate.send(record);
```

**Transactional Outbox Pattern:**
```java
@Transactional
public void createAnalysis(FileEvent fileEvent) {
    // 1. Update domain state
    Analysis analysis = analysisRepository.save(...);
    List<AnalysisTask> tasks = createTasks(analysis);
    taskRepository.saveAll(tasks);
    
    // 2. Write events to outbox (same transaction)
    for (AnalysisTask task : readyTasks) {
        OutboxEvent outboxEvent = OutboxEvent.builder()
            .aggregateId(analysis.getId().toString())
            .eventType("TASK_READY")
            .topic(getTopicForEngineType(task.getEngineType()))
            .partitionKey(analysis.getId().toString()) // analysisId for ordering
            .payload(toJson(taskEvent))
            .build();
        outboxRepository.save(outboxEvent);
    }
    
    // Both succeed or both rollback
}
```

**Outbox Poller (Spring @Scheduled):**
```java
@Component
@RequiredArgsConstructor  // Constructor injection
public class OutboxPoller {
    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void pollOutbox() {
        List<OutboxEvent> events = outboxRepository
            .findUnprocessedBatch(batchSize); // batchSize = 50
        
        for (OutboxEvent event : events) {
            try {
                ProducerRecord<String, Object> record = new ProducerRecord<>(
                    event.getTopic(),
                    event.getPartitionKey(), // analysisId for ordering
                    parsePayload(event.getPayload())
                );
                kafkaTemplate.send(record).get(); // Synchronous send
                
                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                outboxRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to publish event, will retry. eventId={}", 
                    event.getId(), e);
                // Leave processed=false, will retry next poll
            }
        }
    }
}
```

**State Management Pattern (DB-First, Redis-Second):**
```java
@Service
@RequiredArgsConstructor  // Constructor injection
public class StateManager {
    private final AnalysisTaskRepository taskRepository;
    private final RedisTemplate<String, AnalysisState> redisTemplate;
    
    @Transactional
    public void updateTaskStatus(Long taskId, TaskStatus newStatus, String outputPath) {
        // 1. Update database FIRST (in transaction)
        AnalysisTask task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));
        task.setStatus(newStatus);
        task.setOutputPath(outputPath);
        task.setUpdatedAt(Instant.now());
        taskRepository.save(task);
        
        // Transaction commits here
        
        // 2. Update Redis cache SECOND (best-effort, outside transaction)
        try {
            String cacheKey = "analysis-state:" + task.getAnalysisId();
            AnalysisState state = buildAnalysisState(task.getAnalysisId());
            redisTemplate.opsForValue().set(cacheKey, state);
        } catch (Exception e) {
            log.warn("Cache update failed, will be stale. analysisId={}, taskId={}", 
                task.getAnalysisId(), taskId, e);
            // Don't throw - DB is source of truth, cache will self-heal on next read
        }
    }
    
    public AnalysisState getAnalysisState(UUID analysisId) {
        String cacheKey = "analysis-state:" + analysisId;
        
        // Try cache first
        AnalysisState cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Cache miss - read from DB and repopulate
        AnalysisState state = buildAnalysisStateFromDB(analysisId);
        try {
            redisTemplate.opsForValue().set(cacheKey, state);
        } catch (Exception e) {
            log.warn("Cache population failed. analysisId={}", analysisId, e);
        }
        return state;
    }
}
```

**Idempotency Handling:**
```java
// Check idempotency key before processing
Optional<AnalysisTask> existing = taskRepository
    .findByIdempotencyKey(event.getIdempotencyKey());
    
if (existing.isPresent()) {
    log.info("Duplicate event detected. idempotencyKey={}", 
        event.getIdempotencyKey());
    return; // No-op - safe to skip
}

// Process normally if not duplicate
```

**Heartbeat Handling:**
```java
// Heartbeat stored directly in analysis_task table
@Transactional
public void updateHeartbeat(Long taskId) {
    AnalysisTask task = taskRepository.findById(taskId)
        .orElseThrow(() -> new TaskNotFoundException(taskId));
    task.setLastHeartbeatAt(Instant.now());
    taskRepository.save(task);
}

// Stale task detection
@Scheduled(fixedDelayString = "${app.heartbeat.check-interval-ms:60000}")
public void checkStaleHeartbeats() {
    Instant staleThreshold = Instant.now().minus(2, ChronoUnit.MINUTES);
    List<AnalysisTask> staleTasks = taskRepository
        .findByStatusAndLastHeartbeatAtBefore(TaskStatus.RUNNING, staleThreshold);
    
    for (AnalysisTask task : staleTasks) {
        log.warn("Stale task detected. taskId={}, lastHeartbeat={}", 
            task.getId(), task.getLastHeartbeatAt());
        markTaskAsFailed(task, "No heartbeat for 2 minutes");
    }
}
```

### Configuration Management

**application.yml Structure:**
```yaml
spring:
  application:
    name: service-name
  profiles:
    active: ${SPRING_PROFILE:local}
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mobile_analysis}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      enable-auto-commit: false  # IMPORTANT: Manual commit after DB transaction
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  storage:
    base-path: ${STORAGE_PATH:/storage}
  outbox:
    poll-interval-ms: 1000  # Poll every 1 second
    batch-size: 50          # Process 50 events per poll
  heartbeat:
    check-interval-ms: 60000  # Check for stale tasks every 60 seconds
    stale-threshold-minutes: 2
  engine:
    type: STATIC_ANALYSIS  # Engine-specific config
    timeout-seconds: ${ENGINE_TIMEOUT:300}
    max-retries: ${MAX_RETRIES:3}
    heartbeat-interval-ms: 30000  # Send heartbeat every 30 seconds
```

**Best Practices:**
- Use environment variables for all environment-specific values
- Provide sensible defaults for local development
- Document all configuration properties in comments
- Group related configs under `app.*` namespace
- Always disable Kafka auto-commit: `enable-auto-commit: false`

### Event Design

**All events must include:**
- `eventId`: Unique UUID for this event
- `timestamp`: ISO-8601 timestamp
- `correlationId`: Analysis ID for tracing (analysisId serves as correlation ID)

**Example:**
```java
@Data
@Builder
public class TaskEvent {
    private String eventId;           // UUID.randomUUID()
    private Long taskId;
    private String analysisId;         // Also serves as correlationId
    private String engineType;
    private String filePath;
    private String dependentTaskOutputPath;
    private String idempotencyKey;     // UUID for duplicate detection
    private Integer timeoutSeconds;
    private Instant timestamp;         // Instant.now()
}
```

**Kafka Topic Partitioning:**
- **All task-related events**: Use `analysisId` as partition key
- **Rationale**: Ensures all tasks for same analysis go to same partition, maintaining order
- **Example**: `ProducerRecord<>(topic, analysisId.toString(), taskEvent)`

## Logging

### Logging Standards

**All logs must include:**
- Correlation ID (analysisId)
- Structured key-value pairs
- Appropriate log level

**Log Levels:**
- `ERROR`: Failures requiring attention (task failed after retries, system errors)
- `WARN`: Recoverable issues (retry triggered, cache miss, stale heartbeat)
- `INFO`: Important business events (analysis started, task completed)
- `DEBUG`: Detailed flow information (state transitions, dependency checks)
- `TRACE`: Very detailed (Kafka offsets, cache operations)

**Examples:**
```java
// INFO - Business events
log.info("Analysis started. correlationId={}, fileType={}, configId={}", 
    analysisId, fileType, configId);

// WARN - Recoverable
log.warn("Task retry triggered. correlationId={}, taskId={}, attempt={}/{}",
    analysisId, taskId, currentAttempt, maxRetries);

log.warn("Cache update failed, will be stale. correlationId={}, taskId={}", 
    analysisId, taskId, e);

// ERROR - Failure
log.error("Task failed permanently. correlationId={}, taskId={}, error={}",
    analysisId, taskId, e.getMessage(), e);

// DEBUG - Flow
log.debug("Dependency check. correlationId={}, taskId={}, dependsOn={}, ready={}",
    analysisId, taskId, dependsOnTaskId, isReady);
```

**MDC (Mapped Diagnostic Context):**
```java
try {
    MDC.put("correlationId", analysisId.toString());
    MDC.put("taskId", taskId.toString());
    // Your code here
    log.info("Processing..."); // Automatically includes MDC values
} finally {
    MDC.clear();
}
```

**Logback Configuration (logback-spring.xml):**
```xml
<pattern>
    %d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - [correlationId=%X{correlationId}] [taskId=%X{taskId}] - %msg%n
</pattern>
```

## Database

### Connection Details (Local Dev)
```yaml
Host: localhost
Port: 5432
Database: mobile_analysis
Username: postgres
Password: postgres
```

### Schema Management
- All schema changes via **Flyway migrations** in `src/main/resources/db/migration/`
- Naming: `V{version}__{description}.sql` (e.g., `V001__create_analysis_tables.sql`)
- Never modify existing migrations - create new ones
- Test migrations locally before committing

### Key Tables
- `analysis_config` - Analysis workflow configurations
- `task_config` - Task definitions with dependencies (template level)
- `analysis` - Runtime analysis instances
- `analysis_task` - Runtime task instances with state, **includes `last_heartbeat_at` column**
- `outbox` - Transactional outbox for event publishing

### Key Columns to Note
- `task_config.depends_on_task_config_id` - Template dependency (configuration)
- `analysis_task.depends_on_task_id` - Runtime dependency (specific analysis)
- `analysis_task.last_heartbeat_at` - Heartbeat timestamp (updated every 30s, checked for staleness)
- `analysis_task.idempotency_key` - UUID for duplicate detection (unique constraint)

### Indexing Strategy
- Index all foreign keys
- Index status columns used in WHERE clauses
- Index `idempotency_key` (unique)
- Index `last_heartbeat_at` with partial index: `WHERE status = 'RUNNING'`
- Use composite indexes for common query patterns

**Example Indexes:**
```sql
CREATE INDEX idx_analysis_task_status ON analysis_task(status);
CREATE INDEX idx_analysis_task_idempotency ON analysis_task(idempotency_key);
CREATE INDEX idx_analysis_task_heartbeat ON analysis_task(last_heartbeat_at) 
    WHERE status = 'RUNNING';
```

### Query Best Practices
- Use **pagination** for large result sets
- Fetch only needed columns (projection)
- Use `@Query` with native SQL for complex queries
- Enable query logging in dev: `spring.jpa.show-sql=true`
- PostgreSQL is source of truth - always read from DB if cache unavailable

## Testing Strategy

### Testing Pyramid

**Unit Tests (70%):**
- Test business logic in isolation
- Mock external dependencies (repositories, Kafka producers, Redis)
- Fast execution (<1 second per test)
- Location: `src/test/java/` alongside source files

**Integration Tests (25%):**
- Test with real infrastructure (Testcontainers)
- Verify database operations, Kafka consumption/production, Redis caching
- Test transactional behavior and rollback scenarios
- Location: `src/test/java/` with `@SpringBootTest`

**End-to-End Tests (5%):**
- Full workflow tests (file event → analysis completion)
- Multiple services interacting
- Verify fault tolerance scenarios (service restarts, Kafka downtime)
- Location: Separate `e2e-tests/` module (future)

### Test Organization

```
src/test/java/com/mobileanalysis/[service]/
├── service/              # Unit tests for services
├── messaging/            # Integration tests for Kafka
├── repository/           # Integration tests for DB
└── integration/          # Full integration tests
```

### Testing Conventions

**Naming:**
- Unit test classes: `{ClassName}Test` (e.g., `AnalysisOrchestratorTest`)
- Integration test classes: `{ClassName}IntegrationTest`
- Test methods: `should{ExpectedBehavior}_when{Condition}` (e.g., `shouldCreateAnalysis_whenValidFileEvent`)

**Testcontainers Setup:**
```java
@SpringBootTest
@Testcontainers
class AnalysisRepositoryIntegrationTest {
    
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
    void shouldSaveAnalysis_whenValidData() {
        // Test implementation
    }
}
```

**Assertions:**
```java
// Use AssertJ for fluent assertions
assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.COMPLETED);
assertThat(analysis.getTasks())
    .hasSize(3)
    .allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
```

## Conventional Commits

All commits must follow the [Conventional Commits](https://www.conventionalcommits.org/) specification:

### Format
```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, missing semicolons, etc.)
- `refactor`: Code refactoring (no functional changes)
- `perf`: Performance improvements
- `test`: Adding or updating tests
- `build`: Build system or dependency changes
- `ci`: CI/CD configuration changes
- `chore`: Other changes (maintenance, tooling)

### Scopes
- `orchestrator`: Orchestrator service changes
- `engine`: Engine-related changes (specify engine if possible)
- `common`: Common/shared module changes
- `db`: Database migrations or schema changes
- `kafka`: Kafka configuration or event changes
- `config`: Configuration changes
- `docker`: Docker or docker-compose changes

### Examples
```bash
# Feature
feat(orchestrator): add dependency resolution logic

Implements task dependency checking and automatic dispatch
of ready-to-run tasks after parent completion.

Closes #42

# Bug fix
fix(engine): prevent memory leak in heartbeat service

Heartbeat scheduled task was not being cancelled on shutdown,
causing thread pool exhaustion.

# Documentation
docs: update README with local setup instructions

# Refactoring
refactor(common): extract event creation to builder pattern

# Database
feat(db): add indexes for performance optimization

Adds partial index on analysis_task.last_heartbeat_at for
stale task detection queries.

# Breaking change
feat(kafka)!: change task event schema to include correlation ID

BREAKING CHANGE: TaskEvent now requires correlationId field.
All consumers must be updated to handle new schema.
```

### Commit Guidelines
- Keep subject line under 72 characters
- Use imperative mood ("add feature" not "added feature")
- Don't capitalize first letter
- No period at the end of subject
- Provide detailed body for non-trivial changes
- Reference issues/PRs in footer

## PIV Loop Standards

**PIV Loop** = Plan → Implement → Verify cycle for iterative development.

### Planning Phase
1. **Review PRD** section relevant to the feature
2. **Create subtasks** - Break feature into small, testable units
3. **Identify dependencies** - What needs to exist first?
4. **Define acceptance criteria** - How will we know it works?
5. **Estimate effort** - Rough sizing (S/M/L)

### Implementation Phase
1. **Create feature branch**: `feature/{type}/{short-description}`
   - Example: `feature/orchestrator/dependency-resolution`
2. **Write tests first** (TDD when applicable)
3. **Implement minimum code** to pass tests
4. **Follow code conventions** (see sections above)
5. **Add logging** with correlation IDs (analysisId)
6. **Handle idempotency** - Check idempotency keys
7. **Update DB state first, cache second** (DB-first pattern)
8. **Use analysisId as Kafka partition key** for all task events
9. **Update comments/docs** inline
10. **Commit frequently** with conventional commits

### Verification Phase
1. **Run all tests** locally
   ```bash
   ./mvnw verify
   ```
2. **Manual testing** in Docker Compose environment
3. **Check logs** for proper correlation ID flow (analysisId)
4. **Verify in database** - State changes as expected?
5. **Check Redis cache** - Cache updated correctly?
6. **Kafka verification** - Events published with correct partition key?
7. **Integration test** - Full flow works?
8. **Idempotency test** - Duplicate events are no-ops?

### PIV Loop Checklist

Before marking a PIV loop complete:

- [ ] All tests passing (unit + integration)
- [ ] Code follows conventions (naming, structure, patterns)
- [ ] Constructor injection used (preferably with @RequiredArgsConstructor)
- [ ] Logging includes correlation IDs (analysisId)
- [ ] Error handling implemented with retries
- [ ] Database changes have Flyway migration
- [ ] Configuration documented in application.yml
- [ ] Idempotency considered and handled
- [ ] DB-first, Redis-second pattern followed
- [ ] Kafka events use analysisId as partition key
- [ ] Manual commit pattern used for Kafka consumers
- [ ] Heartbeat updates go to analysis_task.last_heartbeat_at
- [ ] Manual testing completed in Docker environment
- [ ] Conventional commits used
- [ ] Code reviewed (self-review at minimum)
- [ ] Documentation updated if needed

### PIV Loop Anti-Patterns

**Avoid:**
- ❌ Implementing large features in single commit
- ❌ Skipping tests ("will add later")
- ❌ Hardcoding values instead of configuration
- ❌ Missing error handling
- ❌ No correlation ID in logs
- ❌ Ignoring idempotency
- ❌ Not testing with Docker Compose
- ❌ Unclear commit messages
- ❌ Kafka auto-commit enabled
- ❌ Redis failures blocking operations
- ❌ Using random partition keys for Kafka events
- ❌ Field injection with @Autowired

**Instead:**
- ✅ Small, incremental changes
- ✅ Tests alongside implementation
- ✅ Configuration-driven behavior
- ✅ Comprehensive error handling
- ✅ Structured logging with correlation IDs
- ✅ Idempotent operations
- ✅ Real environment testing
- ✅ Conventional commits with context
- ✅ Manual Kafka commits after DB transaction
- ✅ Best-effort Redis updates with logging
- ✅ analysisId as partition key for ordering
- ✅ Constructor injection with @RequiredArgsConstructor

## Development Workflow

### Starting New Work
1. Pull latest `main` branch
2. Create feature branch: `feature/{scope}/{description}`
3. Review relevant PRD sections
4. Review architecture decisions in PRD Section 6
5. Start PIV loop

### Daily Development
1. Ensure Docker Compose is running
2. Run tests frequently during development
3. Commit small, logical units
4. Push to remote regularly
5. Check logs for correlation ID presence

### Before Creating PR
1. Rebase on latest `main`
2. Squash WIP commits (optional)
3. Ensure all tests pass
4. Run integration tests
5. Self-review code changes
6. Verify all patterns followed (DB-first, manual commit, constructor injection, etc.)
7. Update CLAUDE.md if conventions changed

### Code Review
1. Check adherence to conventions
2. Verify test coverage
3. Validate error handling
4. Confirm logging includes correlation IDs
5. Ensure idempotency
6. Check DB-first, Redis-second pattern
7. Verify Kafka partition keys are analysisId
8. Confirm manual Kafka commits
9. Verify constructor injection used (not field injection)
10. Test locally if possible

---

**Last Updated**: January 21, 2026  
**Version**: 1.2  
**Maintainer**: Development Team
