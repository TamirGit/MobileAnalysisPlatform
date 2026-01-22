package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobileanalysis.common.domain.EngineType;
import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.orchestrator.domain.AnalysisEntity;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.domain.TaskConfigEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisRepository;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.TaskConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DependencyResolverTest {

    @Mock
    private AnalysisTaskRepository analysisTaskRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private TaskConfigRepository taskConfigRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DependencyResolver dependencyResolver;

    private AnalysisTaskEntity completedTask;
    private AnalysisTaskEntity dependentTask;
    private TaskConfigEntity taskConfig;
    private AnalysisEntity analysis;

    @BeforeEach
    void setUp() {
        UUID analysisId = UUID.randomUUID();

        completedTask = new AnalysisTaskEntity();
        completedTask.setId(1L);
        completedTask.setAnalysisId(analysisId);
        completedTask.setStatus(TaskStatus.COMPLETED);
        completedTask.setOutputPath("/output/task1.json");

        dependentTask = new AnalysisTaskEntity();
        dependentTask.setId(2L);
        dependentTask.setAnalysisId(analysisId);
        dependentTask.setTaskConfigId(1L);
        dependentTask.setStatus(TaskStatus.PENDING);
        dependentTask.setEngineType(EngineType.DYNAMIC_ANALYSIS);
        dependentTask.setDependsOnTaskId(1L);
        dependentTask.setIdempotencyKey(UUID.randomUUID());

        taskConfig = new TaskConfigEntity();
        taskConfig.setId(1L);
        taskConfig.setTimeoutSeconds(300);

        analysis = new AnalysisEntity();
        analysis.setId(analysisId);
        analysis.setFilePath("/path/to/file.apk");
    }

    @Test
    void resolveAndDispatch_noDependents_returnsEmpty() {
        // Given
        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of());

        // When
        List<OutboxEventEntity> events = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(events).isEmpty();
        verify(analysisTaskRepository, never()).save(any());
    }

    @Test
    void resolveAndDispatch_dependenciesMet_dispatchesTask() {
        // Given
        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(dependentTask));
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(completedTask));
        when(taskConfigRepository.findById(1L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(dependentTask.getAnalysisId())).thenReturn(Optional.of(analysis));

        // When
        List<OutboxEventEntity> events = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getTopic()).isEqualTo("dynamic-analysis-tasks");

        verify(analysisTaskRepository).save(argThat(task ->
            task.getId().equals(2L) &&
            task.getStatus() == TaskStatus.DISPATCHED
        ));
    }

    @Test
    void resolveAndDispatch_dependenciesNotMet_doesNotDispatch() {
        // Given
        AnalysisTaskEntity incompleteParent = new AnalysisTaskEntity();
        incompleteParent.setId(1L);
        incompleteParent.setStatus(TaskStatus.RUNNING); // Not COMPLETED

        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(dependentTask));
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(incompleteParent));

        // When
        List<OutboxEventEntity> events = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(events).isEmpty();
        verify(analysisTaskRepository, never()).save(any());
    }
}
