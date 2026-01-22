# Test Failure Fix: Jackson JSR310 Module

**Date**: January 22, 2026  
**Branch**: `feature/phase2/task-response-handling`  
**Issue**: Test failures in TaskRetryServiceTest and DependencyResolverTest

## Problem Summary

4 out of 4 tests failing in two test classes with Jackson serialization errors:
- **TaskRetryServiceTest**: 3 errors + 1 unnecessary stubbing warning
- **DependencyResolverTest**: 1 error

## Root Cause

### Issue 1: Jackson Can't Serialize java.time.Instant

**Error Message**:
```
com.fasterxml.jackson.databind.exc.InvalidDefinitionException: 
Java 8 date/time type `java.time.Instant` not supported by default: 
add Module "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" 
to enable handling (through reference chain: 
com.mobileanalysis.common.events.TaskEvent["timestamp"])
```

**Root Cause**:
The tests use `@Spy` on a raw `ObjectMapper()`, which doesn't have the JSR310 module registered. When tests try to serialize `TaskEvent` objects containing `Instant` timestamps, Jackson fails because it doesn't know how to handle Java 8 date/time types by default.

**Why Production Works But Tests Fail**:
- Production: Spring Boot auto-configuration registers `JavaTimeModule` automatically
- Tests: Using `@Spy private ObjectMapper objectMapper = new ObjectMapper()` bypasses Spring's auto-configuration

### Issue 2: Unnecessary Stubbing

**Error Message**:
```
org.mockito.exceptions.misusing.UnnecessaryStubbingException:
Unnecessary stubbings detected.
Following stubbings are unnecessary:
  1. -> at TaskRetryServiceTest.retryIfPossible_budgetExhausted_doesNotRetry
       (line 96)
```

**Root Cause**:
The test `retryIfPossible_budgetExhausted_doesNotRetry` was stubbing `analysisTaskRepository.findById()` but never using it, because when retry budget is exhausted, the method returns early without looking up the task.

---

## The Fix

### Fix 1: Register JSR310 Module in Tests

**Files Modified**:
- `TaskRetryServiceTest.java`
- `DependencyResolverTest.java`

**Changes Applied**:

```java
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@BeforeEach
void setUp() {
    // Register JSR310 module for Java 8 date/time support
    objectMapper.registerModule(new JavaTimeModule());
    
    // ... rest of setup
}
```

**Why This Works**:
- `JavaTimeModule` tells Jackson how to serialize/deserialize Java 8 date/time types
- Must be registered on each `ObjectMapper` instance
- In production, Spring Boot does this automatically
- In tests, we must do it manually when using `@Spy`

### Fix 2: Remove Unnecessary Stubbing

**File**: `TaskRetryServiceTest.java`

**Before**:
```java
@Test
void retryIfPossible_budgetExhausted_doesNotRetry() {
    // Given
    failedTask.setAttempts(3); // Already at max
    when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(failedTask)); // UNUSED!
    when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));

    // When
    taskRetryService.retryIfPossible(failedTask, "Test failure");

    // Then
    verify(outboxRepository, never()).save(any());
    verify(analysisTaskRepository, never()).save(any());
}
```

**After**:
```java
@Test
void retryIfPossible_budgetExhausted_doesNotRetry() {
    // Given
    failedTask.setAttempts(3); // Already at max
    // Removed: when(analysisTaskRepository.findById(1L))...
    when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));

    // When
    taskRetryService.retryIfPossible(failedTask, "Test failure");

    // Then
    verify(outboxRepository, never()).save(any());
    verify(analysisTaskRepository, never()).save(any());
}
```

**Why**: The method signature is `retryIfPossible(AnalysisTaskEntity task, String reason)`, so the task is already passed in - no need to stub `findById()`.

### Fix 3: Add Missing `attempts` Field

**File**: `DependencyResolverTest.java`

**Added**:
```java
dependentTask.setAttempts(1);  // Required for TaskEvent serialization
```

**Why**: `TaskEvent` now has a required `attempts` field (added in Issue #3 fix). Tests must set this to avoid NPE during event creation.

---

## Validation

### Before Fix
```bash
mvn test -Dtest=TaskRetryServiceTest
# Tests run: 3, Failures: 0, Errors: 3, Skipped: 0

mvn test -Dtest=DependencyResolverTest
# Tests run: 3, Failures: 0, Errors: 1, Skipped: 0
```

### After Fix
```bash
mvn test -Dtest=TaskRetryServiceTest
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 ✅

mvn test -Dtest=DependencyResolverTest
# Tests run: 3, Failures: 0, Errors: 0, Skipped: 0 ✅
```

---

## Summary

| Issue | Tests Affected | Fix | Status |
|-------|---------------|-----|--------|
| Jackson JSR310 missing | 4 tests | Register `JavaTimeModule` in `setUp()` | ✅ Fixed |
| Unnecessary stubbing | 1 test | Remove unused mock | ✅ Fixed |
| Missing `attempts` field | 1 test | Set `attempts=1` in test data | ✅ Fixed |

**All 6 tests now passing!**

---

## Lessons Learned

1. **Test ObjectMapper Configuration**: When using `@Spy` on `ObjectMapper`, manually register modules (JSR310, etc.)
2. **Mockito Strictness**: Unnecessary stubbings are caught by default - keep tests clean
3. **Field Requirements**: When DTOs gain new required fields, update all test data
4. **Spring Auto-Configuration**: Be aware of what Spring Boot does automatically that must be manual in unit tests

---

## Alternative Approaches

If this becomes a recurring pattern, consider:

### Option 1: Test Configuration Class
```java
@TestConfiguration
public class TestObjectMapperConfig {
    @Bean
    public ObjectMapper testObjectMapper() {
        return new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }
}
```

### Option 2: Shared Test Base Class
```java
public abstract class ServiceTestBase {
    protected ObjectMapper objectMapper;
    
    @BeforeEach
    void baseSetUp() {
        objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    }
}
```

### Option 3: Use Real ObjectMapper from Spring Context
```java
@SpringBootTest
class DependencyResolverTest {
    @Autowired
    private ObjectMapper objectMapper;  // Already configured
    
    // ... tests
}
```

**Current Approach**: Manual registration in each test (minimal overhead, explicit dependencies)

---

*Fixes completed: January 22, 2026*  
*Branch: feature/phase2/task-response-handling*  
*Commit: f18e49b2c18131af91566ef71a7afa60e8f9c0c9*
