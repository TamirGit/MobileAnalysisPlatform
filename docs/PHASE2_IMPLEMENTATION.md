# Phase 2 Implementation: Task Response Handling & Dependency Resolution

## Overview

Phase 2 transforms the Mobile Analysis Platform from a one-way task dispatcher into a complete **bidirectional workflow orchestrator** with:

- ✅ **Task response handling** - Engines report completion status back to orchestrator
- ✅ **Dependency resolution** - Automatic cascading task execution
- ✅ **Analysis completion detection** - Workflow lifecycle management
- ✅ **Heartbeat monitoring** - Stale task detection and recovery
- ✅ **Retry logic** - Automatic retry with exponential backoff
- ✅ **Engine framework** - Reusable template for all analysis engines

## Architecture

### Bidirectional Workflow

```
┌─────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR SERVICE                      │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  FileEventConsumer ──► AnalysisOrchestrator                │
│         │                      │                            │
│         └──► Creates Analysis + Tasks                      │
│                      │                                      │
│                      ▼                                      │
│              OutboxPoller ────────────┐                     │
│                                       │                     │
│  TaskResponseConsumer ◄───────────────┘                     │
│         │                                                   │
│         ├──► DependencyResolver ──► Dispatch child tasks   │
│         ├──► AnalysisCompletionService                     │
│         └──► Update task status                            │
│                                                             │
│  HeartbeatConsumer ──► Update last_heartbeat_at            │
│                                                             │
│  HeartbeatMonitor (@Scheduled)                             │
│         └──► Detect stale tasks                            │
│         └──► TaskRetryService ──► Retry or DLQ             │
│                                                             │
└─────────────────────────────────────────────────────────────┘
                       │         ▲
                       │         │
                 Kafka Topics
                       │         │
        ┌──────────────┴─────────┴────────────────┐
        │                                         │
        ▼                                         │
  task-events                         orchestrator-responses
  task-heartbeats                                 │
        │                                         │
        │                                         │
┌───────▼─────────────────────────────────────────┴──────────┐
│                 ANALYSIS ENGINE (e.g., Static)             │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  TaskConsumer ──► AbstractAnalysisEngine                  │
│                          │                                 │
│                          ├──► validateTask()              │
│                          ├──► processTask()               │
│                          └──► Send TaskResponseEvent      │
│                                                            │
│  HeartbeatService (@Scheduled every 30s)                  │
│         └──► Send HeartbeatEvent                          │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## Components

### Orchestrator Services

#### 1. TaskResponseConsumer
**Purpose**: Process task completion events from engines

**Responsibilities**:
- Consume `TaskResponseEvent` from `orchestrator-responses` topic
- Update task status (COMPLETED/FAILED) in database
- Invoke `DependencyResolver` to dispatch dependent tasks
- Invoke `AnalysisCompletionService` to check workflow completion
- Manual Kafka acknowledgment for at-least-once processing

**Key Methods**:
```java
public void handleTaskResponse(TaskResponseEvent event, Acknowledgment ack)
public void processTaskResponse(TaskResponseEvent event) // @Transactional
```

#### 2. DependencyResolver
**Purpose**: Resolve task dependencies and dispatch ready tasks

**Responsibilities**:
- Find all tasks depending on completed task
- Check if dependencies are satisfied
- Create `OutboxEventEntity` for ready tasks
- Update task status to DISPATCHED

**Dependency Rules** (Phase 2):
- Simple 1:1 parent-child dependencies
- Child runs only after parent COMPLETED
- DAG support planned for future phase

#### 3. AnalysisCompletionService
**Purpose**: Detect and mark analysis completion

**Terminal States**:
- **COMPLETED**: All tasks have status COMPLETED
- **FAILED**: Any task has status FAILED (after retry exhaustion)

**Responsibilities**:
- Check task statuses for analysis
- Update analysis status to COMPLETED/FAILED
- Delete Redis cache for completed analyses
- Log completion summary with duration

#### 4. HeartbeatConsumer
**Purpose**: Track engine health via heartbeat signals

**Responsibilities**:
- Consume `HeartbeatEvent` from `task-heartbeats` topic
- Update `last_heartbeat_at` timestamp in database
- Lightweight processing (just timestamp update)

**Frequency**: Engines send heartbeat every 30 seconds

#### 5. HeartbeatMonitor
**Purpose**: Detect and handle stale/zombie tasks

**Responsibilities**:
- Run every minute via `@Scheduled`
- Find RUNNING tasks with no heartbeat for 2+ minutes
- Mark stale tasks as FAILED
- Trigger `TaskRetryService` for retry

**Stale Task Definition**: No heartbeat for 2 minutes

#### 6. TaskRetryService
**Purpose**: Retry failed tasks with budget tracking

**Retry Strategy**:
- Check `maxRetries` from `TaskConfig`
- If `attempts < maxRetries`: Create new outbox event for retry
- If `attempts >= maxRetries`: Keep FAILED (DLQ candidate)

**Retry Mechanism**:
- Reset task status to PENDING
- Increment attempt counter
- Create outbox event with retry metadata

### Engine Common Framework

#### 1. AbstractAnalysisEngine
**Purpose**: Template Method pattern base for all engines

**Workflow**:
```java
1. validateTask(event)      // Validate requirements
2. startHeartbeat()          // Begin health reporting
3. processTask(event)        // Engine-specific logic (abstract)
4. sendSuccessResponse()     // Report COMPLETED
   OR
   sendFailureResponse()     // Report FAILED
