package com.mobileanalysis.staticanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Static Analysis Engine Application.
 * Performs static analysis on APK/IPA files.
 */
@SpringBootApplication(scanBasePackages = {
    "com.mobileanalysis.staticanalysis",
    "com.mobileanalysis.engine"
})
public class StaticAnalysisApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(StaticAnalysisApplication.class, args);
    }
}
