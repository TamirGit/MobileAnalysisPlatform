# Mobile Analysis Platform - Product Requirements Document

## 1. Executive Summary

The Mobile Analysis Platform is a scalable, event-driven security backend application designed to analyze mobile application files (.apk for Android and .ipa for iOS). The system receives file events, orchestrates multiple analysis tasks with configurable dependencies, and manages distributed processing across specialized analysis engines. Built on Java 21 and Spring Boot 3.x with Kafka for event streaming, the platform ensures fault tolerance, high availability, and efficient resource utilization through dynamic scaling.

The core value proposition is providing a robust, production-ready framework for automating complex multi-stage security analysis workflows with built-in retry mechanisms, state management, and real-time progress tracking. This MVP focuses on establishing the foundational orchestration layer, event-driven architecture, and engine framework that can accommodate various analysis types.

The MVP goal is to deliver a fully functional orchestration system capable of receiving file events, managing task dependencies, distributing work to specialized engines, tracking analysis state, and handling failures gracefully—all while maintaining data consistency and supporting horizontal scaling.

## 2. Mission

**Mission Statement:** Build a resilient, scalable platform that automates complex mobile security analysis workflows through intelligent orchestration, enabling security teams to efficiently process and analyze mobile applications at scale.

**Core Principles:**
1. **Resilience First:** Every component must handle failures gracefully with automatic retry mechanisms and state recovery
2. **Scalability by Design:** Architecture supports horizontal scaling of both orchestrator and engines based on workload
3. **Consistency Guaranteed:** Use transactional outbox pattern and idempotency to ensure data consistency across distributed components
4. **Observable Operations:** Track every analysis and task with correlation IDs for complete visibility into system behavior
5. **Configuration-Driven:** Analysis workflows are defined through configuration, enabling rapid addition of new analysis types without code changes

## 3. Target Users

**Primary User Persona: Security Operations Engineer**
- **Technical Level:** Advanced (experienced with distributed systems, Docker, Kafka)
- **Key Needs:**
  - Automated analysis of mobile applications at scale
  - Reliable processing with minimal manual intervention
  - Clear visibility into analysis progress and failures
  - Easy addition of new analysis engines
  - Fault tolerance to handle infrastructure issues
- **Pain Points:**
  - Manual coordination of multiple analysis tools is error-prone
  - Difficult to track which analyses completed successfully
  - Hard to retry failed analyses without reprocessing everything
  - Scaling analysis capacity requires significant manual effort

**Secondary User Persona: Security Analyst**
- **Technical Level:** Intermediate (comfortable with APIs, basic container operations)
- **Key Needs:**
  - Submit mobile apps for analysis
  - Monitor analysis progress in real-time
  - Access analysis results quickly
  - Understand why analyses failed

## 4. MVP Scope

### In Scope: Core Functionality
✅ File event ingestion for .apk and .ipa files
✅ Dynamic analysis configuration loading from database and Redis cache
✅ Task dependency management (simple 1:1 parent-child relationships)
✅ Orchestrator service for workflow coordination
✅ Multiple specialized analysis engine services (static, dynamic, decompiler, signature)
✅ Transactional outbox pattern for reliable event publishing
✅ Task status tracking and state persistence
✅ Automatic retry mechanism with configurable max attempts
✅ Dead Letter Queue (DLQ) for failed tasks
✅ Heartbeat mechanism for long-running tasks
✅ Idempotency handling for duplicate events
✅ Correlation ID tracking across entire analysis flow
✅ Parallel execution of independent tasks
✅ Per-engine timeout configuration
✅ Analysis result storage on local filesystem
✅ Complete analysis state summary (which tasks failed and why)

### In Scope: Technical
✅ Java 21 with Spring Boot 3.x
✅ Kafka with KRaft for event streaming
✅ PostgreSQL for persistent storage
✅ Redis for caching
✅ Dedicated Kafka topic per engine type
✅ Docker Compose for local development
✅ Database migrations with Flyway
✅ Multi-module Maven/Gradle project structure
✅ Integration tests with Testcontainers

### In Scope: Fault Tolerance
✅ Automatic recovery from orchestrator restarts
✅ Automatic recovery from engine restarts
✅ Database transaction rollback on failures
✅ Kafka consumer offset management
✅ Task state recovery after system failures

### Out of Scope: Advanced Features
❌ Complex DAG dependencies (multiple parents per task)
❌ Analysis cancellation
❌ Manual retry UI
❌ Real-time alerting and notifications
❌ Metrics dashboards and visualization
❌ Authentication and authorization
❌ REST API for external integrations
❌ File validation and malware pre-screening
❌ File metadata tracking (size, hash) - deferred to future
❌ Kubernetes deployment configurations
❌ Horizontal Pod Autoscaler (HPA) setup
❌ Distributed tracing integration (Jaeger/Zipkin)
❌ Advanced monitoring (Prometheus/Grafana)

### Out of Scope: Future Enhancements
❌ Cloud storage integration (S3/MinIO) for production
❌ Task output versioning
❌ Analysis prioritization and SLA management
❌ Multi-tenant support
❌ Webhook notifications on completion
❌ Advanced caching strategies (multi-layered cache)
❌ Circuit breaker pattern for unhealthy engines
❌ Dynamic engine scaling based on queue depth

## 5. User Stories

### Core Workflow Stories

**Story 1: Automated Analysis Execution**
- **As a** Security Operations Engineer
- **I want to** drop a .apk or .ipa file into the storage system and have it automatically analyzed
- **So that** I don't need to manually coordinate multiple analysis tools
- **Example:** Upload `malicious_app.apk` to `/storage/incoming/`, system automatically runs static analysis, decompiler, and signature checking in the correct order

