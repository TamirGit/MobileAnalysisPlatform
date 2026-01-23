# Dead Letter Queue (DLQ) Operations Guide

## Overview

The Mobile Analysis Platform uses Dead Letter Queues (DLQ) to capture messages that cannot be processed successfully. This prevents data loss and enables investigation and recovery of failed messages.

## What is the DLQ?

A Dead Letter Queue is a special Kafka topic that receives messages that:
- **Cannot be deserialized** (malformed JSON, wrong schema)
- **Fail validation** (missing required fields)
- **Cause processing errors** that shouldn't be retried

### DLQ Topics

Each main topic has a corresponding DLQ topic with `.DLQ` suffix:

| Original Topic | DLQ Topic | Purpose |
|---|---|---|
| `file-events` | `file-events.DLQ` | Failed file event messages |
| `static-analysis-tasks` | `static-analysis-tasks.DLQ` | Failed static analysis tasks |
| `dynamic-analysis-tasks` | `dynamic-analysis-tasks.DLQ` | Failed dynamic analysis tasks |
| `decompiler-tasks` | `decompiler-tasks.DLQ` | Failed decompiler tasks |
| `signature-check-tasks` | `signature-check-tasks.DLQ` | Failed signature check tasks |
| `orchestrator-responses` | `orchestrator-responses.DLQ` | Failed response events |

### DLQ Configuration

- **Partitions**: 1 (single partition for simplicity)
- **Retention**: 30 days (long retention for investigation)
- **Replication**: 1 (development), 3+ (production)

## Why Messages Go to DLQ

### 1. Deserialization Errors

**Cause**: Message payload cannot be parsed as JSON or doesn't match expected schema.

**Example**:
```json
{invalid json} // Missing quotes, malformed
```

**Log Pattern**:
```
ERROR - Failed to deserialize message: Unexpected character...
```

### 2. Missing Required Fields

**Cause**: JSON is valid but missing critical fields.

**Example**:
```json
{"eventId": "123"} // Missing filePath, fileType
```

### 3. Type Mismatches

**Cause**: Field values don't match expected types.

**Example**:
```json
{"taskId": "not-a-number", "analysisId": 123} // taskId should be Long, analysisId should be UUID
```

## Monitoring DLQ Topics

### Check DLQ Message Count

```bash
# List all DLQ topics and their message counts
for topic in $(kafka-topics --bootstrap-server localhost:9092 --list | grep DLQ); do
    count=$(kafka-run-class kafka.tools.GetOffsetShell \
        --broker-list localhost:9092 \
        --topic $topic \
        --time -1 | awk -F ":" '{sum += $3} END {print sum}')
    echo "$topic: $count messages"
done
```

### Monitor DLQ in Real-Time

```bash
# Tail all DLQ topics for new messages
kafka-console-consumer --bootstrap-server localhost:9092 \
    --whitelist '.*\.DLQ' \
    --from-beginning
```

### Check Specific DLQ Topic

```bash
# View messages in static-analysis DLQ
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic static-analysis-tasks.DLQ \
    --from-beginning \
    --max-messages 10
```

## Inspecting DLQ Messages

### View Message Details

```bash
# Consume with full metadata (partition, offset, timestamp)
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic file-events.DLQ \
    --from-beginning \
    --property print.timestamp=true \
    --property print.key=true \
    --property print.offset=true \
    --property print.partition=true
```

### Export DLQ Messages for Analysis

```bash
# Export to JSON file
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic orchestrator-responses.DLQ \
    --from-beginning \
    --timeout-ms 5000 > dlq_messages_$(date +%Y%m%d).json
```

### Count Messages by Error Type

```bash
# Extract error patterns (requires jq)
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic file-events.DLQ \
    --from-beginning \
    --timeout-ms 5000 | \
    jq -r '.errorMessage' | \
    sort | uniq -c | sort -rn
```

## Replaying Messages from DLQ

