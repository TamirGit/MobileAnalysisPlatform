package com.mobileanalysis.common.events;

import com.mobileanalysis.common.domain.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Event representing a heartbeat signal from an analysis engine.
 * Sent periodically (every 30 seconds) to indicate a task is still being processed.
 * Used by orchestrator to detect stale/zombie tasks.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatEvent {
    
    /**
     * Unique identifier for this heartbeat event.
     */
    private UUID eventId;
    
    /**
     * ID of the task being processed.
     */
    private Long taskId;
    
    /**
     * ID of the parent analysis.
     */
    private UUID analysisId;
    
    /**
     * Type of engine sending the heartbeat (e.g., STATIC_ANALYSIS).
     */
    private String engineType;
    
    /**
     * Current status of the task (should be RUNNING).
     */
    private TaskStatus status;
    
    /**
     * Timestamp when this heartbeat was sent.
     */
    private Instant timestamp;
}
