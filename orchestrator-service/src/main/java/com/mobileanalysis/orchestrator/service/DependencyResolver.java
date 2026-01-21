package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.TaskConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service responsible for resolving task dependencies and dispatching ready dependent tasks.
 * When a task completes, this service:
 * 1. Finds all tasks that depend on the completed task
 * 2. Checks if their dependencies are satisfied
 * 3. Creates outbox events for tasks that are ready to run
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DependencyResolver {
    
    private final AnalysisTaskRepository analysisTaskRepository;
    private final TaskConfigRepository taskConfigRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Resolve dependencies for a completed task and dispatch any ready dependent tasks.
     * This method is transactional and creates outbox events for ready tasks.
     * 
     * @param completedTask The task that just completed
     * @return List of outbox events for dependent tasks that are ready to run
     */
    @Transactional
    public List<OutboxEventEntity> resolveAndDispatch(AnalysisTaskEntity completedTask) {
        List<OutboxEventEntity> outboxEvents = new ArrayList<>();
        
        log.debug("Resolving dependencies for completed task: taskId={}, analysisId={}", 
            completedTask.getId(), completedTask.getAnalysisId());
        
        // Find all tasks that depend on this task
        List<AnalysisTaskEntity> dependentTasks = analysisTaskRepository
            .findByDependsOnTaskId(completedTask.getId());
        
        if (dependentTasks.isEmpty()) {
            log.debug("No dependent tasks found for task: {}", completedTask.getId());
            return outboxEvents;
        }
        
        log.info("Found {} dependent tasks for completed task: {}", 
            dependentTasks.size(), completedTask.getId());
        
        // Check each dependent task to see if it's ready to run
        for (AnalysisTaskEntity dependentTask : dependentTasks) {
            if (areAllDependenciesMet(dependentTask)) {
                log.info("All dependencies met for task: {}, dispatching", dependentTask.getId());
                
                // Update task status to DISPATCHED
                dependentTask.setStatus(TaskStatus.DISPATCHED);
                analysisTaskRepository.save(dependentTask);
                
                // Create outbox event for this task
                OutboxEventEntity outboxEvent = createTaskDispatchEvent(dependentTask, completedTask);
                outboxEvents.add(outboxEvent);
                
                log.info("Task {} dispatched to outbox for engine: {}", 
                    dependentTask.getId(), dependentTask.getEngineType());
            } else {
                log.debug("Task {} still has unmet dependencies", dependentTask.getId());
            }
        }
        
        return outboxEvents;
    }
    
    /**
     * Check if all dependencies for a task are met.
     * A task is ready to run if:
     * - It has no dependencies (dependsOnTaskId is null), OR
     * - All its dependency tasks have status COMPLETED
     * 
     * Currently supports simple 1:1 parent-child dependencies (Phase 2).
     * DAG support with multiple parents will be added in future phase.
     * 
     * @param task The task to check
     * @return true if all dependencies are met, false otherwise
     */
    private boolean areAllDependenciesMet(AnalysisTaskEntity task) {
        if (task.getDependsOnTaskId() == null) {
            // No dependencies - ready to run
            return true;
        }
        
        // Check if parent task is completed
        AnalysisTaskEntity parentTask = analysisTaskRepository
            .findById(task.getDependsOnTaskId())
            .orElse(null);
        
        if (parentTask == null) {
            log.error("Parent task not found: {} for dependent task: {}", 
                task.getDependsOnTaskId(), task.getId());
            return false;
        }
        
        boolean isReady = parentTask.getStatus() == TaskStatus.COMPLETED;
        log.debug("Dependency check for task {}: parent task {} status is {}, ready={}", 
            task.getId(), parentTask.getId(), parentTask.getStatus(), isReady);
        
        return isReady;
    }
    
    /**
     * Create outbox event for dispatching a task to its engine.
     * Uses transactional outbox pattern - event will be published by OutboxPoller.
     * 
     * @param task The task to dispatch
     * @param parentTask The parent task that completed (provides output path)
     * @return Outbox event entity (not yet persisted)
     */
    private OutboxEventEntity createTaskDispatchEvent(AnalysisTaskEntity task, 
                                                      AnalysisTaskEntity parentTask) {
        try {
            // Get timeout from task config
            TaskConfigEntity taskConfig = taskConfigRepository
                .findById(task.getTaskConfigId())
                .orElseThrow(() -> new IllegalStateException(
                    "Task config not found: " + task.getTaskConfigId()));
            
            // Create task event with parent's output path as input
            TaskEvent taskEvent = TaskEvent.builder()
                .eventId(UUID.randomUUID())
                .taskId(task.getId())
                .analysisId(task.getAnalysisId())
                .engineType(task.getEngineType())
                .filePath(task.getAnalysis().getFilePath())
                .dependentTaskOutputPath(parentTask.getOutputPath()) // Parent output as input
                .idempotencyKey(task.getIdempotencyKey())
                .timeoutSeconds(taskConfig.getTimeoutSeconds())
                .timestamp(Instant.now())
                .build();
            
            String topic = getTopicForEngineType(task.getEngineType().name());
            String payload = objectMapper.writeValueAsString(taskEvent);
            
            return OutboxEventEntity.builder()
                .aggregateType("AnalysisTask")
                .aggregateId(task.getId().toString())
                .eventType("TASK_DISPATCHED")
                .topic(topic)
                .partitionKey(task.getAnalysisId().toString()) // Partition by analysisId
                .payload(payload)
                .processed(false)
                .build();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task event: taskId={}", task.getId(), e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }
    
    /**
     * Map engine type to Kafka topic name.
     * 
     * @param engineType Engine type string
     * @return Kafka topic name
     */
    private String getTopicForEngineType(String engineType) {
        return switch (engineType) {
            case "STATIC_ANALYSIS" -> "static-analysis-tasks";
            case "DYNAMIC_ANALYSIS" -> "dynamic-analysis-tasks";
            case "DECOMPILER" -> "decompiler-tasks";
            case "SIGNATURE_CHECK" -> "signature-check-tasks";
            default -> throw new IllegalArgumentException("Unknown engine type: " + engineType);
        };
    }
}
