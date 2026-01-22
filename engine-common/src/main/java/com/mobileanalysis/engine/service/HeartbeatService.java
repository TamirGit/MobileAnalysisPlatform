package com.mobileanalysis.engine.service;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.HeartbeatEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Service responsible for sending periodic heartbeat signals to the orchestrator.
 * Heartbeats indicate that a task is still actively being processed.
 * <p>
 * Orchestrator uses these heartbeats to detect stale/zombie tasks.
 * If no heartbeat is received for 2+ minutes, the task is marked as failed.
 * <p>
 * Heartbeat schedule:
 * - Immediate heartbeat when task starts (t=0s)
 * - Periodic heartbeats every 30 seconds (t=30s, 60s, 90s, ...)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HeartbeatService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${app.kafka.topics.task-heartbeats:task-heartbeats}")
    private String heartbeatTopic;
    
    // Thread-safe reference to the currently running task
    private final AtomicReference<RunningTaskContext> currentTask = new AtomicReference<>();
    
    /**
     * Context for a running task that needs heartbeats.
     */
    private record RunningTaskContext(
        Long taskId,
        UUID analysisId,
        String engineType
    ) {}
    
    /**
     * Start sending heartbeats for a task.
     * Sends an immediate heartbeat, then schedules periodic heartbeats.
     * Call this when a task begins processing.
     * 
     * @param taskId Task ID
     * @param analysisId Analysis ID
     * @param engineType Engine type
     */
    public void startHeartbeat(Long taskId, UUID analysisId, String engineType) {
        currentTask.set(new RunningTaskContext(taskId, analysisId, engineType));
        log.info("Started heartbeat tracking for task: {}", taskId);
        
        // Send immediate heartbeat (t=0s)
        sendHeartbeatEvent();
        log.debug("Sent immediate heartbeat for task: {}", taskId);
    }
    
    /**
     * Stop sending heartbeats for the current task.
     * Call this when a task completes (success or failure).
     */
    public void stopHeartbeat() {
        RunningTaskContext task = currentTask.getAndSet(null);
        if (task != null) {
            log.info("Stopped heartbeat tracking for task: {}", task.taskId());
        }
    }
    
    /**
     * Send periodic heartbeat for the currently running task.
     * Scheduled to run every 30 seconds after 30 second initial delay.
     * This creates consistent 30-second intervals: 30s, 60s, 90s, etc.
     * Combined with the immediate heartbeat in startHeartbeat(),
     * the full schedule is: 0s, 30s, 60s, 90s, ...
     * <p>
     * If no task is running, this is a no-op.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void sendHeartbeat() {
        sendHeartbeatEvent();
    }
    
    /**
     * Helper method to send a heartbeat event for the current task.
     * Used by both startHeartbeat() (immediate) and sendHeartbeat() (scheduled).
     */
    private void sendHeartbeatEvent() {
        RunningTaskContext task = currentTask.get();
        
        if (task == null) {
            log.debug("No active task - skipping heartbeat");
            return;
        }
        
        try {
            HeartbeatEvent event = HeartbeatEvent.builder()
                .eventId(UUID.randomUUID())
                .taskId(task.taskId())
                .analysisId(task.analysisId())
                .engineType(task.engineType())
                .status(TaskStatus.RUNNING)
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send(heartbeatTopic, task.analysisId().toString(), event);
            
            log.debug("Sent heartbeat for task: {} at {}", task.taskId(), event.getTimestamp());
            
        } catch (Exception e) {
            log.error("Failed to send heartbeat for task: {} - {}", 
                task.taskId(), e.getMessage(), e);
            // Don't crash - just log and continue
            // Orchestrator will eventually mark task as stale if heartbeats stop
        }
    }
}
