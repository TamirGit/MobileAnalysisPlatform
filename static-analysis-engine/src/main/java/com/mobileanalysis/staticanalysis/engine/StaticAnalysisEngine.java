package com.mobileanalysis.staticanalysis.engine;

import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.engine.AbstractAnalysisEngine;
import com.mobileanalysis.engine.service.HeartbeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.UUID;

/**
 * Static analysis engine for APK/IPA files.
 * Implements the abstract engine template with static analysis logic.
 */
@Component
@Slf4j
public class StaticAnalysisEngine extends AbstractAnalysisEngine {
    
    @Value("${app.storage.base-path:/storage}")
    private String storageBasePath;
    
    public StaticAnalysisEngine(HeartbeatService heartbeatService, 
                                KafkaTemplate<String, Object> kafkaTemplate) {
        super(heartbeatService, kafkaTemplate);
    }
    
    @Override
    protected String getEngineType() {
        return "STATIC_ANALYSIS";
    }
    
    @Override
    protected void validateTask(TaskEvent event) {
        super.validateTask(event);
        
        // Additional static analysis specific validations
        String filePath = event.getFilePath();
        if (!filePath.endsWith(".apk") && !filePath.endsWith(".ipa")) {
            throw new IllegalArgumentException(
                "Static analysis only supports APK and IPA files: " + filePath);
        }
        
        log.debug("Static analysis validation passed for: {}", filePath);
    }
    
    @Override
    protected String processTask(TaskEvent event) throws Exception {
        log.info("Starting static analysis for file: {}", event.getFilePath());
        
        // TODO: Implement actual static analysis logic
        // For Phase 2, this is a placeholder that simulates processing
        
        String filePath = event.getFilePath();
        Path inputFile = Paths.get(filePath);
        
        // Verify input file exists
        if (!Files.exists(inputFile)) {
            throw new IllegalArgumentException("Input file not found: " + filePath);
        }
        
        // Simulate static analysis processing (replace with real analysis)
        log.info("Analyzing file: {} (size: {} bytes)", 
            filePath, Files.size(inputFile));
        
        // Simulate some processing time
        Thread.sleep(2000);
        
        // Generate output path for analysis results
        String outputPath = generateOutputPath(event);
        
        // Write analysis results to output file
        writeAnalysisResults(outputPath, event, inputFile);
        
        log.info("Static analysis completed. Results saved to: {}", outputPath);
        
        return outputPath;
    }
    
    /**
     * Generate output path for analysis results using configured storage base path.
     * Format: {storageBasePath}/analysis-results/{analysisId}/static-analysis-{uuid}.json
     * 
     * @param event Task event containing analysis ID
     * @return Absolute path where results will be stored
     */
    private String generateOutputPath(TaskEvent event) {
        return String.format("%s/analysis-results/%s/static-analysis-%s.json",
            storageBasePath,
            event.getAnalysisId(),
            UUID.randomUUID());
    }
    
    /**
     * Write analysis results to output file.
     * Creates parent directories if they don't exist.
     * 
     * @param outputPath Path where results will be written
     * @param event Task event
     * @param inputFile Input file that was analyzed
     * @throws IOException if file operations fail
     */
    private void writeAnalysisResults(String outputPath, TaskEvent event, Path inputFile) 
            throws IOException {
        Path outputFile = Paths.get(outputPath);
        
        // Create parent directories if they don't exist
        Files.createDirectories(outputFile.getParent());
        
        // TODO: Write actual analysis results JSON
        // For now, write placeholder results
        String results = String.format(
            "Static analysis completed at %s\n" +
            "Input: %s\n" +
            "Analysis ID: %s\n" +
            "Task ID: %s\n" +
            "File size: %d bytes\n",
            Instant.now(),
            event.getFilePath(),
            event.getAnalysisId(),
            event.getTaskId(),
            Files.size(inputFile)
        );
        
        Files.writeString(outputFile, results);
        
        log.debug("Analysis results written to: {} ({} bytes)", 
            outputPath, Files.size(outputFile));
    }
}
