package com.mobileanalysis.dynamicanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Dynamic Analysis Engine - Spring Boot Application
 * 
 * Performs dynamic behavioral analysis of mobile applications including:
 * - Runtime behavior monitoring
 * - Network traffic analysis
 * - API call tracking
 * - Permission usage detection
 * 
 * This is a long-running engine with 30-minute timeout.
 */
@SpringBootApplication(scanBasePackages = {
    "com.mobileanalysis.dynamicanalysis",
    "com.mobileanalysis.common",
    "com.mobileanalysis.enginecommon"
})
@EnableKafka
public class DynamicAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(DynamicAnalysisApplication.class, args);
    }
}
