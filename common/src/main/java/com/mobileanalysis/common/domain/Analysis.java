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
public class Analysis {
    private UUID id;
    private String filePath;
    private FileType fileType;
    private Long analysisConfigId;
    private AnalysisStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
    private Instant updatedAt;
}
