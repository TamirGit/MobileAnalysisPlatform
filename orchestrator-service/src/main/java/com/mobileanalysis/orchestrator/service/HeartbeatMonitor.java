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
 *
 * Default behavior (per PRD):
 * - Engines send heartbeat every 30 seconds.
 * - Orchestrator marks a RUNNING task stale if no heartbeat for 2 minutes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HeartbeatMonitor {

    private final AnalysisTaskRepository analysisTaskRepository;
    private final TaskRetryService taskRetryService;

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(2);

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    @Transactional
    public void checkForStaleTasks() {
        Instant staleThreshold = Instant.now().minus(STALE_THRESHOLD);

        log.debug("Checking for stale RUNNING tasks (lastHeartbeatAt < {})", staleThreshold);

        List<AnalysisTaskEntity> staleTasks = analysisTaskRepository.findStaleRunningTasks(staleThreshold);

        if (staleTasks.isEmpty()) {
            log.debug("No stale tasks found");
            return;
        }

        log.warn("Found {} stale task(s) (no heartbeat for {}+ minutes)",
                staleTasks.size(), STALE_THRESHOLD.toMinutes());

        for (AnalysisTaskEntity task : staleTasks) {
            handleStaleTask(task);
        }
    }

    private void handleStaleTask(AnalysisTaskEntity task) {
        Instant lastHeartbeat = task.getLastHeartbeatAt();
        Duration timeSinceHeartbeat = lastHeartbeat != null
                ? Duration.between(lastHeartbeat, Instant.now())
                : Duration.ZERO;

        String reason = String.format(
                "Task timeout: no heartbeat for %d seconds (threshold: %d seconds)",
                timeSinceHeartbeat.getSeconds(),
                STALE_THRESHOLD.getSeconds()
        );

        log.error("Stale task detected: taskId={}, analysisId={}, engineType={}, lastHeartbeatAt={}, reason={}",
                task.getId(), task.getAnalysisId(), task.getEngineType(), lastHeartbeat, reason);

        // Mark as failed (will be retried if budget remains)
        task.setStatus(TaskStatus.FAILED);
        task.setErrorMessage(reason);
        task.setCompletedAt(Instant.now());
        analysisTaskRepository.save(task);

        // Trigger retry logic
        taskRetryService.retryIfPossible(task, reason);
    }
}