### Prerequisites

1. **Fix root cause** - Ensure the issue that caused DLQ routing is resolved
2. **Test with sample** - Replay one message first to verify fix
3. **Backup data** - Export DLQ messages before replay

### Replay Single Message

```bash
# 1. Export the message
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic file-events.DLQ \
    --partition 0 \
    --offset 42 \
    --max-messages 1 > message.json

# 2. Extract and fix the message payload
jq -r '.messagePayload' message.json > fixed_message.json

# 3. Replay to original topic
cat fixed_message.json | kafka-console-producer \
    --bootstrap-server localhost:9092 \
    --topic file-events
```

### Batch Replay

```bash
# Export all messages from DLQ
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic static-analysis-tasks.DLQ \
    --from-beginning \
    --timeout-ms 10000 > dlq_export.json

# Process and fix messages (manual or script)
# Then replay each message:
while IFS= read -r message; do
    echo "$message" | jq -r '.messagePayload' | \
        kafka-console-producer --bootstrap-server localhost:9092 \
        --topic static-analysis-tasks
done < dlq_export.json
```

### Automated Replay Script

```bash
#!/bin/bash
# replay-dlq.sh - Replay messages from DLQ after fixing

DLQ_TOPIC="$1"
ORIGINAL_TOPIC="${DLQ_TOPIC%.DLQ}"
BOOTSTRAP="localhost:9092"

echo "Replaying from $DLQ_TOPIC to $ORIGINAL_TOPIC"

# Export DLQ messages
kafka-console-consumer --bootstrap-server $BOOTSTRAP \
    --topic $DLQ_TOPIC \
    --from-beginning \
    --timeout-ms 5000 | \
    jq -r '.messagePayload' | \
    kafka-console-producer --bootstrap-server $BOOTSTRAP \
    --topic $ORIGINAL_TOPIC

echo "Replay complete"
```

**Usage**:
```bash
chmod +x replay-dlq.sh
./replay-dlq.sh file-events.DLQ
```

## Alerting Recommendations

### Critical Alerts

**Alert**: DLQ message count > 10
- **Action**: Investigate immediately - indicates systemic issue
- **Query**: `sum(kafka_log_log_size{topic=~".*\\.DLQ"}) > 10`

**Alert**: DLQ growing rapidly (>5 messages/minute)
- **Action**: Check producer health, schema changes
- **Query**: `rate(kafka_log_log_size{topic=~".*\\.DLQ"}[1m]) > 5`

### Warning Alerts

**Alert**: Any message in DLQ
- **Action**: Review and fix within 24 hours
- **Query**: `kafka_log_log_size{topic=~".*\\.DLQ"} > 0`

**Alert**: DLQ retention approaching (messages older than 25 days)
- **Action**: Review before 30-day expiration
- **Query**: Check oldest message timestamp in DLQ topics

### Alert Configuration Example (Prometheus)

```yaml
groups:
  - name: dlq_alerts
    rules:
      - alert: DLQMessagesPresent
        expr: kafka_log_log_size{topic=~".*\\.DLQ"} > 0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Messages in DLQ: {{ $labels.topic }}"
          description: "{{ $value }} messages in DLQ topic {{ $labels.topic }}"
      
      - alert: DLQHighVolume
        expr: kafka_log_log_size{topic=~".*\\.DLQ"} > 10
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "High DLQ volume: {{ $labels.topic }}"
          description: "{{ $value }} messages in DLQ - investigate immediately"
```

## Common Troubleshooting Scenarios

### Scenario 1: Schema Change Breaks Consumers

**Symptom**: Sudden spike in DLQ messages after deployment

**Investigation**:
```bash
# Check recent DLQ messages
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic orchestrator-responses.DLQ \
    --from-beginning --max-messages 5 | jq .
```

**Solution**:
1. Identify incompatible schema change
2. Deploy backward-compatible fix
3. Replay messages from DLQ

