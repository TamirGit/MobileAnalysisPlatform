Run comprehensive validation of the Spring Boot project.

Execute the following commands in sequence and report results:

## 1. Static Analysis / Linting

```bash
# If using Maven:
mvn -pl backend clean verify

# Optional explicit static analysis (if configured):
mvn -pl backend checkstyle:check spotbugs:check pmd:check
```

**Expected:** No Checkstyle/SpotBugs/PMD violations causing build failure. [github](https://github.com/openhab/static-code-analysis)

## 2. Type / Bytecode Checks

Java compilation and bytecode checks run as part of the Maven build. [petrikainulainen](https://www.petrikainulainen.net/programming/testing/writing-integration-tests-for-spring-boot-web-applications-build-setup/)

```bash
mvn -pl backend clean compile
```

**Expected:** No compilation errors or warnings that fail the build. [petrikainulainen](https://www.petrikainulainen.net/programming/testing/writing-integration-tests-for-spring-boot-web-applications-build-setup/)

## 3. Unit Tests

```bash
# Run unit tests
mvn -pl backend test
```

If integration tests are separated into a profile (for example, `integration-tests`): [docs.spring](https://docs.spring.io/spring-boot/maven-plugin/integration-tests.html)

```bash
# Optional integration tests
mvn -pl backend verify -P integration-tests
```

**Expected:** All tests pass with no failures or errors. [stackoverflow](https://stackoverflow.com/questions/31681855/maven-not-running-spring-boot-tests)

## 4. Test Coverage

Assuming JaCoCo is configured via the `jacoco-maven-plugin` in `pom.xml`: [baeldung](https://www.baeldung.com/jacoco)

```bash
# Generate coverage report
mvn -pl backend clean test jacoco:report
```

If coverage rules are enforced (minimum threshold), run: [youtube](https://www.youtube.com/watch?v=Sa9236XiCtQ)

```bash
mvn -pl backend verify
```

**Expected:** Coverage meets the configured JaCoCo thresholds and the build does not fail on coverage rules. [docs.sonarsource](https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/test-coverage/java-test-coverage)

## 5. Build Artifact

```bash
# Package Spring Boot application (jar/war)
mvn -pl backend clean package
```

If using the Spring Boot Maven plugin: [docs.spring](https://docs.spring.io/spring-boot/maven-plugin/run.html)

```bash
mvn -pl backend clean package spring-boot:repackage
```

**Expected:** Build completes successfully and produces the application artifact in `backend/target/`. [docs.spring](https://docs.spring.io/spring-boot/maven-plugin/run.html)

## 6. Summary Report

After all validations complete, provide a summary report with:

- Linting / static analysis status (Checkstyle/SpotBugs/PMD)
- Compilation / type checking status
- Unit test status (and integration tests if run)
- Coverage percentage and whether thresholds were met
- Build/package status
- Any errors or warnings encountered
- Overall health assessment (**PASS**/**FAIL**)

Format the report clearly with sections and status indicators, for example:

- Linting: PASS/FAIL (with brief notes)
- Compilation: PASS/FAIL
- Tests: PASS/FAIL (include failing suite names if any)
- Coverage: XX% (PASS/FAIL vs threshold)
- Build: PASS/FAIL
- Overall: PASS/FAIL with one-sentence justification.
