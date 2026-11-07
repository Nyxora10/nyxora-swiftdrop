package com.nyxora.swiftdrop.parcel.statemachine;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Guarded Finite State Machine governing deterministic parcel lifecycle transitions.
 * Enforces business rules and validation guards before any status change is persisted.
 *
 * <pre>
 * Permitted Flow:
 * CREATED ───────► ASSIGNED ───► PICKED_UP ───► IN_TRANSIT ───► OUT_FOR_DELIVERY ───► DELIVERED (Terminal)
 *    │                              ▲                                    │
 *    └──────────────────────────────┘                                    ▼
 *                                                                     FAILED
 *                                                                     │     ▲
 *                                      (After 3 failed attempts)      │     │ (Re-attempt < 3)
 *                                                                     ▼     │
 *                                                           RETURNED_TO_SENDER (Terminal)
 * </pre>
 */
@Slf4j
@Component
public class ParcelStateMachine {

    /** Maximum allowed delivery failure attempts before a parcel must be returned to sender. */
    public static final int MAX_FAILED_ATTEMPTS = 3;

    private static final Map<ParcelStatus, Set<ParcelStatus>> TRANSITION_MATRIX = new EnumMap<>(ParcelStatus.class);

    static {
        // CREATED can be assigned by a dispatcher, or directly picked up
        TRANSITION_MATRIX.put(ParcelStatus.CREATED,
                EnumSet.of(ParcelStatus.ASSIGNED, ParcelStatus.PICKED_UP));

        // ASSIGNED courier picks up the parcel from sender
        TRANSITION_MATRIX.put(ParcelStatus.ASSIGNED,
                EnumSet.of(ParcelStatus.PICKED_UP));

        // PICKED_UP moves to sorting hub / transit
        TRANSITION_MATRIX.put(ParcelStatus.PICKED_UP,
                EnumSet.of(ParcelStatus.IN_TRANSIT));

        // IN_TRANSIT arrives at destination hub and goes out for delivery
        TRANSITION_MATRIX.put(ParcelStatus.IN_TRANSIT,
                EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY));

        // OUT_FOR_DELIVERY results in either successful delivery or logged failure
        TRANSITION_MATRIX.put(ParcelStatus.OUT_FOR_DELIVERY,
                EnumSet.of(ParcelStatus.DELIVERED, ParcelStatus.FAILED));

        // FAILED can be re-attempted (OUT_FOR_DELIVERY) or returned to sender (terminal)
        TRANSITION_MATRIX.put(ParcelStatus.FAILED,
                EnumSet.of(ParcelStatus.OUT_FOR_DELIVERY, ParcelStatus.RETURNED_TO_SENDER));

        // Terminal states cannot transition to anything
        TRANSITION_MATRIX.put(ParcelStatus.DELIVERED, Collections.emptySet());
        TRANSITION_MATRIX.put(ParcelStatus.RETURNED_TO_SENDER, Collections.emptySet());
    }

    /**
     * Checks whether a status jump from {@code current} to {@code target} is topologically allowed.
     *
     * @param current current lifecycle status
     * @param target  requested target status
     * @return {@code true} if the transition is allowed in the transition graph
     */
    public boolean isTransitionAllowed(ParcelStatus current, ParcelStatus target) {
        if (current == null || target == null) {
            return false;
        }
        Set<ParcelStatus> allowedTargets = TRANSITION_MATRIX.getOrDefault(current, Collections.emptySet());
        return allowedTargets.contains(target);
    }

    /**
     * Retrieves the set of permitted next statuses from the current status.
     *
     * @param current current lifecycle status
     * @return unmodifiable set of allowed next statuses
     */
    public Set<ParcelStatus> getAllowedNextStatuses(ParcelStatus current) {
        if (current == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(TRANSITION_MATRIX.getOrDefault(current, Collections.emptySet()));
    }

    /**
     * Validates an intended state transition against the state graph and business guardrails.
     *
     * @param currentStatus          current status of the parcel
     * @param targetStatus           desired status to transition to
     * @param recipientSignatureName recipient name/signature (required for DELIVERED)
     * @param proofPhotoUrl          doorstep photo or signature URL (required for DELIVERED)
     * @param failureReason          structured failure reason (required for FAILED)
     * @param currentFailureCount    total failed attempts already recorded for this parcel
     * @throws InvalidStateTransitionException if the transition violates lifecycle rules or guards
     */
    public void validateTransition(ParcelStatus currentStatus,
                                   ParcelStatus targetStatus,
                                   String recipientSignatureName,
                                   String proofPhotoUrl,
                                   String failureReason,
                                   int currentFailureCount) {

        if (currentStatus == null || targetStatus == null) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus,
                    "Current status and target status must not be null.");
        }

        // 1. Guard: Terminal states can never transition
        if (currentStatus.isTerminal()) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus,
                    String.format("Parcel is in terminal state '%s' and cannot be modified.", currentStatus));
        }

        // 2. Guard: Cannot transition to the exact same status
        if (currentStatus == targetStatus) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus,
                    String.format("Parcel is already in status '%s'.", currentStatus));
        }

        // 3. Guard: Base transition graph check
        if (!isTransitionAllowed(currentStatus, targetStatus)) {
            throw new InvalidStateTransitionException(currentStatus, targetStatus,
                    String.format("Illegal state transition from '%s' to '%s'. Allowed targets: %s",
                            currentStatus, targetStatus, getAllowedNextStatuses(currentStatus)));
        }

        // 4. Guard: DELIVERED requires recipient signature name AND proof photo URL
        if (targetStatus == ParcelStatus.DELIVERED) {
            if (recipientSignatureName == null || recipientSignatureName.trim().isEmpty()) {
                throw new InvalidStateTransitionException(currentStatus, targetStatus,
                        "Recipient signature name is strictly required to mark parcel as DELIVERED.");
            }
            if (proofPhotoUrl == null || proofPhotoUrl.trim().isEmpty()) {
                throw new InvalidStateTransitionException(currentStatus, targetStatus,
                        "Proof photo or signature URL is strictly required to mark parcel as DELIVERED.");
            }
        }

        // 5. Guard: FAILED requires a non-blank failure reason
        if (targetStatus == ParcelStatus.FAILED) {
            if (failureReason == null || failureReason.trim().isEmpty()) {
                throw new InvalidStateTransitionException(currentStatus, targetStatus,
                        "Failure reason is strictly required when marking delivery attempt as FAILED.");
            }
        }

        // 6. Guard: Re-attempt vs Return-To-Sender thresholds
        if (currentStatus == ParcelStatus.FAILED) {
            if (targetStatus == ParcelStatus.OUT_FOR_DELIVERY && currentFailureCount >= MAX_FAILED_ATTEMPTS) {
                throw new InvalidStateTransitionException(currentStatus, targetStatus,
                        String.format("Maximum failed delivery attempts (%d) reached. Parcel must be RETURNED_TO_SENDER.",
                                MAX_FAILED_ATTEMPTS));
            }

            if (targetStatus == ParcelStatus.RETURNED_TO_SENDER && currentFailureCount < MAX_FAILED_ATTEMPTS) {
                throw new InvalidStateTransitionException(currentStatus, targetStatus,
                        String.format("Cannot transition to RETURNED_TO_SENDER before %d failed attempts (current failed count: %d).",
                                MAX_FAILED_ATTEMPTS, currentFailureCount));
            }
        }

        log.debug("State transition validated successfully: {} -> {}", currentStatus, targetStatus);
    }
}