5. stopHeartbeat()           // Stop health reporting
6. acknowledge()             // Commit Kafka offset
```

**Subclass Responsibilities**:
```java
protected abstract String processTask(TaskEvent event) throws Exception;
protected abstract String getEngineType();
protected void validateTask(TaskEvent event) // Optional override
```

#### 2. HeartbeatService
**Purpose**: Scheduled heartbeat sender

**Behavior**:
- Maintains `AtomicReference<RunningTaskContext>` for active task
- Sends `HeartbeatEvent` every 30 seconds via `@Scheduled`
- Thread-safe start/stop operations
- Graceful failure handling (logs but doesn't crash)

**Usage**:
```java
heartbeatService.startHeartbeat(taskId, analysisId, engineType);
// Task processing...
heartbeatService.stopHeartbeat();
```

#### 3. EngineConfiguration
**Purpose**: Spring configuration for Kafka and scheduling

**Provides**:
- `ConsumerFactory<String, TaskEvent>` with manual ack
- `KafkaTemplate<String, Object>` for responses/heartbeats
- `@EnableScheduling` for heartbeat service

### Static Analysis Engine

**First Concrete Implementation**:

```java
@Component
public class StaticAnalysisEngine extends AbstractAnalysisEngine {
    
    @Override
    protected String processTask(TaskEvent event) throws Exception {
        // 1. Validate file type (APK/IPA)
        // 2. Run static analysis tools
        // 3. Generate results JSON
        // 4. Return output path
    }
    
    @Override
    protected String getEngineType() {
        return "STATIC_ANALYSIS";
    }
}
```

**Kafka Consumer**:
```java
@KafkaListener(topics = "static-analysis-tasks")
public void consumeTask(TaskEvent event, Acknowledgment ack) {
    engine.handleTaskEvent(event, ack);
}
```

## Task Lifecycle

### Complete Flow (Success Case)

1. **Task Created**: `AnalysisOrchestrator` creates tasks with status PENDING
2. **Task Dispatched**: Ready tasks written to outbox → Kafka topic
3. **Task Running**: Engine consumes event, starts processing
4. **Heartbeats**: Engine sends heartbeat every 30 seconds
5. **Task Completed**: Engine sends `TaskResponseEvent` with COMPLETED status
6. **Response Processed**: Orchestrator updates task, resolves dependencies
7. **Children Dispatched**: Dependent tasks automatically dispatched
8. **Analysis Complete**: When all tasks done, analysis marked COMPLETED

### Failure & Retry Flow

1. **Task Fails**: Engine sends `TaskResponseEvent` with FAILED status
2. **Retry Check**: `TaskRetryService` checks retry budget
3. **Retry Dispatched**: If attempts < maxRetries, new outbox event created
4. **Retry Processing**: Task re-executed by engine with incremented attempts
5. **Final Failure**: If maxRetries reached, task stays FAILED
6. **Analysis Fails**: Analysis marked FAILED if any task fails permanently

### Stale Task Recovery

1. **Heartbeat Missing**: Engine crashes without sending response
2. **Monitor Detects**: `HeartbeatMonitor` finds no heartbeat for 2+ minutes
3. **Mark Failed**: Task marked FAILED with timeout error
4. **Trigger Retry**: `TaskRetryService` attempts retry if budget remains
5. **Recovery**: New engine instance processes retry

## Kafka Topics

### orchestrator-responses
**Producer**: All analysis engines  
**Consumer**: Orchestrator (`TaskResponseConsumer`)  
**Message**: `TaskResponseEvent`

**Schema**:
```json
{
  "taskId": 123,
  "analysisId": "uuid",
  "status": "COMPLETED",
  "outputPath": "/path/to/output.json",
  "errorMessage": null,
  "attempts": 1,
  "timestamp": "2026-01-21T18:00:00Z"
}
```

### task-heartbeats
**Producer**: All analysis engines  
**Consumer**: Orchestrator (`HeartbeatConsumer`)  
**Message**: `HeartbeatEvent`

**Schema**:
```json
{
  "eventId": "uuid",
  "taskId": 123,
  "analysisId": "uuid",
  "engineType": "STATIC_ANALYSIS",
  "status": "RUNNING",
  "timestamp": "2026-01-21T18:00:00Z"
}
```

## Database Schema Updates

No schema changes required for Phase 2! All features use existing columns:

- `analysis_tasks.last_heartbeat_at` - Updated by `HeartbeatConsumer`
- `analysis_tasks.attempts` - Tracked by `TaskRetryService`
- `analysis_tasks.completed_at` - Set on task completion
- `analyses.completed_at` - Set when analysis finishes

## Testing

### Unit Tests Created

**Orchestrator Services** (5 test classes):
- `DependencyResolverTest` - 3 scenarios (no deps, ready, not ready)
- `AnalysisCompletionServiceTest` - 4 scenarios (all done, failed, in-progress, already done)
- `TaskRetryServiceTest` - 3 scenarios (within budget, exhausted, first attempt)
- `HeartbeatMonitorTest` - 3 scenarios (stale tasks, no stale, multiple)
- `TaskResponseConsumerTest` - 4 scenarios (success, failure, not found, error)

**Engine Common** (2 test classes):
- `HeartbeatServiceTest` - 6 scenarios (start, stop, send, no task, failure, replace)
- `AbstractAnalysisEngineTest` - 5 scenarios (success, failure, validation, response error, missing fields)

**Static Analysis Engine** (1 test class):
- `StaticAnalysisEngineTest` - 6 scenarios (APK, IPA, invalid type, not found, empty)

**Total**: 30+ test methods covering success, failure, and edge cases

### Test Tools
- **JUnit 5** for test framework
- **Mockito** for mocking dependencies
- **AssertJ** for fluent assertions
- **@TempDir** for file system tests

## Configuration

### Orchestrator `application.yml`

```yaml
app:
  heartbeat:
    check-interval-ms: 60000        # Monitor runs every minute
    stale-threshold-minutes: 2      # Tasks stale after 2 min
  
  kafka:
    topics:
      orchestrator-responses: orchestrator-responses
      task-heartbeats: task-heartbeats
