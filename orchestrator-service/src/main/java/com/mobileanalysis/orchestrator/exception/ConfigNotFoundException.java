package com.mobileanalysis.orchestrator.exception;

/**
 * Exception thrown when analysis configuration is not found for a file type.
 */
public class ConfigNotFoundException extends RuntimeException {
    
    public ConfigNotFoundException(String message) {
        super(message);
    }
    
    public ConfigNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
