package com.mobileanalysis.orchestrator.messaging;

import com.mobileanalysis.common.domain.TaskStatus;
import com.mobileanalysis.common.events.TaskResponseEvent;
import com.mobileanalysis.orchestrator.domain.AnalysisTaskEntity;
import com.mobileanalysis.orchestrator.domain.OutboxEventEntity;
import com.mobileanalysis.orchestrator.repository.AnalysisTaskRepository;
import com.mobileanalysis.orchestrator.repository.OutboxRepository;
import com.mobileanalysis.orchestrator.service.AnalysisCompletionService;
import com.mobileanalysis.orchestrator.service.DependencyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskResponseConsumerTest {

    @Mock
    private AnalysisTaskRepository taskRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private DependencyResolver dependencyResolver;

    @Mock
    private AnalysisCompletionService completionService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TaskResponseConsumer consumer;

    private UUID analysisId;
    private TaskResponseEvent successEvent;
    private AnalysisTaskEntity task;

    @BeforeEach
    void setUp() {
        analysisId = UUID.randomUUID();

        task = new AnalysisTaskEntity();
        task.setId(1L);
        task.setAnalysisId(analysisId);
        task.setStatus(TaskStatus.RUNNING);

        successEvent = TaskResponseEvent.builder()
            .taskId(1L)
            .analysisId(analysisId)
            .status(TaskStatus.COMPLETED)
            .outputPath("/path/to/output.json")
            .attempts(1)
            .timestamp(Instant.now())
            .build();
    }

    @Test
    void handleTaskResponse_successfulTask_updatesAndResolvesDependencies() {
        // Given
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(dependencyResolver.resolveAndDispatch(task)).thenReturn(List.of(outboxEvent));

        // When
        consumer.handleTaskResponse(successEvent, acknowledgment);

        // Then
        verify(taskRepository).save(argThat(t ->
            t.getStatus() == TaskStatus.COMPLETED &&
            t.getOutputPath().equals("/path/to/output.json") &&
            t.getCompletedAt() != null
        ));

        verify(dependencyResolver).resolveAndDispatch(task);
        verify(outboxRepository).saveAll(argThat(events -> events.size() == 1));
        verify(completionService).checkAndMarkCompletion(analysisId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskResponse_failedTask_updatesWithError() {
        // Given
        TaskResponseEvent failureEvent = TaskResponseEvent.builder()
            .taskId(1L)
            .analysisId(analysisId)
            .status(TaskStatus.FAILED)
            .errorMessage("Processing error")
            .attempts(2)
            .timestamp(Instant.now())
            .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // When
        consumer.handleTaskResponse(failureEvent, acknowledgment);

        // Then
        verify(taskRepository).save(argThat(t ->
            t.getStatus() == TaskStatus.FAILED &&
            t.getErrorMessage().equals("Processing error")
        ));

        verify(dependencyResolver, never()).resolveAndDispatch(any());
        verify(completionService).checkAndMarkCompletion(analysisId);
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskResponse_taskNotFound_throwsException() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> consumer.handleTaskResponse(successEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Task event processing failed");

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleTaskResponse_processingError_doesNotAcknowledge() {
        // Given
        when(taskRepository.findById(1L)).thenThrow(new RuntimeException("Database error"));

        // When/Then
        assertThatThrownBy(() -> consumer.handleTaskResponse(successEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }
}
