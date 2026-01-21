# Mobile Analysis Platform

**A scalable, event-driven security backend for analyzing mobile applications through orchestrated multi-stage workflows.**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Kafka-7.6.0-black.svg)](https://kafka.apache.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

---

## 💡 Overview

Mobile Analysis Platform provides comprehensive security analysis for mobile applications (APK/IPA) through a distributed, event-driven architecture. The platform orchestrates multiple analysis engines in parallel and sequence, ensuring fault tolerance, automatic retry, and complete workflow tracking.

### Key Features
- ✨ **Event-Driven Architecture** - Apache Kafka for asynchronous, scalable processing
- 🔄 **Transactional Outbox Pattern** - At-least-once delivery guarantees
- 💡 **Intelligent Dependency Resolution** - Automatic task orchestration based on dependencies
- 🛡️ **Fault Tolerance** - Automatic retry with exponential backoff
- 📊 **Real-time Tracking** - Complete analysis state visibility via PostgreSQL and Redis
- 🔍 **Comprehensive Logging** - Correlation IDs throughout the system
- 🎯 **Idempotent Operations** - Safe retry semantics for all operations

---

## 🏗️ Architecture

### High-Level Design
```
File Upload → Kafka (file-events) → Orchestrator Service → Task Creation
                                             ↓
                                        Outbox Table
                                             ↓
                                        Outbox Poller
                                             ↓
                            ┌────────────┬────────────┐
                            │   Kafka Topics   │
                            ├────────────┼────────────┤
                            │  Analysis Engines  │
                            └────────────┴────────────┘
                Static | Dynamic | Decompiler | Signature
```

### Core Components
1. **Orchestrator Service** - Central coordination and workflow management
2. **Analysis Engines** - Specialized workers for different analysis types
3. **PostgreSQL** - Source of truth for all state
4. **Redis** - High-speed cache for configuration and state
5. **Kafka** - Event streaming backbone

### Key Patterns
- **Transactional Outbox** - Events written to DB with state changes, then published
- **Manual Kafka Commits** - Only after successful DB transactions
- **DB-First, Redis-Second** - PostgreSQL authoritative, Redis best-effort
- **Correlation IDs** - `analysisId` tracked throughout entire workflow
- **Partition Key Strategy** - All events for same analysis on same partition

---

## 🛠️ Tech Stack

### Backend
- **Java 21** - Modern Java with enhanced features
- **Spring Boot 3.3.0** - Application framework
- **Spring Data JPA** - Database access with Hibernate
- **Spring Kafka 3.2.0** - Kafka integration
- **Spring Data Redis** - Redis caching

### Infrastructure
- **Apache Kafka 7.6.0** - Event streaming (KRaft mode, no ZooKeeper)
- **PostgreSQL 16** - Primary database
- **Redis 7** - Caching layer
- **Docker Compose** - Local development environment

### Build & Testing
- **Maven 3.9+** - Multi-module build system
- **Flyway 10.0** - Database migrations
- **JUnit 5** - Unit testing
- **Testcontainers 1.19.7** - Integration testing
- **AssertJ** - Fluent assertions

---

## 🚀 Quick Start

### Prerequisites
- **Java 21+** (OpenJDK or Oracle JDK)
- **Maven 3.9+** or **Gradle 8+**
- **Docker Desktop** (for local infrastructure)
- **Git**

### 1. Clone Repository
```bash
git clone https://github.com/TamirGit/MobileAnalysisPlatform.git
cd MobileAnalysisPlatform
git checkout feature/foundation/phase1-core-orchestrator
```

### 2. Start Infrastructure
```bash
# Start PostgreSQL, Redis, and Kafka
docker-compose up -d

# Verify all services are healthy
docker-compose ps
```

### 3. Build Project
```bash
# Build all modules
./mvnw clean install
```

### 4. Run Orchestrator Service
```bash
# Start the orchestrator
./mvnw spring-boot:run -pl orchestrator-service

# Verify health (in another terminal)
curl http://localhost:8080/actuator/health
# Expected: {"status":"UP"}
```

### 5. Send Test Event
```bash
# Send a file event to Kafka
echo '{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "filePath": "/storage/incoming/test.apk",
  "fileType": "APK",
  "timestamp": "2026-01-21T15:00:00Z"
}' | docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic file-events

# Verify analysis created (wait 2-3 seconds)
psql -h localhost -U postgres -d mobile_analysis \
  -c "SELECT id, file_path, status FROM analysis;"
```

---

## 📋 Project Structure

```
MobileAnalysisPlatform/
├── .claude/                    # AI assistant commands and workflows
├── common/                     # Shared domain models and events
│   ├── domain/                 # Domain entities (Analysis, Task, Config)
│   ├── events/                 # Event DTOs (FileEvent, TaskEvent)
│   └── config/                 # Shared configs (Kafka, Redis)
├── orchestrator-service/       # Main orchestration service
│   ├── messaging/              # Kafka consumers/producers
│   ├── service/                # Business logic
│   ├── repository/             # JPA repositories
│   ├── outbox/                 # Outbox pattern implementation
│   └── resources/
│       ├── application.yml     # Spring Boot configuration
│       └── db/migration/       # Flyway SQL migrations
├── docker-compose.yml          # Local dev environment
├── pom.xml                     # Root Maven configuration
├── CLAUDE.md                   # Development standards (32KB)
├── PHASE1_STATUS.md            # Current implementation status
├── PHASE1_COMPLETE.md          # Phase 1 completion details
└── TROUBLESHOOTING.md          # Common issues and solutions
```

---

## 📚 Documentation

### For Developers
- **[CLAUDE.md](CLAUDE.md)** - Comprehensive development standards and conventions (32KB)
- **[PHASE1_STATUS.md](PHASE1_STATUS.md)** - Current implementation status and progress
- **[PHASE1_COMPLETE.md](PHASE1_COMPLETE.md)** - Phase 1 completion summary
- **[README_PHASE1.md](README_PHASE1.md)** - Quick start guide for Phase 1
- **[TROUBLESHOOTING.md](TROUBLESHOOTING.md)** - Common setup issues and solutions

### For Architecture
- **[.claude/PRD.md](.claude/PRD.md)** - Product Requirements Document with full architecture

### For Operations
- **[docker-compose.yml](docker-compose.yml)** - Infrastructure configuration
- **[application.yml](orchestrator-service/src/main/resources/application.yml)** - Service configuration

---

## 🧪 Testing

### Run Unit Tests
```bash
mvn test
```

### Run Integration Tests (with Testcontainers)
```bash
mvn verify
```

### Manual End-to-End Test
See [PHASE1_COMPLETE.md](PHASE1_COMPLETE.md#9-manual-end-to-end-test) for detailed workflow validation.

---

## 📈 Development Status

### ✅ Phase 1: Core Orchestrator Foundation (COMPLETE)
**Completion Date:** January 20, 2026

**Implemented:**
- Multi-module Maven project structure
- Domain models and JPA entities
- Database schema with Flyway migrations
- Kafka consumers with manual commit
- Transactional outbox pattern
- Redis caching with fallback
- Integration tests with Testcontainers
- Docker Compose infrastructure

**Post-Completion Refinements (January 21, 2026):**
- Constructor injection standardization
- Docker/Testcontainers Windows/WSL2 compatibility
- Version alignment enforcement
- Enhanced documentation

### ⏳ Phase 2: Task Response Handling (Planned)
**Estimated:** 1-2 days

**Planned Features:**
- TaskResponseConsumer for engine completions
- Dependency resolver for cascading task dispatch
- Analysis completion detection
- Basic retry logic
- First analysis engine implementation

---

## 🤝 Contributing

### Development Workflow
1. Create feature branch: `feature/{scope}/{description}`
2. Follow [CLAUDE.md](CLAUDE.md) conventions
3. Use conventional commits (see [CLAUDE.md](CLAUDE.md#conventional-commits))
4. Ensure all tests pass: `mvn verify`
5. Update documentation as needed
6. Submit pull request

### Code Standards
- **Constructor Injection** - Use `@RequiredArgsConstructor` pattern
- **Manual Kafka Commits** - Only after successful DB transactions
- **Correlation IDs** - Include `analysisId` in all logs
- **Idempotency** - All operations must be safe to retry
- **Version Alignment** - Keep docker-compose and Testcontainers versions synchronized

See [CLAUDE.md](CLAUDE.md) for complete standards and patterns.

---

## 🔗 Useful Commands

### Infrastructure
```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop all services
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

### Database
```bash
# Connect to PostgreSQL
psql -h localhost -U postgres -d mobile_analysis

# View analysis records
SELECT * FROM analysis;

# View task records
SELECT * FROM analysis_task;

# View outbox events
SELECT * FROM outbox WHERE processed = false;
```

### Kafka
```bash
# List topics
docker exec mobile-analysis-kafka kafka-topics \
  --bootstrap-server localhost:9092 --list

# Consume file-events
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic file-events \
  --from-beginning

# Produce test event
docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic file-events
```

### Redis
```bash
# Test connection
redis-cli ping

# View cached config
redis-cli GET "analysis-config:APK"
```

---

## 📝 License

*License information to be added*

---

## 📧 Contact

**Repository:** [https://github.com/TamirGit/MobileAnalysisPlatform](https://github.com/TamirGit/MobileAnalysisPlatform)  
**Branch:** feature/foundation/phase1-core-orchestrator  
**Maintainer:** Tamir S

---

**Phase 1 Complete! 🎉 Ready for Phase 2!**
