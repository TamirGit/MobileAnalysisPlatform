# Troubleshooting Guide

## Common Issues and Solutions

### Issue 1: Integration Tests Failing - Docker Not Running

#### Symptoms
```
Could not find a valid Docker environment. Please check configuration.
java.lang.IllegalStateException: Could not find a valid Docker environment
```

#### Cause
Integration tests use Testcontainers which requires Docker to spin up PostgreSQL, Redis, and Kafka containers.

#### Solution A: Start Docker Desktop (Recommended)

**On Windows:**
1. Install Docker Desktop from [https://www.docker.com/products/docker-desktop](https://www.docker.com/products/docker-desktop)
2. Launch Docker Desktop
3. Wait for Docker to fully start (whale icon in system tray should be steady, not animated)
4. Verify Docker is running:
   ```bash
   docker ps
   # Should show empty list or running containers
   ```
5. Run tests again:
   ```bash
   mvn test
   ```

**On Linux:**
```bash
# Start Docker service
sudo systemctl start docker

# Verify
docker ps
```

**On macOS:**
1. Open Docker Desktop application
2. Wait for it to fully start
3. Verify:
   ```bash
   docker ps
   ```

#### Solution B: Skip Integration Tests Temporarily

If you can't run Docker right now, skip integration tests:

```bash
# Skip all tests
mvn clean install -DskipTests

# Or skip only integration tests (run unit tests only)
mvn clean install -DskipITs
```

#### Solution C: Exclude Integration Tests from Surefire

Add to `orchestrator-service/pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <configuration>
                <excludes>
                    <exclude>**/*IntegrationTest.java</exclude>
                </excludes>
            </configuration>
        </plugin>
    </plugins>
</build>
```

Then run:
```bash
mvn test
```

---

### Issue 2: Application Fails to Start - Missing KafkaTemplate Bean

#### Symptoms
```
Field kafkaTemplate in com.mobileanalysis.orchestrator.outbox.OutboxPoller 
required a bean of type 'org.springframework.kafka.core.KafkaTemplate' 
that could not be found.
```

#### Cause
The `OutboxPoller` requires `KafkaTemplate<String, Object>` but the configuration only provided `KafkaTemplate<String, String>`.

#### Solution
This has been **FIXED** in commit [099e052](https://github.com/TamirGit/MobileAnalysisPlatform/commit/099e052cdd105e0ebaf06bb5000e88a38712b767).

The `KafkaConfig` now provides both:
- `stringKafkaTemplate` - for String messages
- `kafkaTemplate` (primary) - for Object/JSON messages

Update your local repository:
```bash
git pull origin feature/foundation/phase1-core-orchestrator
mvn clean install
```

---

### Issue 3: Kafka/Redis/PostgreSQL Not Running

#### Symptoms
```
Connection refused to localhost:9092 (Kafka)
Connection refused to localhost:6379 (Redis)
Connection refused to localhost:5432 (PostgreSQL)
```

#### Solution
Start the infrastructure using Docker Compose:

```bash
# Start all services in background
docker-compose up -d

# Check status
docker-compose ps

# Expected output:
NAME                          STATUS              PORTS
mobile-analysis-kafka         Up (healthy)        0.0.0.0:9092->9092/tcp
mobile-analysis-postgres      Up (healthy)        0.0.0.0:5432->5432/tcp
mobile-analysis-redis         Up (healthy)        0.0.0.0:6379->6379/tcp

# View logs if needed
docker-compose logs -f

# Stop services when done
docker-compose down
```

---

### Issue 4: Port Already in Use

#### Symptoms
```
Bind for 0.0.0.0:5432 failed: port is already allocated
```

#### Solution

**Option A: Stop conflicting service**
```bash
# Find what's using the port (Windows)
netstat -ano | findstr :5432

# Find what's using the port (Linux/Mac)
lsof -i :5432

# Stop PostgreSQL if it's running locally
# Windows: Services -> PostgreSQL -> Stop
# Linux: sudo systemctl stop postgresql
```

**Option B: Change Docker Compose ports**

Edit `docker-compose.yml`:
```yaml
postgres:
  ports:
    - "5433:5432"  # Change 5432 to 5433
```

Then update `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/mobile_analysis
```

---

### Issue 5: Flyway Migration Errors

#### Symptoms
```
FlywayException: Validate failed: Detected resolved migration not applied to database
```

#### Solution

**Clean and re-migrate:**
```bash
# Stop application

# Clean database
mvn -pl orchestrator-service flyway:clean

# Apply migrations
mvn -pl orchestrator-service flyway:migrate

# Or use Docker Compose to recreate
docker-compose down -v  # -v removes volumes
docker-compose up -d
```

---

### Issue 6: Redis Connection Timeout

#### Symptoms
```
Unable to connect to Redis; nested exception is 
io.lettuce.core.RedisConnectionException
```

#### Solution

**Check Redis is running:**
```bash
# Test Redis connection
redis-cli ping
# Expected: PONG

# If not running
docker-compose restart redis
```

**Check Redis configuration:**
```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms
```

---

### Issue 7: Tests Run but Database Is Empty

#### Symptoms
Tests pass but no data appears in database when checking manually.

#### Cause
Tests use Testcontainers which create **isolated** containers. They don't use your docker-compose infrastructure.

#### Solution

To inspect test data:
1. Add `@Disabled` annotation to cleanup in test
2. Add breakpoint after test execution
3. Connect to Testcontainers database (port will be random)

Or check docker-compose database:
```bash
# Connect to docker-compose PostgreSQL
psql -h localhost -U postgres -d mobile_analysis

# Check tables
\dt

# Query data
SELECT * FROM analysis;
SELECT * FROM analysis_task;
```

---

### Issue 8: Application Starts But No Kafka Messages Consumed

#### Symptoms
Application starts successfully but FileEventConsumer doesn't process messages.

#### Solution

**Verify Kafka topic exists:**
```bash
# List topics
docker exec -it mobile-analysis-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Create topic if missing
docker exec -it mobile-analysis-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --create --topic file-events \
  --partitions 3 --replication-factor 1
```

**Send test message:**
```bash
echo '{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-20T19:00:00Z"
}' | docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic file-events
```

**Check consumer group:**
```bash
docker exec -it mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --describe --group orchestrator-consumer-group
```

---

## Quick Validation Checklist

Before running the application:

- [ ] Docker Desktop is running
- [ ] `docker ps` shows healthy containers OR you've run `docker-compose up -d`
- [ ] PostgreSQL is accessible: `psql -h localhost -U postgres -d mobile_analysis`
- [ ] Redis is accessible: `redis-cli ping` returns PONG
- [ ] Kafka is accessible: `docker exec mobile-analysis-kafka kafka-topics --bootstrap-server localhost:9092 --list`
- [ ] Latest code is pulled: `git pull`
- [ ] Clean build: `mvn clean install`
- [ ] Flyway migrations applied: Check application startup logs

---

## Running Without Integration Tests (Quick Development)

For rapid development iterations when Docker isn't available:

```bash
# Build and skip all tests
mvn clean install -DskipTests

# Run application (requires docker-compose infrastructure)
mvn -pl orchestrator-service spring-boot:run
```

---

## Getting Help

If you encounter issues not covered here:

1. Check application logs in `orchestrator-service/target/` directory
2. Enable debug logging in `application.yml`:
   ```yaml
   logging:
     level:
       com.mobileanalysis: DEBUG
       org.springframework.kafka: DEBUG
   ```
3. Check Docker logs: `docker-compose logs -f`
4. Verify environment: `mvn --version`, `java --version`, `docker --version`

---

## Environment Requirements

### Minimum Requirements
- Java 21+
- Maven 3.9+
- Docker Desktop (for tests) OR Docker Engine + Docker Compose
- 8GB RAM (4GB for Docker)
- 10GB disk space

### Verified Working Configurations
- Windows 11 + Docker Desktop 4.x + Java 21
- Ubuntu 22.04 + Docker 24.x + Java 21
- macOS 13+ + Docker Desktop 4.x + Java 21

---

## Related Documentation

- [PHASE1_COMPLETE.md](./PHASE1_COMPLETE.md) - Validation commands
- [README_PHASE1.md](./README_PHASE1.md) - Quick start guide
- [docker-compose.yml](./docker-compose.yml) - Infrastructure setup
- [Testcontainers Docs](https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/)
