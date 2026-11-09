package com.nyxora.swiftdrop.parcel.websocket.listener;

import com.nyxora.swiftdrop.parcel.websocket.broadcaster.ParcelWebSocketBroadcaster;
import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

/**
 * Transactional Event Listener for Parcel Lifecycle Transitions.
 *
 * Enforces transactional consistency:
 * Listens for {@link ParcelStatusChangedLocalEvent} strictly during
 * {@link TransactionPhase#AFTER_COMMIT}. If the courier status update
 * database transaction encounters an error or rolls back, this listener
 * is never executed, guaranteeing zero phantom WebSocket broadcasts.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ParcelStatusEventListener {

    private final ParcelWebSocketBroadcaster broadcaster;

    /**
     * Handles post-commit status changes by transforming internal events into
     * privacy-safe tracking update DTOs and dispatching via the WebSocket broadcaster.
     *
     * @param event The parcel status changed local domain event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onParcelStatusChanged(ParcelStatusChangedLocalEvent event) {
        if (event == null) {
            log.warn("Discarding null ParcelStatusChangedLocalEvent in post-commit listener.");
            return;
        }

        if (event.trackingNumber() == null || event.trackingNumber().isBlank()) {
            log.warn("Discarding ParcelStatusChangedLocalEvent: missing trackingNumber.");
            return;
        }

        String maskedRecipient = maskRecipientName(event.recipientName());

        ParcelTrackingUpdateDTO updateDTO = ParcelTrackingUpdateDTO.builder()
                .trackingNumber(event.trackingNumber())
                .currentStatus(event.newStatus() != null ? event.newStatus().name() : null)
                .statusDescription(event.statusDescription())
                .currentZone(event.currentZone())
                .recipientNameMasked(maskedRecipient)
                .deliveryNote(event.deliveryNote())
                .proofPhotoUrl(event.proofPhotoUrl())
                .timestamp(event.timestamp() != null ? event.timestamp() : Instant.now())
                .build();

        log.info("Database transaction committed. Broadcasting live tracking update: trackingNumber='{}', status='{}'",
                event.trackingNumber(),
                event.newStatus());

        broadcaster.broadcastStatusUpdate(updateDTO);
    }

    /**
     * Masks recipient full name into privacy-preserving format (e.g. "Kamal Perera" -> "K***l P***a").
     *
     * @param fullName Raw recipient full name
     * @return Masked name, or "Customer" if input is null or blank
     */
    public String maskRecipientName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "Customer";
        }

        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.length() <= 2) {
                sb.append(part.charAt(0)).append("*");
            } else {
                sb.append(part.charAt(0)).append("***").append(part.charAt(part.length() - 1));
            }
            if (i < parts.length - 1) {
                sb.append(" ");
            }
        }

        return sb.toString();
    }
}

