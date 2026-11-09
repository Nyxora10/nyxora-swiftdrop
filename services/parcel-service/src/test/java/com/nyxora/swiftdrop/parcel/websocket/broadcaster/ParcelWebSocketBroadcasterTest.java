package com.nyxora.swiftdrop.parcel.websocket.broadcaster;

import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ParcelWebSocketBroadcasterTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ParcelWebSocketBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new ParcelWebSocketBroadcaster(messagingTemplate);
    }

    private ParcelTrackingUpdateDTO createSampleUpdate(String trackingNumber) {
        return ParcelTrackingUpdateDTO.builder()
                .trackingNumber(trackingNumber)
                .currentStatus("OUT_FOR_DELIVERY")
                .statusDescription("Courier is heading to delivery address")
                .currentZone("Colombo Zone 03")
                .recipientNameMasked("A***e P***a")
                .deliveryNote("Leave at the security post")
                .proofPhotoUrl(null)
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should broadcast status update to /topic/parcels/{trackingNumber} successfully")
    void shouldBroadcastStatusUpdateSuccessfully() {
        ParcelTrackingUpdateDTO update = createSampleUpdate("SWIFT-WS-100-LK");

        boolean result = broadcaster.broadcastStatusUpdate(update);

        assertTrue(result);
        verify(messagingTemplate).convertAndSend(eq("/topic/parcels/SWIFT-WS-100-LK"), eq(update));
    }

    @Test
    @DisplayName("Should return false when update payload is null")
    void shouldReturnFalseWhenUpdateIsNull() {
        boolean result = broadcaster.broadcastStatusUpdate(null);

        assertFalse(result);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should return false when trackingNumber is null")
    void shouldReturnFalseWhenTrackingNumberIsNull() {
        ParcelTrackingUpdateDTO update = createSampleUpdate(null);

        boolean result = broadcaster.broadcastStatusUpdate(update);

        assertFalse(result);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should return false when trackingNumber is blank")
    void shouldReturnFalseWhenTrackingNumberIsBlank() {
        ParcelTrackingUpdateDTO update = createSampleUpdate("   ");

        boolean result = broadcaster.broadcastStatusUpdate(update);

        assertFalse(result);
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("Should handle messaging exceptions gracefully and return false without throwing")
    void shouldHandleMessagingExceptionGracefully() {
        ParcelTrackingUpdateDTO update = createSampleUpdate("SWIFT-ERR-001");

        doThrow(new MessagingException("In-memory broker buffer full"))
                .when(messagingTemplate).convertAndSend(anyString(), any(Object.class));

        boolean result = broadcaster.broadcastStatusUpdate(update);

        assertFalse(result);
        verify(messagingTemplate).convertAndSend(eq("/topic/parcels/SWIFT-ERR-001"), eq(update));
    }
}

