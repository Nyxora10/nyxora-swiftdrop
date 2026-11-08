package com.nyxora.swiftdrop.parcel.websocket.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParcelTrackingUpdateDTOTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Test
    @DisplayName("Should serialize and deserialize ParcelTrackingUpdateDTO with ISO-8601 timestamps")
    void shouldSerializeAndDeserializeJson() throws Exception {
        Instant now = Instant.parse("2026-11-08T02:00:00Z");

        ParcelTrackingUpdateDTO dto = ParcelTrackingUpdateDTO.builder()
                .trackingNumber("SWIFT-WS-001")
                .currentStatus("OUT_FOR_DELIVERY")
                .statusDescription("Assigned courier is on route")
                .currentZone("Colombo North Zone")
                .recipientNameMasked("A***e P***a")
                .deliveryNote("Leave with reception")
                .proofPhotoUrl("https://storage.swiftdrop.lk/proofs/p-001.jpg")
                .timestamp(now)
                .build();

        String json = objectMapper.writeValueAsString(dto);
        assertNotNull(json);

        ParcelTrackingUpdateDTO deserialized = objectMapper.readValue(json, ParcelTrackingUpdateDTO.class);

        assertEquals("SWIFT-WS-001", deserialized.getTrackingNumber());
        assertEquals("OUT_FOR_DELIVERY", deserialized.getCurrentStatus());
        assertEquals("Assigned courier is on route", deserialized.getStatusDescription());
        assertEquals("Colombo North Zone", deserialized.getCurrentZone());
        assertEquals("A***e P***a", deserialized.getRecipientNameMasked());
        assertEquals("Leave with reception", deserialized.getDeliveryNote());
        assertEquals("https://storage.swiftdrop.lk/proofs/p-001.jpg", deserialized.getProofPhotoUrl());
        assertEquals(now, deserialized.getTimestamp());
    }

    @Test
    @DisplayName("Should tolerate unknown JSON properties gracefully without failing")
    void shouldTolerateUnknownProperties() throws Exception {
        String json = """
                {
                    "trackingNumber": "SWIFT-UNKNOWN-002",
                    "currentStatus": "DELIVERED",
                    "unexpectedField": "extra_data",
                    "nestedObject": { "k": "v" }
                }
                """;

        ParcelTrackingUpdateDTO deserialized = objectMapper.readValue(json, ParcelTrackingUpdateDTO.class);

        assertNotNull(deserialized);
        assertEquals("SWIFT-UNKNOWN-002", deserialized.getTrackingNumber());
        assertEquals("DELIVERED", deserialized.getCurrentStatus());
    }

    @Test
    @DisplayName("Should support full POJO contract: getters, setters, equals, hashCode, and toString")
    void shouldSupportPojoContract() {
        ParcelTrackingUpdateDTO dto1 = new ParcelTrackingUpdateDTO();
        dto1.setTrackingNumber("SWIFT-001");
        dto1.setCurrentStatus("CREATED");

        ParcelTrackingUpdateDTO dto2 = ParcelTrackingUpdateDTO.builder()
                .trackingNumber("SWIFT-001")
                .currentStatus("CREATED")
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
        assertTrue(dto1.toString().contains("SWIFT-001"));

        dto2.setCurrentStatus("DELIVERED");
        assertNotEquals(dto1, dto2);
    }

    @Test
    @DisplayName("Should instantiate and verify ParcelStatusChangedLocalEvent record contract")
    void shouldVerifyLocalEventRecordContract() {
        UUID parcelId = UUID.randomUUID();
        Instant now = Instant.now();

        ParcelStatusChangedLocalEvent event = ParcelStatusChangedLocalEvent.builder()
                .parcelId(parcelId)
                .trackingNumber("SWIFT-EVT-003")
                .previousStatus(ParcelStatus.IN_TRANSIT)
                .newStatus(ParcelStatus.OUT_FOR_DELIVERY)
                .statusDescription("Courier dispatched")
                .currentZone("Kandy Metro Zone")
                .recipientName("Alice Fernando")
                .deliveryNote("Fragile")
                .proofPhotoUrl(null)
                .timestamp(now)
                .build();

        assertEquals(parcelId, event.parcelId());
        assertEquals("SWIFT-EVT-003", event.trackingNumber());
        assertEquals(ParcelStatus.IN_TRANSIT, event.previousStatus());
        assertEquals(ParcelStatus.OUT_FOR_DELIVERY, event.newStatus());
        assertEquals("Courier dispatched", event.statusDescription());
        assertEquals("Kandy Metro Zone", event.currentZone());
        assertEquals("Alice Fernando", event.recipientName());
        assertEquals("Fragile", event.deliveryNote());
        assertEquals(now, event.timestamp());
        assertTrue(event.toString().contains("SWIFT-EVT-003"));
    }
}