**Story 2: Dependency-Aware Task Execution**
- **As a** Security Operations Engineer
- **I want** tasks with dependencies to wait for their parent task to complete
- **So that** analysis results are always based on complete prerequisite data
- **Example:** Decompiler task waits for static analysis to complete, then uses its output to perform deeper code analysis

**Story 3: Fault-Tolerant Processing**
- **As a** Security Operations Engineer
- **I want** failed tasks to automatically retry with configurable limits
- **So that** transient failures don't require manual intervention
- **Example:** Database timeout during task processing triggers automatic retry (up to 3 attempts), only moving to DLQ after all retries exhausted

**Story 4: Analysis State Visibility**
- **As a** Security Analyst
- **I want to** query the current status of any analysis and see which tasks have completed
- **So that** I know when results are available and can investigate failures
- **Example:** Query analysis ID 456 returns: Static Analysis (COMPLETED), Decompiler (RUNNING, attempt 1), Signature Check (PENDING)

**Story 5: Scalable Engine Capacity**
- **As a** Security Operations Engineer
- **I want to** scale up heavy analysis engines independently from lighter engines
- **So that** I can optimize resource usage and throughput
- **Example:** Run 5 replicas of dynamic analysis engine (slow) but only 2 replicas of signature check engine (fast)

**Story 6: Configuration-Driven Workflows**
- **As a** Security Operations Engineer
- **I want to** define new analysis workflows by updating database configuration
- **So that** I can add new analysis types without redeploying code
- **Example:** Add a new "permissions analyzer" task to APK analysis config, next APK file automatically includes this new task

**Story 7: Complete Failure Tracking**
- **As a** Security Analyst
- **I want** to see a summary of all failed tasks with error messages
- **So that** I can understand what went wrong and decide if re-analysis is needed
- **Example:** Analysis marked FAILED with summary showing "Decompiler task failed after 3 attempts: OutOfMemoryError"

**Story 8: System Recovery After Downtime**
- **As a** Security Operations Engineer
- **I want** the system to automatically resume processing pending tasks after restart
- **So that** maintenance windows don't cause data loss or require manual recovery
- **Example:** Orchestrator restart during analysis → system reads pending tasks from DB and resumes processing

## 6. Core Architecture & Patterns

### High-Level Architecture

The platform follows an **event-driven microservices architecture** with the following key components:

```
[File Storage] → [file-events topic] → [Orchestrator Service]
                                              ↓
                                    [PostgreSQL + Redis]
                                              ↓
                                [Outbox Poller] → [Engine Topics]
                                              ↓
        ┌─────────────────────────────────────┼─────────────────────────────────┐
        ↓                     ↓               ↓               ↓                 ↓
[Static Analysis]   [Dynamic Analysis]  [Decompiler]  [Signature Check]  [More Engines]
        ↓                     ↓               ↓               ↓                 ↓
                      [orchestrator-responses topic]
                                              ↓
                                      [Orchestrator Service]
                                              ↓
                                    [Update State + Next Tasks]
```

### Architecture Decisions (Finalized)

These decisions were made to balance simplicity, performance, and fault tolerance for the MVP:

#### Database Architecture

**Decision 1: Heartbeat Storage**
- **Choice:** Store `last_heartbeat_at` directly in `analysis_task` table
- **Rationale:** Simpler queries without joins, heartbeat is transient data with no history needs
- **Trade-off:** Accepted - table width increases but queries are faster

**Decision 2: Dependency Storage**
- **Choice:** Store both `task_config.depends_on_task_config_id` (template) AND `analysis_task.depends_on_task_id` (runtime)
- **Rationale:** Clear separation between configuration blueprint and runtime execution state
- **Trade-off:** Accepted - slight redundancy for better query performance and clarity

**Decision 3: File Metadata**
- **Choice:** Do NOT include file_size or file_hash in MVP
- **Rationale:** Not critical for core functionality, can be added in future iteration
- **Future:** Will add for auditing and duplicate detection in post-MVP phase

#### Caching Architecture

**Decision 4: Cache Depth**
- **Choice:** Store full task details in Redis cache (not just IDs)
- **Rationale:** Reduces DB roundtrips during active analysis, memory cost acceptable for MVP scale
- **Trade-off:** Accepted - higher memory usage for significantly better read performance

**Decision 5: Cache Update Pattern**
- **Choice:** DB-first, then Redis (best-effort)
- **Pattern:** Update PostgreSQL in transaction → Update Redis cache → If Redis fails, log warning
- **Rationale:** Database is source of truth, cache staleness is temporary and self-healing
- **Trade-off:** Accepted - brief cache staleness vs. system availability

#### Spring Boot Configuration

**Decision 6: Database Strategy**
- **Choice:** Shared PostgreSQL database, same schema for all services
- **Rationale:** MVP simplicity, orchestrator writes state, engines rarely read (only for recovery)
- **Future:** May introduce separate schemas per service in microservices evolution

**Decision 7: Kafka Commit Strategy**
- **Choice:** Manual commit after DB transaction completes (at-least-once delivery)
- **Rationale:** Simpler than Kafka transactions, safe with idempotency keys, better performance
- **Trade-off:** Accepted - at-least-once with idempotency vs. exactly-once complexity

**Decision 8: Outbox Polling**
- **Choice:** Spring `@Scheduled` with fixed delay (1 second interval)
- **Rationale:** Built-in Spring feature, easy to configure, adequate for MVP throughput
- **Configuration:** `@Scheduled(fixedDelay = 1000)` with batch size of 50 events

