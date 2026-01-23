package com.mobileanalysis.common.events;

import com.mobileanalysis.common.domain.EngineType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskEvent {
    @JsonProperty("eventId")
    private UUID eventId;
    
    @JsonProperty("taskId")
    private Long taskId;
    
    @JsonProperty("analysisId")
    private UUID analysisId;
    
    @JsonProperty("engineType")
    private EngineType engineType;
    
    @JsonProperty("filePath")
    private String filePath;
    
    @JsonProperty("dependentTaskOutputPath")
    private String dependentTaskOutputPath;
    
    @JsonProperty("idempotencyKey")
    private UUID idempotencyKey;
    
    @JsonProperty("timeoutSeconds")
    private Integer timeoutSeconds;
    
    /**
     * Current attempt number for this task (1-indexed).
     * First execution = 1, first retry = 2, etc.
     * Never null - defaults to 1 if not specified.
     */
    @JsonProperty("attempts")
    @NonNull
    @Builder.Default
    private Integer attempts = 1;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
}
