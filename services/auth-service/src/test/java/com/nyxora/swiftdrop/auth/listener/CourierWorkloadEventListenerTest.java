package com.nyxora.swiftdrop.auth.listener;

import com.nyxora.swiftdrop.auth.config.RabbitMQConfig;
import com.nyxora.swiftdrop.auth.dto.event.ParcelWorkloadEvent;
import com.nyxora.swiftdrop.auth.entity.ProcessedWorkloadEvent;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.ProcessedWorkloadEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourierWorkloadEventListener Unit Tests (Member 5: Task 2.2)")
class CourierWorkloadEventListenerTest {

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private ProcessedWorkloadEventRepository processedWorkloadEventRepository;

    @InjectMocks
    private CourierWorkloadEventListener listener;

    private final UUID courierId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();
    private final String trackingNumber = "SD-20261106-000001";

    @Test
    @DisplayName("Should atomically increment active delivery count on parcel.assigned and record event")
    void shouldIncrementCountOnParcelAssigned() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(courierRepository.incrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED);

        // Assert
        verify(courierRepository, times(1)).incrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).decrementActiveDeliveryCount(any());
        verify(processedWorkloadEventRepository, times(1)).saveAndFlush(any(ProcessedWorkloadEvent.class));
    }

    @Test
    @DisplayName("Idempotency: duplicate parcel.assigned event should be skipped without incrementing count")
    void shouldSkipDuplicateParcelAssignedEvent() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(true);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED);

        // Assert
        verifyNoInteractions(courierRepository);
        verify(processedWorkloadEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should atomically decrement active delivery count on parcel.status.delivered and record event")
    void shouldDecrementCountOnParcelDelivered() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "DELIVERED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED);

        // Assert
        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).incrementActiveDeliveryCount(any());
        verify(processedWorkloadEventRepository, times(1)).saveAndFlush(any(ProcessedWorkloadEvent.class));
    }

    @Test
    @DisplayName("Idempotency: duplicate parcel.status.delivered event should be skipped without decrementing count")
    void shouldSkipDuplicateParcelDeliveredEvent() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "DELIVERED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(true);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED);

        // Assert
        verifyNoInteractions(courierRepository);
        verify(processedWorkloadEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should atomically decrement active delivery count on parcel.status.failed and record event")
    void shouldDecrementCountOnParcelFailed() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "FAILED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_FAILED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_FAILED);

        // Assert
        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).incrementActiveDeliveryCount(any());
        verify(processedWorkloadEventRepository, times(1)).saveAndFlush(any(ProcessedWorkloadEvent.class));
    }

    @Test
    @DisplayName("Should handle null courierId gracefully without invoking repository")
    void shouldHandleNullCourierIdGracefully() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(null, parcelId, trackingNumber, "ASSIGNED");

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verifyNoInteractions(courierRepository);
        verifyNoInteractions(processedWorkloadEventRepository);
    }

    @Test
    @DisplayName("Should handle null parcelId gracefully without invoking repository")
    void shouldHandleNullParcelIdGracefully() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, null, trackingNumber, "ASSIGNED");

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verifyNoInteractions(courierRepository);
        verifyNoInteractions(processedWorkloadEventRepository);
    }

    @Test
    @DisplayName("Should handle null event gracefully without invoking repository")
    void shouldHandleNullEventGracefully() {
        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(null, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verifyNoInteractions(courierRepository);
        verifyNoInteractions(processedWorkloadEventRepository);
    }

    @Test
    @DisplayName("Should ignore unhandled routing key without invoking courier repository")
    void shouldHandleUnknownRoutingKeyGracefully() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "PICKED_UP");
        String eventKey = parcelId + ":parcel.status.picked_up";
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);

        // Act
        listener.handleParcelWorkloadEvent(event, "parcel.status.picked_up");

        // Assert
        verifyNoInteractions(courierRepository);
        verify(processedWorkloadEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should log warning and not throw when courier is not found for increment")
    void shouldHandleCourierNotFoundOnIncrement() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(courierRepository.incrementActiveDeliveryCount(courierId)).thenReturn(0);

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verify(courierRepository, times(1)).incrementActiveDeliveryCount(courierId);
        verify(processedWorkloadEventRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Concurrent duplicate event: DataIntegrityViolationException on saveAndFlush is caught and count is not updated")
    void shouldHandleConcurrentDuplicateDataIntegrityViolation() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(processedWorkloadEventRepository.saveAndFlush(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("Duplicate key"));

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verifyNoInteractions(courierRepository);
    }

    @Test
    @DisplayName("Should log warning and not throw when courier is not found for decrement")
    void shouldHandleCourierNotFoundOnDecrement() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "DELIVERED");
        String eventKey = parcelId + ":" + RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED;
        when(processedWorkloadEventRepository.existsById(eventKey)).thenReturn(false);
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(0);

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED));

        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
        verify(processedWorkloadEventRepository, times(1)).saveAndFlush(any());
    }
}
