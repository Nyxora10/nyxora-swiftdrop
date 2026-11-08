package com.nyxora.swiftdrop.auth.listener;

import com.nyxora.swiftdrop.auth.config.RabbitMQConfig;
import com.nyxora.swiftdrop.auth.dto.event.ParcelWorkloadEvent;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
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

    @InjectMocks
    private CourierWorkloadEventListener listener;

    private final UUID courierId = UUID.randomUUID();
    private final UUID parcelId = UUID.randomUUID();
    private final String trackingNumber = "SD-20261106-000001";

    @Test
    @DisplayName("Should atomically increment active delivery count on parcel.assigned")
    void shouldIncrementCountOnParcelAssigned() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        when(courierRepository.incrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED);

        // Assert
        verify(courierRepository, times(1)).incrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).decrementActiveDeliveryCount(any());
    }

    @Test
    @DisplayName("Should atomically decrement active delivery count on parcel.status.delivered")
    void shouldDecrementCountOnParcelDelivered() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "DELIVERED");
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED);

        // Assert
        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).incrementActiveDeliveryCount(any());
    }

    @Test
    @DisplayName("Should atomically decrement active delivery count on parcel.status.failed")
    void shouldDecrementCountOnParcelFailed() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "FAILED");
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(1);

        // Act
        listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_FAILED);

        // Assert
        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
        verify(courierRepository, never()).incrementActiveDeliveryCount(any());
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
    }

    @Test
    @DisplayName("Should handle null event gracefully without invoking repository")
    void shouldHandleNullEventGracefully() {
        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(null, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verifyNoInteractions(courierRepository);
    }

    @Test
    @DisplayName("Should ignore unhandled routing key without invoking repository")
    void shouldHandleUnknownRoutingKeyGracefully() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "PICKED_UP");

        // Act
        listener.handleParcelWorkloadEvent(event, "parcel.status.picked_up");

        // Assert
        verifyNoInteractions(courierRepository);
    }

    @Test
    @DisplayName("Should log warning and not throw when courier is not found for increment")
    void shouldHandleCourierNotFoundOnIncrement() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "ASSIGNED");
        when(courierRepository.incrementActiveDeliveryCount(courierId)).thenReturn(0);

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED));

        verify(courierRepository, times(1)).incrementActiveDeliveryCount(courierId);
    }

    @Test
    @DisplayName("Should log warning and not throw when courier is not found for decrement")
    void shouldHandleCourierNotFoundOnDecrement() {
        // Arrange
        ParcelWorkloadEvent event = new ParcelWorkloadEvent(courierId, parcelId, trackingNumber, "DELIVERED");
        when(courierRepository.decrementActiveDeliveryCount(courierId)).thenReturn(0);

        // Act & Assert
        assertDoesNotThrow(() ->
                listener.handleParcelWorkloadEvent(event, RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED));

        verify(courierRepository, times(1)).decrementActiveDeliveryCount(courierId);
    }
}