#### Kafka Topic Architecture

**Decision 9: File Events Partitioning**
- **Choice:** 3 partitions for `file-events` topic
- **Rationale:** Allows parallel processing of file events, can scale to 3 orchestrator instances
- **Partition Key:** fileType (APK vs IPA) for even distribution

**Decision 10: Engine Topic Partitioning**
- **Choice:** Partition by `analysisId`
- **Rationale:** Ensures all tasks for same analysis go to same partition, maintains ordering for dependent tasks
- **Pattern:** `ProducerRecord<>(topic, analysisId.toString(), taskEvent)`

**Decision 11: Heartbeat Topic Strategy**
- **Choice:** Separate `task-heartbeats` topic (not piggybacked on responses)
- **Rationale:** High volume (every 30s per task), different retention (1 day vs 7 days), separate processing
- **Configuration:** 3 partitions, 1 day retention

### Directory Structure

```
MobileAnalysisPlatform/
├── common/                              # Shared code across services
│   ├── domain/                          # Domain models, enums
│   │   ├── Analysis.java
│   │   ├── AnalysisTask.java
│   │   └── TaskStatus.java
│   ├── events/                          # Event DTOs
│   │   ├── FileEvent.java
│   │   ├── TaskEvent.java
│   │   └── TaskResponseEvent.java
│   └── config/                          # Shared Spring configurations
│       ├── KafkaConfig.java
│       └── RedisConfig.java
├── orchestrator-service/                # Orchestration service
│   ├── src/main/java/.../orchestrator/
│   │   ├── api/                         # REST controllers (future)
│   │   ├── messaging/                   # Kafka consumers/producers
│   │   │   ├── FileEventConsumer.java
│   │   │   ├── TaskResponseConsumer.java
│   │   │   └── TaskEventProducer.java
│   │   ├── service/                     # Business logic
│   │   │   ├── AnalysisOrchestrator.java
│   │   │   ├── ConfigurationService.java
│   │   │   └── StateManager.java
│   │   ├── repository/                  # JPA repositories
│   │   │   ├── AnalysisRepository.java
│   │   │   ├── AnalysisTaskRepository.java
│   │   │   └── OutboxRepository.java
│   │   └── outbox/                      # Outbox pattern
│   │       └── OutboxPoller.java
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/                # Flyway scripts
├── engine-common/                       # Shared engine logic
│   ├── AbstractAnalysisEngine.java      # Base class for engines
│   ├── EngineConfiguration.java
│   └── HeartbeatService.java
├── static-analysis-engine/              # Static analysis engine
│   ├── src/main/java/.../engine/
│   │   ├── StaticAnalysisConsumer.java
│   │   └── StaticAnalyzer.java
│   └── src/main/resources/
│       └── application.yml
├── dynamic-analysis-engine/             # Dynamic analysis engine
├── decompiler-engine/                   # Decompiler engine
├── signature-check-engine/              # Signature verification engine
├── docker-compose.yml                   # Local development stack
├── pom.xml / build.gradle              # Multi-module build
└── .claude/
    ├── PRD.md                           # This document
    └── CLAUDE.md                        # Development standards
```

### Key Design Patterns

1. **Transactional Outbox Pattern**
   - All Kafka events written to `outbox` table in same transaction as domain changes
   - Separate poller reads outbox and publishes to Kafka
   - Ensures exactly-once semantics between DB and Kafka

2. **Event-Driven Architecture**
   - All inter-service communication via Kafka events
   - Services are loosely coupled
   - Natural horizontal scalability

3. **Idempotency Pattern**
   - Each task has unique `idempotency_key` (UUID)
   - Duplicate event processing is no-op
   - Prevents double execution on Kafka redelivery

4. **State Machine Pattern**
   - Analysis and Task states follow defined transitions
   - Invalid state transitions rejected
   - Clear lifecycle tracking

5. **Abstract Template Pattern** (Engine Common)
   - `AbstractAnalysisEngine` handles common logic (Kafka consumption, heartbeat, error handling)
   - Concrete engines implement `processTask()` method
   - Reduces boilerplate across engines

6. **Cache-Aside Pattern with Write-Through**
   - Read: Check Redis → if miss, read DB → populate Redis
   - Write: Update DB first → Update Redis (best-effort)
   - Configuration cached with long TTL
   - Analysis state cached during execution, deleted on completion

### Technology-Specific Patterns

**Spring Boot Patterns:**
- `@KafkaListener` with manual commit for transactional processing
- `@Scheduled(fixedDelay = 1000)` for outbox polling
- `@Transactional` for atomic DB operations
- `@Async` for non-blocking task execution

**Kafka Patterns:**
- Consumer groups for load balancing
- Partition key = analysisId for ordering and locality
- Dead Letter Topic for failed messages
- Manual offset commits after DB transaction

## 7. Core Features

### Feature 1: Dynamic Analysis Orchestration

**Purpose:** Coordinate multi-stage analysis workflows based on database configuration

**Operations:**
1. Receive file event from `file-events` topic
2. Load analysis configuration from Redis cache (fallback to PostgreSQL)
3. Create `analysis` record with status PENDING
4. Create all `analysis_task` records based on configuration
5. Update cache with initial state
6. Identify tasks without dependencies (ready to run)
7. Write task events to outbox for immediate dispatch
8. Transition analysis status to RUNNING

