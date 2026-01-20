package com.mobileanalysis.orchestrator.repository;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnalysisTaskRepository extends JpaRepository<AnalysisTaskEntity, Long> {
    
    /**
     * Find all tasks for a specific analysis, ordered by creation time
     */
    List<AnalysisTaskEntity> findByAnalysisIdOrderByCreatedAt(UUID analysisId);
    
    /**
     * Find tasks by analysis ID and status
     */
    List<AnalysisTaskEntity> findByAnalysisIdAndStatus(UUID analysisId, TaskStatus status);
    
    /**
     * Find task by idempotency key for duplicate detection
     */
    Optional<AnalysisTaskEntity> findByIdempotencyKey(UUID idempotencyKey);
    
    /**
     * Find tasks that are ready to run:
     * - Status is PENDING
     * - Either no dependency OR dependency is COMPLETED
     */
    @Query("SELECT t FROM AnalysisTaskEntity t WHERE t.analysisId = :analysisId " +
           "AND t.status = 'PENDING' " +
           "AND (t.dependsOnTaskId IS NULL OR " +
           "  EXISTS (SELECT 1 FROM AnalysisTaskEntity dep WHERE dep.id = t.dependsOnTaskId " +
           "           AND dep.status = 'COMPLETED'))")
    List<AnalysisTaskEntity> findReadyToRunTasks(@Param("analysisId") UUID analysisId);
    
    /**
     * Find stale tasks with no recent heartbeat
     * Used for detecting tasks that may have crashed or timed out
     */
    @Query("SELECT t FROM AnalysisTaskEntity t WHERE t.status = 'RUNNING' " +
           "AND t.lastHeartbeatAt < :staleThreshold")
    List<AnalysisTaskEntity> findStaleRunningTasks(@Param("staleThreshold") Instant staleThreshold);
    
    /**
     * Find all tasks by status
     */
    List<AnalysisTaskEntity> findByStatus(TaskStatus status);
}
