package com.nyxora.swiftdrop.parcel.websocket.integration;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.websocket.dto.ParcelTrackingUpdateDTO;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * End-to-End WebSocket & Transactional Commit Integration Test.
 *
 * Verifies the full Task 1.11 slice:
 * - Status change events published inside a committed database transaction
 *   trigger the {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 *   and broadcast real-time tracking updates via STOMP to {@code /topic/parcels/{trackingNumber}}.
 * - Status change events published inside a rolled-back transaction are
 *   cancelled, ensuring ZERO phantom messages are pushed to public tracking sessions.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(ParcelWebSocketIntegrationTest.TransactionTestConfig.class)
class ParcelWebSocketIntegrationTest {

    @MockBean
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private TransactionalEventPublisherHelper testPublisher;

    @TestConfiguration
    static class TransactionTestConfig {
        @Bean
        public TransactionalEventPublisherHelper transactionalEventPublisherHelper(ApplicationEventPublisher publisher) {
            return new TransactionalEventPublisherHelper(publisher);
        }
    }

    static class TransactionalEventPublisherHelper {
        private final ApplicationEventPublisher eventPublisher;

        public TransactionalEventPublisherHelper(ApplicationEventPublisher eventPublisher) {
            this.eventPublisher = eventPublisher;
        }

        @Transactional
        public void publishAndCommit(ParcelStatusChangedLocalEvent event) {
            eventPublisher.publishEvent(event);
        }

        @Transactional
        public void publishAndRollback(ParcelStatusChangedLocalEvent event) {
            eventPublisher.publishEvent(event);
            throw new RuntimeException("Simulated courier status update failure triggering rollback");
        }
    }

    private ParcelStatusChangedLocalEvent createEvent(String trackingNumber, ParcelStatus status, String recipientName) {
        return ParcelStatusChangedLocalEvent.builder()
                .parcelId(UUID.randomUUID())
                .trackingNumber(trackingNumber)
                .previousStatus(ParcelStatus.IN_TRANSIT)
                .newStatus(status)
                .statusDescription("Courier is approaching destination")
                .currentZone("Colombo Port Zone")
                .recipientName(recipientName)
                .deliveryNote("Leave at the security reception")
                .proofPhotoUrl("https://swiftdrop.lk/proofs/delivery-001.jpg")
                .timestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Transactional E2E: After database commit -> Broadcasts STOMP update to /topic/parcels/{trackingNumber}")
    void shouldBroadcastUpdateAfterDatabaseCommit() {
        ParcelStatusChangedLocalEvent event = createEvent(
                "SWIFT-COMMIT-001",
                ParcelStatus.DELIVERED,
                "Kamal Silva"
        );

        assertDoesNotThrow(() -> testPublisher.publishAndCommit(event));

        ArgumentCaptor<ParcelTrackingUpdateDTO> captor = ArgumentCaptor.forClass(ParcelTrackingUpdateDTO.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/parcels/SWIFT-COMMIT-001"), captor.capture());

        ParcelTrackingUpdateDTO broadcasted = captor.getValue();
        assertEquals("SWIFT-COMMIT-001", broadcasted.getTrackingNumber());
        assertEquals("DELIVERED", broadcasted.getCurrentStatus());
        assertEquals("K***l S***a", broadcasted.getRecipientNameMasked());
        assertEquals("Colombo Port Zone", broadcasted.getCurrentZone());
        assertEquals("https://swiftdrop.lk/proofs/delivery-001.jpg", broadcasted.getProofPhotoUrl());
        assertNotNull(broadcasted.getTimestamp());
    }

    @Test
    @DisplayName("Transactional E2E: On database rollback -> Cancels WebSocket broadcast (zero phantom messages)")
    void shouldNotBroadcastUpdateWhenDatabaseRollsBack() {
        ParcelStatusChangedLocalEvent event = createEvent(
                "SWIFT-ROLLBACK-002",
                ParcelStatus.OUT_FOR_DELIVERY,
                "Alice Perera"
        );

        assertThrows(RuntimeException.class, () -> testPublisher.publishAndRollback(event));

        // Verifies zero WebSocket messages were broadcast
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}