**Key Features:**
- Configuration cached for fast lookup
- Atomic transaction for state persistence and outbox writes
- Parallel dispatch of independent tasks
- Analysis ID serves as correlation ID for end-to-end tracing

### Feature 2: Task Dependency Management

**Purpose:** Ensure tasks execute in correct order based on dependencies

**Operations:**
1. Store dependency chain in `analysis_task.depends_on_task_id`
2. When task completes, query for dependent tasks
3. Check if all dependencies are COMPLETED
4. If ready, transition dependent task to DISPATCHED
5. Write to outbox for engine consumption

**Key Features:**
- Simple 1:1 parent-child relationships
- Automatic resolution of ready-to-run tasks
- Output path from parent task passed to child task
- Prevents execution of tasks with incomplete dependencies

### Feature 3: Transactional Outbox Pattern

**Purpose:** Guarantee exactly-once event delivery between database and Kafka

**Operations:**
1. All domain changes and event publishing in single transaction
2. Write events to `outbox` table (processed=false)
3. Commit transaction
4. Separate poller reads unprocessed outbox entries (Spring @Scheduled)
5. Publish to Kafka in batches of 50
6. Mark as processed=true
7. Handle idempotency on consumer side

**Key Features:**
- No lost events even if Kafka is unavailable
- Polling interval: 1 second (configurable)
- Batch size: 50 messages (configurable)
- Automatic retry on publish failures
- Cleanup of old processed records (7 day retention)

### Feature 4: Automatic Retry with DLQ

**Purpose:** Handle transient failures without manual intervention

**Operations:**
1. Engine fails to process task
2. Increment `analysis_task.attempts`
3. Check if attempts < max_retries (from config)
4. If yes: Send task back to engine topic with exponential backoff
5. If no: Send to DLQ, mark task as FAILED
6. Update analysis status to FAILED if any task fails completely

**Key Features:**
- Configurable max retries per engine type (default: 3)
- Kafka-level retry with backoff
- DLQ for manual investigation
- Error message persisted in `analysis_task.error_message`
- Analysis marked FAILED only after all retries exhausted

### Feature 5: Heartbeat Mechanism

**Purpose:** Detect zombie tasks and prevent indefinite blocking

**Operations:**
1. Engine starts task execution
2. Send heartbeat event every 30 seconds to `task-heartbeats` topic
3. Orchestrator updates `analysis_task.last_heartbeat_at` (in DB)
4. Background job checks for stale heartbeats (no update in 2 minutes)
5. Mark stale tasks as FAILED, trigger retry logic

**Key Features:**
- Heartbeat interval: 30 seconds
- Stale threshold: 2 minutes
- Heartbeat timestamp stored directly in analysis_task table (no separate table)
- Automatic detection of crashed engine instances
- Prevents analyses from hanging indefinitely

### Feature 6: State Management & Caching

**Purpose:** Fast reads/writes of analysis state during execution

**Operations:**
1. On analysis creation: Write to PostgreSQL, cache in Redis
2. On task status update: Update PostgreSQL in transaction
3. Update Redis cache immediately after DB commit (best-effort)
4. On state query: Read from Redis (fast)
5. If Redis miss: Read from PostgreSQL, repopulate cache
6. On analysis completion: Delete from Redis cache

**Key Features:**
- Redis cache key: `analysis-state:{analysisId}`
- Cache stores full task details (status, outputPath, errorMessage, attempts)
- DB-first update pattern: PostgreSQL is source of truth
- Fallback to DB if Redis unavailable
- Configuration cache key: `analysis-config:{fileType}`
- Configuration cached indefinitely (invalidate on update)

### Feature 7: Idempotency Handling

**Purpose:** Safe handling of duplicate Kafka messages

**Operations:**
1. Generate UUID `idempotency_key` for each task event
2. Store in `analysis_task.idempotency_key` (unique constraint)
3. On event consumption, check if key exists
4. If exists and task already processed: Log and skip (no-op)
5. If exists and task in progress: Update heartbeat and continue
6. If not exists: Process normally

**Key Features:**
- Database-level uniqueness enforcement
- Duplicate detection within milliseconds
- Safe Kafka redelivery handling
- No accidental double-processing
- At-least-once delivery with idempotency = effectively exactly-once

### Feature 8: Analysis Result Storage

**Purpose:** Persist task outputs for downstream processing

**Operations:**
1. Engine completes task processing
2. Write output to filesystem: `/storage/analysis/{analysisId}/task_{taskId}_output.json`
3. Include output path in task response event
4. Store path in `analysis_task.output_path`
5. Dependent tasks read from this path

**Key Features:**
- Consistent path structure
- Each task gets dedicated output file
- Local filesystem for MVP (S3 in production)
- Output path automatically provided to dependent tasks

## 8. Technology Stack

### Backend
- **Java:** 21 LTS (latest long-term support)
- **Spring Boot:** 3.3.x (latest 3.x, uses Java 17+ features)
- **Spring Framework:** 6.x (included with Spring Boot 3.x)
- **Spring Data JPA:** For database access
- **Spring Kafka:** For Kafka integration
- **Spring Data Redis:** For caching

### Message Streaming
- **Apache Kafka:** 3.8.x (latest with KRaft mode)
- **KRaft:** Kafka's native consensus protocol (no ZooKeeper)

### Data Storage
- **PostgreSQL:** 16.x (primary data store)
- **Redis:** 7.x (caching layer)

### Database Migrations
- **Flyway:** 10.x (database version control)

### Build & Dependencies
- **Maven:** 3.9.x OR **Gradle:** 8.x (multi-module build)
- **Lombok:** Reduce boilerplate code
- **Jackson:** JSON serialization

