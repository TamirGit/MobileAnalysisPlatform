package com.mobileanalysis.decompiler;

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
 * Kafka consumer for decompilation tasks.
 * 
 * Extends AbstractAnalysisEngine which provides:
 * - Heartbeat management (every 30 seconds)
 * - Timeout handling (900 seconds = 15 minutes)
 * - Error recovery and retry logic
 * - Kafka acknowledgment handling
 */
@Service
@Slf4j
public class DecompilerConsumer extends AbstractAnalysisEngine {

    private final Decompiler decompiler;
    
    @Value("${app.storage.base-path}")
    private String storagePath;

    public DecompilerConsumer(Decompiler decompiler) {
        this.decompiler = decompiler;
    }

    @Override
    @KafkaListener(
        topics = "${app.kafka.topics.decompiler}",
        groupId = "${app.kafka.consumer-group}",
        containerFactory = "taskEventConsumerFactory"
    )
    public void handleTaskEvent(TaskEvent event, Acknowledgment acknowledgment) {
        super.handleTaskEvent(event, acknowledgment);
    }

    @Override
    protected TaskResponseEvent processTask(TaskEvent event) {
        Long taskId = event.getTaskId();
        UUID analysisId = event.getAnalysisId();
        String filePath = event.getFilePath();
        
        log.info("Processing decompilation task {} for analysis {}", taskId, analysisId);
        
        try {
            // Perform decompilation
            Map<String, Object> results = decompiler.decompile(filePath);
            
            // Write results to filesystem
            String outputPath = writeResults(analysisId, taskId, results);
            
            log.info("Decompilation task {} completed successfully", taskId);
            
            return TaskResponseEvent.builder()
                .taskId(taskId)
                .analysisId(analysisId)
                .status(TaskStatus.COMPLETED)
                .outputPath(outputPath)
                .attempts(1)
                .build();
                
        } catch (Exception e) {
            log.error("Decompilation task {} failed", taskId, e);
            throw new RuntimeException("Decompilation failed: " + e.getMessage(), e);
        }
    }

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
        StringBuilder json = new StringBuilder("{\n");
        results.forEach((key, value) -> {
            json.append("  \"").append(key).append("\": ");
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Boolean) {
                json.append(value);
            } else if (value instanceof Map) {
                json.append(convertToJson((Map<String, Object>) value));
            } else {
                json.append(value);
            }
            json.append(",\n");
        });
        json.setLength(json.length() - 2);
        json.append("\n}");
        return json.toString();
    }
}
