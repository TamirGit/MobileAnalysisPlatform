package com.mobileanalysis.orchestrator.repository;

import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEventEntity, Long> {
    
    /**
     * Find unprocessed events in batch for outbox polling
     * Ordered by creation time to maintain event ordering
     */
    @Query("SELECT o FROM OutboxEventEntity o WHERE o.processed = false ORDER BY o.createdAt ASC")
    List<OutboxEventEntity> findUnprocessedBatch(Pageable pageable);
    
    /**
     * Find all unprocessed events (for monitoring)
     */
    List<OutboxEventEntity> findByProcessedFalseOrderByCreatedAt();
    
    /**
     * Count unprocessed events
     */
    long countByProcessedFalse();
    
    /**
     * Delete old processed events (cleanup)
     * Removes events processed before the given threshold
     */
    @Modifying
    @Query("DELETE FROM OutboxEventEntity o WHERE o.processed = true AND o.processedAt < :threshold")
    int deleteProcessedBefore(@Param("threshold") Instant threshold);
    
    /**
     * Find events by aggregate ID (for debugging/troubleshooting)
     */
    List<OutboxEventEntity> findByAggregateIdOrderByCreatedAt(String aggregateId);
}
