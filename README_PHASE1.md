# Mobile Analysis Platform - Phase 1 Quick Start

## 🚀 Fast Track Setup (5 minutes)

### Prerequisites
- Java 21 JDK
- Maven 3.9+
- Docker & Docker Compose
- Git

### 1. Clone & Checkout
```bash
git clone https://github.com/TamirGit/MobileAnalysisPlatform.git
cd MobileAnalysisPlatform
git checkout feature/foundation/phase1-core-orchestrator
```

### 2. Start Infrastructure
```bash
# Start PostgreSQL, Redis, Kafka
docker-compose up -d

# Wait ~30 seconds for Kafka to create topics
# Verify all healthy:
docker-compose ps
```

### 3. Build Project
```bash
./mvnw clean install -DskipTests
```

### 4. Check What's Built
```bash
# See implemented vs. remaining
cat PHASE1_STATUS.md
```

---

## 💻 What's Working Now

✅ **Database Schema**: Flyway migrations with sample APK/IPA configs  
✅ **Domain Models**: All enums and entities  
✅ **JPA Entities**: Analysis, Task, Config, Outbox  
✅ **Docker Compose**: PostgreSQL + Redis + Kafka with KRaft  
✅ **Spring Boot Config**: application.yml with all settings  

---

## 🛠️ What Needs Completion (2-3 hours)

### Remaining Files to Create:

1. **Repositories** (10 min)
   - `AnalysisTaskRepository.java`
   - `AnalysisConfigRepository.java`
   - `TaskConfigRepository.java`
   - `OutboxRepository.java`

2. **Shared Config** (15 min)
   - `common/config/KafkaConfig.java` - Manual commit setup
   - `common/config/RedisConfig.java` - JSON serialization

3. **Core Services** (45 min)
   - `ConfigurationService.java` - Redis cache + DB fallback
   - `AnalysisOrchestrator.java` - Main business logic

4. **Messaging** (30 min)
   - `FileEventConsumer.java` - Kafka listener
   - `OutboxPoller.java` - @Scheduled outbox processor

5. **Tests** (30 min)
   - Integration test with Testcontainers

---

## 🧪 Manual Testing

Once services are implemented:

### 1. Run Orchestrator
```bash
./mvnw spring-boot:run -pl orchestrator-service
```

### 2. Send FileEvent
```bash
# In another terminal
docker exec -it mobile-analysis-kafka kafka-console-producer \
  --bootstrap-server localhost:9092 \
  --topic file-events

# Paste this JSON and press Enter:
{"eventId":"550e8400-e29b-41d4-a716-446655440000","filePath":"/storage/incoming/test.apk","fileType":"APK","timestamp":"2026-01-20T14:00:00Z"}

# Press Ctrl+D to exit
```

### 3. Verify in Database
```bash
# Connect to PostgreSQL
psql -h localhost -U postgres -d mobile_analysis

# Check analysis created
SELECT id, file_type, status, started_at FROM analysis;

# Check tasks created (should be 4 for APK)
SELECT id, analysis_id, engine_type, status, depends_on_task_id FROM analysis_task;

# Check outbox (should have 2 events for tasks without dependencies)
SELECT id, event_type, topic, processed FROM outbox;

# Exit
\q
```

### 4. Verify in Kafka
```bash
# Check static-analysis-tasks topic
docker exec -it mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic static-analysis-tasks \
  --from-beginning \
  --max-messages 1
```

---

## 🐛 Troubleshooting

### Kafka Not Starting
```bash
# Check logs
docker logs mobile-analysis-kafka

# If storage format error, remove volumes and restart
docker-compose down -v
docker-compose up -d
```

### Database Connection Failed
```bash
# Check PostgreSQL
docker logs mobile-analysis-postgres

# Test connection
psql -h localhost -U postgres -d mobile_analysis -c "SELECT 1;"
```

### Build Errors
```bash
# Clean and rebuild
./mvnw clean install -U

# Check Java version
java -version  # Should be 21
```

---

## 📚 Architecture at a Glance

```
[File Event] → Kafka (file-events)
     ↓
[FileEventConsumer] → Manual Commit
     ↓
[AnalysisOrchestrator.createAnalysis()]
     ↓
[Database Transaction]
  - Create Analysis (RUNNING)
  - Create All Tasks (PENDING)
  - Write Ready Tasks to Outbox
     ↓
[@Scheduled OutboxPoller] (every 1 second)
  - Read unprocessed (batch 50)
  - Publish to engine topics
  - Mark processed
     ↓
[Engine Topics] (static-analysis-tasks, etc.)
```

---

## 📄 Key Files Reference

| Purpose | Location |
|---------|----------|
| **PRD** | `.claude/PRD.md` |
| **Dev Standards** | `CLAUDE.md` |
| **Database Schema** | `orchestrator-service/src/main/resources/db/migration/` |
| **Domain Models** | `common/src/main/java/.../domain/` |
| **Event DTOs** | `common/src/main/java/.../events/` |
| **Configuration** | `orchestrator-service/src/main/resources/application.yml` |
| **Docker Compose** | `docker-compose.yml` |
| **Status** | `PHASE1_STATUS.md` |

---

## ✅ Phase 1 Complete Checklist

- [ ] All repositories created
- [ ] Shared configs (Kafka, Redis) implemented
- [ ] ConfigurationService with Redis caching
- [ ] AnalysisOrchestrator with createAnalysis
- [ ] FileEventConsumer with manual commit
- [ ] OutboxPoller with @Scheduled
- [ ] Integration test passing
- [ ] Manual test: FileEvent → Analysis + Tasks + Outbox
- [ ] Logs show correlation IDs (analysisId)
- [ ] Ready to merge to main

---

## 👨‍💻 Next Phase Preview

**Phase 2** will add:
- Engine framework (`engine-common` module)
- Abstract engine base class
- First concrete engine (static-analysis-engine)
- Heartbeat mechanism
- Task execution with timeout

**Estimated**: 1-2 days after Phase 1 complete

---

**Created**: January 20, 2026  
**Branch**: `feature/foundation/phase1-core-orchestrator`  
**Status**: Foundation complete, services in progress