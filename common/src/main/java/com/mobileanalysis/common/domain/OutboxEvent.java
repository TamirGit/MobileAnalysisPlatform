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
public class OutboxEvent {
    private Long id;
    private String aggregateType;
    private String aggregateId;
    private String eventType;
    private String topic;
    private String partitionKey;
    private String payload;
    private Boolean processed;
    private Instant createdAt;
    private Instant processedAt;
}
