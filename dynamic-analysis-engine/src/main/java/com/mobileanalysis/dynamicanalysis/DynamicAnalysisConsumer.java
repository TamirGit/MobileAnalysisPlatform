package com.mobileanalysis.dynamicanalysis;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.enginecommon.AbstractAnalysisEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for dynamic analysis tasks.
 * 
 * Extends AbstractAnalysisEngine which provides:
 * - Heartbeat management (every 30 seconds)
 * - Timeout handling (1800 seconds = 30 minutes)
 * - Error recovery and retry logic
 * - Kafka acknowledgment handling
 * 
 * This consumer only implements the core analysis logic in processTask().
 */
@Service
@Slf4j
public class DynamicAnalysisConsumer extends AbstractAnalysisEngine {

    private final DynamicAnalyzer analyzer;
    
    @Value("${app.storage.base-path}")
    private String storagePath;

    public DynamicAnalysisConsumer(DynamicAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * Kafka listener for dynamic analysis tasks.
     * Delegates to AbstractAnalysisEngine for common processing logic.
     */
    @Override
    @KafkaListener(
        topics = "${app.kafka.topics.dynamic-analysis}",
        groupId = "${app.kafka.consumer-group}",
        containerFactory = "taskEventConsumerFactory"
    )
    public void handleTaskEvent(TaskEvent event, Acknowledgment acknowledgment) {
        super.handleTaskEvent(event, acknowledgment);
    }

    /**
     * Core analysis logic - called by AbstractAnalysisEngine.
     * 
     * @param event Task event containing file path and analysis details
     * @return Task response event with results or error
     */
    @Override
    protected TaskResponseEvent processTask(TaskEvent event) {
        Long taskId = event.getTaskId();
        UUID analysisId = event.getAnalysisId();
        String filePath = event.getFilePath();
        
        log.info("Processing dynamic analysis task {} for analysis {}", taskId, analysisId);
        
        try {
            // Perform dynamic analysis
            Map<String, Object> results = analyzer.analyze(filePath);
            
            // Write results to filesystem
            String outputPath = writeResults(analysisId, taskId, results);
            
            log.info("Dynamic analysis task {} completed successfully", taskId);
            
            return TaskResponseEvent.builder()
                .taskId(taskId)
                .analysisId(analysisId)
                .status(TaskStatus.COMPLETED)
                .outputPath(outputPath)
                .attempts(1)
                .build();
                
        } catch (Exception e) {
            log.error("Dynamic analysis task {} failed", taskId, e);
            throw new RuntimeException("Dynamic analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Write analysis results to filesystem.
     */
    private String writeResults(UUID analysisId, Long taskId, Map<String, Object> results) throws IOException {
        Path analysisDir = Paths.get(storagePath, "analysis", analysisId.toString());
        Files.createDirectories(analysisDir);
        
        Path outputFile = analysisDir.resolve("task_" + taskId + "_output.json");
        String jsonResults = convertToJson(results);
        Files.writeString(outputFile, jsonResults);
        
        log.debug("Results written to: {}", outputFile);
        return outputFile.toString();
    }

    private String convertToJson(Map<String, Object> results) {
        // Simple JSON conversion (in production, use Jackson ObjectMapper)
        StringBuilder json = new StringBuilder("{\n");
        results.forEach((key, value) -> {
            json.append("  \"").append(key).append("\": ");
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof String[]) {
                json.append("[");
                String[] arr = (String[]) value;
                for (int i = 0; i < arr.length; i++) {
                    json.append("\"").append(arr[i]).append("\"");
                    if (i < arr.length - 1) json.append(", ");
                }
                json.append("]");
            } else {
                json.append(value);
            }
            json.append(",\n");
        });
        json.setLength(json.length() - 2); // Remove last comma
        json.append("\n}");
        return json.toString();
    }
}