### Testing
- **JUnit 5:** Unit testing framework
- **Mockito:** Mocking framework
- **Testcontainers:** Integration tests with real Kafka, PostgreSQL, Redis
- **Spring Boot Test:** Testing utilities
- **AssertJ:** Fluent assertions

### Development Tools
- **Docker:** Container runtime
- **Docker Compose:** Local development environment orchestration
- **Git:** Version control

### Optional Dependencies
- **Spring Boot Actuator:** Health checks, metrics endpoints (for heartbeat)
- **Spring Retry:** Declarative retry logic
- **Micrometer:** Metrics instrumentation (future)
- **SLF4J + Logback:** Logging

### Future Production Stack
- **Kubernetes:** Container orchestration
- **MinIO / S3:** Object storage for analysis results
- **Prometheus:** Metrics collection
- **Grafana:** Metrics visualization
- **Jaeger / Zipkin:** Distributed tracing

## 9. Security & Configuration

### Authentication & Authorization
**MVP Scope:** None (deferred to post-MVP)
- No user authentication required
- All services trust each other
- No API access control

**Post-MVP:** OAuth2 / JWT-based authentication for REST APIs

### Configuration Management

All services use environment-specific configuration files:

**application.yml** (per service):
```yaml
spring:
  application:
    name: orchestrator-service
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/mobile_analysis}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      enable-auto-commit: false  # Manual commit after DB transaction
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

app:
  storage:
    base-path: ${STORAGE_PATH:/storage}
  outbox:
    poll-interval-ms: 1000
    batch-size: 50
```

**Engine Configuration:**
```yaml
app:
  engine:
    type: STATIC_ANALYSIS
    timeout-seconds: 300
    max-retries: 3
    heartbeat-interval-ms: 30000
```

### Security - In Scope
- Database credentials via environment variables
- No secrets in source code or configuration files
- Local filesystem access control (Unix permissions)
- Kafka topic ACLs (future)

### Security - Out of Scope
- Network encryption (TLS)
- Service-to-service authentication
- File content validation and malware scanning
- Rate limiting
- DDoS protection
- Secrets management (Vault, AWS Secrets Manager)

### Deployment Configuration

**Local Development (Docker Compose):**
- All services in same Docker network
- Shared PostgreSQL instance (same schema)
- Shared Redis instance
- Single Kafka broker with KRaft

**Production (Future):**
- Kubernetes deployments with separate namespaces
- Managed database (RDS, Cloud SQL)
- Managed Kafka (MSK, Confluent Cloud)
- Managed Redis (ElastiCache, Redis Cloud)
- Auto-scaling based on queue depth

## 10. API Specification

### Internal Kafka Events (Not Public APIs)

#### File Event (file-events topic)
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/app_v1.2.3.apk",
  "fileType": "APK",
  "timestamp": "2026-01-19T20:30:00Z"
}
```

**Note:** analysisId generated by orchestrator serves as correlation ID

#### Task Event (engine-specific topics)
```json
{
  "eventId": "660e8400-e29b-41d4-a716-446655440001",
  "taskId": 12345,
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "engineType": "STATIC_ANALYSIS",
  "filePath": "/storage/incoming/app_v1.2.3.apk",
  "dependentTaskOutputPath": null,
  "idempotencyKey": "880e8400-e29b-41d4-a716-446655440003",
  "timeoutSeconds": 300,
  "timestamp": "2026-01-19T20:30:05Z"
}
```

**Kafka Details:**
- Partition key: `analysisId` (ensures task ordering)
- Topic: Dedicated per engine type (e.g., `static-analysis-tasks`)

#### Task Response Event (orchestrator-responses topic)
```json
{
  "eventId": "990e8400-e29b-41d4-a716-446655440004",
  "taskId": 12345,
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "status": "COMPLETED",
  "outputPath": "/storage/analysis/770e8400-e29b-41d4-a716-446655440002/task_12345_output.json",
  "errorMessage": null,
  "attempts": 1,
  "timestamp": "2026-01-19T20:35:00Z"
}
```

**Kafka Details:**
- Partition key: `analysisId`
- Topic: `orchestrator-responses` (3 partitions)

#### Heartbeat Event (task-heartbeats topic)
```json
{
  "taskId": 12345,
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "engineType": "DYNAMIC_ANALYSIS",
  "status": "RUNNING",
  "timestamp": "2026-01-19T20:32:30Z"
}
```

**Kafka Details:**
- Separate topic with shorter retention (1 day)
- 3 partitions
- High volume (every 30 seconds per running task)

### REST API (Future - Post MVP)

#### POST /api/v1/analysis
Submit file for analysis
```json
Request:
{
  "filePath": "/storage/incoming/app.apk",
  "fileType": "APK"
}

