package com.mobileanalysis.orchestrator.service;

import com.mobileanalysis.common.domain.EngineType;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    @Mock
    private AnalysisTaskRepository analysisTaskRepository;

    @Mock
    private TaskRetryService taskRetryService;

    @InjectMocks
    private HeartbeatMonitor heartbeatMonitor;

    private AnalysisTaskEntity staleTask;

    @BeforeEach
    void setUp() {
        staleTask = new AnalysisTaskEntity();
        staleTask.setId(1L);
        staleTask.setAnalysisId(UUID.randomUUID());
        staleTask.setStatus(TaskStatus.RUNNING);
        staleTask.setEngineType(EngineType.STATIC_ANALYSIS);
        staleTask.setLastHeartbeatAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        staleTask.setAttempts(1);
    }

    @Test
    void checkForStaleTasks_staleTasks_marksFailedAndRetries() {
        // Given
        when(analysisTaskRepository.findStaleRunningTasks(any(Instant.class)))
            .thenReturn(List.of(staleTask));

        // When
        heartbeatMonitor.checkForStaleTasks();

        // Then
        verify(analysisTaskRepository).save(argThat(task ->
            task.getId().equals(1L) &&
            task.getStatus() == TaskStatus.FAILED &&
            task.getErrorMessage().contains("Task timeout") &&
            task.getCompletedAt() != null
        ));

        verify(taskRetryService).retryIfPossible(
            argThat((AnalysisTaskEntity task) -> task.getId().equals(1L)),
            argThat(reason -> reason.contains("no heartbeat"))
        );
    }

    @Test
    void checkForStaleTasks_noStaleTasks_doesNothing() {
        // Given
        when(analysisTaskRepository.findStaleRunningTasks(any(Instant.class)))
            .thenReturn(List.of());

        // When
        heartbeatMonitor.checkForStaleTasks();

        // Then
        verify(analysisTaskRepository, never()).save(any());
        verify(taskRetryService, never()).retryIfPossible(any(AnalysisTaskEntity.class), any());
    }

    @Test
    void checkForStaleTasks_multipleStaleTasks_handlesAll() {
        // Given
        AnalysisTaskEntity staleTask2 = new AnalysisTaskEntity();
        staleTask2.setId(2L);
        staleTask2.setAnalysisId(UUID.randomUUID());
        staleTask2.setStatus(TaskStatus.RUNNING);
        staleTask2.setEngineType(EngineType.STATIC_ANALYSIS);
        staleTask2.setLastHeartbeatAt(Instant.now().minus(3, ChronoUnit.MINUTES));

        when(analysisTaskRepository.findStaleRunningTasks(any(Instant.class)))
            .thenReturn(List.of(staleTask, staleTask2));

        // When
        heartbeatMonitor.checkForStaleTasks();

        // Then
        verify(analysisTaskRepository, times(2)).save(any());
        verify(taskRetryService, times(2)).retryIfPossible(any(AnalysisTaskEntity.class), any());
    }
}
