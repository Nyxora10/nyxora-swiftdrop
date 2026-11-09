package com.nyxora.swiftdrop.parcel.websocket.listener;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.websocket.broadcaster.ParcelWebSocketBroadcaster;
import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParcelStatusEventListenerTest {

    @Mock
    private ParcelWebSocketBroadcaster broadcaster;

    private ParcelStatusEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new ParcelStatusEventListener(broadcaster);
    }

    private ParcelStatusChangedLocalEvent buildSampleEvent(String trackingNumber, ParcelStatus status, String recipientName) {
        return ParcelStatusChangedLocalEvent.builder()
                .parcelId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .previousStatus(ParcelStatus.IN_TRANSIT)
                .newStatus(status)
                .statusDescription("Courier is heading to delivery location")
                .currentZone("Colombo Zone 07")
                .recipientName(recipientName)
                .deliveryNote("Call before arrival")
                .proofPhotoUrl("https://swiftdrop.lk/proofs/123.jpg")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should transform event and delegate to broadcaster on valid status update")
    void shouldTransformAndBroadcastValidEvent() {
        ParcelStatusChangedLocalEvent event = buildSampleEvent(
                "SWIFT-EVT-200-LK",
                ParcelStatus.OUT_FOR_DELIVERY,
                "Kamal Perera"
        );

        assertDoesNotThrow(() -> listener.onParcelStatusChanged(event));

        ArgumentCaptor<ParcelTrackingUpdateDTO> captor = ArgumentCaptor.forClass(ParcelTrackingUpdateDTO.class);
        verify(broadcaster).broadcastStatusUpdate(captor.capture());

        ParcelTrackingUpdateDTO captured = captor.getValue();
        assertEquals("SWIFT-EVT-200-LK", captured.getTrackingNumber());
        assertEquals("OUT_FOR_DELIVERY", captured.getCurrentStatus());
        assertEquals("Courier is heading to delivery location", captured.getStatusDescription());
        assertEquals("Colombo Zone 07", captured.getCurrentZone());
        assertEquals("K***l P***a", captured.getRecipientNameMasked());
        assertEquals("Call before arrival", captured.getDeliveryNote());
        assertEquals("https://swiftdrop.lk/proofs/123.jpg", captured.getProofPhotoUrl());
        assertNotNull(captured.getTimestamp());
    }

    @Test
    @DisplayName("Should safely discard null event without invoking broadcaster")
    void shouldDiscardNullEvent() {
        assertDoesNotThrow(() -> listener.onParcelStatusChanged(null));

        verify(broadcaster, never()).broadcastStatusUpdate(any());
    }

    @Test
    @DisplayName("Should safely discard event when trackingNumber is null")
    void shouldDiscardEventWhenTrackingNumberIsNull() {
        ParcelStatusChangedLocalEvent event = buildSampleEvent(null, ParcelStatus.DELIVERED, "Alice");

        assertDoesNotThrow(() -> listener.onParcelStatusChanged(event));

        verify(broadcaster, never()).broadcastStatusUpdate(any());
    }

    @Test
    @DisplayName("Should safely discard event when trackingNumber is blank")
    void shouldDiscardEventWhenTrackingNumberIsBlank() {
        ParcelStatusChangedLocalEvent event = buildSampleEvent("   ", ParcelStatus.DELIVERED, "Alice");

        assertDoesNotThrow(() -> listener.onParcelStatusChanged(event));

        verify(broadcaster, never()).broadcastStatusUpdate(any());
    }

    @Test
    @DisplayName("Should fallback timestamp to Instant.now() when event timestamp is null")
    void shouldFallbackTimestampWhenNull() {
        ParcelStatusChangedLocalEvent event = ParcelStatusChangedLocalEvent.builder()
                .trackingNumber("SWIFT-TIME-001")
                .newStatus(ParcelStatus.DELIVERED)
                .timestamp(null)
                .build();

        listener.onParcelStatusChanged(event);

        ArgumentCaptor<ParcelTrackingUpdateDTO> captor = ArgumentCaptor.forClass(ParcelTrackingUpdateDTO.class);
        verify(broadcaster).broadcastStatusUpdate(captor.capture());
        assertNotNull(captor.getValue().getTimestamp());
    }

    @Test
    @DisplayName("Should mask short, standard, and blank recipient names appropriately")
    void shouldMaskRecipientNamesCorrectly() {
        assertEquals("Customer", listener.maskRecipientName(null));
        assertEquals("Customer", listener.maskRecipientName("   "));
        assertEquals("A*", listener.maskRecipientName("Al"));
        assertEquals("A***e", listener.maskRecipientName("Alice"));
        assertEquals("K***l S***a", listener.maskRecipientName("Kamal Silva"));
    }
}

