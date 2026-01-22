package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.EngineType;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
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
import org.mockito.Spy;
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

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TaskRetryService taskRetryService;

    private AnalysisTaskEntity failedTask;
    private TaskConfigEntity taskConfig;
    private AnalysisEntity analysis;

    @BeforeEach
    void setUp() {
        failedTask = new AnalysisTaskEntity();
        failedTask.setId(1L);
        failedTask.setAnalysisId(UUID.randomUUID());
        failedTask.setTaskConfigId(1L);
        failedTask.setStatus(TaskStatus.FAILED);
        failedTask.setEngineType(EngineType.STATIC_ANALYSIS);
        failedTask.setAttempts(1);
        failedTask.setIdempotencyKey(UUID.randomUUID());

        taskConfig = new TaskConfigEntity();
        taskConfig.setId(1L);
        taskConfig.setMaxRetries(3);
        taskConfig.setTimeoutSeconds(300);

        analysis = new AnalysisEntity();
        analysis.setId(failedTask.getAnalysisId());
        analysis.setFilePath("/path/to/file.apk");
    }

    @Test
    void retryIfPossible_withinBudget_createsRetry() {
        // Given
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(failedTask));
        when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(failedTask.getAnalysisId())).thenReturn(Optional.of(analysis));

        // When
        taskRetryService.retryIfPossible(failedTask, "Test failure");

        // Then
        verify(outboxRepository).save(any());
        verify(analysisTaskRepository).save(argThat(task ->
            task.getAttempts() == 2 &&
            task.getStatus() == TaskStatus.PENDING
        ));
    }

    @Test
    void retryIfPossible_budgetExhausted_doesNotRetry() {
        // Given
        failedTask.setAttempts(3); // Already at max
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(failedTask));
        when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));

        // When
        taskRetryService.retryIfPossible(failedTask, "Test failure");

        // Then
        verify(outboxRepository, never()).save(any());
        verify(analysisTaskRepository, never()).save(any());
    }

    @Test
    void retryIfPossible_firstFailure_incrementsToAttempt2() {
        // Given
        failedTask.setAttempts(1);
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(failedTask));
        when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(failedTask.getAnalysisId())).thenReturn(Optional.of(analysis));

        // When
        taskRetryService.retryIfPossible(failedTask, "First failure");

        // Then
        verify(analysisTaskRepository).save(argThat(task -> {
            assertThat(task.getAttempts()).isEqualTo(2);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.PENDING);
            return true;
        }));
    }
}
