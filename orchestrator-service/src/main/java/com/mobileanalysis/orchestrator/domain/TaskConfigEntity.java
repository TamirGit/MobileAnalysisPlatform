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
    
    @Column(name = "max_retries", nullable = false)
    @Builder.Default
    private Integer maxRetries = 3;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}