# Kafka Consumer Groups Guide

## Overview

This document explains how Kafka consumer groups are configured in the Mobile Analysis Platform and how to ensure messages are never missed.

## The Problem: Missing Messages

### Without Pre-Initialization

1. **Kafka starts** - Topics are created, but no consumer groups exist
2. **Message sent** - A file event is published to `file-events` topic at offset 0
3. **Orchestrator starts** - Creates consumer group `orchestrator-service`
4. **Consumer group has no history** - Uses `auto-offset-reset: earliest`
5. **But...** the consumer group was created AFTER Kafka started, so Kafka sets initial offset to current position
6. **Result**: Message at offset 0 is skipped!

### With Pre-Initialization (Current Solution)

1. **Kafka starts** - Topics are created
2. **kafka-init service runs** - Creates consumer groups with offset 0 committed
3. **Message sent** - File event is published at offset 0
4. **Orchestrator starts** - Joins existing consumer group, reads from committed offset 0
5. **Result**: Message is processed! ✅

## How It Works

### docker-compose.yml Configuration

```yaml
kafka-init:
  image: confluentinc/cp-kafka:7.6.0
  depends_on:
    kafka:
      condition: service_healthy
  command: |
    bash -c '
    # Create topics
    kafka-topics --bootstrap-server kafka:29092 --create --if-not-exists --topic file-events ...
    
    # Initialize consumer groups
    timeout 5 kafka-console-consumer \
      --bootstrap-server kafka:29092 \
      --topic file-events \
      --group orchestrator-service \
      --from-beginning \
      --max-messages 0 || true
    '
```

**Key Points:**
- Runs after Kafka is healthy
- Uses `kafka-console-consumer` with `--from-beginning` to create group
- `--max-messages 0` means "consume nothing, just initialize"
- `timeout 5` prevents hanging if no messages exist
- `|| true` ensures script continues even if timeout occurs

### Consumer Groups Initialized

| Consumer Group | Topic | Purpose |
|----------------|-------|----------|
| `orchestrator-service` | `file-events` | Orchestrator consumes file upload events |
| `orchestrator-service` | `orchestrator-responses` | Orchestrator consumes task responses |
| `static-analysis-engine-group` | `static-analysis-tasks` | Static analysis engine consumes tasks |
| `dynamic-analysis-engine-group` | `dynamic-analysis-tasks` | Dynamic analysis engine (future) |
| `decompiler-engine-group` | `decompiler-tasks` | Decompiler engine (future) |
| `signature-check-engine-group` | `signature-check-tasks` | Signature check engine (future) |

## Testing the Solution

### Test Scenario: Message Before Service Start

```bash
# 1. Start infrastructure (Kafka, Postgres, Redis)
docker-compose up -d

# Wait for kafka-init to complete
docker logs mobile-analysis-kafka-init
# Should see: "Consumer groups initialized successfully."

# 2. Send a test message BEFORE starting any services
echo '{"eventId":"550e8400-e29b-41d4-a716-446655440000","filePath":"/storage/incoming/test.apk","fileType":"APK","timestamp":"2026-01-22T10:00:00Z"}' | \
  docker exec -i mobile-analysis-kafka kafka-console-producer \
  --broker-list localhost:9092 \
  --topic file-events

# 3. Verify the message is in the topic
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic file-events \
  --from-beginning \
  --max-messages 1

# 4. Check consumer group status (should show LAG = 1)
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe

# Expected output:
# GROUP                TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# orchestrator-service file-events     0          0               1               1

# 5. Now start the orchestrator service
./mvnw spring-boot:run -pl orchestrator-service

# 6. Watch the logs - the message should be processed!
# Expected log:
# "Received file event: eventId=550e8400-e29b-41d4-a716-446655440000..."

# 7. Verify LAG is now 0 (message consumed)
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe

# Expected output:
# GROUP                TOPIC           PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# orchestrator-service file-events     0          1               1               0
```

## Useful Commands

### Check Consumer Group Status

```bash
# List all consumer groups
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --list

# Describe specific consumer group
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe

# Check all engine consumer groups
for group in orchestrator-service static-analysis-engine-group dynamic-analysis-engine-group; do
  echo "\n=== $group ==="
  docker exec mobile-analysis-kafka kafka-consumer-groups \
    --bootstrap-server localhost:9092 \
    --group $group \
    --describe
done
```

### Reset Consumer Group (For Testing)

```bash
# Reset orchestrator consumer group to beginning
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --reset-offsets \
  --to-earliest \
  --all-topics \
  --execute

# Reset specific engine consumer group
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group static-analysis-engine-group \
  --reset-offsets \
  --to-earliest \
  --all-topics \
  --execute
```

### View Messages in Topics

```bash
# View all messages in file-events topic
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic file-events \
  --from-beginning

# View last 10 messages
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic file-events \
  --from-beginning \
  --max-messages 10

# View messages with keys and timestamps
docker exec mobile-analysis-kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic file-events \
  --from-beginning \
  --property print.key=true \
  --property print.timestamp=true
```

### Check Topic Details

```bash
# List all topics
docker exec mobile-analysis-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --list

# Describe specific topic
docker exec mobile-analysis-kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic file-events
```

## Troubleshooting

### Consumer Group Not Created

**Symptom**: `kafka-consumer-groups --list` doesn't show your group

**Solution**:
```bash
# Check kafka-init logs
docker logs mobile-analysis-kafka-init

# Manually re-run initialization
docker-compose restart kafka-init
```

### Messages Still Being Missed

**Symptom**: Service starts but doesn't process old messages

**Diagnosis**:
```bash
# Check current offset vs log end offset
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe

# If CURRENT-OFFSET > 0, the group was created after messages were sent
```

**Solution**:
```bash
# Reset offsets to beginning
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --reset-offsets \
  --to-earliest \
  --all-topics \
  --execute

# Restart service to pick up reset offsets
```

### Consumer Group Offset Stuck

**Symptom**: LAG keeps growing, service isn't consuming

**Diagnosis**:
```bash
# Check if consumer is active
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe \
  --members

# Check service logs for errors
./mvnw spring-boot:run -pl orchestrator-service
```

## Production Considerations

### Do Not Reset Offsets in Production

The reset commands above are for **development/testing only**. In production:
- Consumer groups track processing progress
- Resetting offsets causes reprocessing of all messages
- Can lead to duplicate analysis runs

### Monitor Consumer Lag

Set up monitoring for consumer group lag:
```bash
# Export lag metrics
docker exec mobile-analysis-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group orchestrator-service \
  --describe | grep -v "PARTITION" | awk '{sum+=$6} END {print sum}'
```

High lag indicates:
- Service is down
- Service is processing slower than message rate
- Service has errors preventing message processing

## References

- [Kafka Consumer Groups Documentation](https://kafka.apache.org/documentation/#consumerconfigs)
- [auto.offset.reset Configuration](https://kafka.apache.org/documentation/#consumerconfigs_auto.offset.reset)
- [Phase 2 Status](../PHASE1_STATUS.md)
