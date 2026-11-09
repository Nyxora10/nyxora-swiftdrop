package com.nyxora.swiftdrop.notification.dispatcher;

import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;

/**
 * Dispatcher contract for processing and routing parcel status update events
 * to appropriate notification channels (HTML email, SMS, etc.).
 *
 * Implemented by downstream notification routers to adhere to the
 * Dependency Inversion Principle and maintain clean service boundaries.
 */
public interface NotificationDispatcher {

    /**
     * Dispatches notifications to the relevant recipients based on the event status.
     *
     * @param event Validated parcel status update event
     */
    void dispatch(ParcelStatusUpdatedEvent event);
}

