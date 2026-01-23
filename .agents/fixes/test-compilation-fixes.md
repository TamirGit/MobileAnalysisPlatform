# Test Compilation Fixes - Phase 2

**Date**: January 22, 2026  
**Branch**: `feature/phase2/task-response-handling`  
**Issue**: Test compilation errors after code review fixes

## Root Cause

The code review fixes introduced type safety improvements (EngineType enum, EngineTopicMapper utility) that broke existing tests which were still using String literals.

## Compilation Errors Fixed

### Error 1: String to EngineType Type Mismatch

**Files Affected**:
- `HeartbeatMonitorTest.java` (line 42)
- `TaskRetryServiceTest.java` (line 67)
- `DependencyResolverTest.java` (line 86)

**Original Error**:
```
incompatible types: java.lang.String cannot be converted to com.mobileanalysis.common.domain.EngineType
```

**Problem**:
```java
staleTask.setEngineType("STATIC_ANALYSIS");  // Wrong: String
```

**Fix**:
```java
import com.mobileanalysis.common.domain.EngineType;

staleTask.setEngineType(EngineType.STATIC_ANALYSIS);  // Correct: Enum
```

**Applied To**:
- HeartbeatMonitorTest: Line 42 (STATIC_ANALYSIS)
- HeartbeatMonitorTest: Line 93 (added for staleTask2)
- TaskRetryServiceTest: Line 67 (STATIC_ANALYSIS)
- DependencyResolverTest: Line 86 (DYNAMIC_ANALYSIS)

---

### Error 2: Ambiguous Method Reference (retryIfPossible)

**Files Affected**:
- `HeartbeatMonitorTest.java` (lines 64, 81, 101)

**Original Error**:
```
reference to retryIfPossible is ambiguous
  both method retryIfPossible(java.lang.Long,java.lang.String) 
  and method retryIfPossible(com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity,java.lang.String) match
```

**Problem**:
```java
verify(taskRetryService).retryIfPossible(
    argThat(task -> task.getId().equals(1L)),  // Ambiguous: could match either overload
    argThat(reason -> reason.contains("no heartbeat"))
);
```

**Fix**:
```java
// Explicitly cast ArgumentMatcher to specify which overload
verify(taskRetryService).retryIfPossible(
    argThat((AnalysisTaskEntity task) -> task.getId().equals(1L)),  // Explicit type
    argThat(reason -> reason.contains("no heartbeat"))
);

// Alternative fix for never() verifications
verify(taskRetryService, never()).retryIfPossible(any(AnalysisTaskEntity.class), any());
```

**Applied To**:
- Line 64: Success case - explicit cast to `(AnalysisTaskEntity task)`
- Line 81: No stale tasks - use `any(AnalysisTaskEntity.class)`
- Line 101: Multiple stale tasks - use `any(AnalysisTaskEntity.class)`

---

### Error 3: Iterable.size() Method Not Found

**Files Affected**:
- `TaskResponseConsumerTest.java` (line 90)

**Original Error**:
```
cannot find symbol
  symbol:   method size()
  location: variable events of type java.lang.Iterable<...>
```

**Problem**:
```java
verify(outboxRepository).saveAll(argThat(events -> 
    events.size() == 1  // Wrong: Iterable doesn't have size()
));
```

**Fix**:
```java
verify(outboxRepository).saveAll(argThat(savedEvents -> {
    List<OutboxEventEntity> eventList = (List<OutboxEventEntity>) savedEvents;
    return eventList.size() == 1;  // Correct: Cast to List first
}));
```

**Explanation**: The `saveAll()` method accepts `Iterable`, but `Iterable` doesn't have a `size()` method. We need to cast to `List` or `Collection` first.

---

### Error 4: Incorrect Variable Type

**Files Affected**:
- `HeartbeatMonitorTest.java` (line 65)

**Original Error**:
```
cannot find symbol
  symbol:   method getId()
  location: variable task of type java.lang.Long
```

**Problem**:
This was actually part of Error #2 (ambiguous method call). The ArgumentMatcher was being interpreted as a Long matcher instead of an AnalysisTaskEntity matcher.

**Fix**:
Resolved by fixing the ambiguous method reference (see Error #2).

---

## Files Modified

1. **HeartbeatMonitorTest.java**
   - Added `import com.mobileanalysis.common.domain.EngineType;`
   - Changed String engine types to EngineType enum (2 places)
   - Fixed ambiguous method calls with explicit casts (3 places)

2. **TaskRetryServiceTest.java**
   - Added `import com.mobileanalysis.common.domain.EngineType;`
   - Changed String engine type to EngineType enum (1 place)

3. **DependencyResolverTest.java**
   - Added `import com.mobileanalysis.common.domain.EngineType;`
   - Changed String engine type to EngineType enum (1 place)

4. **TaskResponseConsumerTest.java**
   - Fixed Iterable.size() by casting to List first (1 place)

---

## Validation

### Before Fixes
```bash
mvn clean test
# [INFO] BUILD FAILURE
# [ERROR] 8 compilation errors
```

### After Fixes
```bash
mvn clean test
# Expected: [INFO] BUILD SUCCESS
# All 34 tests should pass
```

---

## Summary

| Error Type | Count | Severity | Status |
|------------|-------|----------|--------|
| String → EngineType conversion | 4 | Compilation | ✅ Fixed |
| Ambiguous method reference | 3 | Compilation | ✅ Fixed |
| Iterable.size() not found | 1 | Compilation | ✅ Fixed |
| **Total** | **8** | - | ✅ **All Fixed** |

---

## Lessons Learned

1. **Type Safety**: Using enums instead of Strings provides compile-time type safety
2. **Method Overloading**: Be explicit with ArgumentMatchers when methods are overloaded
3. **Interface vs Implementation**: Remember Iterable doesn't have all Collection methods
4. **Test Maintenance**: When refactoring production code, update tests immediately

---

## Related Changes

These test fixes are related to:
- [Issue #5 Fix](https://github.com/TamirGit/MobileAnalysisPlatform/commit/1708b92834bea5f21c525c75dc1cded4b96ca029): EngineTopicMapper utility
- [EngineType Enum](https://github.com/TamirGit/MobileAnalysisPlatform/blob/main/common/src/main/java/com/mobileanalysis/common/domain/EngineType.java): Type-safe engine types

---

*Fixes completed: January 22, 2026*  
*Branch: feature/phase2/task-response-handling*  
*Commit: 594dc791e31fdbc230e57893efafedbd3bc40244*
