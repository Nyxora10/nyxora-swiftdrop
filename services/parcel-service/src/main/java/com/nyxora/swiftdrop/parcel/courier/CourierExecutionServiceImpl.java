package com.nyxora.swiftdrop.parcel.courier;

import com.nyxora.swiftdrop.parcel.booking.config.RabbitMqConfig;
import com.nyxora.swiftdrop.parcel.courier.dto.*;
import com.nyxora.swiftdrop.parcel.courier.event.ParcelStatusUpdatedEvent;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.statemachine.ParcelStateMachine;
import com.nyxora.swiftdrop.parcel.websocket.event.ParcelStatusChangedLocalEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Production implementation of {@link CourierExecutionService}.
 * Orchestrates courier runsheet retrieval, strict ownership verification (HTTP 403),
 * state machine rule enforcement (HTTP 409), audit history tracking, asynchronous
 * RabbitMQ cross-service messaging, and real-time WebSocket notifications.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourierExecutionServiceImpl implements CourierExecutionService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository parcelStatusHistoryRepository;
    private final ParcelStateMachine parcelStateMachine;
    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public CourierRunsheetResponseDto getCourierRunsheet(UUID courierId) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID cannot be null.");
        }

        List<Parcel> assignedParcels = parcelRepository.findByAssignedCourierId(courierId);
        List<CourierParcelCardDto> pickups = new ArrayList<>();
        List<CourierParcelCardDto> deliveries = new ArrayList<>();

        for (Parcel parcel : assignedParcels) {
            ParcelStatus status = parcel.getCurrentStatus();

            // Ignore completed terminal parcels from the daily active runsheet
            if (status.isTerminal()) {
                continue;
            }

            CourierParcelCardDto card = toCourierParcelCardDto(parcel);

            if (status == ParcelStatus.ASSIGNED || status == ParcelStatus.CREATED) {
                pickups.add(card);
            } else {
                // PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY, FAILED
                deliveries.add(card);
            }
        }

        log.info("Loaded runsheet for courier {}: {} pickups, {} deliveries",
                courierId, pickups.size(), deliveries.size());

        return CourierRunsheetResponseDto.builder()
                .courierId(courierId)
                .totalActiveParcels(pickups.size() + deliveries.size())
                .pickups(pickups)
                .deliveries(deliveries)
                .generatedAt(Instant.now())
                .build();
    }

    @Override
    public CourierParcelCardDto getCourierParcelById(UUID courierId, UUID parcelId) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID cannot be null.");
        }
        if (parcelId == null) {
            throw new IllegalArgumentException("Parcel ID cannot be null.");
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel with ID " + parcelId + " was not found."));

        // Strict Resource Ownership Check (HTTP 403 Forbidden)
        if (parcel.getAssignedCourierId() == null || !parcel.getAssignedCourierId().equals(courierId)) {
            log.warn("Access Denied: Courier {} attempted to access parcel {} assigned to courier {}",
                    courierId, parcelId, parcel.getAssignedCourierId());
            throw new AccessDeniedException("Access denied: You are not authorized to view this parcel.");
        }

        return toCourierParcelCardDto(parcel);
    }

    @Override
    @Transactional
    public ParcelStatusUpdateResponseDto updateParcelStatus(UUID courierId, UUID parcelId, UpdateParcelStatusRequestDto request) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID cannot be null.");
        }
        if (parcelId == null) {
            throw new IllegalArgumentException("Parcel ID cannot be null.");
        }
        if (request == null || request.getTargetStatus() == null) {
            throw new IllegalArgumentException("Target status is required.");
        }

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel with ID " + parcelId + " was not found."));

        // 1. Strict Resource Ownership Check (HTTP 403 Forbidden)
        if (parcel.getAssignedCourierId() == null || !parcel.getAssignedCourierId().equals(courierId)) {
            log.warn("Access Denied: Courier {} attempted to transition parcel {} assigned to courier {}",
                    courierId, parcelId, parcel.getAssignedCourierId());
            throw new AccessDeniedException("Access denied: You are not authorized to update this parcel.");
        }

        ParcelStatus previousStatus = parcel.getCurrentStatus();
        ParcelStatus targetStatus = request.getTargetStatus();

        // 2. Count existing failed delivery attempts for threshold check
        int failureCount = (int) parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcel.getId(), ParcelStatus.FAILED);

        // 3. Enforce State Machine validation (throws InvalidStateTransitionException -> HTTP 409 Conflict)
        parcelStateMachine.validateTransition(
                previousStatus,
                targetStatus,
                request.getRecipientSignatureName(),
                request.getProofPhotoUrl(),
                request.getFailureReason(),
                failureCount
        );

        // 4. Update parcel lifecycle status
        parcel.setCurrentStatus(targetStatus);
        parcelRepository.save(parcel);

        // 5. Persist immutable audit record in parcel_status_history
        ParcelStatusHistory history = ParcelStatusHistory.builder()
                .parcel(parcel)
                .status(targetStatus)
                .updatedByUserId(courierId)
                .deliveryNote(request.getDeliveryNote())
                .recipientSignatureName(request.getRecipientSignatureName())
                .proofPhotoUrl(request.getProofPhotoUrl())
                .failureReason(request.getFailureReason())
                .build();
        parcelStatusHistoryRepository.save(history);

        log.info("Courier {} transitioned parcel {} ({}) from {} to {}",
                courierId, parcel.getId(), parcel.getTrackingNumber(), previousStatus, targetStatus);

        // 6. Asynchronously publish RabbitMQ domain event
        publishRabbitMqStatusUpdatedEvent(parcel, previousStatus, targetStatus, courierId, request);

        // 7. Publish local application event for transactional WebSocket tracking push
        publishLocalWebSocketTrackingEvent(parcel, previousStatus, targetStatus, request);

        return ParcelStatusUpdateResponseDto.builder()
                .parcelId(parcel.getId())
                .trackingNumber(parcel.getTrackingNumber())
                .previousStatus(previousStatus)
                .currentStatus(targetStatus)
                .updatedAt(Instant.now())
                .message(String.format("Parcel %s successfully transitioned from %s to %s.",
                        parcel.getTrackingNumber(), previousStatus, targetStatus))
                .build();
    }

    @Override
    public CourierHistoryResponseDto getCourierHistory(UUID courierId) {
        if (courierId == null) {
            throw new IllegalArgumentException("Courier ID cannot be null.");
        }

        List<Parcel> assignedParcels = parcelRepository.findByAssignedCourierId(courierId);
        List<CourierDeliveryHistoryItemDto> historyItems = new ArrayList<>();
        int deliveredCount = 0;
        int failedCount = 0;
        int returnedCount = 0;

        for (Parcel parcel : assignedParcels) {
            ParcelStatus status = parcel.getCurrentStatus();

            // Only consider terminal or completed deliveries for history
            if (status != ParcelStatus.DELIVERED && status != ParcelStatus.FAILED && status != ParcelStatus.RETURNED_TO_SENDER) {
                continue;
            }

            if (status == ParcelStatus.DELIVERED) {
                deliveredCount++;
            } else if (status == ParcelStatus.FAILED) {
                failedCount++;
            } else if (status == ParcelStatus.RETURNED_TO_SENDER) {
                returnedCount++;
            }

            // Retrieve latest audit record to extract proof URL, signature, and timestamp
            List<ParcelStatusHistory> history = parcelStatusHistoryRepository.findByParcel_IdOrderByCreatedAtAsc(parcel.getId());
            ParcelStatusHistory latest = history.isEmpty() ? null : history.get(history.size() - 1);

            CourierDeliveryHistoryItemDto item = CourierDeliveryHistoryItemDto.builder()
                    .parcelId(parcel.getId())
                    .trackingNumber(parcel.getTrackingNumber())
                    .finalStatus(status)
                    .recipientName(parcel.getRecipientName())
                    .recipientPhone(parcel.getRecipientPhone())
                    .dropoffAddress(parcel.getDropoffAddress())
                    .destinationZoneName(parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : null)
                    .weightKg(parcel.getWeightKg())
                    .parcelSize(parcel.getParcelSize())
                    .estimatedFee(parcel.getEstimatedFee())
                    .recipientSignatureName(latest != null ? latest.getRecipientSignatureName() : null)
                    .proofPhotoUrl(latest != null ? latest.getProofPhotoUrl() : null)
                    .deliveryNote(latest != null ? latest.getDeliveryNote() : null)
                    .failureReason(latest != null ? latest.getFailureReason() : null)
                    .completedAt(latest != null && latest.getCreatedAt() != null ? latest.getCreatedAt() : parcel.getUpdatedAt())
                    .build();

            historyItems.add(item);
        }

        // Sort descending by completion time (most recent deliveries first)
        historyItems.sort((a, b) -> {
            if (a.getCompletedAt() == null || b.getCompletedAt() == null) return 0;
            return b.getCompletedAt().compareTo(a.getCompletedAt());
        });

        log.info("Loaded delivery history for courier {}: {} items (delivered: {}, failed: {}, returned: {})",
                courierId, historyItems.size(), deliveredCount, failedCount, returnedCount);

        return CourierHistoryResponseDto.builder()
                .courierId(courierId)
                .totalCompleted(historyItems.size())
                .deliveredCount(deliveredCount)
                .failedCount(failedCount)
                .returnedCount(returnedCount)
                .history(historyItems)
                .generatedAt(Instant.now())
                .build();
    }

    private CourierParcelCardDto toCourierParcelCardDto(Parcel parcel) {
        int failureCount = (int) parcelStatusHistoryRepository.countByParcel_IdAndStatus(parcel.getId(), ParcelStatus.FAILED);
        String originZoneName = parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneName() : null;
        String destinationZoneName = parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : null;

        return CourierParcelCardDto.builder()
                .id(parcel.getId())
                .trackingNumber(parcel.getTrackingNumber())
                .currentStatus(parcel.getCurrentStatus())
                .originZoneName(originZoneName)
                .destinationZoneName(destinationZoneName)
                .pickupAddress(parcel.getPickupAddress())
                .dropoffAddress(parcel.getDropoffAddress())
                .recipientName(parcel.getRecipientName())
                .recipientPhone(parcel.getRecipientPhone())
                .weightKg(parcel.getWeightKg())
                .parcelSize(parcel.getParcelSize())
                .estimatedFee(parcel.getEstimatedFee())
                .failureCount(failureCount)
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }

    private void publishRabbitMqStatusUpdatedEvent(Parcel parcel, ParcelStatus previousStatus, ParcelStatus targetStatus,
                                                   UUID courierId, UpdateParcelStatusRequestDto request) {
        try {
            ParcelStatusUpdatedEvent event = ParcelStatusUpdatedEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType("PARCEL_STATUS_UPDATED")
                    .parcelId(parcel.getId())
                    .trackingNumber(parcel.getTrackingNumber())
                    .previousStatus(previousStatus.name())
                    .currentStatus(targetStatus.name())
                    .status(targetStatus.name())
                    .courierId(courierId)
                    .merchantId(parcel.getMerchantId())
                    .senderUserId(parcel.getSenderUserId())
                    .recipientName(parcel.getRecipientName())
                    .recipientPhone(parcel.getRecipientPhone())
                    .deliveryNote(request.getDeliveryNote())
                    .failureReason(request.getFailureReason())
                    .timestamp(Instant.now())
                    .build();

            // Publish general status update
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC,
                    RabbitMqConfig.ROUTING_KEY_STATUS_UPDATED,
                    event
            );

            // Specific event for DELIVERED (triggers courier workload counter decrement)
            if (targetStatus == ParcelStatus.DELIVERED) {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC,
                        RabbitMqConfig.ROUTING_KEY_STATUS_DELIVERED,
                        event
                );
            }

            // Specific event for FAILED / RETURNED_TO_SENDER
            if (targetStatus == ParcelStatus.FAILED || targetStatus == ParcelStatus.RETURNED_TO_SENDER) {
                rabbitTemplate.convertAndSend(
                        RabbitMqConfig.SWIFTDROP_EVENTS_TOPIC,
                        RabbitMqConfig.ROUTING_KEY_STATUS_FAILED,
                        event
                );
            }

            log.debug("Published RabbitMQ status event for parcel {}", parcel.getTrackingNumber());
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to publish status update event to RabbitMQ: {}", ex.getMessage());
        }
    }

    private void publishLocalWebSocketTrackingEvent(Parcel parcel, ParcelStatus previousStatus, ParcelStatus targetStatus,
                                                    UpdateParcelStatusRequestDto request) {
        try {
            String currentZone = parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : "";
            ParcelStatusChangedLocalEvent localEvent = ParcelStatusChangedLocalEvent.builder()
                    .parcelId(parcel.getId())
                    .trackingNumber(parcel.getTrackingNumber())
                    .previousStatus(previousStatus)
                    .newStatus(targetStatus)
                    .statusDescription("Courier transitioned parcel status to " + targetStatus.name())
                    .currentZone(currentZone)
                    .recipientName(parcel.getRecipientName())
                    .deliveryNote(request.getDeliveryNote())
                    .proofPhotoUrl(request.getProofPhotoUrl())
                    .timestamp(Instant.now())
                    .build();

            eventPublisher.publishEvent(localEvent);
            log.debug("Published local application event for WebSocket broadcast of {}", parcel.getTrackingNumber());
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to publish local application event: {}", ex.getMessage());
        }
    }
}