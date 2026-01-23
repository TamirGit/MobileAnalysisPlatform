# Feature: Phase 3 - Additional Engines, DLQ, and Production Readiness

**The following plan should be complete, but it's important that you validate documentation and codebase patterns and task sanity before you start implementing.**

**Pay special attention to naming of existing utils, types, and models. Import from the right files etc.**

## Feature Description

Phase 3 completes the Mobile Analysis Platform MVP by implementing the remaining analysis engines (Dynamic Analysis, Decompiler, Signature Check), adding Dead Letter Queue (DLQ) functionality for malformed messages, and establishing production-ready monitoring foundations. This phase transforms the platform from a functional prototype into a production-grade system capable of handling multiple analysis types with comprehensive error handling and observability.

The implementation builds upon Phase 1 (foundation and Static Analysis engine) and Phase 2 (task response handling, heartbeat monitoring, and orchestration completion) to deliver a complete, scalable, multi-engine analysis platform.

## User Story

As a **Security Operations Engineer**
I want to **analyze mobile applications using multiple specialized engines with comprehensive error handling**
So that **I can detect different security issues, investigate processing failures, and confidently deploy to production**

## Problem Statement

The current system (post-Phase 2) has:
1. **Limited analysis capability** - Only Static Analysis engine implemented
2. **Lost malformed messages** - No DLQ; bad messages are logged and discarded
3. **Minimal observability** - No centralized metrics or health monitoring
4. **Single engine type** - Cannot process decompilation, dynamic analysis, or signature verification
5. **Production gaps** - Missing operational features like graceful shutdown and resource management

This prevents:
- Comprehensive security analysis workflows
- Debugging of production message processing issues
- Effective monitoring and alerting
- Confident production deployment

## Solution Statement

Implement three additional analysis engines following the established `AbstractAnalysisEngine` pattern, add DLQ routing for malformed Kafka messages, and establish production readiness through health checks, metrics collection, and operational improvements. This completes the MVP scope defined in the PRD while maintaining the existing architecture patterns and error handling strategies.

## Feature Metadata

**Feature Type**: New Capability + Enhancement
**Estimated Complexity**: High
**Primary Systems Affected**: 
- New engine services (dynamic-analysis-engine, decompiler-engine, signature-check-engine)
- Common module (KafkaConfig for DLQ)
- All services (health checks, metrics)

**Dependencies**: 
- Spring Boot 3.x
- Spring Kafka
- Spring Actuator (for health checks)
- Micrometer (metrics)
- Existing AbstractAnalysisEngine framework

---

## CONTEXT REFERENCES

### Relevant Codebase Files - IMPORTANT: YOU MUST READ THESE FILES BEFORE IMPLEMENTING!

#### Engine Framework (Existing Pattern to Follow)
- `engine-common/src/main/java/com/mobileanalysis/engine/AbstractAnalysisEngine.java` - **CRITICAL**: Base class pattern for all engines. Study this thoroughly - all new engines MUST extend this class.
- `engine-common/src/main/java/com/mobileanalysis/engine/service/HeartbeatService.java` - Heartbeat mechanism already implemented
- `static-analysis-engine/src/main/java/com/mobileanalysis/staticanalysis/StaticAnalysisConsumer.java` (lines 1-80) - **Reference implementation**: Shows how to extend AbstractAnalysisEngine properly
- `static-analysis-engine/src/main/java/com/mobileanalysis/staticanalysis/StaticAnalyzer.java` - Example analysis logic implementation

#### Kafka Configuration (For DLQ Implementation)
- `common/src/main/java/com/mobileanalysis/common/config/KafkaConfig.java` (lines 200-220) - **Why**: Contains error handling with TODO comment for DLQ. This is where DLQ routing will be added.
- `orchestrator-service/src/main/java/com/mobileanalysis/orchestrator/messaging/TaskResponseConsumer.java` (lines 1-100) - **Why**: Shows Kafka consumer pattern with manual acknowledgment

#### Domain Models
- `common/src/main/java/com/mobileanalysis/common/domain/AnalysisTask.java` - Task model with all fields
- `common/src/main/java/com/mobileanalysis/common/events/TaskEvent.java` - Event structure engines receive
- `common/src/main/java/com/mobileanalysis/common/events/TaskResponseEvent.java` - Event structure engines send

#### Testing Patterns
- `static-analysis-engine/src/test/java/com/mobileanalysis/staticanalysis/StaticAnalysisConsumerTest.java` - Unit test pattern
- `orchestrator-service/src/test/java/com/mobileanalysis/orchestrator/messaging/TaskResponseConsumerTest.java` - Consumer test with Testcontainers

### New Files to Create

