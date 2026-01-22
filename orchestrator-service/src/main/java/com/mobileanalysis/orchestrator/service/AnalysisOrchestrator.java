package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.AnalysisStatus;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.FileEvent;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.orchestrator.domain.*;
import com.mobileanalysis.orchestrator.repository.*;
import com.mobileanalysis.orchestrator.util.EngineTopicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Core orchestration service for processing file events and creating analyses.
 * Implements transactional outbox pattern:
 * 1. Create analysis and tasks in database
 * 2. Write task events to outbox table
 * 3. Both succeed or both rollback (single transaction)
 * <p>
 * Outbox poller will publish events to Kafka asynchronously.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AnalysisOrchestrator {
    
    private final AnalysisRepository analysisRepository;
    private final AnalysisTaskRepository analysisTaskRepository;
    private final ConfigurationService configurationService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Process a file event and create analysis workflow.
     * This method is transactional - all database operations succeed or fail together.
     * 
     * @param fileEvent File event from Kafka
     */
    @Transactional
    public void processFileEvent(FileEvent fileEvent) {
        UUID analysisId = null;
        
        try {
            // Load configuration for file type
            AnalysisConfigEntity config = configurationService.getAnalysisConfig(fileEvent.getFileType());
            log.debug("Loaded configuration '{}' for file type {}", config.getName(), fileEvent.getFileType());
            
            // Step 1: Create analysis entity
            AnalysisEntity analysis = AnalysisEntity.builder()
                .filePath(fileEvent.getFilePath())
                .fileType(fileEvent.getFileType())
                .analysisConfigId(config.getId())
                .status(AnalysisStatus.PENDING)
                .startedAt(Instant.now())
                .build();
            
            analysis = analysisRepository.save(analysis);
            analysisId = analysis.getId();
            
            // Set MDC for correlation ID logging
            MDC.put("correlationId", analysisId.toString());
            MDC.put("analysisId", analysisId.toString());
            
            log.info("Created analysis: {} for file: {}, fileType: {}", 
                analysisId, fileEvent.getFilePath(), fileEvent.getFileType());
            
            // Step 2: Create all analysis tasks from configuration
            Map<Long, AnalysisTaskEntity> configIdToTaskMap = new HashMap<>();
            List<AnalysisTaskEntity> allTasks = new ArrayList<>();
            
            for (TaskConfigEntity taskConfig : config.getTasks()) {
                AnalysisTaskEntity task = AnalysisTaskEntity.builder()
                    .analysisId(analysisId)
                    .taskConfigId(taskConfig.getId())
                    .engineType(taskConfig.getEngineType())
                    .status(TaskStatus.PENDING)
                    .attempts(0)
                    .idempotencyKey(UUID.randomUUID())
                    .build();
                
                task = analysisTaskRepository.save(task);
                configIdToTaskMap.put(taskConfig.getId(), task);
                allTasks.add(task);
                
                log.debug("Created task: {} for engine: {}, order: {}", 
                    task.getId(), task.getEngineType(), taskConfig.getTaskOrder());
            }
            
            // Step 3: Set dependencies (map from config-level to runtime task-level)
            for (TaskConfigEntity taskConfig : config.getTasks()) {
                if (taskConfig.getDependsOnTaskConfigId() != null) {
                    AnalysisTaskEntity task = configIdToTaskMap.get(taskConfig.getId());
                    AnalysisTaskEntity dependentTask = configIdToTaskMap.get(taskConfig.getDependsOnTaskConfigId());
                    
                    if (dependentTask != null) {
                        task.setDependsOnTaskId(dependentTask.getId());
                        analysisTaskRepository.save(task);
                        
                        log.debug("Set dependency: task {} depends on task {}", 
                            task.getId(), dependentTask.getId());
                    }
                }
            }
            
            // Step 4: Update analysis status to RUNNING
            analysis.setStatus(AnalysisStatus.RUNNING);
            analysisRepository.save(analysis);
            log.info("Analysis {} status updated to RUNNING", analysisId);
            
            // Step 5: Identify ready-to-run tasks and write to outbox
            List<AnalysisTaskEntity> readyTasks = allTasks.stream()
                .filter(task -> task.getDependsOnTaskId() == null)
                .toList();
            
            log.info("Found {} ready-to-run tasks (no dependencies) for analysis {}", 
                readyTasks.size(), analysisId);
            
            for (AnalysisTaskEntity task : readyTasks) {
                // Mark task as dispatched, set started timestamp, and increment attempts
                task.setStatus(TaskStatus.DISPATCHED);
                task.setStartedAt(Instant.now()); // Set when task execution begins
                task.setAttempts(1); // First attempt
                analysisTaskRepository.save(task);
                
                // Create task event with attempt number
                TaskEvent taskEvent = TaskEvent.builder()
                    .eventId(UUID.randomUUID())
                    .taskId(task.getId())
                    .analysisId(analysisId)
                    .engineType(task.getEngineType())
                    .filePath(fileEvent.getFilePath())
                    .dependentTaskOutputPath(null) // No dependency
                    .idempotencyKey(task.getIdempotencyKey())
                    .timeoutSeconds(300) // Default, will be from config in Phase 2
                    .attempts(1) // First attempt
                    .timestamp(Instant.now())
                    .build();
                
                // Write to outbox (transactional)
                writeToOutbox(analysisId, task, taskEvent);
                
                log.info("Task {} dispatched to outbox for engine: {}", 
                    task.getId(), task.getEngineType());
            }
            
            log.info("Analysis creation complete: {}, {} tasks created, {} ready to run", 
                analysisId, allTasks.size(), readyTasks.size());
            
        } catch (Exception e) {
            if (analysisId != null) {
                log.error("Failed to process file event for analysis: {}", analysisId, e);
            } else {
                log.error("Failed to process file event: {}", fileEvent.getEventId(), e);
            }
            throw new RuntimeException("Analysis creation failed", e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Write task event to outbox table (transactional outbox pattern).
     * Outbox poller will read these and publish to Kafka.
     */
    private void writeToOutbox(UUID analysisId, AnalysisTaskEntity task, TaskEvent taskEvent) {
        try {
            String topic = EngineTopicMapper.getTopicForEngineType(task.getEngineType());
            String payload = objectMapper.writeValueAsString(taskEvent);
            
            OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .aggregateType("Analysis")
                .aggregateId(analysisId.toString())
                .eventType("TASK_READY")
                .topic(topic)
                .partitionKey(analysisId.toString()) // Partition by analysisId for ordering
                .payload(payload)
                .processed(false)
                .build();
            
            outboxRepository.save(outboxEvent);
            
            log.debug("Outbox event created: topic={}, taskId={}", topic, task.getId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize task event for outbox: taskId={}", task.getId(), e);
            throw new RuntimeException("Failed to write to outbox", e);
        }
    }
}
