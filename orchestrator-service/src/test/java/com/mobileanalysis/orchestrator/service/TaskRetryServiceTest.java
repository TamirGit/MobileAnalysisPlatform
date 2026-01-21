package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisRepository;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.OutboxRepository;
import com.mobileanalysis.orchestrator.repository.TaskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskRetryServiceTest {

    @Mock
    private AnalysisTaskRepository analysisTaskRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private TaskRetryService retryService;

    private UUID analysisId;
    private AnalysisTaskEntity task;
    private TaskConfigEntity taskConfig;
    private AnalysisEntity analysis;

    @BeforeEach
    void setUp() throws Exception {
        analysisId = UUID.randomUUID();

        analysis = new AnalysisEntity();
        analysis.setId(analysisId);
        analysis.setFilePath("/path/to/file.apk");

        task = new AnalysisTaskEntity();
        task.setId(1L);
        task.setAnalysisId(analysisId);
        task.setTaskConfigId(100L);
        task.setStatus(TaskStatus.FAILED);
        task.setAttempts(1);
        task.setEngineType("STATIC_ANALYSIS");
        task.setIdempotencyKey(UUID.randomUUID());

        taskConfig = new TaskConfigEntity();
        taskConfig.setId(100L);
        taskConfig.setMaxRetries(3);
        taskConfig.setTimeoutSeconds(300);

        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
    }

    @Test
    void retryIfPossible_withinBudget_schedulesRetry() {
        // Given
        when(taskConfigRepository.findById(100L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        // When
        retryService.retryIfPossible(task, "Connection timeout");

        // Then
        verify(analysisTaskRepository).save(argThat(t ->
            t.getStatus() == TaskStatus.PENDING &&
            t.getAttempts() == 2 &&
            t.getErrorMessage().equals("Connection timeout") &&
            t.getCompletedAt() == null
        ));

        verify(outboxRepository).save(argThat(event ->
            event.getEventType().equals("TASK_RETRY") &&
            event.getTopic().equals("static-analysis-tasks")
        ));
    }

    @Test
    void retryIfPossible_budgetExhausted_doesNotRetry() {
        // Given: Task already at max retries
        task.setAttempts(3);
        when(taskConfigRepository.findById(100L)).thenReturn(Optional.of(taskConfig));

        // When
        retryService.retryIfPossible(task, "Final failure");

        // Then
        verify(outboxRepository, never()).save(any());
        verify(analysisTaskRepository, never()).save(any());
    }

    @Test
    void retryIfPossible_firstAttempt_incrementsTo2() {
        // Given: First attempt (attempts = 1)
        task.setAttempts(1);
        when(taskConfigRepository.findById(100L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        // When
        retryService.retryIfPossible(task, "First failure");

        // Then
        verify(analysisTaskRepository).save(argThat(t -> t.getAttempts() == 2));
    }
}
