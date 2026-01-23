package com.mobileanalysis.orchestrator.service;

import com.mobileanalysis.common.domain.AnalysisStatus;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisRepository;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnalysisCompletionServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private AnalysisTaskRepository analysisTaskRepository;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks
    private AnalysisCompletionService completionService;

    private UUID analysisId;
    private AnalysisEntity analysis;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();

        analysis = new AnalysisEntity();
        analysis.setId(analysisId);
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis.setStartedAt(Instant.now().minusSeconds(100));
    }

    @Test
    void checkAndMarkCompletion_allTasksCompleted_marksAnalysisCompleted() {
        // Given
        AnalysisTaskEntity task1 = createTask(1L, TaskStatus.COMPLETED);
        AnalysisTaskEntity task2 = createTask(2L, TaskStatus.COMPLETED);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(analysisTaskRepository.findByAnalysisId(analysisId)).thenReturn(List.of(task1, task2));
        when(redisTemplate.delete(any(String.class))).thenReturn(true);

        // When
        completionService.checkAndMarkCompletion(analysisId);

        // Then
        verify(analysisRepository).save(argThat(a ->
            a.getStatus() == AnalysisStatus.COMPLETED && a.getCompletedAt() != null
        ));
        verify(redisTemplate).delete("analysis-state:" + analysisId);
    }

    @Test
    void checkAndMarkCompletion_anyTaskFailed_marksAnalysisFailed() {
        // Given
        AnalysisTaskEntity task1 = createTask(1L, TaskStatus.COMPLETED);
        AnalysisTaskEntity task2 = createTask(2L, TaskStatus.FAILED);
        task2.setErrorMessage("Test error");

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(analysisTaskRepository.findByAnalysisId(analysisId)).thenReturn(List.of(task1, task2));
        when(redisTemplate.delete(any(String.class))).thenReturn(true);

        // When
        completionService.checkAndMarkCompletion(analysisId);

        // Then
        verify(analysisRepository).save(argThat(a ->
            a.getStatus() == AnalysisStatus.FAILED && a.getCompletedAt() != null
        ));
    }

    @Test
    void checkAndMarkCompletion_tasksInProgress_doesNotMarkComplete() {
        // Given
        AnalysisTaskEntity task1 = createTask(1L, TaskStatus.COMPLETED);
        AnalysisTaskEntity task2 = createTask(2L, TaskStatus.RUNNING);

        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(analysisTaskRepository.findByAnalysisId(analysisId)).thenReturn(List.of(task1, task2));

        // When
        completionService.checkAndMarkCompletion(analysisId);

        // Then
        verify(analysisRepository, never()).save(any());
        verify(redisTemplate, never()).delete(any(String.class));
    }

    @Test
    void checkAndMarkCompletion_alreadyCompleted_skipsProcessing() {
        // Given
        analysis.setStatus(AnalysisStatus.COMPLETED);
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        // When
        completionService.checkAndMarkCompletion(analysisId);

        // Then
        verify(analysisTaskRepository, never()).findByAnalysisId(any());
        verify(analysisRepository, never()).save(any());
    }

    private AnalysisTaskEntity createTask(Long id, TaskStatus status) {
        AnalysisTaskEntity task = new AnalysisTaskEntity();
        task.setId(id);
        task.setAnalysisId(analysisId);
        task.setStatus(status);
        return task;
    }
}
