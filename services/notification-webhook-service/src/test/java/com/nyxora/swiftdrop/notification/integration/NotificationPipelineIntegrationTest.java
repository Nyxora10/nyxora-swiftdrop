package com.nyxora.swiftdrop.notification.integration;

import com.nyxora.swiftdrop.notification.consumer.ParcelStatusNotificationConsumer;
import com.nyxora.swiftdrop.notification.dispatcher.sms.MockSmsSender;
import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * End-to-End Pipeline Integration Test.
 *
 * Verifies the complete customer notification slice:
 * AMQP Consumer Ingestion -> Defensive Validation -> NotificationRouter -> Thymeleaf HTML Email & Mock SMS Dispatch.
 */
@SpringBootTest
@ActiveProfiles("test")
class NotificationPipelineIntegrationTest {

    @Autowired
    private ParcelStatusNotificationConsumer consumer;

    @MockBean
    private JavaMailSender mailSender;

    @SpyBean
    private MockSmsSender smsSender;

    @BeforeEach
    void setUp() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
    }

    private ParcelStatusUpdatedEvent buildEvent(String trackingNumber, String status) {
        return ParcelStatusUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("PARCEL_STATUS_UPDATED")
                .parcelId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .previousStatus("IN_TRANSIT")
                .currentStatus(status)
                .merchantId(UUID.randomUUID())
                .senderUserId(UUID.randomUUID())
                .senderEmail("merchant@swiftdrop.lk")
                .recipientName("Nimal Jayawardena")
                .recipientEmail("nimal.j@example.com")
                .recipientPhone("+94771239876")
                .courierName("Sunil Perera")
                .courierPhone("+94779876543")
                .deliveryNote("Leave with building concierge")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Pipeline E2E: Ingest OUT_FOR_DELIVERY event -> Dispatches rendered HTML email and cellular SMS")
    void shouldProcessOutForDeliveryPipelineE2E() {
        ParcelStatusUpdatedEvent event = buildEvent("SWIFT-E2E-OUT-001", "OUT_FOR_DELIVERY");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        // Verifies JavaMailSender was called with generated MimeMessage
        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));

        // Verifies Mock SMS dispatcher received formatted alert
        verify(smsSender).sendSms(eq("+94771239876"), contains("Sunil Perera"));
    }

    @Test
    @DisplayName("Pipeline E2E: Ingest DELIVERED event -> Dispatches delivery confirmation HTML email and SMS")
    void shouldProcessDeliveredPipelineE2E() {
        ParcelStatusUpdatedEvent event = buildEvent("SWIFT-E2E-DEL-002", "DELIVERED");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        verify(smsSender).sendSms(eq("+94771239876"), contains("successfully delivered"));
    }

    @Test
    @DisplayName("Pipeline E2E: Ingest CREATED event -> Dispatches booking confirmation email to sender without SMS")
    void shouldProcessCreatedPipelineE2E() {
        ParcelStatusUpdatedEvent event = buildEvent("SWIFT-E2E-NEW-003", "CREATED");

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(event));

        verify(mailSender, atLeastOnce()).send(any(MimeMessage.class));
        verify(smsSender, never()).sendSms(any(), any());
    }

    @Test
    @DisplayName("Pipeline E2E: Discards corrupt event without invoking downstream email or SMS dispatchers")
    void shouldDiscardCorruptEventInPipeline() {
        ParcelStatusUpdatedEvent corruptEvent = ParcelStatusUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .trackingNumber(null)
                .currentStatus("OUT_FOR_DELIVERY")
                .build();

        assertDoesNotThrow(() -> consumer.handleParcelStatusEvent(corruptEvent));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(smsSender, never()).sendSms(any(), any());
    }
}

