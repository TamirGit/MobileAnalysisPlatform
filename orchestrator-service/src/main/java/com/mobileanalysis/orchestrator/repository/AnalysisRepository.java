package com.mobileanalysis.orchestrator.repository;

import com.mobileanalysis.common.domain.AnalysisStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AnalysisRepository extends JpaRepository<AnalysisEntity, UUID> {
    
    List<AnalysisEntity> findByStatus(AnalysisStatus status);
    
    List<AnalysisEntity> findByStatusIn(List<AnalysisStatus> statuses);
}