package com.mobileanalysis.engine;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.engine.service.HeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;

/**
 * Abstract base class for all analysis engines.
 * Implements the Template Method pattern:
 * 1. Validate task event
 * 2. Start heartbeat tracking
 * 3. Process the analysis task (implemented by subclass)
 * 4. Stop heartbeat tracking
 * 5. Send response event to orchestrator
 * <p>
 * Subclasses must implement:
 * - validateTask(TaskEvent): Validate engine-specific requirements
 * - processTask(TaskEvent): Perform the actual analysis work
 * - getEngineType(): Return the engine type name
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractAnalysisEngine {
    
    protected final HeartbeatService heartbeatService;
    protected final KafkaTemplate<String, Object> kafkaTemplate;
    
    /**
     * Main entry point for processing a task event.
     * This method orchestrates the entire task lifecycle.
     * 
     * @param event Task event from Kafka
     * @param acknowledgment Kafka acknowledgment (manual commit)
     */
    public void handleTaskEvent(TaskEvent event, Acknowledgment acknowledgment) {
        // Set MDC for logging context
        MDC.put("analysisId", event.getAnalysisId().toString());
        MDC.put("taskId", event.getTaskId().toString());
        MDC.put("engineType", getEngineType());
        
        try {
            log.info("Received task event: taskId={}, analysisId={}, filePath={}", 
                event.getTaskId(), event.getAnalysisId(), event.getFilePath());
            
            // Step 1: Validate task event
            validateTask(event);
            
            // Step 2: Start heartbeat tracking
            heartbeatService.startHeartbeat(
                event.getTaskId(), 
                event.getAnalysisId(), 
                getEngineType()
            );
            
            try {
                // Step 3: Process the task (implemented by subclass)
                String outputPath = processTask(event);
                
                // Step 4: Send success response
                sendSuccessResponse(event, outputPath);
                
                log.info("Task completed successfully: taskId={}, outputPath={}", 
                    event.getTaskId(), outputPath);
                
            } catch (Exception e) {
                // Step 4b: Send failure response
                log.error("Task processing failed: taskId={}, error={}", 
                    event.getTaskId(), e.getMessage(), e);
                sendFailureResponse(event, e.getMessage());
            } finally {
                // Step 5: Stop heartbeat tracking
                heartbeatService.stopHeartbeat();
            }
            
            // Step 6: Acknowledge Kafka message (commit offset)
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.debug("Task event acknowledged: taskId={}", event.getTaskId());
            }
            
        } catch (Exception e) {
            log.error("Fatal error handling task event: taskId={}, error={}", 
                event.getTaskId(), e.getMessage(), e);
            // Don't acknowledge - let Kafka redeliver
            throw new RuntimeException("Task event processing failed", e);
        } finally {
            MDC.clear();
        }
    }
    
    /**
     * Validate the task event before processing.
     * Subclasses can override to add engine-specific validations.
     * 
     * @param event Task event to validate
     * @throws IllegalArgumentException if validation fails
     */
    protected void validateTask(TaskEvent event) {
        if (event.getTaskId() == null) {
            throw new IllegalArgumentException("Task ID is required");
        }
        if (event.getAnalysisId() == null) {
            throw new IllegalArgumentException("Analysis ID is required");
        }
        if (event.getFilePath() == null || event.getFilePath().isBlank()) {
            throw new IllegalArgumentException("File path is required");
        }
        log.debug("Task validation passed: taskId={}", event.getTaskId());
    }
    
    /**
     * Process the analysis task.
     * This is the core analysis logic implemented by each engine.
     * 
     * @param event Task event containing task details
     * @return Output path where analysis results are stored
     * @throws Exception if processing fails
     */
    protected abstract String processTask(TaskEvent event) throws Exception;
    
    /**
     * Get the engine type name.
     * Used for logging and event metadata.
     * 
     * @return Engine type (e.g., "STATIC_ANALYSIS", "DYNAMIC_ANALYSIS")
     */
    protected abstract String getEngineType();
    
    /**
     * Send success response to orchestrator.
     * 
     * @param event Original task event
     * @param outputPath Path where analysis results are stored
     */
    private void sendSuccessResponse(TaskEvent event, String outputPath) {
        TaskResponseEvent response = TaskResponseEvent.builder()
            .taskId(event.getTaskId())
            .analysisId(event.getAnalysisId())
            .status(TaskStatus.COMPLETED)
            .outputPath(outputPath)
            .errorMessage(null)
            .attempts(event.getAttempts() != null ? event.getAttempts() : 1)
            .timestamp(Instant.now())
            .build();
        
        sendResponse(response, event);
    }
    
    /**
     * Send failure response to orchestrator.
     * 
     * @param event Original task event
     * @param errorMessage Error message describing the failure
     */
    private void sendFailureResponse(TaskEvent event, String errorMessage) {
        TaskResponseEvent response = TaskResponseEvent.builder()
            .taskId(event.getTaskId())
            .analysisId(event.getAnalysisId())
            .status(TaskStatus.FAILED)
            .outputPath(null)
            .errorMessage(errorMessage)
            .attempts(event.getAttempts() != null ? event.getAttempts() : 1)
            .timestamp(Instant.now())
            .build();
        
        sendResponse(response, event);
    }
    
    /**
     * Send response event to orchestrator via Kafka.
     * 
     * @param response Task response event
     * @param originalEvent Original task event (for partition key)
     */
    private void sendResponse(TaskResponseEvent response, TaskEvent originalEvent) {
        String responseTopic = "orchestrator-responses";
        String partitionKey = originalEvent.getAnalysisId().toString();
        
        try {
            kafkaTemplate.send(responseTopic, partitionKey, response);
            log.info("Sent task response: taskId={}, status={}", 
                response.getTaskId(), response.getStatus());
        } catch (Exception e) {
            log.error("Failed to send task response: taskId={}, status={}, error={}", 
                response.getTaskId(), response.getStatus(), e.getMessage(), e);
            throw new RuntimeException("Failed to send task response", e);
        }
    }
}
