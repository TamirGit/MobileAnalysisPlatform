package com.mobileanalysis.common.domain;

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
public class AnalysisTask {
    private Long id;
    private UUID analysisId;
    private Long taskConfigId;
    private EngineType engineType;
    private TaskStatus status;
    private Long dependsOnTaskId;
    private Integer attempts;
    private String outputPath;
    private String errorMessage;
    private UUID idempotencyKey;
    private Instant startedAt;
    private Instant completedAt;
    private Instant lastHeartbeatAt;
    private Instant createdAt;
    private Instant updatedAt;
}
