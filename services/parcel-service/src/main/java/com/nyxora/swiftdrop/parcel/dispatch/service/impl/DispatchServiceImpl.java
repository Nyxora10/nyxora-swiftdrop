package com.nyxora.swiftdrop.parcel.dispatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.dispatch.config.DispatchRabbitMqConfig;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse;
import com.nyxora.swiftdrop.parcel.dispatch.event.ParcelAssignedEvent;
import com.nyxora.swiftdrop.parcel.dispatch.exception.CourierWorkloadExceededException;
import com.nyxora.swiftdrop.parcel.dispatch.exception.InvalidParcelStateException;
import com.nyxora.swiftdrop.parcel.dispatch.lock.CourierAdvisoryLockManager;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxEvent;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxPublisher;
import com.nyxora.swiftdrop.parcel.dispatch.outbox.DispatchOutboxRepository;
import com.nyxora.swiftdrop.parcel.dispatch.repository.DispatchParcelRepository;
import com.nyxora.swiftdrop.parcel.dispatch.service.DispatchService;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Production implementation of DispatchService (Member 5: Task 1.6).
 *
 * <p>Key Guarantees:
 * <ul>
 *   <li>Evaluates active courier workload against canonical statuses: ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY.</li>
 *   <li>Enforces the 20-parcel maximum workload cap (< 20).</li>
 *   <li>Acquires a PostgreSQL transaction-scoped advisory lock per courier to guarantee multi-pod Kubernetes safety.</li>
 *   <li>Advances parcel status from CREATED to ASSIGNED and records an immutable audit history record.</li>
 *   <li>Uses a Transactional Outbox pattern to ensure guaranteed event publication without phantom events on rollback.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    public static final int MAX_COURIER_ACTIVE_DELIVERY_LIMIT = 20;

    /**
     * Canonical active parcel statuses for courier workload calculation according to Task 1.6:
     * ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY.
     */
    private static final Set<ParcelStatus> ACTIVE_DELIVERY_STATUSES = Set.of(
            ParcelStatus.ASSIGNED,
            ParcelStatus.PICKED_UP,
            ParcelStatus.IN_TRANSIT,
            ParcelStatus.OUT_FOR_DELIVERY
    );

    private final DispatchParcelRepository dispatchParcelRepository;
    private final ParcelStatusHistoryRepository parcelStatusHistoryRepository;
    private final CourierAdvisoryLockManager courierAdvisoryLockManager;
    private final DispatchOutboxRepository outboxRepository;
    private final DispatchOutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<UnassignedParcelResponse> getUnassignedParcels(Integer originZoneId) {
        log.info("[DispatchService] Querying unassigned parcels (originZoneId: {})", originZoneId);

        List<Parcel> unassignedParcels;
        if (originZoneId != null) {
            unassignedParcels = dispatchParcelRepository.findUnassignedParcelsByOriginZone(ParcelStatus.CREATED, originZoneId);
        } else {
            unassignedParcels = dispatchParcelRepository.findUnassignedParcels(ParcelStatus.CREATED);
        }

        log.debug("[DispatchService] Found {} unassigned parcels", unassignedParcels.size());
        return unassignedParcels.stream()
                .map(UnassignedParcelResponse::fromEntity)
                .toList();
    }

    @Override
    @Transactional
    public AssignCourierResponse assignCourier(UUID parcelId, AssignCourierRequest request, UUID adminUserId) {
        UUID courierId = request.courierId();
        log.info("[DispatchService] Processing courier assignment: parcelId={}, courierId={}, adminUserId={}",
                parcelId, courierId, adminUserId);

        // 1. Acquire transaction-scoped advisory lock on PostgreSQL to prevent concurrent assignment race conditions across pods
        courierAdvisoryLockManager.acquireCourierLock(courierId);

        // 2. Fetch parcel or throw 400 Bad Request
        Parcel parcel = dispatchParcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel not found with ID: " + parcelId));

        // 3. Validate parcel is unassigned and in CREATED status
        if (parcel.getCurrentStatus() != ParcelStatus.CREATED || parcel.getAssignedCourierId() != null) {
            log.warn("[DispatchService] Rejection: Parcel {} is in status {}, assignedCourierId={}",
                    parcel.getTrackingNumber(), parcel.getCurrentStatus(), parcel.getAssignedCourierId());
            throw new InvalidParcelStateException(parcel.getTrackingNumber(), parcel.getCurrentStatus());
        }

        // 4. Check target courier active delivery count (The 20-Parcel Guard) using canonical active statuses
        long activeCount = dispatchParcelRepository.countByAssignedCourierIdAndCurrentStatusIn(courierId, ACTIVE_DELIVERY_STATUSES);

        if (activeCount >= MAX_COURIER_ACTIVE_DELIVERY_LIMIT) {
            log.warn("[DispatchService] Workload cap exceeded: Courier {} has {} active parcels (limit: {})",
                    courierId, activeCount, MAX_COURIER_ACTIVE_DELIVERY_LIMIT);
            throw new CourierWorkloadExceededException(courierId, activeCount);
        }

        // 5. Update parcel assignment and transition status to ASSIGNED
        parcel.setAssignedCourierId(courierId);
        parcel.setCurrentStatus(ParcelStatus.ASSIGNED);
        Parcel savedParcel = dispatchParcelRepository.save(parcel);

        // 6. Append immutable audit history record with ASSIGNED status
        UUID recordedAdminId = adminUserId != null ? adminUserId : UUID.fromString("00000000-0000-0000-0000-000000000000");
        ParcelStatusHistory history = ParcelStatusHistory.builder()
                .parcel(savedParcel)
                .status(ParcelStatus.ASSIGNED)
                .updatedByUserId(recordedAdminId)
                .deliveryNote("Assigned to courier " + courierId)
                .build();
        parcelStatusHistoryRepository.save(history);

        // 7. Persist domain event to Transactional Outbox (committed atomically with the parcel update)
        Instant now = Instant.now();
        ParcelAssignedEvent event = new ParcelAssignedEvent(
                courierId,
                savedParcel.getId(),
                savedParcel.getTrackingNumber(),
                "ASSIGNED",
                now
        );

        String eventPayload;
        try {
            eventPayload = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.error("[DispatchService] Failed to serialize ParcelAssignedEvent for parcelId={}: {}",
                    savedParcel.getId(), e.getMessage());
            throw new IllegalStateException("Failed to serialize domain event", e);
        }

        DispatchOutboxEvent outboxEvent = DispatchOutboxEvent.builder()
                .aggregateId(savedParcel.getId())
                .eventType(DispatchRabbitMqConfig.ROUTING_KEY_PARCEL_ASSIGNED)
                .payload(eventPayload)
                .published(false)
                .createdAt(now)
                .build();
        DispatchOutboxEvent savedOutboxEvent = outboxRepository.save(outboxEvent);

        // 8. Register after-commit hook to trigger reliable asynchronous publishing
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        outboxPublisher.publishEvent(savedOutboxEvent.getId());
                    } catch (Exception ex) {
                        log.warn("[DispatchService] Immediate after-commit publish failed; scheduled outbox runner will retry: {}", ex.getMessage());
                    }
                }
            });
        } else {
            // Non-transactional fallback (e.g. certain unit test harnesses)
            outboxPublisher.publishEvent(savedOutboxEvent.getId());
        }

        return new AssignCourierResponse(
                savedParcel.getId(),
                savedParcel.getTrackingNumber(),
                courierId,
                "ASSIGNED",
                now,
                "Parcel " + savedParcel.getTrackingNumber() + " successfully assigned to courier."
        );
    }
}
