# Phase 2: Bug Fixes Summary

## Critical Fixes

### 1. Kafka Malformed Message Infinite Loop
**Commit**: [1f4d9f1](https://github.com/TamirGit/MobileAnalysisPlatform/commit/1f4d9f1f20481a4d9d24ad7af685901579992e4b)  
**Component**: `common/config/KafkaConfig.java`

**Issue**: Malformed JSON causes infinite `DeserializationException` retry loop, blocking all message processing.

**Fix**: 
- Added `ErrorHandlingDeserializer` wrapper around `JsonDeserializer`
- Added `DefaultErrorHandler` with zero retries
- Logs error and commits offset (skips bad message)
- Consumer continues processing next messages

**Test**:
```bash
echo '{"bad":"json"}' | docker exec -i kafka kafka-console-producer \
  --broker-list localhost:9092 --topic file-events
# Should log error but continue processing
```

---

### 2. @Transactional Self-Invocation
**Commits**: [4b14472](https://github.com/TamirGit/MobileAnalysisPlatform/commit/4b14472f4b48846c70e153fbb5f2945b2eb8c819), [d4343e0](https://github.com/TamirGit/MobileAnalysisPlatform/commit/d4343e083f3c862a6a5b32588c7297993483c828)  
**Components**: `HeartbeatConsumer.java`, `TaskResponseConsumer.java`

**Issue**: Spring AOP doesn't intercept self-invocation. `@Transactional` on internal methods never executes, causing non-atomic DB + Kafka operations.

**Fix**:
- Moved `@Transactional` to entry point methods
- Inlined logic to avoid self-invocation
- Kafka offset committed only after successful DB transaction

---

### 3. Missing Task Cancellation
**Commit**: [e02828a](https://github.com/TamirGit/MobileAnalysisPlatform/commit/e02828a02e011229b4d831d49a8f8f86f7a5b144)  
**Component**: `AnalysisCompletionService.java`

**Issue**: When task fails, sibling tasks remain PENDING/DISPATCHED forever (zombie tasks).

**Fix**: 
- `markAsFailed()` now cancels all non-terminal tasks
- Sets status to FAILED with error: "Cancelled due to sibling task failure"
- Fail-fast behavior prevents wasted resources

---

## Important Fixes

### 4. Null started_at Timestamps
**Commits**: [c72b8a6](https://github.com/TamirGit/MobileAnalysisPlatform/commit/c72b8a604f3c4c2c08edebea3bd92b4cd4993df3), [b81de1c](https://github.com/TamirGit/MobileAnalysisPlatform/commit/b81de1cfdaa364d45f99beb40ab8cfb878a76e5f)

**Issue**: `analysis_task.started_at` always null.

**Fix**: Set `started_at = Instant.now()` in `AnalysisOrchestrator` and `DependencyResolver` when dispatching tasks.

---

### 5. Immediate Heartbeat Missing
**Commit**: [c587a87](https://github.com/TamirGit/MobileAnalysisPlatform/commit/c587a875e2901d60f0d30ce78033119026d9e038)

**Issue**: First heartbeat sent after 30s delay, leaving `last_heartbeat_at` null for fast tasks.

**Fix**: 
- Send immediate heartbeat in `startHeartbeat()`
- Schedule: t=0s (immediate), 30s, 60s, 90s...
- Ensures all processed tasks have heartbeat timestamp

---

### 6. Consumer Groups Miss Messages
**Commits**: [78bcf7e](https://github.com/TamirGit/MobileAnalysisPlatform/commit/78bcf7e87984dfe0ae4be35d95ba81c340dea6a1), [98ed578](https://github.com/TamirGit/MobileAnalysisPlatform/commit/98ed5784c581a3f9ed9654783eabebc7dcf910bf)

**Issue**: Messages sent before services start are never processed.

**Fix**: 
- Added `kafka-init` service to docker-compose
- Pre-creates consumer groups with `--reset-offsets --to-earliest`
- No lost messages during startup or rolling deployments

---

### 7. Wrong Container Factory
**Commits**: [4f96bf6](https://github.com/TamirGit/MobileAnalysisPlatform/commit/4f96bf67fa13bafa0d041e589480864ca36e875a), [b48338](https://github.com/TamirGit/MobileAnalysisPlatform/commit/b48338102b8e8a4c57c627577e37a3c18056709a)

**Issue**: "Cannot convert FileEvent to TaskResponseEvent" errors.

**Fix**: 
- Created `taskResponseKafkaListenerContainerFactory` in KafkaConfig
- Updated `TaskResponseConsumer` to use correct factory

---

## Impact Summary

| Fix | Severity | Impact |
|-----|----------|--------|
| Malformed message handling | Critical | Prevents service outage |
| Transaction self-invocation | Critical | Data consistency |
| Task cancellation | Critical | Resource efficiency |
| started_at timestamps | Medium | Monitoring |
| Immediate heartbeat | Medium | Tracking accuracy |
| Consumer group init | Medium | No lost messages |
| Container factory | Medium | Correct deserialization |

**Total**: 7 fixes (3 critical, 4 important)  
**All verified and tested**
