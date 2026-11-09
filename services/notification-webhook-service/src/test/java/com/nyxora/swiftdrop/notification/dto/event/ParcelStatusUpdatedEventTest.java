package com.nyxora.swiftdrop.notification.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParcelStatusUpdatedEventTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("Should serialize and deserialize ParcelStatusUpdatedEvent correctly")
    void shouldSerializeAndDeserialize() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-06T12:00:00Z");

        ParcelStatusUpdatedEvent event = ParcelStatusUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("PARCEL_STATUS_UPDATED")
                .parcelId(parcelId)
                .trackingNumber("SWIFT-12345678-LK")
                .previousStatus("IN_TRANSIT")
                .currentStatus("OUT_FOR_DELIVERY")
                .merchantId(merchantId)
                .senderUserId(senderUserId)
                .senderEmail("sender@merchant.com")
                .recipientName("Alice Perera")
                .recipientEmail("alice@example.com")
                .recipientPhone("+94771234567")
                .courierName("Kamal Silva")
                .courierPhone("+94779876543")
                .deliveryNote("Leave at the front porch")
                .failureReason(null)
                .timestamp(now)
                .build();

        String json = objectMapper.writeValueAsString(event);
        assertNotNull(json);

        ParcelStatusUpdatedEvent deserialized = objectMapper.readValue(json, ParcelStatusUpdatedEvent.class);

        assertEquals(eventId, deserialized.getEventId());
        assertEquals("PARCEL_STATUS_UPDATED", deserialized.getEventType());
        assertEquals(parcelId, deserialized.getParcelId());
        assertEquals("SWIFT-12345678-LK", deserialized.getTrackingNumber());
        assertEquals("IN_TRANSIT", deserialized.getPreviousStatus());
        assertEquals("OUT_FOR_DELIVERY", deserialized.getCurrentStatus());
        assertEquals(merchantId, deserialized.getMerchantId());
        assertEquals(senderUserId, deserialized.getSenderUserId());
        assertEquals("sender@merchant.com", deserialized.getSenderEmail());
        assertEquals("Alice Perera", deserialized.getRecipientName());
        assertEquals("alice@example.com", deserialized.getRecipientEmail());
        assertEquals("+94771234567", deserialized.getRecipientPhone());
        assertEquals("Kamal Silva", deserialized.getCourierName());
        assertEquals("+94779876543", deserialized.getCourierPhone());
        assertEquals("Leave at the front porch", deserialized.getDeliveryNote());
        assertEquals(now, deserialized.getTimestamp());
    }

    @Test
    @DisplayName("Should tolerate unknown JSON properties gracefully without failing")
    void shouldIgnoreUnknownProperties() throws Exception {
        String jsonWithUnknownFields = """
                {
                    "eventId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
                    "eventType": "PARCEL_STATUS_UPDATED",
                    "trackingNumber": "SWIFT-99999999-LK",
                    "currentStatus": "DELIVERED",
                    "recipientEmail": "test@example.com",
                    "unknownFutureField": "should_be_ignored",
                    "extraNestedObject": { "foo": "bar" }
                }
                """;

        ParcelStatusUpdatedEvent event = objectMapper.readValue(jsonWithUnknownFields, ParcelStatusUpdatedEvent.class);

        assertNotNull(event);
        assertEquals(UUID.fromString("a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d"), event.getEventId());
        assertEquals("SWIFT-99999999-LK", event.getTrackingNumber());
        assertEquals("DELIVERED", event.getCurrentStatus());
        assertEquals("test@example.com", event.getRecipientEmail());
    }

    @Test
    @DisplayName("Should support full POJO getters, setters, equals, hashCode, and toString")
    void shouldSupportPojoContract() {
        ParcelStatusUpdatedEvent event1 = new ParcelStatusUpdatedEvent();
        event1.setTrackingNumber("SWIFT-001");
        event1.setCurrentStatus("CREATED");

        ParcelStatusUpdatedEvent event2 = ParcelStatusUpdatedEvent.builder()
                .trackingNumber("SWIFT-001")
                .currentStatus("CREATED")
                .build();

        assertEquals(event1, event2);
        assertEquals(event1.hashCode(), event2.hashCode());
        assertTrue(event1.toString().contains("SWIFT-001"));

        event2.setCurrentStatus("DELIVERED");
        assertNotEquals(event1, event2);
    }
}

