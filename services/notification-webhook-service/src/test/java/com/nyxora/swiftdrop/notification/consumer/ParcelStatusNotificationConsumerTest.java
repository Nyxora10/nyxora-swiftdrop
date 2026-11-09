package com.nyxora.swiftdrop.notification.consumer;

import com.nyxora.swiftdrop.notification.dispatcher.NotificationDispatcher;
import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParcelStatusNotificationConsumerTest {

    @Mock
    private NotificationDispatcher notificationDispatcher;

    private ParcelStatusNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ParcelStatusNotificationConsumer(notificationDispatcher);
    }

    private ParcelStatusUpdatedEvent createSampleEvent(String trackingNumber, String currentStatus) {
        return ParcelStatusUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PARCEL_STATUS_UPDATED")
                .parcelId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .previousStatus("IN_TRANSIT")
                .currentStatus(currentStatus)
                .merchantId(UUID.randomUUID())
                .senderUserId(UUID.randomUUID())
                .senderEmail("merchant@swiftdrop.lk")
                .recipientName("Alice Perera")
                .recipientEmail("alice.perera@example.com")
                .recipientPhone("+94771234567")
                .courierName("Sunil Perera")
                .courierPhone("+94779876543")
                .deliveryNote("Leave at the security desk")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should successfully process and dispatch valid parcel status event")
    void shouldProcessAndDispatchValidEvent() {
        ParcelStatusUpdatedEvent event = createSampleEvent("SWIFT-10000001-LK", "OUT_FOR_DELIVERY");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher).dispatch(event);
    }

    @Test
    @DisplayName("Should discard null event without invoking dispatcher")
    void shouldDiscardNullEvent() {
        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(null));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should discard event when trackingNumber is null")
    void shouldDiscardEventWhenTrackingNumberIsNull() {
        ParcelStatusUpdatedEvent event = createSampleEvent(null, "DELIVERED");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should discard event when trackingNumber is blank")
    void shouldDiscardEventWhenTrackingNumberIsBlank() {
        ParcelStatusUpdatedEvent event = createSampleEvent("   ", "DELIVERED");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should discard event when currentStatus is null")
    void shouldDiscardEventWhenCurrentStatusIsNull() {
        ParcelStatusUpdatedEvent event = createSampleEvent("SWIFT-10000002-LK", null);

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should discard event when currentStatus is blank")
    void shouldDiscardEventWhenCurrentStatusIsBlank() {
        ParcelStatusUpdatedEvent event = createSampleEvent("SWIFT-10000003-LK", "  ");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should handle dispatcher exceptions gracefully without rethrowing (preventing message poison pill)")
    void shouldHandleDispatcherExceptionGracefully() {
        ParcelStatusUpdatedEvent event = createSampleEvent("SWIFT-10000004-LK", "DELIVERED");

        doThrow(new RuntimeException("Simulated SMTP timeout or SMS failure"))
                .when(notificationDispatcher).dispatch(any());

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(notificationDispatcher).dispatch(event);
    }

    @Test
    @DisplayName("Should initialize and use fallback dispatcher when no dispatcher bean is provided")
    void shouldUseFallbackDispatcherWhenNullPassed() {
        ParcelStatusNotificationConsumer standaloneConsumer = new ParcelStatusNotificationConsumer(null);

        assertNotNull(standaloneConsumer.getNotificationDispatcher());

        ParcelStatusUpdatedEvent event = createSampleEvent("SWIFT-10000005-LK", "CREATED");
        assertDoesNotThrow(() -> standaloneConsumer.handleParcelStatusEvent(event));
    }
}