### Scenario 2: Malformed External Input

**Symptom**: file-events.DLQ has messages with invalid JSON

**Investigation**:
```bash
# View malformed messages
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic file-events.DLQ \
    --from-beginning | jq -r '.messagePayload'
```

**Solution**:
1. Identify source of malformed messages
2. Add validation at producer
3. Manually fix and replay messages

### Scenario 3: Temporary Service Outage

**Symptom**: DLQ messages during known downtime

**Investigation**:
```bash
# Check message timestamps
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic static-analysis-tasks.DLQ \
    --property print.timestamp=true
```

**Solution**:
1. Verify service is now healthy
2. Replay all messages from DLQ
3. Monitor for successful processing

### Scenario 4: Corrupt Message Data

**Symptom**: Single message in DLQ with unrecoverable error

**Investigation**:
```bash
# Extract specific message
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic decompiler-tasks.DLQ \
    --partition 0 --offset 15 --max-messages 1
```

**Solution**:
1. Analyze message content
2. Determine if message is valid
3. If invalid: Delete/skip message, log incident
4. If valid: Fix consumer code, replay message

## DLQ Maintenance

### Cleanup Old DLQ Messages

```bash
# Delete messages older than 7 days (after investigation)
kafka-configs --bootstrap-server localhost:9092 \
    --entity-type topics \
    --entity-name file-events.DLQ \
    --alter --add-config retention.ms=604800000
```

### Archive DLQ Messages

```bash
# Export for long-term storage before cleanup
kafka-console-consumer --bootstrap-server localhost:9092 \
    --topic orchestrator-responses.DLQ \
    --from-beginning \
    --timeout-ms 10000 | \
    gzip > dlq_archive_$(date +%Y%m%d).json.gz
```

### Monitor DLQ Disk Usage

```bash
# Check DLQ topic sizes
du -sh /var/lib/kafka/data/*DLQ*
```

## Best Practices

### Prevention

1. **Validate schemas** - Use schema registry for strict validation
2. **Test thoroughly** - Include negative test cases
3. **Monitor producers** - Catch issues before messages reach Kafka
4. **Use integration tests** - Test full message lifecycle

### Detection

1. **Alert on DLQ messages** - Don't let DLQ grow silently
2. **Daily DLQ reviews** - Check for patterns
3. **Log analysis** - Correlate DLQ messages with application errors

### Recovery

1. **Fix root cause first** - Don't replay until issue is resolved
2. **Test with samples** - Replay 1-2 messages before batch
3. **Monitor replays** - Ensure replayed messages succeed
4. **Document incidents** - Track DLQ patterns over time

### Operational Hygiene

1. **Regular cleanup** - Archive and delete old DLQ messages
2. **Capacity planning** - Ensure DLQ topics have adequate storage
3. **Runbooks** - Document team-specific replay procedures
4. **Post-mortems** - Analyze recurring DLQ patterns

## Support & Escalation

### When to Escalate

- DLQ message count > 100
- Recurring DLQ patterns (same error multiple times)
- Unable to determine root cause after initial investigation
- Messages approaching 30-day retention limit

### Escalation Checklist

- [ ] Export DLQ messages for analysis
- [ ] Document error patterns and timestamps
- [ ] Check application logs for correlation
- [ ] Identify recent deployments or config changes
- [ ] Prepare summary of investigation steps taken

## References

- [Kafka Documentation - Error Handling](https://kafka.apache.org/documentation/#consumerconfigs)
- [Spring Kafka - Dead Letter Publishing](https://docs.spring.io/spring-kafka/reference/html/#dead-letters)
- [PRD.md - DLQ Architecture](../.claude/PRD.md#dlq-implementation)
- [KafkaConfig.java](../common/src/main/java/com/mobileanalysis/common/config/KafkaConfig.java) - DLQ implementation

---

**Last Updated**: January 23, 2026  
**Document Version**: 1.0  
**Status**: Production Ready
