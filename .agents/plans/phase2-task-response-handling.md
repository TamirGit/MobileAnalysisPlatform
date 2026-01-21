# Feature: Phase 2 - Task Response Handling & Dependency Resolution

The following plan should be complete, but it's important that you validate documentation and codebase patterns and task sanity before you start implementing.

Pay special attention to naming of existing utils, types, and models. Import from the right files etc.

## Feature Description

Phase 2 builds upon the Phase 1 orchestrator foundation by implementing the complete task lifecycle management system. This phase enables engines to report task completion back to the orchestrator, which then resolves dependencies and dispatches the next wave of tasks. It includes the engine framework (`engine-common` module), the first concrete engine (static analysis), and critical fault-tolerance features like heartbeat monitoring and retry logic.

This phase transforms the platform from a one-way task dispatcher into a full bidirectional workflow orchestrator capable of managing complex multi-stage analysis pipelines with automatic dependency resolution, failure recovery, and completion detection.

## User Story

As a Security Operations Engineer
I want task completion events from engines to automatically trigger dependent tasks
So that complex analysis workflows execute automatically without manual coordination

## Problem Statement

Phase 1 successfully dispatches initial tasks from file events but lacks the ability to:
- Receive and process task completion events from engines
- Resolve task dependencies and dispatch dependent tasks automatically
- Detect when an entire analysis workflow has completed
- Retry failed tasks with exponential backoff
- Monitor long-running tasks for health (heartbeat)
- Provide a reusable engine framework for building analysis engines
- Execute actual analysis work (no concrete engines exist yet)

Without Phase 2, analyses start but never complete, dependent tasks never execute, and the system cannot recover from failures automatically.

## Solution Statement

Implement a complete task response handling system with the following components:

1. **TaskResponseConsumer**: Kafka consumer that processes task completion events from engines
2. **DependencyResolver**: Service that identifies and dispatches dependent tasks when parent tasks complete
3. **AnalysisCompletionService**: Service that detects workflow completion and marks analyses as COMPLETED or FAILED
4. **Engine Framework**: Abstract base class and shared utilities for building analysis engines
5. **StaticAnalysisEngine**: First concrete engine implementation serving as reference
6. **HeartbeatService**: Long-running task health monitoring with automatic timeout detection
7. **RetryService**: Intelligent retry logic with exponential backoff and DLQ routing

## Feature Metadata

**Feature Type**: New Capability
**Estimated Complexity**: High
**Primary Systems Affected**: 
- Orchestrator Service (new consumers and services)
- Common Module (new event DTOs)
- Engine-Common Module (new base classes)
- Static-Analysis-Engine Module (new engine service)

**Dependencies**: 
- Phase 1 orchestrator foundation (COMPLETED)
- Spring Kafka
- Spring Data JPA
- Spring Data Redis

---

## CONTEXT REFERENCES

### Relevant Codebase Files - IMPORTANT: YOU MUST READ THESE FILES BEFORE IMPLEMENTING!

#### Phase 1 Foundation Files

- `common/src/main/java/com/mobileanalysis/common/domain/AnalysisTask.java` - Domain model for tasks, shows status transitions
- `common/src/main/java/com/mobileanalysis/common/domain/TaskStatus.java` - Task status enum (PENDING, DISPATCHED, RUNNING, COMPLETED, FAILED)
- `common/src/main/java/com/mobileanalysis/common/domain/AnalysisStatus.java` - Analysis status enum
- `common/src/main/java/com/mobileanalysis/common/events/TaskEvent.java` - Task dispatch event structure
- `common/src/main/java/com/mobileanalysis/common/events/TaskResponseEvent.java` - Task response event structure (already defined)
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/AnalysisOrchestrator.java` (lines 1-250) - Analysis creation and initial task dispatch logic to mirror
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/ConfigurationService.java` - Configuration loading pattern for caching
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/FileEventConsumer.java` - Kafka consumer pattern with manual commit
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/outbox/OutboxPoller.java` - Outbox polling and Kafka publishing pattern
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/AnalysisEntity.java` - JPA entity structure
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/domain/AnalysisTaskEntity.java` - JPA task entity with heartbeat field
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/repository/AnalysisTaskRepository.java` - Task repository with dependency queries

#### Configuration Files

- `orchestrator-service/src/main/resources/application.yml` - Kafka topic configuration, consumer groups
- `docker-compose.yml` - Infrastructure setup including topic creation
- `CLAUDE.md` - Project coding standards and patterns
- `.claude/PRD.md` - Complete Phase 2 requirements and architecture decisions

### New Files to Create

#### Orchestrator Service
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/TaskResponseConsumer.java` - Consumer for engine responses
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/DependencyResolver.java` - Dependency resolution logic
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/AnalysisCompletionService.java` - Completion detection
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/HeartbeatMonitor.java` - Scheduled heartbeat checker
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/TaskRetryService.java` - Retry logic with backoff

#### Common Module
- `common/src/main/java/com/mobileanalysis/common/events/HeartbeatEvent.java` - Heartbeat event DTO

#### Engine Common Module (NEW MODULE)
- `engine-common/pom.xml` - Maven configuration for engine framework
- `engine-common/src/main/java/com/mobileanalysis/engine/AbstractAnalysisEngine.java` - Base engine class
- `engine-common/src/main/java/com/mobileanalysis/engine/HeartbeatService.java` - Engine-side heartbeat sender
- `engine-common/src/main/java/com/mobileanalysis/engine/EngineConfiguration.java` - Shared engine config

#### Static Analysis Engine (NEW MODULE)
- `static-analysis-engine/pom.xml` - Maven configuration
- `static-analysis-engine/src/main/java/com/mobileanalysis/engine/staticanalysis/StaticAnalysisApplication.java` - Spring Boot app
- `static-analysis-engine/src/main/java/com/mobileanalysis/engine/staticanalysis/StaticAnalysisEngine.java` - Concrete engine
- `static-analysis-engine/src/main/resources/application.yml` - Engine configuration

