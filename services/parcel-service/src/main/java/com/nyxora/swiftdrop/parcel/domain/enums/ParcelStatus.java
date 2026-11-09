package com.nyxora.swiftdrop.parcel.domain.enums;

/**
 * Finite state machine statuses for a parcel's lifecycle.
 * Transitions are managed by the Courier (via status update endpoints)
 * and the Dispatcher (automated assignment and failure detection).
 *
 * <p>Allowed transitions are enforced centrally by
 * {@code com.nyxora.swiftdrop.parcel.statemachine.ParcelStateMachine}.
 *
 * <pre>
 * CREATED ───────► ASSIGNED ───► PICKED_UP ───► IN_TRANSIT ───► OUT_FOR_DELIVERY ───► DELIVERED (Terminal)
 *                                                                    │
 *                                                                    ▼
 *                                                                 FAILED
 *                                                                 │     ▲
 *                                  (After 3 failed attempts)      │     │ (Re-attempt < 3)
 *                                                                 ▼     │
 *                                                       RETURNED_TO_SENDER (Terminal)
 * </pre>
 */
public enum ParcelStatus {

    /** Booking created by a customer or merchant; no courier assigned yet. */
    CREATED,

    /** Dispatcher assigned the parcel to a courier; awaiting pickup from the sender. */
    ASSIGNED,

    /** Courier collected the parcel from the sender's pickup address. */
    PICKED_UP,

    /** Parcel is moving through a sorting hub towards the destination zone. */
    IN_TRANSIT,

    /** Courier is on the last-mile route to the recipient. */
    OUT_FOR_DELIVERY,

    /** Parcel handed over with recipient signature and proof photo (terminal). */
    DELIVERED,

    /** Delivery attempt failed with a mandatory reason; may be re-attempted. */
    FAILED,

    /** Returned to the original sender after the maximum failed attempts (terminal). */
    RETURNED_TO_SENDER;

    /**
     * Terminal states cannot transition to any other state.
     *
     * @return {@code true} if this status ends the parcel lifecycle
     */
    public boolean isTerminal() {
        return this == DELIVERED || this == RETURNED_TO_SENDER;
    }
}
