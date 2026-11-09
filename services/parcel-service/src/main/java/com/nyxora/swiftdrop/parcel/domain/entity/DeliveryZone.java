package com.nyxora.swiftdrop.parcel.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Represents a logical Sri Lankan delivery zone (e.g., a district or sub-district).
 * Zone codes follow the format: {@code <PROVINCE>-<DISTRICT>-<INDEX>}, e.g. {@code WP-COL-01}.
 *
 * <p>Maps to the {@code delivery_zones} table in {@code parcel_db}.
 */
@Entity
@Table(name = "delivery_zones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "zone_code", nullable = false, unique = true, length = 20)
    private String zoneCode;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "district", nullable = false, length = 50)
    private String district;

    @Column(name = "base_surcharge", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal baseSurcharge = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;
}

