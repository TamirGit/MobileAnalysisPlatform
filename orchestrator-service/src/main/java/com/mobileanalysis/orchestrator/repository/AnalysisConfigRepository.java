package com.mobileanalysis.orchestrator.repository;

import com.mobileanalysis.common.domain.FileType;
import com.mobileanalysis.orchestrator.domain.AnalysisConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisConfigRepository extends JpaRepository<AnalysisConfigEntity, Long> {
    
    /**
     * Find analysis configuration by file type
     * Used to load the workflow configuration for APK or IPA files
     */
    Optional<AnalysisConfigEntity> findByFileType(FileType fileType);
    
    /**
     * Find active configurations only
     */
    List<AnalysisConfigEntity> findByActiveTrue();
    
    /**
     * Find configuration by file type and active status
     * Ensures we only load active configurations
     */
    @Query("SELECT ac FROM AnalysisConfigEntity ac WHERE ac.fileType = :fileType AND ac.active = true")
    Optional<AnalysisConfigEntity> findActiveByFileType(@Param("fileType") FileType fileType);
}
