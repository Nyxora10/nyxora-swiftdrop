package com.nyxora.swiftdrop.notification.dispatcher;

import com.nyxora.swiftdrop.notification.dispatcher.email.EmailNotificationDispatcher;
import com.nyxora.swiftdrop.notification.dispatcher.sms.MockSmsSender;
import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationRouterTest {

    @Mock
    private EmailNotificationDispatcher emailDispatcher;

    @Mock
    private MockSmsSender smsSender;

    private NotificationRouter router;

    @BeforeEach
    void setUp() {
        router = new NotificationRouter(emailDispatcher, smsSender);
    }

    private ParcelStatusUpdatedEvent createSampleEvent(String status) {
        return ParcelStatusUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PARCEL_STATUS_UPDATED")
                .parcelId(UUID.randomUUID())
                .trackingNumber("SWIFT-ROUTER-001")
                .previousStatus("IN_TRANSIT")
                .currentStatus(status)
                .merchantId(UUID.randomUUID())
                .senderUserId(UUID.randomUUID())
                .senderEmail("merchant@swiftdrop.lk")
                .recipientName("Alice Perera")
                .recipientEmail("alice.perera@example.com")
                .recipientPhone("+94771234567")
                .courierName("Kamal Silva")
                .courierPhone("+94779876543")
                .deliveryNote("Leave at the front door")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should route CREATED status to sender email without SMS")
    void shouldRouteCreatedStatusToSenderEmail() {
        ParcelStatusUpdatedEvent event = createSampleEvent("CREATED");

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(
                eq("merchant@swiftdrop.lk"),
                contains("Parcel Booking Confirmed"),
                eq(NotificationRouter.TEMPLATE_PARCEL_BOOKED),
                any()
        );
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should fallback to recipientEmail when senderEmail is blank for BOOKED status")
    void shouldFallbackToRecipientEmailWhenSenderEmailBlank() {
        ParcelStatusUpdatedEvent event = createSampleEvent("BOOKED");
        event.setSenderEmail("   ");

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(
                eq("alice.perera@example.com"),
                contains("Parcel Booking Confirmed"),
                eq(NotificationRouter.TEMPLATE_PARCEL_BOOKED),
                any()
        );
    }

    @Test
    @DisplayName("Should handle missing sender and recipient emails gracefully on CREATED")
    void shouldHandleMissingEmailsGracefullyOnCreated() {
        ParcelStatusUpdatedEvent event = createSampleEvent("CREATED");
        event.setSenderEmail(null);
        event.setRecipientEmail(null);

        assertDoesNotThrow(() -> router.dispatch(event));

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should route OUT_FOR_DELIVERY status to both HTML email and SMS with courier details")
    void shouldRouteOutForDeliveryStatus() {
        ParcelStatusUpdatedEvent event = createSampleEvent("OUT_FOR_DELIVERY");

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(
                eq("alice.perera@example.com"),
                contains("Out for Delivery"),
                eq(NotificationRouter.TEMPLATE_OUT_FOR_DELIVERY),
                any()
        );

        ArgumentCaptor<String> smsCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendSms(eq("+94771234567"), smsCaptor.capture());
        assertTrue(smsCaptor.getValue().contains("Kamal Silva"));
        assertTrue(smsCaptor.getValue().contains("SWIFT-ROUTER-001"));
    }

    @Test
    @DisplayName("Should use fallback courier name in SMS when courierName is null on OUT_FOR_DELIVERY")
    void shouldUseFallbackCourierNameInSms() {
        ParcelStatusUpdatedEvent event = createSampleEvent("OUT_FOR_DELIVERY");
        event.setCourierName(null);

        router.dispatch(event);

        ArgumentCaptor<String> smsCaptor = ArgumentCaptor.forClass(String.class);
        verify(smsSender).sendSms(eq("+94771234567"), smsCaptor.capture());
        assertTrue(smsCaptor.getValue().contains("SwiftDrop Courier"));
    }

    @Test
    @DisplayName("Should handle missing phone number gracefully on OUT_FOR_DELIVERY")
    void shouldHandleMissingPhoneOnOutForDelivery() {
        ParcelStatusUpdatedEvent event = createSampleEvent("OUT_FOR_DELIVERY");
        event.setRecipientPhone(null);

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(eq("alice.perera@example.com"), any(), any(), any());
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should handle missing email gracefully on OUT_FOR_DELIVERY")
    void shouldHandleMissingEmailOnOutForDelivery() {
        ParcelStatusUpdatedEvent event = createSampleEvent("OUT_FOR_DELIVERY");
        event.setRecipientEmail(null);

        router.dispatch(event);

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
        verify(smsSender).sendSms(eq("+94771234567"), any());
    }

    @Test
    @DisplayName("Should route DELIVERED status to both HTML email and SMS confirmation")
    void shouldRouteDeliveredStatus() {
        ParcelStatusUpdatedEvent event = createSampleEvent("DELIVERED");

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(
                eq("alice.perera@example.com"),
                contains("Delivered Successfully"),
                eq(NotificationRouter.TEMPLATE_DELIVERY_SUCCESS),
                any()
        );

        verify(smsSender).sendSms(
                eq("+94771234567"),
                contains("successfully delivered")
        );
    }

    @Test
    @DisplayName("Should handle missing phone gracefully on DELIVERED")
    void shouldHandleMissingPhoneOnDelivered() {
        ParcelStatusUpdatedEvent event = createSampleEvent("DELIVERED");
        event.setRecipientPhone(null);

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(eq("alice.perera@example.com"), any(), any(), any());
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should handle missing email gracefully on DELIVERED")
    void shouldHandleMissingEmailOnDelivered() {
        ParcelStatusUpdatedEvent event = createSampleEvent("DELIVERED");
        event.setRecipientEmail(null);

        router.dispatch(event);

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
        verify(smsSender).sendSms(eq("+94771234567"), any());
    }

    @Test
    @DisplayName("Should log and skip customer notifications for intermediate status like IN_TRANSIT")
    void shouldSkipCustomerNotificationsForInTransit() {
        ParcelStatusUpdatedEvent event = createSampleEvent("IN_TRANSIT");

        router.dispatch(event);

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should safely discard null event without throwing exceptions")
    void shouldDiscardNullEvent() {
        assertDoesNotThrow(() -> router.dispatch(null));

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should safely discard event with null or blank currentStatus")
    void shouldDiscardEventWithNullOrBlankStatus() {
        ParcelStatusUpdatedEvent event = createSampleEvent(null);
        assertDoesNotThrow(() -> router.dispatch(event));

        event.setCurrentStatus("   ");
        assertDoesNotThrow(() -> router.dispatch(event));

        verify(emailDispatcher, never()).sendHtmlEmail(any(), any(), any(), any());
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Should populate model with fallback timestamp when event timestamp is null")
    void shouldHandleNullTimestampInModel() {
        ParcelStatusUpdatedEvent event = createSampleEvent("CREATED");
        event.setTimestamp(null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> modelCaptor = ArgumentCaptor.forClass(Map.class);

        router.dispatch(event);

        verify(emailDispatcher).sendHtmlEmail(any(), any(), any(), modelCaptor.capture());
        assertNotNull(modelCaptor.getValue().get("timestamp"));
    }
}

