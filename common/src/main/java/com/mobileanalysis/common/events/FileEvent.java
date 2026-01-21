package com.mobileanalysis.common.events;

import com.mobileanalysis.common.domain.FileType;
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
public class FileEvent {
    @JsonProperty("eventId")
    private UUID eventId;
    
    @JsonProperty("filePath")
    private String filePath;
    
    @JsonProperty("fileType")
    private FileType fileType;
    
    @JsonProperty("timestamp")
    private Instant timestamp;
}
