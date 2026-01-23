package com.mobileanalysis.orchestrator.domain;

import com.mobileanalysis.common.domain.EngineType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "task_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "analysis_config_id", nullable = false)
    private Long analysisConfigId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false, length = 50)
    private EngineType engineType;
    
    @Column(name = "task_order", nullable = false)
    private Integer taskOrder;
    
    @Column(name = "depends_on_task_config_id")
    private Long dependsOnTaskConfigId;
    
    @Column(name = "timeout_seconds", nullable = false)
    @Builder.Default
    private Integer timeoutSeconds = 300;
    
    /**
     * Maximum number of TOTAL attempts for this task (including the original attempt).
     * <p>
     * Semantics:
     * - maxRetries = 1: Only original attempt, no retries
     * - maxRetries = 2: Original + 1 retry (2 total attempts)
     * - maxRetries = 3: Original + 2 retries (3 total attempts) [DEFAULT]
     * <p>
     * Example: If maxRetries=3:
     * - Attempt 1: Original execution
     * - Attempt 2: First retry (if attempt 1 fails)
     * - Attempt 3: Second retry (if attempt 2 fails)
     * - After attempt 3 fails: Task permanently FAILED
     * <p>
     * Note: Despite the field name "maxRetries", this value represents the maximum
     * number of total attempts, not additional retries. This is for backward compatibility
     * with existing database schema and configurations.
     */
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}