Response (202 Accepted):
{
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "status": "PENDING",
  "createdAt": "2026-01-19T20:30:00Z"
}
```

#### GET /api/v1/analysis/{analysisId}
Get analysis status
```json
Response (200 OK):
{
  "analysisId": "770e8400-e29b-41d4-a716-446655440002",
  "fileType": "APK",
  "status": "RUNNING",
  "startedAt": "2026-01-19T20:30:05Z",
  "tasks": [
    {
      "taskId": 12345,
      "engineType": "STATIC_ANALYSIS",
      "status": "COMPLETED",
      "outputPath": "/storage/analysis/.../task_12345_output.json",
      "completedAt": "2026-01-19T20:35:00Z"
    },
    {
      "taskId": 12346,
      "engineType": "DECOMPILER",
      "status": "RUNNING",
      "attempts": 1
    },
    {
      "taskId": 12347,
      "engineType": "SIGNATURE_CHECK",
      "status": "PENDING"
    }
  ]
}
```

## 11. Success Criteria

### MVP Success Definition
The MVP is successful when:
1. A file event triggers end-to-end analysis completion with multiple dependent tasks
2. Failed tasks automatically retry and eventually reach DLQ after max attempts
3. System recovers gracefully from orchestrator or engine restarts
4. Multiple analyses run concurrently without interference
5. Analysis state is queryable at any time with accurate task status

### Functional Requirements
✅ File event consumption triggers analysis creation within 1 second
✅ Configuration loaded from cache in <10ms, from DB in <100ms
✅ All independent tasks dispatch in parallel immediately
✅ Dependent tasks execute only after parent completion
✅ Task failures retry automatically up to configured max attempts
✅ Failed tasks move to DLQ after final retry
✅ Heartbeat mechanism detects stale tasks within 2 minutes
✅ Duplicate events result in no-op (idempotency)
✅ Analysis state persists across orchestrator restarts
✅ Pending tasks resume processing after engine restarts
✅ Each task output stored at consistent filesystem path
✅ Analysis marked FAILED with error summary when any task fails completely
✅ System handles 10+ concurrent analyses without degradation

### Quality Indicators
✅ 95%+ of transient errors recovered via retry mechanism
✅ Zero data loss during normal operations
✅ Zero duplicate task executions
✅ Mean time to recovery (MTTR) < 5 minutes after service restart
✅ Task state transitions follow defined state machine (no invalid states)

### User Experience Goals
✅ Clear visibility into analysis progress (which tasks completed, which pending)
✅ Understandable error messages for failed tasks
✅ Predictable task execution order based on dependencies
✅ No manual intervention required for transient failures

## 12. Implementation Phases

### Phase 1: Foundation & Orchestrator Core
**Duration:** 2-3 weeks

**Goal:** Establish project structure, database schema, and basic orchestrator

**Deliverables:**
✅ Multi-module project structure (common, orchestrator, engine-common)
✅ Database schema with Flyway migrations (including heartbeat in analysis_task)
✅ PostgreSQL and Redis integration
✅ Domain models (Analysis, AnalysisTask, TaskConfig, AnalysisConfig)
✅ Configuration service with Redis caching (full task details)
✅ File event consumer in orchestrator
✅ Analysis creation logic (read config, create tasks, persist state)
✅ Basic task dispatch to Kafka topics (partition by analysisId)
✅ Unit tests for domain logic
✅ Docker Compose setup (PostgreSQL, Redis, Kafka with KRaft)

**Validation:**
- File event consumed successfully
- Analysis and task records created in database
- Initial state cached in Redis (full task objects)
- Task events published to Kafka topic with correct partition key

### Phase 2: Engine Framework & First Engine
**Duration:** 2 weeks

**Goal:** Build engine framework and implement first analysis engine

**Deliverables:**
✅ AbstractAnalysisEngine base class
✅ Task event consumption in engine (manual commit)
✅ Heartbeat mechanism implementation (separate topic)
✅ Task execution with timeout handling
✅ Task response event publishing
✅ Static analysis engine (first concrete engine)
✅ Output file writing to filesystem
✅ Error handling and retry logic
✅ Integration tests with Testcontainers

**Validation:**
- Engine consumes task event
- Task executes within timeout
- Heartbeat events sent every 30 seconds to separate topic
- Output file written to correct path
- Response event sent to orchestrator-responses topic
- Failed tasks trigger retry

### Phase 3: Orchestrator Response Handling & Dependencies
**Duration:** 2-3 weeks

**Goal:** Complete orchestration cycle with dependency resolution

**Deliverables:**
✅ Task response consumer in orchestrator
✅ Task status update logic (DB first, then Redis)
✅ Dependency resolution (check if dependent tasks ready)
✅ Next task dispatch logic
✅ Analysis completion detection
✅ Analysis failure detection and summary
✅ Idempotency handling (check idempotency key)
✅ State cache updates on every change (best-effort)
✅ End-to-end integration test (file → analysis → completion)

**Validation:**
- Task completion updates database and cache
- Dependent tasks dispatch only after parent completes
- Analysis marked COMPLETED when all tasks done
- Analysis marked FAILED if any task fails
- Error summary includes all failed tasks with messages
- Duplicate events are no-ops

### Phase 4: Transactional Outbox & Fault Tolerance
**Duration:** 2 weeks

**Goal:** Implement outbox pattern and recovery mechanisms

**Deliverables:**
✅ Outbox table and repository
✅ Transactional event writing (DB + outbox in single transaction)
✅ Outbox poller service (@Scheduled with fixedDelay=1000)
✅ Kafka event publishing from outbox (batch size 50)
✅ Outbox cleanup for processed events
✅ Heartbeat monitoring job (detect stale tasks using last_heartbeat_at)
✅ DLQ configuration for failed tasks
✅ Recovery logic for system restarts
✅ Manual Kafka offset commits (after DB transaction)
✅ Comprehensive failure scenario tests

**Validation:**
- Events persisted to outbox before Kafka publish
- Outbox poller publishes and marks processed
- Stale tasks (no heartbeat for 2 min) marked FAILED
- Orchestrator restart resumes pending analyses
- Engine restart resumes pending tasks
- Failed tasks move to DLQ after max retries
- Zero lost events even with Kafka downtime

### Phase 5: Additional Engines & Production Readiness
**Duration:** 2-3 weeks

**Goal:** Add more engines and prepare for production

**Deliverables:**
✅ Dynamic analysis engine
✅ Decompiler engine
✅ Signature check engine
✅ Multi-engine integration test (complex dependency chain)
✅ Configuration management documentation
✅ Deployment guide (Docker Compose)
✅ Logging with analysis IDs as correlation IDs across all services
✅ Health check endpoints (Spring Actuator)
✅ Performance testing (10+ concurrent analyses)
✅ Load testing for each engine type
✅ Production checklist document

**Validation:**
- All four engines process tasks successfully
- Complex analysis (APK with 10 tasks, mixed dependencies) completes
- System handles 20 concurrent analyses
- All logs contain correlation IDs (analysisId)
- Health checks return accurate status
- Recovery scenarios tested and documented

## 13. Future Considerations

### Post-MVP Enhancements (Q2 2026)

**Analysis Management UI**
- Web dashboard for analysis submission
- Real-time progress visualization
- Failed analysis investigation tools
- Manual retry from UI
- Search and filter analyses

**Advanced Dependency Management**
- DAG support (multiple parents per task)
- Conditional task execution
- Dynamic task generation based on analysis results
- Task priority management

**Observability & Monitoring**
- Prometheus metrics integration
- Grafana dashboards (throughput, latency, failure rates)
- Distributed tracing with Jaeger/Zipkin
- Alerting on high failure rates
- SLO/SLA tracking

**Cloud Storage Integration**
- S3/MinIO for analysis outputs
- Pre-signed URLs for file access
- Automatic cleanup of old analyses
- Output versioning on task retries

**File Metadata Tracking**
- Add file_size and file_hash columns to analysis table
- Duplicate analysis detection by hash
- Storage usage analytics
- Large file timeout correlation

### Integration Opportunities (Q3 2026)

**Security Tools Integration**
- VirusTotal API for malware scanning
- OWASP Dependency Check integration
- Mobile Security Framework (MobSF) integration
- Custom security rule engine

**CI/CD Integration**
- Jenkins/GitLab CI plugin for automated analysis
- GitHub Actions integration
- Webhook notifications on completion
- API for programmatic access

**Enterprise Features**
- Multi-tenancy support
- Role-based access control (RBAC)
- Organization-level configuration
- Audit logging
- Compliance reporting

### Advanced Features (Q4 2026)

**Intelligent Scaling**
- Kubernetes HPA based on Kafka queue depth
- Predictive scaling using historical data
- Cost optimization through spot instances
- Dynamic resource allocation per engine

**Advanced Failure Handling**
- Circuit breaker for unhealthy engines
- Automatic rerouting to healthy instances
- Gradual retry backoff with jitter
- Partial result handling (some tasks succeed, some fail)

**Analysis Orchestration Enhancements**
- Analysis cancellation
- Analysis pause/resume
- Task result caching (skip if input unchanged)
- Incremental analysis (only changed parts)

## 14. Risks & Mitigations

### Risk 1: Kafka Message Ordering
**Risk:** Out-of-order message consumption could cause dependent tasks to execute before parents

**Impact:** High - Core functionality broken

**Mitigation:**
- Use `analysisId` as partition key to ensure all events for same analysis go to same partition
- Implement strict state checks (task cannot transition to RUNNING unless dependency is COMPLETED)
- Add validation layer that rejects invalid state transitions
- Integration tests specifically for ordering scenarios

### Risk 2: Database Connection Pool Exhaustion
**Risk:** High concurrency could exhaust DB connections, causing failures

**Impact:** Medium - Transient failures, covered by retry

**Mitigation:**
- Configure appropriate connection pool size (HikariCP)
- Set reasonable max pool size and timeout
- Monitor connection pool metrics
- Implement connection leak detection
- Use database connection pooling best practices

### Risk 3: Redis Cache Inconsistency
**Risk:** Redis cache could become stale if update fails but DB succeeds

**Impact:** Low - Temporary inconsistency, self-healing

**Mitigation:**
- DB-first update pattern (PostgreSQL is source of truth)
- Best-effort Redis updates with logging on failure
- Fallback to DB if Redis unavailable
- Cache automatically repopulated on next read
- Delete cache on analysis completion (no long-term staleness)

### Risk 4: Long-Running Task Memory Leaks
**Risk:** Engine memory leaks could cause OOM errors over time

**Impact:** High - Engine crashes, analyses fail

**Mitigation:**
- Implement per-task timeout (kills runaway tasks)
- Monitor heap usage with JVM metrics
- Restart engines periodically in production
- Use container memory limits
- Comprehensive load testing to detect leaks early

### Risk 5: Outbox Table Growth
**Risk:** Outbox table could grow indefinitely if cleanup fails

**Impact:** Low - Performance degradation over time

**Mitigation:**
- Scheduled cleanup job (delete processed records older than 7 days)
- Database partitioning by date
- Monitor table size with alerts
- Archive old records to separate table if needed
- Index on `processed` and `created_at` for efficient cleanup

## 15. Appendix

### Related Documents
- [CLAUDE.md](./../CLAUDE.md) - Development standards and conventions
- [Spring Boot 3.x Documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Transactional Outbox Pattern](https://microservices.io/patterns/data/transactional-outbox.html)
- [PostgreSQL 16 Documentation](https://www.postgresql.org/docs/16/)
- [Redis Documentation](https://redis.io/documentation)

### Key Dependencies
- **Spring Boot Starter Web** - REST API framework (future)
- **Spring Boot Starter Data JPA** - Database access
- **Spring Boot Starter Data Redis** - Redis integration
- **Spring Kafka** - Kafka integration
- **PostgreSQL JDBC Driver** - Database connectivity
- **Flyway Core** - Database migrations
- **Lombok** - Reduce boilerplate
- **Testcontainers** - Integration testing

### Repository Structure
- **Main Branch:** `main` - Production-ready code
- **Feature Branches:** `feature/{scope}/{description}` - Individual features
- **Commit Convention:** Conventional Commits (see CLAUDE.md)

### Database Schema (Finalized)

```sql
-- Configuration Tables
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

