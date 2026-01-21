package com.mobileanalysis.orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DependencyResolver dependencyResolver;

    private UUID analysisId;
    private AnalysisTaskEntity completedTask;
    private AnalysisEntity analysis;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();

        analysis = new AnalysisEntity();
        analysis.setId(analysisId);
        analysis.setFilePath("/path/to/file.apk");

        completedTask = new AnalysisTaskEntity();
        completedTask.setId(1L);
        completedTask.setAnalysisId(analysisId);
        completedTask.setStatus(TaskStatus.COMPLETED);
        completedTask.setOutputPath("/path/to/output.json");
    }

    @Test
    void resolveAndDispatch_noDependentTasks_returnsEmptyList() {
        // Given
        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of());

        // When
        List<OutboxEventEntity> result = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(result).isEmpty();
        verify(analysisTaskRepository, never()).save(any());
    }

    @Test
    void resolveAndDispatch_dependentTaskReady_createsOutboxEvent() throws Exception {
        // Given
        AnalysisTaskEntity dependentTask = new AnalysisTaskEntity();
        dependentTask.setId(2L);
        dependentTask.setAnalysisId(analysisId);
        dependentTask.setTaskConfigId(100L);
        dependentTask.setDependsOnTaskId(1L);
        dependentTask.setStatus(TaskStatus.PENDING);
        dependentTask.setEngineType("STATIC_ANALYSIS");
        dependentTask.setIdempotencyKey(UUID.randomUUID());

        TaskConfigEntity taskConfig = new TaskConfigEntity();
        taskConfig.setId(100L);
        taskConfig.setTimeoutSeconds(300);

        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(dependentTask));
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(completedTask));
        when(taskConfigRepository.findById(100L)).thenReturn(Optional.of(taskConfig));
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // When
        List<OutboxEventEntity> result = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("TASK_DISPATCHED");
        assertThat(result.get(0).getTopic()).isEqualTo("static-analysis-tasks");

        verify(analysisTaskRepository).save(argThat(task ->
            task.getId().equals(2L) && task.getStatus() == TaskStatus.DISPATCHED
        ));
    }

    @Test
    void resolveAndDispatch_dependentTaskNotReady_doesNotDispatch() {
        // Given: Parent task still RUNNING
        AnalysisTaskEntity dependentTask = new AnalysisTaskEntity();
        dependentTask.setId(2L);
        dependentTask.setAnalysisId(analysisId);
        dependentTask.setDependsOnTaskId(1L);

        AnalysisTaskEntity runningParent = new AnalysisTaskEntity();
        runningParent.setId(1L);
        runningParent.setStatus(TaskStatus.RUNNING);

        when(analysisTaskRepository.findByDependsOnTaskId(1L)).thenReturn(List.of(dependentTask));
        when(analysisTaskRepository.findById(1L)).thenReturn(Optional.of(runningParent));

        // When
        List<OutboxEventEntity> result = dependencyResolver.resolveAndDispatch(completedTask);

        // Then
        assertThat(result).isEmpty();
        verify(analysisTaskRepository, never()).save(any());
    }
}
