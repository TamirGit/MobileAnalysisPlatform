package com.mobileanalysis.orchestrator.domain;

import com.mobileanalysis.common.domain.EngineType;
import com.mobileanalysis.common.domain.TaskStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analysis_task")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisTaskEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;
    
    @Column(name = "task_config_id", nullable = false)
    private Long taskConfigId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false, length = 50)
    private EngineType engineType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;
    
    @Column(name = "depends_on_task_id")
    private Long dependsOnTaskId;
    
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private Integer attempts = 0;
    
    @Column(name = "output_path", length = 500)
    private String outputPath;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;
    
    @Column(name = "started_at")
    private Instant startedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}