-- Runtime Tables
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

-- Outbox Table
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

**Key Schema Notes:**
- `analysis.id` is UUID (serves as correlation ID)
- `last_heartbeat_at` stored directly in `analysis_task` table
- Both `task_config.depends_on_task_config_id` AND `analysis_task.depends_on_task_id` present
- NO file_size or file_hash columns in MVP (deferred to post-MVP)
- Index on `last_heartbeat_at` for stale task detection

### Database Connection Details (Local Dev)
```yaml
PostgreSQL:
  Host: localhost
  Port: 5432
  Database: mobile_analysis
  Username: postgres
  Password: postgres
  Connection Pool: HikariCP (default)
  Max Pool Size: 10

Redis:
  Host: localhost
  Port: 6379
  Database: 0

Kafka:
  Bootstrap: localhost:9092
  Protocol: PLAINTEXT
```

### Kafka Topics Configuration (Finalized)

```yaml
file-events:
  Partitions: 3
  Replication: 1
  Retention: 7 days
  Partition Key: fileType

static-analysis-tasks:
  Partitions: 3
  Replication: 1
  Retention: 7 days
  Partition Key: analysisId

dynamic-analysis-tasks:
  Partitions: 5
  Replication: 1
  Retention: 7 days
  Partition Key: analysisId

decompiler-tasks:
  Partitions: 2
  Replication: 1
  Retention: 7 days
  Partition Key: analysisId

signature-check-tasks:
  Partitions: 1
  Replication: 1
  Retention: 7 days
  Partition Key: analysisId

orchestrator-responses:
  Partitions: 3
  Replication: 1
  Retention: 7 days
  Partition Key: analysisId

task-heartbeats:
  Partitions: 3
  Replication: 1
  Retention: 1 day
  Partition Key: analysisId
  Note: Separate topic, high volume, shorter retention

orchestrator-responses-dlq:
  Partitions: 1
  Replication: 1
  Retention: 30 days
  Partition Key: analysisId
```

