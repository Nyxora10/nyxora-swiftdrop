package com.nyxora.swiftdrop.parcel.booking.event;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published to {@code swiftdrop.parcel.events} with routing key {@code parcel.created}
 * when a customer or merchant successfully books a new delivery parcel.
 */
public record ParcelCreatedEvent(
        UUID parcelId,
        String trackingNumber,
        UUID senderUserId,
        UUID merchantId,
        Integer originZoneId,
        Integer destinationZoneId,
        String pickupAddress,
        String dropoffAddress,
        String recipientName,
        String recipientPhone,
        BigDecimal weightKg,
        String parcelSize,
        String quoteTier,
        BigDecimal estimatedFee,
        String status,
        Instant createdAt
) implements Serializable {}

