package com.mobileanalysis.orchestrator.service;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Scheduled job to monitor task heartbeats and detect stale/zombie tasks.
 * Runs every minute to find tasks that haven't sent a heartbeat in 2+ minutes.
 * Stale tasks are marked as FAILED and eligible for retry.
 * <p>
 * This prevents analyses from hanging indefinitely when engines crash or become unresponsive.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HeartbeatMonitor {
    
    private final AnalysisTaskRepository analysisTaskRepository;
    // TaskRetryService will be added in Task 8
    
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(2);
    
    /**
     * Check for stale tasks and mark them as failed.
     * Runs every minute (60000 ms).
     */
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void checkForStaleTasks() {
        Instant staleThreshold = Instant.now().minus(STALE_THRESHOLD);
        
        log.debug("Checking for stale tasks (no heartbeat since {})", staleThreshold);
        
        // Find all RUNNING tasks with last_heartbeat_at older than threshold
        List<AnalysisTaskEntity> staleTasks = analysisTaskRepository.findByStatusAndLastHeartbeatAtBefore(
            TaskStatus.RUNNING, staleThreshold
        );
        
        if (staleTasks.isEmpty()) {
            log.debug("No stale tasks found");
            return;
        }
        
        log.warn("Found {} stale tasks (no heartbeat for {}+ minutes)", 
            staleTasks.size(), STALE_THRESHOLD.toMinutes());
        
        for (AnalysisTaskEntity task : staleTasks) {
            handleStaleTask(task, staleThreshold);
        }
    }
    
    /**
     * Handle a single stale task:
     * 1. Mark as FAILED
     * 2. Set error message
     * 3. Trigger retry logic (if under max attempts)
     * 
     * @param task The stale task
     * @param staleThreshold The timestamp threshold for staleness
     */
    private void handleStaleTask(AnalysisTaskEntity task, Instant staleThreshold) {
        Instant lastHeartbeat = task.getLastHeartbeatAt();
        Duration timeSinceHeartbeat = lastHeartbeat != null ? 
            Duration.between(lastHeartbeat, Instant.now()) : Duration.ZERO;
        
        log.error("Stale task detected: taskId={}, analysisId={}, engineType={}, " +
                "lastHeartbeat={}, timeSinceHeartbeat={} seconds", 
            task.getId(), task.getAnalysisId(), task.getEngineType(), 
            lastHeartbeat, timeSinceHeartbeat.getSeconds());
        
        // Mark task as FAILED
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(String.format(
            "Task timeout: no heartbeat for %d seconds (threshold: %d seconds)",
            timeSinceHeartbeat.getSeconds(),
            STALE_THRESHOLD.getSeconds()
        ));
        task.setCompletedAt(Instant.now());
        
        analysisTaskRepository.save(task);
        
        log.info("Marked stale task as FAILED: taskId={}, attempts={}", 
            task.getId(), task.getAttempts());
        
        // TODO: Trigger retry via TaskRetryService (will be implemented in Task 8)
        // For now, task remains FAILED until retry service is added
    }
}
