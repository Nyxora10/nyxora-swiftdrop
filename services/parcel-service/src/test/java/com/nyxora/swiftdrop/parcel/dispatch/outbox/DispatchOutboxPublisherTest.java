package com.nyxora.swiftdrop.parcel.dispatch.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.parcel.dispatch.config.DispatchRabbitMqConfig;
import com.nyxora.swiftdrop.parcel.dispatch.event.ParcelAssignedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DispatchOutboxPublisher Unit Tests (Member 5: Task 1.6)")
class DispatchOutboxPublisherTest {

    @Mock
    private DispatchOutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private DispatchOutboxPublisher publisher;

    private UUID eventId;
    private UUID parcelId;
    private UUID courierId;
    private DispatchOutboxEvent validOutboxEvent;

    @BeforeEach
    void setUp() throws Exception {
        eventId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
        courierId = UUID.randomUUID();

        ParcelAssignedEvent event = new ParcelAssignedEvent(
                courierId,
                parcelId,
                "SD-20261108-123456",
                "ASSIGNED",
                Instant.now()
        );
        String payload = objectMapper.writeValueAsString(event);

        validOutboxEvent = DispatchOutboxEvent.builder()
                .id(eventId)
                .aggregateId(parcelId)
                .eventType(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED)
                .payload(payload)
                .published(false)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("publishEvent() does nothing when outbox event ID does not exist in repository")
    void publishEvent_eventNotFound_doesNothing() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.empty());

        publisher.publishEvent(eventId);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("publishEvent() returns early when outbox event is already marked as published")
    void publishEvent_alreadyPublished_returnsEarly() {
        validOutboxEvent.setPublished(true);
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        publisher.publishEvent(eventId);

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("publishEvent() successfully marks event published upon RabbitMQ broker ACK")
    void publishEvent_brokerAck_marksPublished() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null && cd.getFuture() != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.publishEvent(eventId);

        assertTrue(validOutboxEvent.isPublished());
        assertNotNull(validOutboxEvent.getPublishedAt());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() increments retry count when RabbitMQ broker sends NACK with reason")
    void publishEvent_brokerNackWithReason_incrementsRetry() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null && cd.getFuture() != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(false, "Queue quota exceeded"));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.publishEvent(eventId);

        assertFalse(validOutboxEvent.isPublished());
        assertEquals(1, validOutboxEvent.getRetryCount());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() increments retry count when confirm is null")
    void publishEvent_nullConfirm_incrementsRetry() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null && cd.getFuture() != null) {
                cd.getFuture().complete(null);
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.publishEvent(eventId);

        assertFalse(validOutboxEvent.isPublished());
        assertEquals(1, validOutboxEvent.getRetryCount());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() increments retry count when publisher confirm throws TimeoutException")
    void publishEvent_confirmTimeout_incrementsRetry() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null && cd.getFuture() != null) {
                cd.getFuture().completeExceptionally(new TimeoutException("Confirmation timed out after 5s"));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.publishEvent(eventId);

        assertFalse(validOutboxEvent.isPublished());
        assertEquals(1, validOutboxEvent.getRetryCount());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() falls back to ack=true when correlationData future is null")
    void publishEvent_nullFuture_fallsBackToAckTrue() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null) {
                Field futureField = CorrelationData.class.getDeclaredField("future");
                futureField.setAccessible(true);
                futureField.set(cd, null);
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.publishEvent(eventId);

        assertTrue(validOutboxEvent.isPublished());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() increments retry count when RabbitTemplate throws AmqpException")
    void publishEvent_rabbitException_incrementsRetry() {
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        doThrow(new AmqpException("Broker connection refused"))
                .when(rabbitTemplate).convertAndSend(
                        eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                        eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                        any(ParcelAssignedEvent.class),
                        any(CorrelationData.class)
                );

        publisher.publishEvent(eventId);

        assertFalse(validOutboxEvent.isPublished());
        assertEquals(1, validOutboxEvent.getRetryCount());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
    }

    @Test
    @DisplayName("publishEvent() increments retry count when outbox payload is invalid JSON")
    void publishEvent_invalidJson_incrementsRetry() {
        validOutboxEvent.setPayload("{unparseable json content");
        when(outboxRepository.findById(eventId)).thenReturn(Optional.of(validOutboxEvent));

        publisher.publishEvent(eventId);

        assertFalse(validOutboxEvent.isPublished());
        assertEquals(1, validOutboxEvent.getRetryCount());
        verify(outboxRepository, times(1)).save(validOutboxEvent);
        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("processPendingOutboxEvents() does nothing when no pending events exist")
    void processPendingOutboxEvents_noEvents_doesNothing() {
        when(outboxRepository.findPendingEventsForProcessing()).thenReturn(List.of());

        publisher.processPendingOutboxEvents();

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    @DisplayName("processPendingOutboxEvents() iterates and dispatches all pending outbox records")
    void processPendingOutboxEvents_multipleEvents_dispatchesAll() throws Exception {
        UUID event2Id = UUID.randomUUID();
        ParcelAssignedEvent event2 = new ParcelAssignedEvent(
                courierId,
                UUID.randomUUID(),
                "SD-20261108-654321",
                "ASSIGNED",
                Instant.now()
        );
        DispatchOutboxEvent outboxEvent2 = DispatchOutboxEvent.builder()
                .id(event2Id)
                .aggregateId(event2.parcelId())
                .eventType(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED)
                .payload(objectMapper.writeValueAsString(event2))
                .published(false)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        when(outboxRepository.findPendingEventsForProcessing()).thenReturn(List.of(validOutboxEvent, outboxEvent2));

        doAnswer(invocation -> {
            CorrelationData cd = invocation.getArgument(3);
            if (cd != null && cd.getFuture() != null) {
                cd.getFuture().complete(new CorrelationData.Confirm(true, null));
            }
            return null;
        }).when(rabbitTemplate).convertAndSend(
                eq(DispatchRabbitMqConfig.EXCHANGE_NAME),
                eq(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED),
                any(ParcelAssignedEvent.class),
                any(CorrelationData.class)
        );

        publisher.processPendingOutboxEvents();

        assertTrue(validOutboxEvent.isPublished());
        assertTrue(outboxEvent2.isPublished());
        verify(rabbitTemplate, times(2)).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));
        verify(outboxRepository, times(2)).save(any(DispatchOutboxEvent.class));
    }
}
