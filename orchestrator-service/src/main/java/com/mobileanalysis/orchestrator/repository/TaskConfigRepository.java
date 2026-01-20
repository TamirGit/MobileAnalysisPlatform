package com.mobileanalysis.orchestrator.repository;

import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfigEntity, Long> {
    
    /**
     * Find all task configurations for a specific analysis configuration
     * Ordered by task_order to maintain workflow sequence
     */
    List<TaskConfigEntity> findByAnalysisConfigIdOrderByTaskOrder(Long analysisConfigId);
    
    /**
     * Find task configs by analysis config ID (unordered)
     */
    List<TaskConfigEntity> findByAnalysisConfigId(Long analysisConfigId);
}
