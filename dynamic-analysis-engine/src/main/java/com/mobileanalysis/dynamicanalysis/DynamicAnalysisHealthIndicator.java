package com.mobileanalysis.dynamicanalysis;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Custom health indicator for Dynamic Analysis Engine.
 * 
 * Reports engine status and active task information for monitoring and load balancing.
 */
@Component
public class DynamicAnalysisHealthIndicator implements HealthIndicator {

    private final AtomicInteger activeTasksCount = new AtomicInteger(0);
    private volatile boolean isProcessing = false;

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        
        builder.withDetail("engineType", "DYNAMIC_ANALYSIS")
               .withDetail("isProcessing", isProcessing)
               .withDetail("activeTasksCount", activeTasksCount.get())
               .withDetail("maxTimeout", "1800s");
        
        return builder.build();
    }

    public void incrementActiveTasks() {
        activeTasksCount.incrementAndGet();
        isProcessing = true;
    }

    public void decrementActiveTasks() {
        int count = activeTasksCount.decrementAndGet();
        if (count <= 0) {
            isProcessing = false;
            activeTasksCount.set(0);
        }
    }
}