#### Dynamic Analysis Engine
- `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisConsumer.java` - Consumer extending AbstractAnalysisEngine
- `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalyzer.java` - Analysis implementation
- `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisApplication.java` - Spring Boot application
- `dynamic-analysis-engine/src/main/resources/application.yml` - Configuration
- `dynamic-analysis-engine/src/test/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisConsumerTest.java` - Unit tests
- `dynamic-analysis-engine/pom.xml` - Maven dependencies

#### Decompiler Engine
- `decompiler-engine/src/main/java/com/mobileanalysis/decompiler/DecompilerConsumer.java` - Consumer
- `decompiler-engine/src/main/java/com/mobileanalysis/decompiler/Decompiler.java` - Decompilation logic
- `decompiler-engine/src/main/java/com/mobileanalysis/decompiler/DecompilerApplication.java` - Application
- `decompiler-engine/src/main/resources/application.yml` - Configuration
- `decompiler-engine/src/test/java/com/mobileanalysis/decompiler/DecompilerConsumerTest.java` - Tests
- `decompiler-engine/pom.xml` - Dependencies

#### Signature Check Engine
- `signature-check-engine/src/main/java/com/mobileanalysis/signature/SignatureCheckConsumer.java` - Consumer
- `signature-check-engine/src/main/java/com/mobileanalysis/signature/SignatureChecker.java` - Verification logic
- `signature-check-engine/src/main/java/com/mobileanalysis/signature/SignatureCheckApplication.java` - Application
- `signature-check-engine/src/main/resources/application.yml` - Configuration
- `signature-check-engine/src/test/java/com/mobileanalysis/signature/SignatureCheckConsumerTest.java` - Tests
- `signature-check-engine/pom.xml` - Dependencies

#### DLQ Components
- `common/src/main/java/com/mobileanalysis/common/dlq/DLQPublisher.java` - DLQ message publisher
- `common/src/main/java/com/mobileanalysis/common/dlq/DLQMessage.java` - DLQ message wrapper

#### Docker & Infrastructure
- `docker-compose.yml` - Update with new engine services
- `dynamic-analysis-engine/Dockerfile` - Container image
- `decompiler-engine/Dockerfile` - Container image
- `signature-check-engine/Dockerfile` - Container image

#### Documentation
- `PHASE3_STATUS.md` - Phase 3 completion status
- `docs/ENGINE_DEVELOPMENT_GUIDE.md` - How to create new engines
- `docs/DLQ_OPERATIONS.md` - DLQ monitoring and recovery procedures

### Relevant Documentation - YOU SHOULD READ THESE BEFORE IMPLEMENTING!

