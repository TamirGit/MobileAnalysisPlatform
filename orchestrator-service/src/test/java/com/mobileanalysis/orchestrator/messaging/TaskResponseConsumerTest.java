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
import java.util.Collections;
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

    private TaskResponseEvent successEvent;
    private AnalysisTaskEntity task;

    @BeforeEach
    void setUp() {
        UUID analysisId = UUID.randomUUID();

        successEvent = TaskResponseEvent.builder()
            .taskId(1L)
            .analysisId(analysisId)
            .status(TaskStatus.COMPLETED)
            .outputPath("/output/result.json")
            .attempts(1)
            .timestamp(Instant.now())
            .build();

        task = new AnalysisTaskEntity();
        task.setId(1L);
        task.setAnalysisId(analysisId);
        task.setStatus(TaskStatus.RUNNING);
    }

    @Test
    void handleTaskResponse_success_updatesTaskAndAcknowledges() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(dependencyResolver.resolveAndDispatch(any())).thenReturn(Collections.emptyList());

        // When
        consumer.handleTaskResponse(successEvent, acknowledgment);

        // Then
        verify(taskRepository).save(argThat(t ->
            t.getStatus() == TaskStatus.COMPLETED &&
            t.getOutputPath().equals("/output/result.json")
        ));
        verify(completionService).checkAndMarkCompletion(successEvent.getAnalysisId());
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskResponse_withDependents_savesOutboxEvents() {
        // Given
        OutboxEventEntity outboxEvent = new OutboxEventEntity();
        List<OutboxEventEntity> events = List.of(outboxEvent);

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(dependencyResolver.resolveAndDispatch(any())).thenReturn(events);

        // When
        consumer.handleTaskResponse(successEvent, acknowledgment);

        // Then
        verify(outboxRepository).saveAll(argThat(savedEvents -> {
            List<OutboxEventEntity> eventList = (List<OutboxEventEntity>) savedEvents;
            return eventList.size() == 1;
        }));
        verify(acknowledgment).acknowledge();
    }

    @Test
    void handleTaskResponse_taskNotFound_throwsException() {
        // Given
        when(taskRepository.findById(1L)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> consumer.handleTaskResponse(successEvent, acknowledgment))
            .isInstanceOf(RuntimeException.class);

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void handleTaskResponse_failedStatus_doesNotResolveDependencies() {
        // Given
        TaskResponseEvent failedEvent = TaskResponseEvent.builder()
            .taskId(1L)
            .analysisId(task.getAnalysisId())
            .status(TaskStatus.FAILED)
            .errorMessage("Processing failed")
            .attempts(2)
            .timestamp(Instant.now())
            .build();

        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));

        // When
        consumer.handleTaskResponse(failedEvent, acknowledgment);

        // Then
        verify(dependencyResolver, never()).resolveAndDispatch(any());
        verify(completionService).checkAndMarkCompletion(task.getAnalysisId());
        verify(acknowledgment).acknowledge();
    }
}
