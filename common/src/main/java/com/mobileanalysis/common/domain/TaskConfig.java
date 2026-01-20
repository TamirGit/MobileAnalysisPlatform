package com.mobileanalysis.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskConfig {
    private Long id;
    private Long analysisConfigId;
    private EngineType engineType;
    private Integer taskOrder;
    private Long dependsOnTaskConfigId;
    private Integer timeoutSeconds;
    private Integer maxRetries;
    private Instant createdAt;
}