**Key Kafka Decisions:**
- All engine topics partitioned by `analysisId` for ordering
- file-events has 3 partitions for parallel orchestrator instances
- task-heartbeats is separate topic with 1-day retention
- All topics use analysisId as partition key except file-events (uses fileType)

### Redis Cache Structure (Finalized)

```json
// Configuration Cache
// Key: "analysis-config:APK" or "analysis-config:IPA"
// TTL: None (manual invalidation on config updates)
{
  "id": 1,
  "fileType": "APK",
  "name": "Android Analysis v1",
  "tasks": [
    {
      "id": 101,
      "engineType": "STATIC_ANALYSIS",
      "order": 1,
      "dependsOnTaskConfigId": null,
      "timeoutSeconds": 300,
      "maxRetries": 3
    },
    {
      "id": 102,
      "engineType": "DECOMPILER",
      "order": 2,
      "dependsOnTaskConfigId": 101,
      "timeoutSeconds": 600,
      "maxRetries": 2
    }
  ]
}

// Analysis State Cache (FULL TASK DETAILS)
// Key: "analysis-state:{analysisId}"
// TTL: None (deleted on completion)
// Update Pattern: DB first, then Redis (best-effort)
{
  "id": "770e8400-e29b-41d4-a716-446655440002",
  "status": "RUNNING",
  "fileType": "APK",
  "tasks": [
    {
      "id": 12345,
      "engineType": "STATIC_ANALYSIS",
      "status": "COMPLETED",
      "outputPath": "/storage/analysis/.../task_12345_output.json",
      "attempts": 1,
      "errorMessage": null,
      "completedAt": "2026-01-19T20:35:00Z"
    },
    {
      "id": 12346,
      "engineType": "DECOMPILER",
      "status": "RUNNING",
      "outputPath": null,
      "attempts": 1,
      "errorMessage": null,
      "lastHeartbeatAt": "2026-01-19T20:36:30Z"
    }
  ],
  "updatedAt": "2026-01-19T20:36:00Z"
}
```

### Analysis Configuration Example
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

-- Task 3: Signature Check (no dependency, runs in parallel)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'SIGNATURE_CHECK', 3, NULL, 120, 3);

-- Task 4: Dynamic Analysis (depends on Task 2)
INSERT INTO task_config (analysis_config_id, engine_type, task_order, depends_on_task_config_id, timeout_seconds, max_retries)
VALUES (1, 'DYNAMIC_ANALYSIS', 4, 2, 1800, 1);
```

### Contact & Support
- **Repository:** [https://github.com/TamirGit/MobileAnalysisPlatform](https://github.com/TamirGit/MobileAnalysisPlatform)
- **Issues:** GitHub Issues for bug reports and feature requests
- **Discussions:** GitHub Discussions for questions and ideas

---

**Document Version:** 1.1  
**Last Updated:** January 19, 2026  
**Status:** Architecture Finalized  
**Author:** Architecture Team  
**Next Steps:** Begin Phase 1 Implementation