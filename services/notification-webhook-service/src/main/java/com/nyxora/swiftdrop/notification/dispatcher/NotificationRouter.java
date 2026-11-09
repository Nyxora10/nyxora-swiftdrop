package com.nyxora.swiftdrop.notification.dispatcher;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.notification.dispatcher.email.EmailNotificationDispatcher;
import com.nyxora.swiftdrop.notification.dispatcher.sms.MockSmsSender;
import com.nyxora.swiftdrop.notification.dto.event.ParcelStatusUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Central Notification Routing Engine.
 *
 * Implements the {@link NotificationDispatcher} contract to orchestrate customer-facing
 * multi-channel notifications (HTML Email and SMS) based on parcel lifecycle status transitions.
 *
 * Routing Rules:
 * - CREATED / BOOKED:
 *     - Email to Sender: parcel-booked.html confirmation
 * - OUT_FOR_DELIVERY:
 *     - Email to Recipient: out-for-delivery.html with courier information
 *     - SMS to Recipient: Cellular alert with tracking link
 * - DELIVERED:
 *     - Email to Recipient: delivery-success.html completion notice
 *     - SMS to Recipient: Delivery confirmation alert
 * - IN_TRANSIT / Other intermediate statuses:
 *     - Recorded for diagnostics without unnecessary customer message noise
 */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class NotificationRouter implements NotificationDispatcher {

    public static final String TEMPLATE_PARCEL_BOOKED = "email/parcel-booked";
    public static final String TEMPLATE_OUT_FOR_DELIVERY = "email/out-for-delivery";
    public static final String TEMPLATE_DELIVERY_SUCCESS = "email/delivery-success";

    private final EmailNotificationDispatcher emailDispatcher;
    private final MockSmsSender smsSender;

    @Override
    public void dispatch(ParcelStatusUpdatedEvent event) {
        if (event == null) {
            log.warn("Cannot route notification: event is null.");
            return;
        }

        if (event.getCurrentStatus() == null || event.getCurrentStatus().isBlank()) {
            log.warn("Cannot route notification: currentStatus is missing for trackingNumber={}",
                    event.getTrackingNumber());
            return;
        }

        String status = event.getCurrentStatus().trim().toUpperCase();
        Map<String, Object> model = buildTemplateModel(event);

        switch (status) {
            case "CREATED", "BOOKED" -> handleParcelCreated(event, model);
            case "OUT_FOR_DELIVERY" -> handleOutForDelivery(event, model);
            case "DELIVERED" -> handleDelivered(event, model);
            default -> log.info("Status transition '{}' recorded for trackingNumber={}. No customer notification required.",
                    status, event.getTrackingNumber());
        }
    }

    private void handleParcelCreated(ParcelStatusUpdatedEvent event, Map<String, Object> model) {
        String targetEmail = event.getSenderEmail() != null && !event.getSenderEmail().isBlank()
                ? event.getSenderEmail()
                : event.getRecipientEmail();

        if (targetEmail != null && !targetEmail.isBlank()) {
            String subject = "SwiftDrop - Parcel Booking Confirmed: " + event.getTrackingNumber();
            emailDispatcher.sendHtmlEmail(targetEmail, subject, TEMPLATE_PARCEL_BOOKED, model);
        } else {
            log.warn("No sender/recipient email available for booking confirmation: trackingNumber={}",
                    event.getTrackingNumber());
        }
    }

    private void handleOutForDelivery(ParcelStatusUpdatedEvent event, Map<String, Object> model) {
        // 1. Dispatch HTML Email to recipient
        if (event.getRecipientEmail() != null && !event.getRecipientEmail().isBlank()) {
            String subject = "SwiftDrop - Your Parcel is Out for Delivery: " + event.getTrackingNumber();
            emailDispatcher.sendHtmlEmail(event.getRecipientEmail(), subject, TEMPLATE_OUT_FOR_DELIVERY, model);
        } else {
            log.warn("Recipient email missing for OUT_FOR_DELIVERY notification: trackingNumber={}",
                    event.getTrackingNumber());
        }

        // 2. Dispatch simulated SMS to recipient
        if (event.getRecipientPhone() != null && !event.getRecipientPhone().isBlank()) {
            String courier = event.getCourierName() != null ? event.getCourierName() : "SwiftDrop Courier";
            String smsText = String.format("SwiftDrop: Your parcel %s is out for delivery today with %s. Track live: http://localhost:3000/track/%s",
                    event.getTrackingNumber(),
                    courier,
                    event.getTrackingNumber());
            smsSender.sendSms(event.getRecipientPhone(), smsText);
        } else {
            log.info("No recipient phone provided for OUT_FOR_DELIVERY SMS: trackingNumber={}",
                    event.getTrackingNumber());
        }
    }

    private void handleDelivered(ParcelStatusUpdatedEvent event, Map<String, Object> model) {
        // 1. Dispatch HTML Email to recipient
        if (event.getRecipientEmail() != null && !event.getRecipientEmail().isBlank()) {
            String subject = "SwiftDrop - Parcel Delivered Successfully: " + event.getTrackingNumber();
            emailDispatcher.sendHtmlEmail(event.getRecipientEmail(), subject, TEMPLATE_DELIVERY_SUCCESS, model);
        } else {
            log.warn("Recipient email missing for DELIVERED notification: trackingNumber={}",
                    event.getTrackingNumber());
        }

        // 2. Dispatch simulated SMS to recipient
        if (event.getRecipientPhone() != null && !event.getRecipientPhone().isBlank()) {
            String smsText = String.format("SwiftDrop: Your parcel %s has been successfully delivered. Thank you for using SwiftDrop!",
                    event.getTrackingNumber());
            smsSender.sendSms(event.getRecipientPhone(), smsText);
        } else {
            log.info("No recipient phone provided for DELIVERED SMS: trackingNumber={}",
                    event.getTrackingNumber());
        }
    }

    private Map<String, Object> buildTemplateModel(ParcelStatusUpdatedEvent event) {
        Map<String, Object> model = new HashMap<>();
        model.put("trackingNumber", event.getTrackingNumber());
        model.put("currentStatus", event.getCurrentStatus());
        model.put("previousStatus", event.getPreviousStatus());
        model.put("recipientName", event.getRecipientName());
        model.put("courierName", event.getCourierName());
        model.put("courierPhone", event.getCourierPhone());
        model.put("deliveryNote", event.getDeliveryNote());
        model.put("timestamp", event.getTimestamp() != null
                ? event.getTimestamp().toString()
                : Instant.now().toString());
        return model;
    }
}

