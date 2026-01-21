package com.mobileanalysis.common.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisConfig {
    private Long id;
    private FileType fileType;
    private String name;
    private Integer version;
    private Boolean active;
    private List<TaskConfig> tasks;
    private Instant createdAt;
    private Instant updatedAt;
}
