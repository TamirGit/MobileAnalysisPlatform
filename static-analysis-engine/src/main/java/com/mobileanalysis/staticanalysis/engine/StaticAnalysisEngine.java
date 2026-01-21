package com.mobileanalysis.staticanalysis.engine;

import com.mobileanalysis.common.events.TaskEvent;
import com.mobileanalysis.engine.AbstractAnalysisEngine;
import com.mobileanalysis.engine.service.HeartbeatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

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
        
        // TODO: Write actual analysis results to outputPath
        // For now, just create a placeholder file
        Path outputFile = Paths.get(outputPath);
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, 
            String.format("Static analysis completed at %s\nInput: %s\n", 
                Instant.now(), filePath));
        
        log.info("Static analysis completed. Results saved to: {}", outputPath);
        
        return outputPath;
    }
    
    /**
     * Generate output path for analysis results.
     */
    private String generateOutputPath(TaskEvent event) {
        return String.format("/tmp/analysis-results/%s/static-analysis-%s.json",
            event.getAnalysisId(),
            UUID.randomUUID());
    }
}
