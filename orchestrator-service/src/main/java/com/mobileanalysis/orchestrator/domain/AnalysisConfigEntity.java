package com.mobileanalysis.orchestrator.domain;

import com.mobileanalysis.common.domain.FileType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "analysis_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisConfigEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, unique = true, length = 10)
    private FileType fileType;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
    
    @OneToMany(mappedBy = "analysisConfigId", fetch = FetchType.LAZY)
    private List<TaskConfigEntity> tasks;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}