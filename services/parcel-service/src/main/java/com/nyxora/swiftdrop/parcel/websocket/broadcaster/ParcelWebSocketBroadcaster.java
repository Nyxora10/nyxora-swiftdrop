package com.nyxora.swiftdrop.parcel.websocket.broadcaster;

import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Real-Time WebSocket Broadcaster.
 *
 * Utilizes {@link SimpMessagingTemplate} to publish live tracking updates
 * to topic subscriptions: {@code /topic/parcels/{trackingNumber}}.
 *
 * All public tracking clients subscribed to that specific tracking number
 * receive immediate push notifications over their active STOMP connection.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParcelWebSocketBroadcaster {

    public static final String TOPIC_PARCEL_PREFIX = "/topic/parcels/";

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Broadcasts a parcel tracking update DTO to the parcel-specific STOMP topic.
     *
     * @param update The parcel tracking update DTO
     * @return true if successfully converted and dispatched to the broker; false if discarded due to invalid inputs or errors
     */
    public boolean broadcastStatusUpdate(ParcelTrackingUpdateDTO update) {
        if (update == null) {
            log.warn("Cannot broadcast real-time update: payload is null.");
            return false;
        }

        if (update.getTrackingNumber() == null || update.getTrackingNumber().isBlank()) {
            log.warn("Cannot broadcast real-time update: trackingNumber is missing.");
            return false;
        }

        String destination = TOPIC_PARCEL_PREFIX + update.getTrackingNumber().trim();

        try {
            messagingTemplate.convertAndSend(destination, update);
            log.info("Broadcast STOMP tracking update to destination='{}': status='{}', zone='{}'",
                    destination,
                    update.getCurrentStatus(),
                    update.getCurrentZone());
            return true;
        } catch (Exception ex) {
            log.error("Failed to broadcast STOMP update to destination='{}': {}",
                    destination,
                    ex.getMessage(),
                    ex);
            return false;
        }
    }
}

