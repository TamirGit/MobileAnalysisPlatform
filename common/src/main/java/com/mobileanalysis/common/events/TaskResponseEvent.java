package com.mobileanalysis.common.events;

import com.mobileanalysis.common.domain.TaskStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponseEvent {
    @JsonProperty("eventId")
    private UUID eventId;
    
    @JsonProperty("taskId")
    private Long taskId;
    
    @JsonProperty("analysisId")
    private UUID analysisId;
    
    @JsonProperty("status")
    private TaskStatus status;
    
    @JsonProperty("outputPath")
    private String outputPath;
    
    @JsonProperty("errorMessage")
    private String errorMessage;
    
    @JsonProperty("attempts")
    private Integer attempts;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
}
