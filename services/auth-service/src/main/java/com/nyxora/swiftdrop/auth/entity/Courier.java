package com.nyxora.swiftdrop.auth.entity;

import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Courier / Rider profile entity linked 1:1 to a User account.
 * Tracks vehicle details, availability, and active delivery load.
 */
@Entity
@Table(name = "couriers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Courier extends BaseAuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 50)
    private VehicleType vehicleType;

    @Column(name = "license_plate", nullable = false, unique = true, length = 50)
    private String licensePlate;

    @Column(name = "is_available", nullable = false)
    @Builder.Default
    private Boolean isAvailable = true;

    @Column(name = "active_delivery_count", nullable = false)
    @Builder.Default
    private Integer activeDeliveryCount = 0;

    /**
     * Logical reference to parcel_db.delivery_zones.id.
     * Determines geographic operational area for dispatcher filtering (UC18, UC19).
     */
    @Column(name = "assigned_zone_id")
    private Integer assignedZoneId;
}

