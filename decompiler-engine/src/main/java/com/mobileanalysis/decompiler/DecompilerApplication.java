package com.mobileanalysis.decompiler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Decompiler Engine - Spring Boot Application
 * 
 * Performs decompilation of mobile applications including:
 * - DEX to Java conversion (Android)
 * - IPA binary analysis (iOS)
 * - Source code reconstruction
 * - Code structure analysis
 * 
 * This is a long-running engine with 15-minute timeout.
 */
@SpringBootApplication(scanBasePackages = {
    "com.mobileanalysis.decompiler",
    "com.mobileanalysis.common",
    "com.mobileanalysis.enginecommon"
})
@EnableKafka
public class DecompilerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecompilerApplication.class, args);
    }
}
