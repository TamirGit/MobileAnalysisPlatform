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
 * An analysis is considered:
 * - COMPLETED: When all tasks have status COMPLETED
 * - FAILED: When any task has status FAILED (after exhausting retries)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisCompletionService {
    
    private final AnalysisRepository analysisRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    
    /**
     * Check if an analysis is complete and update its status accordingly.
     * This method should be called after any task status change.
     * 
     * @param analysisId The analysis to check
     */
    @Transactional
    public void checkAndMarkCompletion(UUID analysisId) {
        log.debug("Checking completion status for analysis: {}", analysisId);
        
        AnalysisEntity analysis = analysisRepository.findById(analysisId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Analysis not found: " + analysisId));
        
        // Don't check if already completed or failed
        if (analysis.getStatus() == AnalysisStatus.COMPLETED || 
            analysis.getStatus() == AnalysisStatus.FAILED) {
            log.debug("Analysis {} already in terminal state: {}", analysisId, analysis.getStatus());
            return;
        }
        
        // Get all tasks for this analysis
        List<AnalysisTaskEntity> tasks = analysisTaskRepository.findByAnalysisId(analysisId);
        
        if (tasks.isEmpty()) {
            log.warn("No tasks found for analysis: {}", analysisId);
            return;
        }
        
        // Check for failure first (any task failed = analysis failed)
        boolean anyTaskFailed = anyTaskFailed(tasks);
        if (anyTaskFailed) {
            markAsFailed(analysis, tasks);
            deleteCacheForCompletedAnalysis(analysisId);
            return;
        }
        
        // Check if all tasks completed
        boolean allTasksCompleted = allTasksCompleted(tasks);
        if (allTasksCompleted) {
            markAsCompleted(analysis);
            deleteCacheForCompletedAnalysis(analysisId);
        } else {
            log.debug("Analysis {} has {} tasks still in progress", 
                analysisId, countInProgressTasks(tasks));
        }
    }
    
    /**
     * Check if all tasks have completed successfully.
     * 
     * @param tasks All tasks for an analysis
     * @return true if all tasks are COMPLETED
     */
    private boolean allTasksCompleted(List<AnalysisTaskEntity> tasks) {
        return tasks.stream()
            .allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }
    
    /**
     * Check if any task has failed (after exhausting retries).
     * 
     * @param tasks All tasks for an analysis
     * @return true if any task is FAILED
     */
    private boolean anyTaskFailed(List<AnalysisTaskEntity> tasks) {
        return tasks.stream()
            .anyMatch(task -> task.getStatus() == TaskStatus.FAILED);
    }
    
    /**
     * Count tasks that are still in progress.
     * 
     * @param tasks All tasks for an analysis
     * @return Count of tasks in PENDING, DISPATCHED, or RUNNING state
     */
    private int countInProgressTasks(List<AnalysisTaskEntity> tasks) {
        return (int) tasks.stream()
            .filter(task -> task.getStatus() == TaskStatus.PENDING ||
                           task.getStatus() == TaskStatus.DISPATCHED ||
                           task.getStatus() == TaskStatus.RUNNING)
            .count();
    }
    
    /**
     * Mark analysis as successfully completed.
     * 
     * @param analysis The analysis to mark
     */
    private void markAsCompleted(AnalysisEntity analysis) {
        analysis.setStatus(AnalysisStatus.COMPLETED);
        analysis.setCompletedAt(Instant.now());
        analysisRepository.save(analysis);
        
        long durationSeconds = java.time.Duration.between(
            analysis.getStartedAt(), analysis.getCompletedAt()).getSeconds();
        
        log.info("Analysis {} marked as COMPLETED. Duration: {} seconds", 
            analysis.getId(), durationSeconds);
    }
    
    /**
     * Mark analysis as failed.
     * Logs summary of failed tasks with error messages.
     * 
     * @param analysis The analysis to mark
     * @param tasks All tasks for the analysis
     */
    private void markAsFailed(AnalysisEntity analysis, List<AnalysisTaskEntity> tasks) {
        analysis.setStatus(AnalysisStatus.FAILED);
        analysis.setCompletedAt(Instant.now());
        analysisRepository.save(analysis);
        
        // Log summary of failed tasks
        List<AnalysisTaskEntity> failedTasks = tasks.stream()
            .filter(task -> task.getStatus() == TaskStatus.FAILED)
            .toList();
        
        log.error("Analysis {} marked as FAILED. {} task(s) failed:", 
            analysis.getId(), failedTasks.size());
        
        for (AnalysisTaskEntity failedTask : failedTasks) {
            log.error("  - Task {} ({}): {} (attempts: {})", 
                failedTask.getId(), 
                failedTask.getEngineType(),
                failedTask.getErrorMessage() != null ? 
                    failedTask.getErrorMessage() : "No error message",
                failedTask.getAttempts());
        }
    }
    
    /**
     * Delete Redis cache entry for a completed analysis.
     * Cache is no longer needed once analysis is in terminal state.
     * Failures are logged but don't affect completion logic.
     * 
     * @param analysisId The analysis ID
     */
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
            log.warn("Failed to delete cache for completed analysis {} - cache will expire naturally", 
                analysisId, e);
        }
    }
}