#### Spring Framework
- [Spring Boot 3.x Reference](https://docs.spring.io/spring-boot/docs/3.3.x/reference/html/)
  - Section: Actuator Endpoints
  - Why: Needed for health checks implementation
- [Spring Kafka Reference](https://docs.spring.io/spring-kafka/reference/html/)
  - Section: Dead Letter Publishing
  - Why: Shows how to implement DLQ with DeadLetterPublishingRecoverer

#### Kafka
- [Kafka Documentation - Error Handling](https://kafka.apache.org/documentation/#consumerconfigs)
  - Section: Consumer Error Handling
  - Why: Understanding Kafka's error handling capabilities

#### Micrometer
- [Micrometer Documentation](https://micrometer.io/docs)
  - Section: Spring Boot Integration
  - Why: For metrics implementation

### Patterns to Follow

#### Engine Implementation Pattern (FROM AbstractAnalysisEngine)

**All engines MUST extend AbstractAnalysisEngine and implement processTask():**

```java
// FROM: static-analysis-engine/src/main/java/.../StaticAnalysisConsumer.java
@Service
@RequiredArgsConstructor
public class StaticAnalysisConsumer extends AbstractAnalysisEngine {
    
    private final StaticAnalyzer analyzer;
    
    @Override
    @KafkaListener(
        topics = "${app.kafka.topics.static-analysis}",
        groupId = "${app.kafka.consumer-group}",
        containerFactory = "taskEventConsumerFactory"
    )
    public void handleTaskEvent(TaskEvent event, Acknowledgment acknowledgment) {
        super.handleTaskEvent(event, acknowledgment);
    }
    
    @Override
    protected TaskResponseEvent processTask(TaskEvent event) {
        // 1. Extract task details
        Long taskId = event.getTaskId();
        UUID analysisId = event.getAnalysisId();
        String filePath = event.getFilePath();
        
        // 2. Perform analysis
        AnalysisResult result = analyzer.analyze(filePath);
        
        // 3. Write output to filesystem
        String outputPath = writeOutput(analysisId, taskId, result);
        
        // 4. Build response event
        return TaskResponseEvent.builder()
            .taskId(taskId)
            .analysisId(analysisId)
            .status(TaskStatus.COMPLETED)
            .outputPath(outputPath)
            .build();
    }
}
```

**Key Points:**
- Override `handleTaskEvent()` with `@KafkaListener` annotation
- Call `super.handleTaskEvent()` to leverage AbstractAnalysisEngine's error handling
- Implement `processTask()` with your engine-specific logic
- Return TaskResponseEvent with appropriate status
- AbstractAnalysisEngine handles: heartbeats, timeouts, error handling, Kafka acknowledgment

#### DLQ Error Handler Pattern

**Kafka Configuration with DLQ (NEW PATTERN):**

```java
// PATTERN: Add to KafkaConfig.java
@Bean
public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer(KafkaTemplate<String, Object> kafkaTemplate) {
    return new DeadLetterPublishingRecoverer(kafkaTemplate,
        (consumerRecord, exception) -> {
            String originalTopic = consumerRecord.topic();
            String dlqTopic = originalTopic + ".DLQ";
            return new TopicPartition(dlqTopic, 0);
        });
}

@Bean
public DefaultErrorHandler defaultErrorHandler(DeadLetterPublishingRecoverer recoverer) {
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer);
    
    // Do NOT retry deserialization errors
    errorHandler.addNotRetryableExceptions(
        DeserializationException.class,
        SerializationException.class,
        JsonProcessingException.class
    );
    
    return errorHandler;
}
```

#### Application Configuration Pattern

**Engine application.yml structure:**

```yaml
# PATTERN: Consistent across all engines
spring:
  application:
    name: dynamic-analysis-engine
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: dynamic-analysis-consumer-group
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
      properties:
        spring.deserializer.value.delegate.class: org.springframework.kafka.support.serializer.JsonDeserializer
        spring.json.trusted.packages: "*"

app:
  kafka:
    topics:
      dynamic-analysis: dynamic-analysis-tasks
      responses: orchestrator-responses
    consumer-group: dynamic-analysis-consumer-group
  engine:
    type: DYNAMIC_ANALYSIS
    timeout-seconds: 1800  # 30 minutes for dynamic analysis
    heartbeat-interval-ms: ${HEARTBEAT_INTERVAL_MS:30000}
  storage:
    base-path: ${STORAGE_PATH:/storage}

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,info
  endpoint:
    health:
      show-details: always
```

#### Health Check Pattern

```java
// PATTERN: Custom health indicator for each engine
@Component
public class AnalysisEngineHealthIndicator implements HealthIndicator {
    
    private final HeartbeatService heartbeatService;
    private final AtomicInteger activeTasksCount = new AtomicInteger(0);
    
    @Override
    public Health health() {
        boolean hasActiveTask = heartbeatService.hasActiveTask();
        
        Health.Builder builder = hasActiveTask ? Health.up() : Health.up();
        
        builder.withDetail("activeTask", hasActiveTask)
               .withDetail("activeTasks", activeTasksCount.get())
               .withDetail("engineType", engineType);
        
        return builder.build();
    }
}
```

---

## IMPLEMENTATION PLAN

### Phase 1: Foundation - DLQ Implementation

**Goal:** Add Dead Letter Queue functionality to prevent loss of malformed messages

**Tasks:**
1. Implement DLQ publisher in common module
2. Update KafkaConfig with DeadLetterPublishingRecoverer
3. Configure DLQ topics in docker-compose
4. Add DLQ monitoring documentation

### Phase 2: Dynamic Analysis Engine

**Goal:** Implement long-running dynamic analysis engine (most complex engine)

**Tasks:**
1. Create module structure and dependencies
2. Implement DynamicAnalysisConsumer extending AbstractAnalysisEngine
3. Implement DynamicAnalyzer with simulated dynamic analysis
4. Add health checks and metrics
5. Configure Kafka topics and Docker service
6. Write unit and integration tests

### Phase 3: Decompiler Engine

**Goal:** Implement decompiler engine for code analysis

**Tasks:**
1. Create module structure
2. Implement DecompilerConsumer
3. Implement Decompiler with simulated decompilation
4. Add configuration and Docker setup
5. Write tests

### Phase 4: Signature Check Engine

**Goal:** Implement fast signature verification engine

**Tasks:**
1. Create module structure
2. Implement SignatureCheckConsumer
3. Implement SignatureChecker
4. Add configuration and Docker setup
5. Write tests

### Phase 5: Production Readiness & Integration

**Goal:** Add monitoring, health checks, and validate end-to-end workflows

**Tasks:**
1. Add Spring Actuator to all services
2. Configure metrics collection
3. Implement graceful shutdown
4. End-to-end integration tests with all engines
5. Performance testing
6. Documentation updates

---

## STEP-BY-STEP TASKS

**IMPORTANT**: Execute every task in order, top to bottom. Each task is atomic and independently testable.

### PHASE 1: DLQ Implementation

#### Task 1.1: CREATE DLQMessage.java

**File**: `common/src/main/java/com/mobileanalysis/common/dlq/DLQMessage.java`

- **IMPLEMENT**: Data class to wrap malformed messages sent to DLQ
- **FIELDS**: originalTopic, originalPartition, originalOffset, errorMessage, errorTimestamp, messagePayload (as String)
- **PATTERN**: Use `@Data` from Lombok, similar to other domain models
- **IMPORTS**: `lombok.Data`, `lombok.Builder`, `java.time.Instant`
- **VALIDATE**: `mvn clean compile -pl common`

#### Task 1.2: CREATE DLQPublisher.java

**File**: `common/src/main/java/com/mobileanalysis/common/dlq/DLQPublisher.java`

- **IMPLEMENT**: Service to publish messages to DLQ topics
- **PATTERN**: Use `@Service` with `KafkaTemplate` injection
- **METHOD**: `publishToDLQ(ConsumerRecord<?, ?> record, Exception exception)`
- **LOGIC**: Build DLQMessage, determine DLQ topic name (originalTopic + ".DLQ"), send via KafkaTemplate
- **GOTCHA**: Handle exceptions during DLQ publishing (log but don't throw)
- **IMPORTS**: `org.springframework.kafka.core.KafkaTemplate`, `org.apache.kafka.clients.consumer.ConsumerRecord`
- **VALIDATE**: `mvn clean compile -pl common`

#### Task 1.3: UPDATE KafkaConfig.java (Add DeadLetterPublishingRecoverer)

**File**: `common/src/main/java/com/mobileanalysis/common/config/KafkaConfig.java`

- **IMPLEMENT**: Add `@Bean` for `DeadLetterPublishingRecoverer`
- **PATTERN**: Mirror existing error handler pattern around line 215
- **LOGIC**: 
  - Create DeadLetterPublishingRecoverer with KafkaTemplate
  - Configure destination resolver: `(record, ex) -> new TopicPartition(record.topic() + ".DLQ", 0)`
  - Update DefaultErrorHandler to use this recoverer instead of just logging
- **IMPORTS**: `org.springframework.kafka.listener.DeadLetterPublishingRecoverer`, `org.apache.kafka.common.TopicPartition`
- **GOTCHA**: Remove or replace existing log-only error handling
- **VALIDATE**: `mvn clean compile -pl common`

#### Task 1.4: UPDATE docker-compose.yml (Add DLQ topics)

**File**: `docker-compose.yml`

- **ADD**: kafka-init service command to create DLQ topics
- **TOPICS**: 
  - `file-events.DLQ`
  - `static-analysis-tasks.DLQ`
  - `dynamic-analysis-tasks.DLQ`
  - `decompiler-tasks.DLQ`
  - `signature-check-tasks.DLQ`
  - `orchestrator-responses.DLQ`
- **PATTERN**: Use same kafka-topics.sh command as existing topic creation
- **CONFIG**: 1 partition, 30-day retention
- **VALIDATE**: `docker-compose up -d kafka-init && docker-compose logs kafka-init | grep "DLQ"`

#### Task 1.5: CREATE DLQ_OPERATIONS.md

**File**: `docs/DLQ_OPERATIONS.md`

- **IMPLEMENT**: Operational guide for DLQ monitoring and message recovery
- **SECTIONS**:
  - What is the DLQ and why messages go there
  - How to list messages in DLQ topics
  - How to replay messages from DLQ
  - Alerting recommendations
- **PATTERN**: Follow format of existing docs/ files
- **VALIDATE**: Manual review

---

### PHASE 2: Dynamic Analysis Engine

#### Task 2.1: CREATE Module Structure

**Directory**: `dynamic-analysis-engine/`

- **CREATE**: Standard Maven module structure
- **PATTERN**: Mirror `static-analysis-engine/` directory structure exactly
- **DIRECTORIES**: `src/main/java`, `src/main/resources`, `src/test/java`
- **VALIDATE**: Directory structure matches static-analysis-engine

#### Task 2.2: CREATE pom.xml

**File**: `dynamic-analysis-engine/pom.xml`

- **IMPLEMENT**: Maven POM with parent reference and dependencies
- **PATTERN**: Copy from `static-analysis-engine/pom.xml` and update artifact IDs
- **DEPENDENCIES**: spring-boot-starter, spring-kafka, common, engine-common, spring-boot-starter-actuator
- **ARTIFACT_ID**: `dynamic-analysis-engine`
- **VALIDATE**: `mvn clean compile -pl dynamic-analysis-engine`

#### Task 2.3: CREATE DynamicAnalysisApplication.java

**File**: `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisApplication.java`

- **IMPLEMENT**: Spring Boot application entry point
- **PATTERN**: Copy from StaticAnalysisApplication.java
- **ANNOTATIONS**: `@SpringBootApplication`, `@EnableKafka`
- **IMPORTS**: `org.springframework.boot.SpringApplication`, `org.springframework.kafka.annotation.EnableKafka`
- **VALIDATE**: `mvn spring-boot:run -pl dynamic-analysis-engine` (should start, then Ctrl+C)

#### Task 2.4: CREATE DynamicAnalyzer.java

**File**: `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalyzer.java`

- **IMPLEMENT**: Simulated dynamic analysis logic
- **PATTERN**: Use `@Service` annotation, similar to StaticAnalyzer
- **METHOD**: `AnalysisResult analyze(String filePath)`
- **LOGIC**: 
  - Log start of dynamic analysis
  - Simulate long-running analysis (Thread.sleep for 5-10 seconds)
  - Generate mock results (behaviors detected, network connections, etc.)
  - Return AnalysisResult with findings
- **GOTCHA**: Handle InterruptedException from Thread.sleep
- **IMPORTS**: `lombok.extern.slf4j.Slf4j`, `org.springframework.stereotype.Service`
- **VALIDATE**: Unit test (next task)

#### Task 2.5: CREATE DynamicAnalysisConsumer.java

**File**: `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisConsumer.java`

- **IMPLEMENT**: Kafka consumer extending AbstractAnalysisEngine
- **PATTERN**: Follow StaticAnalysisConsumer.java pattern EXACTLY
- **MUST OVERRIDE**: `handleTaskEvent()` with @KafkaListener, `processTask()`
- **KAFKA_LISTENER**:
  - topics: `${app.kafka.topics.dynamic-analysis}`
  - groupId: `${app.kafka.consumer-group}`
  - containerFactory: `taskEventConsumerFactory`
- **PROCESS_TASK**: Call DynamicAnalyzer, write output, build TaskResponseEvent
- **IMPORTS**: All from AbstractAnalysisEngine, DynamicAnalyzer, TaskEvent, TaskResponseEvent
- **GOTCHA**: Call `super.handleTaskEvent()` in overridden method
- **VALIDATE**: `mvn clean compile -pl dynamic-analysis-engine`

#### Task 2.6: CREATE application.yml

**File**: `dynamic-analysis-engine/src/main/resources/application.yml`

- **IMPLEMENT**: Spring Boot configuration
- **PATTERN**: Copy from static-analysis-engine and update values
- **KEY_VALUES**:
  - `spring.application.name: dynamic-analysis-engine`
  - `app.kafka.topics.dynamic-analysis: dynamic-analysis-tasks`
  - `app.engine.type: DYNAMIC_ANALYSIS`
  - `app.engine.timeout-seconds: 1800` (30 minutes)
- **ACTUATOR**: Enable health, metrics, info endpoints
- **VALIDATE**: YAML syntax with `yamllint` or IDE

#### Task 2.7: CREATE Dockerfile

**File**: `dynamic-analysis-engine/Dockerfile`

- **IMPLEMENT**: Container image definition
- **PATTERN**: Copy from static-analysis-engine Dockerfile
- **BASE_IMAGE**: `eclipse-temurin:21-jre`
- **WORKDIR**: `/app`
- **JAR_COPY**: Copy target/*.jar to /app/app.jar
- **ENTRYPOINT**: `java -jar app.jar`
- **VALIDATE**: `docker build -t dynamic-analysis-engine ./dynamic-analysis-engine`

#### Task 2.8: UPDATE docker-compose.yml (Add dynamic-analysis-engine service)

**File**: `docker-compose.yml`

- **ADD**: New service definition for dynamic-analysis-engine
- **PATTERN**: Copy static-analysis-engine service block and modify
- **SERVICE_NAME**: `dynamic-analysis-engine`
- **BUILD**: `./dynamic-analysis-engine`
- **DEPENDS_ON**: kafka, postgres, redis
- **ENVIRONMENT**: KAFKA_BOOTSTRAP_SERVERS, DB connection, STORAGE_PATH
- **VOLUMES**: Mount /storage
- **NETWORKS**: Same network as other services
- **VALIDATE**: `docker-compose config` (check syntax)

#### Task 2.9: CREATE DynamicAnalysisConsumerTest.java

**File**: `dynamic-analysis-engine/src/test/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisConsumerTest.java`

- **IMPLEMENT**: Unit tests for DynamicAnalysisConsumer
- **PATTERN**: Mirror StaticAnalysisConsumerTest.java structure
- **FRAMEWORK**: JUnit 5 + Mockito
- **TESTS**:
  - `processTask_success_returnsCompletedResponse()`
  - `processTask_analysisFailure_throwsException()`
  - `handleTaskEvent_validEvent_processesSuccessfully()`
- **MOCKS**: Mock DynamicAnalyzer behavior
- **ASSERTIONS**: AssertJ for fluent assertions
- **VALIDATE**: `mvn test -pl dynamic-analysis-engine`

#### Task 2.10: CREATE DynamicAnalysisHealthIndicator.java

**File**: `dynamic-analysis-engine/src/main/java/com/mobileanalysis/dynamicanalysis/DynamicAnalysisHealthIndicator.java`

- **IMPLEMENT**: Custom health check
- **PATTERN**: Use Spring Boot Actuator HealthIndicator interface
- **LOGIC**: Report engine status, active task count
- **IMPORTS**: `org.springframework.boot.actuate.health.HealthIndicator`, `org.springframework.stereotype.Component`
- **VALIDATE**: `curl http://localhost:8082/actuator/health` (after service starts)

---

### PHASE 3: Decompiler Engine

#### Task 3.1-3.10: CREATE Decompiler Engine (Mirror Dynamic Analysis)

**Follow same pattern as Phase 2 Tasks 2.1-2.10, but for decompiler-engine:**

- **Module**: `decompiler-engine/`
- **Package**: `com.mobileanalysis.decompiler`
- **Classes**: DecompilerApplication, Decompiler, DecompilerConsumer, DecompilerHealthIndicator
- **Kafka Topic**: `decompiler-tasks`
- **Engine Type**: `DECOMPILER`
- **Timeout**: 600 seconds (10 minutes)
- **Port** (if exposing actuator): 8083
- **Analysis Logic**: Simulated decompilation (Thread.sleep 3-5 seconds)

**PATTERN**: Replicate all 10 tasks from Phase 2, just with decompiler-specific values

**VALIDATE**: Same validation commands as Phase 2, but with decompiler paths

---

### PHASE 4: Signature Check Engine

#### Task 4.1-4.10: CREATE Signature Check Engine (Mirror Pattern)

**Follow same pattern as Phase 2, but for signature-check-engine:**

- **Module**: `signature-check-engine/`
- **Package**: `com.mobileanalysis.signature`
- **Classes**: SignatureCheckApplication, SignatureChecker, SignatureCheckConsumer, SignatureCheckHealthIndicator
- **Kafka Topic**: `signature-check-tasks`
- **Engine Type**: `SIGNATURE_CHECK`
- **Timeout**: 120 seconds (2 minutes - fast engine)
- **Port**: 8084
- **Analysis Logic**: Simulated signature verification (Thread.sleep 1-2 seconds)

**PATTERN**: Replicate all 10 tasks from Phase 2

**VALIDATE**: Same validation commands as Phase 2

---

### PHASE 5: Production Readiness

#### Task 5.1: UPDATE All Services with Actuator Dependencies

**Files**: `orchestrator-service/pom.xml`, `static-analysis-engine/pom.xml`, `common/pom.xml`

- **ADD**: `spring-boot-starter-actuator` dependency if not present
- **ADD**: `micrometer-registry-prometheus` for metrics export
- **PATTERN**: Add to `<dependencies>` section
- **VALIDATE**: `mvn clean compile` (all modules)

#### Task 5.2: UPDATE application.yml (Enable Actuator Endpoints)

**Files**: All service application.yml files

- **ADD**: Management endpoints configuration
- **EXPOSE**: health, metrics, info endpoints
- **PATTERN**:
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,metrics,info
    endpoint:
      health:
        show-details: always
  ```
- **VALIDATE**: Start service, curl http://localhost:8080/actuator

#### Task 5.3: CREATE ENGINE_DEVELOPMENT_GUIDE.md

**File**: `docs/ENGINE_DEVELOPMENT_GUIDE.md`

- **IMPLEMENT**: Complete guide for creating new analysis engines
- **SECTIONS**:
  - AbstractAnalysisEngine overview
  - Step-by-step engine creation
  - Configuration guidelines
  - Testing best practices
  - Common pitfalls
- **EXAMPLES**: Code snippets from existing engines
- **VALIDATE**: Manual review

#### Task 5.4: CREATE Integration Test (All Engines)

**File**: `orchestrator-service/src/test/java/integration/MultiEngineIntegrationTest.java`

- **IMPLEMENT**: End-to-end test with all four engines
- **FRAMEWORK**: Spring Boot Test + Testcontainers
- **SCENARIO**: 
  - Submit APK file event
  - Verify all 4 task types dispatched
  - Mock each engine's response
  - Verify analysis completes successfully
- **PATTERN**: Extend existing integration test pattern
- **VALIDATE**: `mvn verify -pl orchestrator-service -Dtest=MultiEngineIntegrationTest`

#### Task 5.5: CREATE PHASE3_STATUS.md

**File**: `PHASE3_STATUS.md`

- **IMPLEMENT**: Phase 3 completion status document
- **PATTERN**: Follow PHASE1_STATUS.md format
- **SECTIONS**:
  - Features implemented
  - Tests passing
  - Known limitations
  - Next steps (post-MVP)
- **VALIDATE**: Manual review

#### Task 5.6: UPDATE Root pom.xml (Add new modules)

**File**: `pom.xml` (root)

- **ADD**: New engine modules to `<modules>` section
- **MODULES**: dynamic-analysis-engine, decompiler-engine, signature-check-engine
- **VALIDATE**: `mvn clean install` (builds all modules)

#### Task 5.7: UPDATE README.md (Document all engines)

**File**: `README.md`

- **UPDATE**: Architecture section with all four engines
- **ADD**: Quick start guide for running all services
- **ADD**: Health check endpoints for each service
- **VALIDATE**: Manual review

---

## TESTING STRATEGY

Design tests with fixtures and assertions following existing testing approaches.

### Unit Tests

**Scope**: Each engine's Consumer and Analyzer classes

**Requirements**:
- Test processTask() with valid input
- Test processTask() with analysis failures
- Test handleTaskEvent() integration
- Mock external dependencies (KafkaTemplate, repositories)
- Use AssertJ for assertions
- Coverage: 80%+ per module

**Framework**: JUnit 5 + Mockito + AssertJ

**Pattern**:
```java
@ExtendWith(MockitoExtension.class)
class DynamicAnalysisConsumerTest {
    @Mock private DynamicAnalyzer analyzer;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @InjectMocks private DynamicAnalysisConsumer consumer;
    
    @Test
    void processTask_success_returnsCompletedResponse() {
        // Given
        TaskEvent event = TaskEvent.builder()...build();
        AnalysisResult result = new AnalysisResult(...);
        when(analyzer.analyze(anyString())).thenReturn(result);
        
        // When
        TaskResponseEvent response = consumer.processTask(event);
        
        // Then
        assertThat(response.getStatus()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(response.getOutputPath()).isNotNull();
    }
}
```

### Integration Tests

**Scope**: 
- DLQ message routing (send malformed message, verify DLQ)
- Multi-engine workflow (all 4 engines process tasks)
- End-to-end analysis completion

**Requirements**:
- Use Testcontainers for Kafka, PostgreSQL, Redis
- Test actual Kafka message flow
- Verify database state changes
- Test DLQ routing with real malformed JSON

**Framework**: Spring Boot Test + Testcontainers

### Edge Cases

**Test these scenarios specifically:**
1. **Malformed JSON message** → DLQ routing
2. **Task timeout during long analysis** → Failure response
3. **Multiple engines processing concurrently** → No interference
4. **Engine restart during task processing** → Task recovery
5. **DLQ publish failure** → Logged but doesn't crash consumer

---

## VALIDATION COMMANDS

Execute every command to ensure zero regressions and 100% feature correctness.

### Level 1: Syntax & Style

```bash
# Compile all modules
mvn clean compile

# Check for compilation errors
mvn clean install -DskipTests

# Verify Maven dependency tree
mvn dependency:tree
```

### Level 2: Unit Tests

```bash
# Run all unit tests
mvn test

# Run tests for specific engine
mvn test -pl dynamic-analysis-engine
mvn test -pl decompiler-engine
mvn test -pl signature-check-engine

# Check test coverage (if using JaCoCo)
mvn jacoco:report
```

### Level 3: Integration Tests

```bash
# Run integration tests
mvn verify

# Run specific integration test
mvn verify -Dtest=MultiEngineIntegrationTest

# Run with Testcontainers
mvn verify -pl orchestrator-service
```

### Level 4: Manual Validation

```bash
# Start all services with Docker Compose
docker-compose up -d

# Wait for services to be healthy
sleep 30

# Check all services are running
docker-compose ps

# Check health endpoints
curl http://localhost:8080/actuator/health  # Orchestrator
curl http://localhost:8081/actuator/health  # Static Analysis
curl http://localhost:8082/actuator/health  # Dynamic Analysis
curl http://localhost:8083/actuator/health  # Decompiler
curl http://localhost:8084/actuator/health  # Signature Check

# Submit a file event (manually or via script)
kafka-console-producer --bootstrap-server localhost:9092 --topic file-events << EOF
{"eventId":"test-123","filePath":"/storage/test.apk","fileType":"APK","timestamp":"2026-01-23T10:00:00Z"}
EOF

# Check orchestrator logs
docker-compose logs -f orchestrator-service

# Check engine logs
docker-compose logs -f dynamic-analysis-engine

# Verify DLQ topic exists
kafka-topics --bootstrap-server localhost:9092 --list | grep DLQ

# Send malformed message to test DLQ
kafka-console-producer --bootstrap-server localhost:9092 --topic static-analysis-tasks << EOF
{invalid json}
EOF

# Check DLQ has message
kafka-console-consumer --bootstrap-server localhost:9092 --topic static-analysis-tasks.DLQ --from-beginning --max-messages 1

# Stop all services
docker-compose down
```

### Level 5: Performance Validation

```bash
# Start services
docker-compose up -d

# Submit 10 file events concurrently
for i in {1..10}; do
  kafka-console-producer --bootstrap-server localhost:9092 --topic file-events << EOF
{"eventId":"test-$i","filePath":"/storage/test$i.apk","fileType":"APK","timestamp":"2026-01-23T10:00:00Z"}
EOF
done

# Monitor task processing time
watch -n 1 'docker-compose logs --tail=50 orchestrator-service | grep "Analysis.*COMPLETED"'

# Check no memory leaks after 10 analyses
docker stats --no-stream
```

---

## ACCEPTANCE CRITERIA

### DLQ Implementation
- [ ] Malformed messages are sent to DLQ topics (not discarded)
- [ ] DLQ topics have 30-day retention
- [ ] DLQ messages contain original message + error details
- [ ] DLQ publish failures are logged but don't crash consumers
- [ ] All engine topics have corresponding DLQ topics

### Additional Engines
- [ ] Dynamic Analysis Engine processes tasks successfully
- [ ] Decompiler Engine processes tasks successfully
- [ ] Signature Check Engine processes tasks successfully
- [ ] All engines extend AbstractAnalysisEngine correctly
- [ ] All engines send heartbeats every 30 seconds
- [ ] All engines handle timeouts properly
- [ ] All engines return proper TaskResponseEvent

### Integration & Testing
- [ ] End-to-end test with all 4 engines passes
- [ ] Unit test coverage ≥ 80% for new code
- [ ] Integration tests verify multi-engine workflows
- [ ] All engines run concurrently without interference
- [ ] Task dependencies work across all engine types

### Production Readiness
- [ ] All services have health check endpoints
- [ ] All services expose metrics endpoints
- [ ] Docker Compose starts all services successfully
- [ ] Services recover gracefully from restarts
- [ ] Documentation complete (ENGINE_DEVELOPMENT_GUIDE, DLQ_OPERATIONS)
- [ ] PHASE3_STATUS.md documents completion

### Operational
- [ ] All validation commands execute successfully
- [ ] No regressions in existing functionality
- [ ] System handles 10+ concurrent analyses
- [ ] Average analysis completion time < 5 minutes
- [ ] No memory leaks after processing 20+ analyses

---

## COMPLETION CHECKLIST

- [ ] All Phase 1 tasks completed (DLQ Implementation)
- [ ] All Phase 2 tasks completed (Dynamic Analysis Engine)
- [ ] All Phase 3 tasks completed (Decompiler Engine)
- [ ] All Phase 4 tasks completed (Signature Check Engine)
- [ ] All Phase 5 tasks completed (Production Readiness)
- [ ] Each task validation passed immediately
- [ ] All validation commands executed successfully
- [ ] Full test suite passes (unit + integration)
- [ ] No linting or compilation errors
- [ ] Manual testing confirms all engines work
- [ ] Performance testing shows acceptable throughput
- [ ] Acceptance criteria all met
- [ ] Code follows existing patterns and conventions
- [ ] Documentation complete and accurate

---

## NOTES

### Design Decisions

**DLQ Topic Strategy**: Each original topic gets a corresponding `.DLQ` suffix topic. This keeps DLQ messages organized by source and simplifies monitoring/recovery.

**Engine Timeouts**: Different engines have different timeout values based on expected analysis duration:
- Signature Check: 2 minutes (fast)
- Static Analysis: 5 minutes (medium)
- Decompiler: 10 minutes (medium-slow)
- Dynamic Analysis: 30 minutes (very slow)

**Simulated Analysis**: For MVP, engines use `Thread.sleep()` to simulate real analysis work. This allows testing of long-running task behavior without implementing actual analysis algorithms.

**Health Checks**: Each engine reports its own health including whether it's actively processing a task. This enables load balancer health checks and auto-scaling decisions.

### Trade-offs

**Simulated vs Real Analysis**: MVP uses simulated analysis to focus on orchestration patterns. Real analysis engines (e.g., MobSF integration) are post-MVP.

**DLQ Partitioning**: All DLQ topics use 1 partition for simplicity. May need to increase for high-volume production use.

**No DLQ Consumer**: MVP includes DLQ routing but not automated replay. Manual investigation and replay will be needed (documented in DLQ_OPERATIONS.md).

### Post-Phase 3 Considerations

**Real Analysis Integration**: Replace simulated analyzers with real tools (MobSF, APKTool, Jadx, etc.)

**Advanced Monitoring**: Add Prometheus metrics, Grafana dashboards, alerting rules

**DLQ Automation**: Build automated DLQ inspection and replay tools

**Performance Optimization**: Profile and optimize slow paths, add caching layers

**Kubernetes Deployment**: Create K8s manifests for production deployment

---

**Estimated Implementation Time**: 3-4 weeks
**Complexity**: High (multiple new services + DLQ + integration)
**Confidence Score**: 8/10 (Clear patterns from existing code, comprehensive plan)