```

### Engine `application.yml`

```yaml
spring:
  kafka:
    consumer:
      group-id: static-analysis-engine-group
      enable-auto-commit: false     # Manual acknowledgment

app:
  kafka:
    topics:
      static-analysis-tasks: static-analysis-tasks
      orchestrator-responses: orchestrator-responses
      task-heartbeats: task-heartbeats
```

## Deployment Considerations

### Orchestrator
- **Scaling**: Single instance recommended (scheduled jobs)
- **Memory**: Minimal overhead (lightweight services)
- **Database**: Transactional updates require connection pooling

### Engines
- **Scaling**: Horizontal scaling supported (Kafka consumer groups)
- **Heartbeat**: Each instance manages its own heartbeat for active task
- **Stateless**: No shared state between engine instances

### Kafka
- **Partitioning**: Use `analysisId` as partition key for ordering
- **Retention**: Standard retention (24h) sufficient
- **Consumer Groups**: One group per engine type

## Future Enhancements

### Phase 3 (Planned)
- **DAG Dependencies**: Support multiple parent dependencies
- **Parallel Execution**: Execute independent tasks concurrently
- **Priority Queues**: High-priority analyses jump queue
- **Dead Letter Queue**: Investigate permanently failed tasks
- **Metrics**: Prometheus metrics for monitoring

### Phase 4 (Planned)
- **Dynamic Scaling**: Auto-scale engines based on queue depth
- **Circuit Breaker**: Pause engines on repeated failures
- **Task Cancellation**: Cancel in-flight tasks
- **Progress Reporting**: Engines report % complete

## Troubleshooting

### Task Stuck in RUNNING
**Symptom**: Task status RUNNING for hours  
**Diagnosis**: Check `last_heartbeat_at` timestamp  
**Solution**: Wait 2 minutes for `HeartbeatMonitor` to mark stale and retry

### Analysis Never Completes
**Symptom**: Analysis status RUNNING but all tasks COMPLETED  
**Diagnosis**: Check `AnalysisCompletionService` logs  
**Solution**: Manually call `checkAndMarkCompletion(analysisId)`

### Duplicate Task Execution
**Symptom**: Same task processed multiple times  
**Diagnosis**: Check `idempotency_key` in logs  
**Solution**: Ensure engines use idempotency key for deduplication

### Missing Heartbeats
**Symptom**: All tasks marked stale immediately  
**Diagnosis**: Check engine `HeartbeatService` scheduler  
**Solution**: Verify `@EnableScheduling` in engine configuration

## Summary

Phase 2 successfully implements:

✅ **Complete bidirectional workflow** - Orchestrator and engines communicate both ways  
✅ **Automatic dependency resolution** - Tasks cascade without manual intervention  
✅ **Robust error handling** - Retry logic and stale task recovery  
✅ **Reusable engine framework** - Easy to add new analysis types  
✅ **Comprehensive testing** - 30+ unit tests covering all scenarios  
✅ **Production-ready** - Transactional processing, manual Kafka commits

**Lines of Code**: ~3,500 production code + ~2,000 test code  
**Components**: 11 new classes (orchestrator) + 6 new classes (engine framework)  
**Test Coverage**: 30+ test methods across 8 test classes
