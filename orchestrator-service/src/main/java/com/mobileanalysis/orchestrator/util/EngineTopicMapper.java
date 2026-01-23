package com.mobileanalysis.orchestrator.util;

import com.mobileanalysis.common.domain.EngineType;

/**
 * Utility class to map engine types to their corresponding Kafka topics.
 * Centralizes topic routing logic to avoid duplication across services.
 */
public final class EngineTopicMapper {
    
    private EngineTopicMapper() {
        // Prevent instantiation
    }
    
    /**
     * Get the Kafka topic name for a given engine type.
     * 
     * @param engineType Engine type enum
     * @return Kafka topic name
     * @throws IllegalArgumentException if engine type is unknown
     */
    public static String getTopicForEngineType(EngineType engineType) {
        return getTopicForEngineType(engineType.name());
    }
    
    /**
     * Get the Kafka topic name for a given engine type string.
     * 
     * @param engineType Engine type as string
     * @return Kafka topic name
     * @throws IllegalArgumentException if engine type is unknown
     */
    public static String getTopicForEngineType(String engineType) {
        return switch (engineType) {
            case "STATIC_ANALYSIS" -> "static-analysis-tasks";
            case "DYNAMIC_ANALYSIS" -> "dynamic-analysis-tasks";
            case "DECOMPILER" -> "decompiler-tasks";
            case "SIGNATURE_CHECK" -> "signature-check-tasks";
            default -> throw new IllegalArgumentException("Unknown engine type: " + engineType);
        };
    }
}