#### Tests
- `orchestrator-service/src/test/java/com/mobileanalysis/orchestrator/integration/Phase2IntegrationTest.java` - End-to-end Phase 2 test
- `engine-common/src/test/java/com/mobileanalysis/engine/AbstractAnalysisEngineTest.java` - Engine framework tests
- `static-analysis-engine/src/test/java/com/mobileanalysis/engine/staticanalysis/StaticAnalysisEngineTest.java` - Static engine tests

### Relevant Documentation - YOU SHOULD READ THESE BEFORE IMPLEMENTING!

- [Spring Kafka Manual Commit](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/message-listeners.html#committing-offsets)
  - Section: Manual Commit with @KafkaListener
  - Why: Required for transactional consumer pattern
  
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
  - Section: Implementation patterns
  - Why: Already used in Phase 1, continue same pattern

- [Spring @Scheduled](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
  - Section: @Scheduled annotation with fixedDelay
  - Why: For heartbeat monitor implementation

- [Spring @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)
  - Section: @Async annotation
  - Why: For non-blocking task processing in engines

- [.claude/PRD.md - Phase 2 Section](https://github.com/TamirGit/MobileAnalysisPlatform/blob/main/.claude/PRD.md)
  - Section: Implementation Phases - Phase 2 & Phase 3
  - Why: Complete Phase 2 requirements and acceptance criteria

- [CLAUDE.md - Coding Standards](https://github.com/TamirGit/MobileAnalysisPlatform/blob/main/CLAUDE.md)
  - Section: Constructor Injection Pattern, Kafka Patterns, Transaction Management
  - Why: Project-specific patterns that MUST be followed

### Patterns to Follow

**Constructor Injection Pattern (CRITICAL - from recent Phase 1 refinements):**
```java
@Service
@RequiredArgsConstructor // Lombok annotation
public class TaskResponseConsumer {
    private final AnalysisTaskRepository taskRepository;
    private final DependencyResolver dependencyResolver;
    private final AnalysisCompletionService completionService;
    
    // NO @Autowired on fields - constructor injection only
}
```

**Kafka Manual Commit Pattern (from FileEventConsumer):**
```java
@KafkaListener(
    topics = "orchestrator-responses",
    groupId = "orchestrator-group",
    containerFactory = "kafkaListenerContainerFactory"
)
public void handleTaskResponse(TaskResponseEvent event, Acknowledgment ack) {
    try {
        // 1. Set MDC correlation ID
        MDC.put("analysisId", event.getAnalysisId().toString());
        
        // 2. Process in transaction
        processTaskResponse(event);
        
        // 3. Commit offset only after DB transaction succeeds
        ack.acknowledge();
        
        log.info("Task response processed: taskId={}", event.getTaskId());
    } catch (Exception e) {
        log.error("Failed to process task response: taskId={}", event.getTaskId(), e);
        // Do NOT acknowledge - Kafka will redeliver
    } finally {
        MDC.clear();
    }
}
```

**Transactional Service Pattern (from AnalysisOrchestrator):**
```java
@Transactional
public void processTaskCompletion(TaskResponseEvent event) {
    // 1. Update DB in transaction
    AnalysisTaskEntity task = updateTaskStatus(event);
    
    // 2. Write next events to outbox (part of same transaction)
    List<OutboxEvent> outboxEvents = resolveAndDispatchDependentTasks(task);
    outboxRepository.saveAll(outboxEvents);
    
    // 3. Update Redis cache (best-effort, after commit)
    updateCacheAsync(task.getAnalysisId());
    
    // Transaction commits here
}
```

**Redis Cache Update Pattern (DB-first, from ConfigurationService):**
```java
// Write path
@Transactional
public void updateTaskStatus(Long taskId, TaskStatus status) {
    // 1. Update PostgreSQL (source of truth)
    taskRepository.updateStatus(taskId, status);
    
    // 2. Update Redis cache (best-effort)
    try {
        redisTemplate.opsForValue().set(
            "analysis-state:" + analysisId,
            getAnalysisState(analysisId)
        );
    } catch (Exception e) {
        log.warn("Redis cache update failed - will self-heal on next read", e);
    }
}

// Read path  
public AnalysisState getAnalysisState(UUID analysisId) {
    // 1. Try Redis
    String key = "analysis-state:" + analysisId;
    AnalysisState cached = redisTemplate.opsForValue().get(key);
    if (cached != null) {
        return cached;
    }
    
    // 2. Fallback to DB
    AnalysisState state = loadFromDatabase(analysisId);
    
    // 3. Populate cache
    try {
        redisTemplate.opsForValue().set(key, state);
    } catch (Exception e) {
        log.warn("Failed to populate cache", e);
    }
    
    return state;
}
```

**Outbox Event Creation Pattern:**
```java
private OutboxEventEntity createTaskDispatchEvent(AnalysisTaskEntity task) {
    TaskEvent event = new TaskEvent(
        UUID.randomUUID(),
        task.getId(),
        task.getAnalysisId(),
        task.getEngineType(),
        task.getAnalysis().getFilePath(),
        task.getDependsOnTask() != null ? task.getDependsOnTask().getOutputPath() : null,
        task.getIdempotencyKey(),
        task.getTaskConfig().getTimeoutSeconds(),
        Instant.now()
    );
    
    return OutboxEventEntity.builder()
        .aggregateType("AnalysisTask")
        .aggregateId(task.getId().toString())
        .eventType("TASK_DISPATCHED")
        .topic(task.getEngineType().getTopic()) // e.g., "static-analysis-tasks"
        .partitionKey(task.getAnalysisId().toString()) // analysisId for ordering
        .payload(objectMapper.writeValueAsString(event))
        .build();
}
```

**Logging Pattern with Correlation IDs:**
```java
// All logs must include analysisId for tracing
log.info("Processing task response: analysisId={}, taskId={}, status={}", 
    event.getAnalysisId(), event.getTaskId(), event.getStatus());

// Use MDC for automatic inclusion in all logs
MDC.put("analysisId", analysisId.toString());
MDC.put("taskId", taskId.toString());
```

**Abstract Engine Template Pattern (for engine-common):**
```java
public abstract class AbstractAnalysisEngine {
    
    @KafkaListener(topics = "#{engineConfig.topic}")
    public final void consumeTask(TaskEvent event, Acknowledgment ack) {
        HeartbeatService heartbeat = startHeartbeat(event);
        try {
            // Template method pattern
            TaskResult result = processTask(event);
            sendSuccessResponse(event, result);
            ack.acknowledge();
        } catch (Exception e) {
            sendFailureResponse(event, e);
            // Do NOT acknowledge if failure is not recoverable
        } finally {
            heartbeat.stop();
        }
    }
    
    // Concrete engines implement this
    protected abstract TaskResult processTask(TaskEvent event) throws Exception;
    
    private void startHeartbeat(TaskEvent event) {
        // Send heartbeat every 30 seconds
    }
}
```

**Other Patterns:**
- Use Lombok `@RequiredArgsConstructor` for constructor injection
- Use `@Slf4j` for logging
- Use `@Transactional` at service method level
- Partition key = analysisId for all Kafka messages
- Idempotency key on all task events (UUID)

---

## IMPLEMENTATION PLAN

### Phase 1: Response Handling Foundation

Set up the infrastructure for receiving and processing task completion events from engines.

**Tasks:**
- Create TaskResponseConsumer with Kafka listener
- Implement transactional task status update logic
- Write task completion events to database
- Update Redis cache after DB commit

### Phase 2: Dependency Resolution

Enable automatic dispatch of dependent tasks when parent tasks complete.

**Tasks:**
- Create DependencyResolver service
- Implement dependency graph traversal logic
- Generate task dispatch events for ready dependent tasks
- Write events to outbox for downstream processing

### Phase 3: Completion Detection

Detect when entire analysis workflows have completed (or failed).

**Tasks:**
- Create AnalysisCompletionService
- Implement completion check logic (all tasks COMPLETED or at least one FAILED)
- Update analysis status to COMPLETED or FAILED
- Delete Redis cache for completed analyses
- Generate analysis completion summary

### Phase 4: Engine Framework

Build reusable framework for all analysis engines.

**Tasks:**
- Create engine-common module
- Implement AbstractAnalysisEngine base class
- Implement HeartbeatService for engine-side heartbeat sending
- Create EngineConfiguration for shared settings
- Add Spring configuration for Kafka consumers

### Phase 5: Static Analysis Engine

Implement first concrete engine as reference implementation.

**Tasks:**
- Create static-analysis-engine module
- Implement StaticAnalysisEngine extending AbstractAnalysisEngine
- Implement actual static analysis logic (placeholder)
- Configure Kafka topic subscription
- Write task output to filesystem

### Phase 6: Heartbeat Monitoring

Monitor long-running tasks and detect stale/zombie tasks.

**Tasks:**
- Create HeartbeatMonitor scheduled service
- Implement stale task detection (no heartbeat for 2 minutes)
- Automatically mark stale tasks as FAILED
- Trigger retry logic for failed tasks

### Phase 7: Retry Logic & DLQ

Handle task failures with automatic retry and Dead Letter Queue routing.

**Tasks:**
- Create TaskRetryService
- Implement retry logic with exponential backoff
- Check max retry attempts from configuration
- Route exhausted retries to DLQ topic
- Update task error messages

### Phase 8: Integration Testing

Comprehensive end-to-end testing of Phase 2 features.

**Tasks:**
- Create Phase2IntegrationTest with Testcontainers
- Test complete workflow: file event → tasks → completion
- Test dependency resolution (parent → child tasks)
- Test failure scenarios and retry
- Test heartbeat timeout detection

---

## STEP-BY-STEP TASKS

IMPORTANT: Execute every task in order, top to bottom. Each task is atomic and independently testable.

### CREATE common/src/main/java/com/mobileanalysis/common/events/HeartbeatEvent.java

- **IMPLEMENT**: Heartbeat event DTO for engine health reporting
- **PATTERN**: Mirror TaskResponseEvent structure (event ID, timestamp, etc.)
- **IMPORTS**: `java.time.Instant`, `java.util.UUID`, Lombok annotations
- **FIELDS**: 
  - `UUID eventId`
  - `Long taskId`
  - `UUID analysisId`
  - `String engineType`
  - `TaskStatus status` (should be RUNNING)
  - `Instant timestamp`
- **GOTCHA**: Must be serializable for Kafka (use Lombok `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`)
- **VALIDATE**: `mvn clean compile -pl common`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/TaskResponseConsumer.java

- **IMPLEMENT**: Kafka consumer for orchestrator-responses topic
- **PATTERN**: Mirror FileEventConsumer.java (manual commit, MDC correlation ID, error handling)
- **IMPORTS**: 
  - `@KafkaListener`, `Acknowledgment`
  - `@RequiredArgsConstructor`, `@Slf4j`
  - `TaskResponseEvent`
  - Services: `AnalysisTaskRepository`, `DependencyResolver`, `AnalysisCompletionService`
  - `MDC` for correlation IDs
- **KEY LOGIC**:
  1. Set MDC with analysisId and taskId
  2. Call service method to process response (transactional)
  3. Acknowledge offset only after success
  4. Do NOT acknowledge on failure (Kafka redelivery)
- **GOTCHA**: Must use `containerFactory = "kafkaListenerContainerFactory"` for manual commit
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/DependencyResolver.java

- **IMPLEMENT**: Service to resolve and dispatch dependent tasks
- **PATTERN**: Use AnalysisOrchestrator.java task dispatch logic as reference
- **IMPORTS**:
  - Repositories: `AnalysisTaskRepository`, `OutboxRepository`
  - Domain: `AnalysisTaskEntity`, `OutboxEventEntity`, `TaskStatus`
  - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional`
  - Jackson `ObjectMapper` for JSON serialization
- **KEY METHODS**:
  - `List<OutboxEventEntity> resolveAndDispatch(AnalysisTaskEntity completedTask)` - Find dependent tasks, check if ready, create outbox events
  - `boolean areAllDependenciesMet(AnalysisTaskEntity task)` - Check if parent tasks are COMPLETED
  - `OutboxEventEntity createTaskDispatchEvent(AnalysisTaskEntity task)` - Create outbox event with proper partition key (analysisId)
- **QUERY**: Use `analysisTaskRepository.findByDependsOnTaskId(completedTask.getId())` to find children
- **GOTCHA**: Must write to outbox, NOT directly to Kafka (transactional outbox pattern)
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/AnalysisCompletionService.java

- **IMPLEMENT**: Service to detect and mark analysis completion
- **PATTERN**: Query pattern from ConfigurationService for loading data
- **IMPORTS**:
  - Repositories: `AnalysisRepository`, `AnalysisTaskRepository`
  - Domain: `AnalysisEntity`, `AnalysisStatus`, `TaskStatus`
  - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional`
  - `RedisTemplate` for cache deletion
- **KEY METHODS**:
  - `void checkAndMarkCompletion(UUID analysisId)` - Check if all tasks done, update analysis status
  - `boolean allTasksCompleted(UUID analysisId)` - Query if all tasks are COMPLETED
  - `boolean anyTaskFailed(UUID analysisId)` - Query if any task is FAILED
  - `void deleteCacheForCompletedAnalysis(UUID analysisId)` - Remove from Redis
- **LOGIC**:
  - If all tasks COMPLETED → mark analysis COMPLETED
  - If any task FAILED → mark analysis FAILED
  - Delete cache entry after marking complete
- **GOTCHA**: Must be called AFTER task status update in same transaction
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### UPDATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/TaskResponseConsumer.java

- **IMPLEMENT**: Wire services together in consumer handler
- **PATTERN**: Transactional service call pattern
- **METHOD**: `@Transactional public void processTaskResponse(TaskResponseEvent event)`
- **LOGIC**:
  1. Find task by ID: `taskRepository.findById(event.getTaskId())`
  2. Update task status, output path, error message, completed timestamp
  3. Save task: `taskRepository.save(task)`
  4. Resolve dependencies: `List<OutboxEventEntity> events = dependencyResolver.resolveAndDispatch(task)`
  5. Save outbox events: `outboxRepository.saveAll(events)`
  6. Check completion: `completionService.checkAndMarkCompletion(event.getAnalysisId())`
  7. Log completion
- **GOTCHA**: All DB writes must be in single transaction
- **VALIDATE**: `mvn clean compile -pl orchestrator-service && mvn test -pl orchestrator-service -Dtest=TaskResponseConsumerTest`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/HeartbeatConsumer.java

- **IMPLEMENT**: Kafka consumer for task-heartbeats topic
- **PATTERN**: Mirror TaskResponseConsumer pattern (simpler, just updates timestamp)
- **IMPORTS**: Same as TaskResponseConsumer
- **KEY LOGIC**:
  1. Receive HeartbeatEvent
  2. Update `analysis_task.last_heartbeat_at` timestamp
  3. Acknowledge offset
- **GOTCHA**: High volume topic, keep processing fast (just timestamp update, no complex logic)
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/HeartbeatMonitor.java

- **IMPLEMENT**: Scheduled job to detect stale tasks
- **PATTERN**: OutboxPoller.java scheduled pattern with `@Scheduled(fixedDelay = 60000)` (1 minute)
- **IMPORTS**:
  - `@Scheduled`, `@EnableScheduling`
  - Repositories: `AnalysisTaskRepository`
  - Services: `TaskRetryService`
  - Domain: `TaskStatus`
  - `Instant`, `Duration`
- **KEY LOGIC**:
  1. Query tasks with status=RUNNING and `last_heartbeat_at < now() - 2 minutes`
  2. For each stale task:
     - Mark as FAILED
     - Set error message "Task timeout: no heartbeat for 2 minutes"
     - Trigger retry via TaskRetryService
- **QUERY**: `taskRepository.findStaleTasks(Instant.now().minus(Duration.ofMinutes(2)))`
- **GOTCHA**: Must check last_heartbeat_at is NOT null before comparing
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### CREATE orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/service/TaskRetryService.java

- **IMPLEMENT**: Service for retry logic with exponential backoff
- **PATTERN**: Use outbox pattern for retry dispatch
- **IMPORTS**:
  - Repositories: `AnalysisTaskRepository`, `OutboxRepository`, `TaskConfigRepository`
  - Domain: `AnalysisTaskEntity`, `TaskStatus`, `OutboxEventEntity`
  - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`, `@Transactional`
- **KEY METHODS**:
  - `void retryTask(AnalysisTaskEntity task)` - Check if under max retries, create retry event or send to DLQ
  - `boolean canRetry(AnalysisTaskEntity task)` - Check attempts < max_retries from task_config
  - `OutboxEventEntity createRetryEvent(AnalysisTaskEntity task)` - Create task event with incremented attempts
  - `void sendToDLQ(AnalysisTaskEntity task)` - Create DLQ event
- **LOGIC**:
  - If attempts < max_retries: increment attempts, create outbox event for retry, update status to PENDING
  - If attempts >= max_retries: send to DLQ topic, keep status as FAILED
- **GOTCHA**: Exponential backoff handled by Kafka consumer config, not in retry service
- **VALIDATE**: `mvn clean compile -pl orchestrator-service`

### CREATE engine-common/pom.xml

- **IMPLEMENT**: Maven module for shared engine code
- **PATTERN**: Mirror common/pom.xml structure
- **PARENT**: mobile-analysis-platform parent
- **DEPENDENCIES**:
  - `common` module
  - `spring-boot-starter`
  - `spring-kafka`
  - `lombok`
  - Test dependencies
- **ARTIFACT ID**: `engine-common`
- **PACKAGING**: `jar`
- **GOTCHA**: Do NOT include spring-boot-maven-plugin (not executable, just library)
- **VALIDATE**: `mvn clean compile -pl engine-common`

### CREATE engine-common/src/main/java/com/mobileanalysis/engine/EngineConfiguration.java

- **IMPLEMENT**: Spring configuration class for engine settings
- **PATTERN**: Standard Spring @ConfigurationProperties
- **IMPORTS**: `@ConfigurationProperties`, `@Validated`, Lombok `@Data`
- **PROPERTIES**:
  - `String engineType` (e.g., "STATIC_ANALYSIS")
  - `String topic` (e.g., "static-analysis-tasks")
  - `int heartbeatIntervalMs` (default: 30000)
  - `int timeoutSeconds` (default: 300)
- **PREFIX**: `app.engine`
- **GOTCHA**: Must enable with `@EnableConfigurationProperties(EngineConfiguration.class)` in main app
- **VALIDATE**: `mvn clean compile -pl engine-common`

### CREATE engine-common/src/main/java/com/mobileanalysis/engine/HeartbeatService.java

- **IMPLEMENT**: Service for sending periodic heartbeat events
- **PATTERN**: ScheduledExecutorService for async periodic tasks
- **IMPORTS**:
  - `KafkaTemplate`
  - `HeartbeatEvent`
  - `ScheduledExecutorService`, `Executors`
  - `@Service`, `@RequiredArgsConstructor`, `@Slf4j`
- **KEY METHODS**:
  - `void start(Long taskId, UUID analysisId, String engineType)` - Start sending heartbeats every 30 seconds
  - `void stop()` - Stop scheduled task
  - `private void sendHeartbeat()` - Create and send HeartbeatEvent to task-heartbeats topic
- **GOTCHA**: Must call `stop()` in finally block to prevent thread leaks
- **VALIDATE**: `mvn clean compile -pl engine-common`

### CREATE engine-common/src/main/java/com/mobileanalysis/engine/AbstractAnalysisEngine.java

- **IMPLEMENT**: Abstract base class for all engines (Template Method pattern)
- **PATTERN**: Template method with hooks for concrete engines
- **IMPORTS**:
  - `@KafkaListener`, `Acknowledgment`
  - `KafkaTemplate` for response events
  - Services: `HeartbeatService`
  - Events: `TaskEvent`, `TaskResponseEvent`
  - `@RequiredArgsConstructor`, `@Slf4j`
  - `MDC` for correlation IDs
- **KEY METHODS**:
  - `final void consumeTask(TaskEvent event, Acknowledgment ack)` - Template method (FINAL, not overridable)
  - `protected abstract TaskResult processTask(TaskEvent event)` - Hook method for concrete engines
  - `private void sendSuccessResponse(...)` - Create and send COMPLETED response
  - `private void sendFailureResponse(...)` - Create and send FAILED response
- **LOGIC** (consumeTask):
  1. Set MDC correlation IDs
  2. Start heartbeat service
  3. Call processTask() (implemented by subclass)
  4. Send success response with output path
  5. Acknowledge Kafka offset
  6. On exception: send failure response, do NOT acknowledge (if retryable)
  7. Finally: stop heartbeat, clear MDC
- **GOTCHA**: Topic name injected via SpEL: `@KafkaListener(topics = "#{engineConfig.topic}")`
- **VALIDATE**: `mvn clean compile -pl engine-common`

### CREATE static-analysis-engine/pom.xml

- **IMPLEMENT**: Maven module for static analysis engine
- **PATTERN**: Mirror orchestrator-service/pom.xml structure
- **PARENT**: mobile-analysis-platform parent
- **DEPENDENCIES**:
  - `common` module
  - `engine-common` module
  - `spring-boot-starter`
  - `spring-kafka`
  - `lombok`
- **ARTIFACT ID**: `static-analysis-engine`
- **PACKAGING**: `jar`
- **PLUGIN**: Include spring-boot-maven-plugin (this IS executable)
- **VALIDATE**: `mvn clean compile -pl static-analysis-engine`

### CREATE static-analysis-engine/src/main/resources/application.yml

- **IMPLEMENT**: Spring Boot configuration for static analysis engine
- **PATTERN**: Mirror orchestrator-service/src/main/resources/application.yml
- **CONFIGURATION**:
  ```yaml
  spring:
    application:
      name: static-analysis-engine
    kafka:
      bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
      consumer:
        group-id: static-analysis-group
        enable-auto-commit: false
      producer:
        key-serializer: org.apache.kafka.common.serialization.StringSerializer
        value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
  
  app:
    engine:
      engine-type: STATIC_ANALYSIS
      topic: static-analysis-tasks
      heartbeat-interval-ms: 30000
      timeout-seconds: 300
    storage:
      base-path: ${STORAGE_PATH:/storage}
  ```
- **GOTCHA**: Must match topic name in docker-compose.yml
- **VALIDATE**: Syntax check with `yamllint` or manual review

### CREATE static-analysis-engine/src/main/java/com/mobileanalysis/engine/staticanalysis/StaticAnalysisApplication.java

- **IMPLEMENT**: Spring Boot main application class
- **PATTERN**: Standard Spring Boot application
- **IMPORTS**:
  - `@SpringBootApplication`
  - `@EnableConfigurationProperties(EngineConfiguration.class)`
  - `SpringApplication`
- **CODE**:
  ```java
  @SpringBootApplication(scanBasePackages = {
      "com.mobileanalysis.engine.staticanalysis",
      "com.mobileanalysis.engine"
  })
  @EnableConfigurationProperties(EngineConfiguration.class)
  public class StaticAnalysisApplication {
      public static void main(String[] args) {
          SpringApplication.run(StaticAnalysisApplication.class, args);
      }
  }
  ```
- **GOTCHA**: Must scan both engine package and engine-common package
- **VALIDATE**: `mvn clean compile -pl static-analysis-engine`

### CREATE static-analysis-engine/src/main/java/com/mobileanalysis/engine/staticanalysis/StaticAnalysisEngine.java

- **IMPLEMENT**: Concrete static analysis engine
- **PATTERN**: Extends AbstractAnalysisEngine, implements processTask()
- **IMPORTS**:
  - `AbstractAnalysisEngine` from engine-common
  - `TaskEvent`, `EngineConfiguration`
  - `@Service`, `@Slf4j`
  - `Files`, `Path`, `Paths` for file I/O
- **KEY METHOD**: `protected TaskResult processTask(TaskEvent event)`
- **LOGIC** (placeholder implementation):
  1. Log task start
  2. Simulate analysis work (sleep 2-5 seconds)
  3. Create output JSON file at `/storage/analysis/{analysisId}/task_{taskId}_output.json`
  4. Write dummy analysis results (JSON with file path, task ID, timestamp, findings array)
  5. Return TaskResult with output file path
- **OUTPUT STRUCTURE**:
  ```json
  {
    "taskId": 12345,
    "analysisId": "uuid",
    "engineType": "STATIC_ANALYSIS",
    "filePath": "/storage/incoming/app.apk",
    "timestamp": "2026-01-21T...",
    "findings": [
      {"severity": "HIGH", "description": "Hardcoded API key found"},
      {"severity": "MEDIUM", "description": "Insecure permissions"}
    ]
  }
  ```
- **GOTCHA**: Must create output directory if not exists: `Files.createDirectories(outputDir)`
- **VALIDATE**: `mvn clean compile -pl static-analysis-engine`

### UPDATE pom.xml (root)

- **IMPLEMENT**: Add new modules to parent POM
- **PATTERN**: Existing `<modules>` section
- **ADD**:
  ```xml
  <module>engine-common</module>
  <module>static-analysis-engine</module>
  ```
- **LOCATION**: After `<module>orchestrator-service</module>`, before commented-out modules
- **GOTCHA**: Maintain alphabetical/logical order
- **VALIDATE**: `mvn clean compile`

### UPDATE docker-compose.yml

- **IMPLEMENT**: Add static-analysis-engine service and create new topics
- **PATTERN**: Mirror orchestrator-service configuration
- **ADD SERVICE**:
  ```yaml
  static-analysis-engine:
    build:
      context: .
      dockerfile: static-analysis-engine/Dockerfile
    container_name: static-analysis-engine
    environment:
      KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      STORAGE_PATH: /storage
    volumes:
      - ./storage:/storage
    depends_on:
      - kafka
    networks:
      - mobile-analysis-network
  ```
- **ADD KAFKA TOPICS** (in kafka service command):
  - `static-analysis-tasks:3:1` (3 partitions, replication 1)
  - `task-heartbeats:3:1`
- **GOTCHA**: Must add to existing topic creation command, separated by spaces
- **VALIDATE**: `docker-compose config` (syntax check)

### CREATE orchestrator-service/src/test/java/com/mobileanalysis/orchestrator/integration/Phase2IntegrationTest.java

- **IMPLEMENT**: Comprehensive Phase 2 end-to-end test
- **PATTERN**: Mirror existing OrchestratorIntegrationTest structure
- **IMPORTS**:
  - `@SpringBootTest`
  - `@Testcontainers`, Testcontainers classes
  - `@DirtiesContext`
  - Repositories, services
  - `KafkaTemplate` for sending test events
  - `Awaitility` for async assertions
- **TEST SCENARIOS**:
  1. **testCompleteWorkflowWithDependencies**:
     - Send FileEvent with APK that has task dependencies
     - Wait for initial tasks to dispatch
     - Simulate engine completing first task (send TaskResponseEvent)
     - Verify dependent task dispatches
     - Verify analysis marked COMPLETED when all tasks done
  2. **testFailureAndRetry**:
     - Send FileEvent
     - Simulate engine failure (send FAILED response)
     - Verify retry event created in outbox
     - Verify attempts incremented
  3. **testHeartbeatTimeout**:
     - Send FileEvent
     - Start task (send TaskResponseEvent with RUNNING status)
     - Do NOT send heartbeats
     - Wait 2+ minutes
     - Verify HeartbeatMonitor marks task as FAILED
  4. **testIdempotency**:
     - Send same TaskResponseEvent twice
     - Verify second event is no-op (no duplicate processing)
- **SETUP**: Testcontainers for PostgreSQL, Redis, Kafka
- **GOTCHA**: Use `Awaitility.await().atMost(10, SECONDS).until(...)` for async verifications
- **VALIDATE**: `mvn verify -pl orchestrator-service -Dit.test=Phase2IntegrationTest`

### UPDATE orchestrator-service/src/main/resources/application.yml

- **IMPLEMENT**: Add configuration for new consumers and topics
- **ADD**:
  ```yaml
  spring:
    kafka:
      consumer:
        properties:
          # Heartbeat consumer config
          heartbeat.interval.ms: 3000
          session.timeout.ms: 10000
          max.poll.records: 100
  
  app:
    heartbeat:
      check-interval-ms: 60000  # Check for stale tasks every minute
      stale-threshold-minutes: 2  # Mark as stale if no heartbeat for 2 minutes
    retry:
      max-attempts: 3
      initial-backoff-ms: 1000
      backoff-multiplier: 2.0
  ```
- **GOTCHA**: Maintain YAML indentation (2 spaces)
- **VALIDATE**: `yamllint` or Spring Boot startup validation

### CREATE CLAUDE.md section for Phase 2 patterns

- **IMPLEMENT**: Document Phase 2-specific patterns for future developers
- **PATTERN**: Append to existing CLAUDE.md
- **CONTENT**:
  - Dependency resolution algorithm explanation
  - Heartbeat mechanism flow diagram (ASCII art)
  - Retry logic decision tree
  - Engine framework usage guide
- **LOCATION**: New section "## Phase 2: Task Response Handling Patterns"
- **GOTCHA**: Keep consistent with existing CLAUDE.md style and tone
- **VALIDATE**: Manual review for clarity and completeness

---

## TESTING STRATEGY

### Unit Tests

**Scope**: Individual service methods and business logic

- **DependencyResolverTest**: Test dependency graph traversal logic with various scenarios (no dependencies, single dependency, multiple dependencies)
- **AnalysisCompletionServiceTest**: Test completion detection logic (all completed, one failed, partial completion)
- **TaskRetryServiceTest**: Test retry decision logic (under max, at max, over max attempts)
- **HeartbeatMonitorTest**: Test stale task detection with various timestamps
- **AbstractAnalysisEngineTest**: Test template method flow with mock processTask()

**Tools**: JUnit 5, Mockito, AssertJ

**Fixtures**: Create test AnalysisTaskEntity objects with various states

**Assertions**: Use AssertJ fluent assertions for readability

### Integration Tests

**Scope**: Multi-component workflows with real infrastructure

- **Phase2IntegrationTest**: Complete file-to-completion workflow with Testcontainers
- **DependencyResolutionIntegrationTest**: Real DB queries for dependency chains
- **HeartbeatTimeoutIntegrationTest**: Test actual scheduled job with real delays

**Tools**: Spring Boot Test, Testcontainers (PostgreSQL, Redis, Kafka)

**Pattern**: Use `@SpringBootTest(webEnvironment = NONE)` for faster tests

**Awaits**: Use Awaitility for async assertions with reasonable timeouts (5-10 seconds)

### Edge Cases

**Critical scenarios to test**:

1. **Race Condition**: Two tasks completing simultaneously for same analysis
2. **Duplicate Events**: Same TaskResponseEvent delivered twice by Kafka
3. **Orphaned Tasks**: Parent task fails, child task should not dispatch
4. **Partial Failure**: Some tasks succeed, one fails, verify analysis marked FAILED
5. **Cache Inconsistency**: Redis unavailable during update, verify fallback to DB
6. **Heartbeat Burst**: Multiple heartbeats arrive out of order, verify only latest timestamp saved
7. **Retry Exhaustion**: Task fails max times, verify DLQ routing
8. **Engine Crash**: Engine crashes mid-task, verify heartbeat timeout triggers retry
9. **Large Dependency Chain**: 10+ tasks with complex dependencies, verify correct ordering
10. **Concurrent Analyses**: Multiple analyses running simultaneously without interference

---

## VALIDATION COMMANDS

Execute every command to ensure zero regressions and 100% feature correctness.

### Level 1: Syntax & Style

```bash
# Clean build all modules
mvn clean compile

# Expected: BUILD SUCCESS for all modules

# Check code formatting (if configured)
mvn spotless:check

# Expected: No formatting violations
```

### Level 2: Unit Tests

```bash
# Run all unit tests
mvn test

# Expected: All tests pass

# Run specific service tests
mvn test -pl orchestrator-service -Dtest=DependencyResolverTest
mvn test -pl orchestrator-service -Dtest=AnalysisCompletionServiceTest
mvn test -pl orchestrator-service -Dtest=TaskRetryServiceTest
mvn test -pl engine-common -Dtest=AbstractAnalysisEngineTest

# Expected: All specified tests pass
```

### Level 3: Integration Tests

```bash
# Run integration tests with Testcontainers (slower)
mvn verify -pl orchestrator-service -Dit.test=Phase2IntegrationTest

# Expected: 
# - Testcontainers start PostgreSQL, Redis, Kafka
# - All Phase 2 scenarios pass
# - Containers shut down cleanly

# Full integration test suite
mvn verify

# Expected: All integration tests pass (may take 5-10 minutes)
```

### Level 4: Manual Validation

**Start Infrastructure:**
```bash
# Start all services
docker-compose up -d

# Wait for services to be healthy
docker-compose ps

# Expected: All services show "Up" status
```

**Test Complete Workflow:**
```bash
# 1. Send file event
echo '{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-21T19:00:00Z"
}' | docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic file-events

# 2. Wait 5 seconds, verify analysis created
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, status FROM analysis ORDER BY created_at DESC LIMIT 1;"

# Expected: One analysis with status=RUNNING

# 3. Check tasks created
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, engine_type, status FROM analysis_task ORDER BY id DESC LIMIT 10;"

# Expected: 4 tasks created (Static Analysis, Decompiler, Signature Check, Dynamic Analysis)
# Independent tasks should be DISPATCHED immediately
# Dependent tasks should be PENDING

# 4. Wait for static analysis engine to complete (~5 seconds)
# Then check task status again
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, engine_type, status, output_path FROM analysis_task ORDER BY id DESC LIMIT 10;"

# Expected: Static Analysis task shows COMPLETED with output_path

# 5. Verify outbox events for dependent tasks
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT event_type, topic, processed FROM outbox WHERE processed = false;"

# Expected: Task dispatch events for ready dependent tasks (Decompiler after Static Analysis completes)

# 6. Monitor logs for dependency resolution
docker logs orchestrator-service | grep "dependency"

# Expected: Logs showing "Resolving dependencies for completed task..."

# 7. Wait for all tasks to complete (~30 seconds total)
# Then verify analysis completion
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, status, completed_at FROM analysis ORDER BY created_at DESC LIMIT 1;"

# Expected: Analysis marked as COMPLETED with completed_at timestamp

# 8. Verify Redis cache deletion
redis-cli GET "analysis-state:{analysisId}"

# Expected: (nil) - cache deleted after completion
```

**Test Heartbeat Mechanism:**
```bash
# 1. Check heartbeat events in Kafka
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic task-heartbeats \
  --from-beginning \
  --max-messages 5

# Expected: HeartbeatEvent JSON messages appearing every ~30 seconds

# 2. Verify heartbeat timestamps in database
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, engine_type, status, last_heartbeat_at FROM analysis_task WHERE status = 'RUNNING';"

# Expected: last_heartbeat_at updating every 30 seconds for running tasks
```

**Test Failure and Retry:**
```bash
# 1. Manually simulate a task failure by sending FAILED response
# (In real scenario, engine would send this)
echo '{
  "eventId": "660e8400-e29b-41d4-a716-446655440001",
  "taskId": 12345,
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "status": "FAILED",
  "outputPath": null,
  "errorMessage": "Simulated failure for testing",
  "attempts": 1,
  "timestamp": "2026-01-21T19:30:00Z"
}' | docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic orchestrator-responses

# 2. Wait 2 seconds, check task attempts
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT id, status, attempts, error_message FROM analysis_task WHERE id = 12345;"

# Expected: attempts=1, status=PENDING (retry queued)

# 3. Verify retry event in outbox
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT * FROM outbox WHERE aggregate_id = '12345' AND event_type = 'TASK_DISPATCHED' AND processed = false;"

# Expected: Retry dispatch event with incremented attempts
```

### Level 5: Performance Validation

```bash
# Send 10 file events rapidly
for i in {1..10}; do
  echo '{
    "eventId": "'$(uuidgen)'",
    "filePath": "/storage/incoming/test'$i'.apk",
    "fileType": "APK",
    "timestamp": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
  }' | docker exec -i mobile-analysis-kafka kafka-console-producer \
    --broker-list localhost:9092 \
    --topic file-events
  sleep 0.5
done

# Wait 60 seconds for all to complete

# Verify all 10 analyses completed
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT status, COUNT(*) FROM analysis GROUP BY status;"

# Expected: 10 analyses with status=COMPLETED (or RUNNING if still processing)

# Check for any failed tasks
psql -h localhost -U postgres -d mobile_analysis -c \
  "SELECT COUNT(*) FROM analysis_task WHERE status = 'FAILED';"

# Expected: 0 (no failures in normal operation)
```

---

## ACCEPTANCE CRITERIA

- [ ] TaskResponseConsumer successfully processes COMPLETED and FAILED responses
- [ ] Dependent tasks dispatch automatically after parent task completion
- [ ] Analysis marked COMPLETED when all tasks complete successfully
- [ ] Analysis marked FAILED if any task exhausts retry attempts
- [ ] HeartbeatEvent sent every 30 seconds from running engines
- [ ] Stale tasks (no heartbeat for 2+ minutes) automatically marked FAILED and retried
- [ ] Failed tasks retry up to max attempts (configured per engine type)
- [ ] Tasks exceeding max retries route to DLQ topic
- [ ] StaticAnalysisEngine successfully processes tasks and writes output files
- [ ] Output files created at `/storage/analysis/{analysisId}/task_{taskId}_output.json`
- [ ] Duplicate TaskResponseEvent results in no-op (idempotency)
- [ ] All validation commands pass with zero errors
- [ ] Integration tests verify end-to-end workflow completion
- [ ] Code follows constructor injection pattern (no @Autowired on fields)
- [ ] All Kafka consumers use manual commit (acknowledge after DB transaction)
- [ ] All database updates use transactional outbox pattern (no direct Kafka publishing)
- [ ] Redis cache updates are best-effort (system works if Redis fails)
- [ ] No regressions in Phase 1 functionality
- [ ] System handles 10+ concurrent analyses without degradation
- [ ] Complete analysis workflow (file event → completion) finishes in <2 minutes

---

## COMPLETION CHECKLIST

- [ ] All tasks completed in order from top to bottom
- [ ] Each task validated immediately after implementation
- [ ] All unit tests pass (`mvn test`)
- [ ] All integration tests pass (`mvn verify`)
- [ ] Manual validation scenarios executed successfully
- [ ] Performance validation shows acceptable throughput
- [ ] No linting or compilation errors
- [ ] Code review checklist applied (see CLAUDE.md)
- [ ] Documentation updated (CLAUDE.md, README.md)
- [ ] Phase 2 acceptance criteria all met
- [ ] No regressions in Phase 1 functionality
- [ ] Docker Compose environment starts all services cleanly
- [ ] Logs contain proper correlation IDs (analysisId, taskId)

---

## NOTES

### Design Decisions

**Transactional Outbox vs Direct Kafka:**
We use transactional outbox pattern for ALL event publishing to guarantee at-least-once delivery and maintain consistency between database and Kafka. Direct Kafka publishing is never used.

**Redis Best-Effort Updates:**
Redis is treated as a cache, not source of truth. Database updates always succeed even if Redis fails. Cache self-heals on next read.

**Heartbeat Separate Topic:**
Heartbeats use a dedicated `task-heartbeats` topic with shorter retention (1 day vs 7 days) and higher volume tolerance.

**AbstractAnalysisEngine Template:**
The abstract engine class uses the Template Method pattern to enforce consistent behavior (heartbeat, error handling, Kafka commit) across all engines while allowing custom analysis logic via `processTask()` hook.

### Known Limitations

- **Simple Dependencies**: Phase 2 only supports 1:1 parent-child task dependencies (no DAG)
- **Placeholder Analysis**: StaticAnalysisEngine performs dummy analysis (real analysis logic in future)
- **Local Storage**: Task outputs written to local filesystem (S3 integration in future)
- **No REST API**: No HTTP endpoints for external querying (Phase 5 or post-MVP)
- **Manual Testing**: Some failure scenarios require manual simulation (no fault injection framework yet)

### Future Enhancements (Post-Phase 2)

- Complex DAG dependency support (multiple parents per task)
- Analysis cancellation feature
- Task priority and SLA management
- Circuit breaker for unhealthy engines
- Advanced retry strategies (jitter, custom backoff curves)
- Distributed tracing integration (Jaeger/Zipkin)
- Real-time metrics dashboards (Prometheus + Grafana)

### Risk Mitigation

**Race Conditions**: All state updates are transactional and use database-level locking (SELECT FOR UPDATE where needed)

**Message Ordering**: analysisId partition key ensures all events for same analysis go to same partition in order

**Duplicate Processing**: Idempotency keys and unique constraints prevent duplicate work

**Engine Failures**: Heartbeat mechanism automatically detects crashed engines and retries tasks

**Database Deadlocks**: Keep transactions short, update in consistent order (analysis before tasks)

### Confidence Score for One-Pass Implementation

**8.5/10** - Very high confidence that execution will succeed on first attempt

**Strengths:**
- Complete context provided (Phase 1 codebase, PRD, patterns)
- Clear step-by-step tasks with validation commands
- All patterns extracted from existing code
- Comprehensive testing strategy
- Well-defined acceptance criteria

**Remaining Risks:**
- First time implementing engine framework (abstract class pattern new to project)
- Testcontainers reliability across different dev environments
- Potential for subtle race conditions in dependency resolution
- Integration test timing (async operations may need tuning)

**Recommendation:** Follow tasks strictly in order, validate each step before proceeding. If any integration test fails, check logs for correlation IDs to trace issue.
