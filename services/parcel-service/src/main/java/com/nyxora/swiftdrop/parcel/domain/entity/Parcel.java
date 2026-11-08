package com.nyxora.swiftdrop.parcel.domain.entity;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Core parcel delivery booking entity.
 * Maps to the {@code parcels} table in {@code parcel_db}.
 */
@Entity
@Table(
        name = "parcels",
        indexes = {
                @Index(name = "idx_parcels_tracking",       columnList = "tracking_number"),
                @Index(name = "idx_parcels_sender",         columnList = "sender_user_id"),
                @Index(name = "idx_parcels_merchant",       columnList = "merchant_id"),
                @Index(name = "idx_parcels_courier_status", columnList = "assigned_courier_id, current_status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Parcel extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tracking_number", nullable = false, unique = true, length = 50)
    private String trackingNumber;

    @Column(name = "sender_user_id", nullable = false)
    private UUID senderUserId;

    @Column(name = "merchant_id")
    private UUID merchantId;

    @Column(name = "assigned_courier_id")
    private UUID assignedCourierId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "origin_zone_id", nullable = false)
    private DeliveryZone originZone;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_zone_id", nullable = false)
    private DeliveryZone destinationZone;

    @Column(name = "pickup_address", nullable = false, columnDefinition = "TEXT")
    private String pickupAddress;

    @Column(name = "dropoff_address", nullable = false, columnDefinition = "TEXT")
    private String dropoffAddress;

    @Column(name = "recipient_name", nullable = false, length = 150)
    private String recipientName;

    @Column(name = "recipient_phone", nullable = false, length = 20)
    private String recipientPhone;

    @Column(name = "weight_kg", nullable = false, precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_size", nullable = false, length = 30)
    private ParcelSize parcelSize;

    @Column(name = "estimated_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedFee;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 40)
    @Builder.Default
    private ParcelStatus currentStatus = ParcelStatus.CREATED;
}

