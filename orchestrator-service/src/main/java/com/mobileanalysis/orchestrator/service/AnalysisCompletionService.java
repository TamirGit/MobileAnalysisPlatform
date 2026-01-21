package com.mobileanalysis.orchestrator.service;

import com.mobileanalysis.common.domain.AnalysisStatus;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisRepository;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for detecting and marking analysis completion.
 *
 * Terminal states:
 * - COMPLETED: all tasks are COMPLETED
 * - FAILED: at least one task is FAILED (after retry exhaustion)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisCompletionService {

    private final AnalysisRepository analysisRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public void checkAndMarkCompletion(UUID analysisId) {
        log.debug("Checking completion status for analysis: {}", analysisId);

        AnalysisEntity analysis = analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));

        if (analysis.getStatus() == AnalysisStatus.COMPLETED || analysis.getStatus() == AnalysisStatus.FAILED) {
            log.debug("Analysis {} already in terminal state: {}", analysisId, analysis.getStatus());
            return;
        }

        List<AnalysisTaskEntity> tasks = analysisTaskRepository.findByAnalysisId(analysisId);

        if (tasks.isEmpty()) {
            log.warn("No tasks found for analysis: {}", analysisId);
            return;
        }

        if (anyTaskFailed(tasks)) {
            markAsFailed(analysis, tasks);
            deleteCacheForCompletedAnalysis(analysisId);
            return;
        }

        if (allTasksCompleted(tasks)) {
            markAsCompleted(analysis);
            deleteCacheForCompletedAnalysis(analysisId);
        } else {
            log.debug("Analysis {} has {} tasks still in progress", analysisId, countInProgressTasks(tasks));
        }
    }

    private boolean allTasksCompleted(List<AnalysisTaskEntity> tasks) {
        return tasks.stream().allMatch(t -> t.getStatus() == TaskStatus.COMPLETED);
    }

    private boolean anyTaskFailed(List<AnalysisTaskEntity> tasks) {
        return tasks.stream().anyMatch(t -> t.getStatus() == TaskStatus.FAILED);
    }

    private int countInProgressTasks(List<AnalysisTaskEntity> tasks) {
        return (int) tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.PENDING
                        || t.getStatus() == TaskStatus.DISPATCHED
                        || t.getStatus() == TaskStatus.RUNNING)
                .count();
    }

    private void markAsCompleted(AnalysisEntity analysis) {
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(Instant.now());
        analysisRepository.save(analysis);

        long durationSeconds = java.time.Duration.between(analysis.getStartedAt(), analysis.getCompletedAt()).getSeconds();
        log.info("Analysis {} marked as COMPLETED. Duration: {} seconds", analysis.getId(), durationSeconds);
    }

    private void markAsFailed(AnalysisEntity analysis, List<AnalysisTaskEntity> tasks) {
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setCompletedAt(Instant.now());
        analysisRepository.save(analysis);

        List<AnalysisTaskEntity> failedTasks = tasks.stream()
                .filter(t -> t.getStatus() == TaskStatus.FAILED)
                .toList();

        log.error("Analysis {} marked as FAILED. {} task(s) failed:", analysis.getId(), failedTasks.size());
        for (AnalysisTaskEntity failedTask : failedTasks) {
            log.error("  - Task {} ({}): {} (attempts: {})",
                    failedTask.getId(),
                    failedTask.getEngineType(),
                    failedTask.getErrorMessage() != null ? failedTask.getErrorMessage() : "No error message",
                    failedTask.getAttempts());
        }
    }

    private void deleteCacheForCompletedAnalysis(UUID analysisId) {
        String cacheKey = "analysis-state:" + analysisId;

        try {
            Boolean deleted = redisTemplate.delete(cacheKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("Deleted cache entry for completed analysis: {}", analysisId);
            } else {
                log.debug("No cache entry found for analysis: {}", analysisId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete cache for completed analysis {} - cache will self-heal/expire", analysisId, e);
        }
    }
}
