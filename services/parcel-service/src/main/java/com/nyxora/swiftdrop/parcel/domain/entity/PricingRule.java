package com.nyxora.swiftdrop.parcel.domain.entity;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Defines the inter-zone pricing matrix entry used by the Dynamic Pricing Strategy Engine.
 * Maps to the {@code pricing_rules} table in {@code parcel_db}.
 */
@Entity
@Table(
        name = "pricing_rules",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_pricing_rule",
                        columnNames = {"origin_zone_id", "destination_zone_id", "parcel_size"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "origin_zone_id", nullable = false)
    private DeliveryZone originZone;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "destination_zone_id", nullable = false)
    private DeliveryZone destinationZone;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "rate_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_size", nullable = false, length = 30)
    private ParcelSize parcelSize;

    @Column(name = "size_multiplier", nullable = false, precision = 4, scale = 2)
    @Builder.Default
    private BigDecimal sizeMultiplier = BigDecimal.ONE;
}

