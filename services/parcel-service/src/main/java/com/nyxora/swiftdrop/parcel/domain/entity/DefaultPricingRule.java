package com.nyxora.swiftdrop.parcel.domain.entity;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * Default pricing used when no exact route override exists.
 * Six rows cover same-zone and different-zone routes for all three parcel sizes.
 */
@Entity
@Table(
        name = "default_pricing_rules",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_default_pricing_rule",
                columnNames = {"route_type", "parcel_size"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefaultPricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_type", nullable = false, length = 30)
    private RouteType routeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "parcel_size", nullable = false, length = 30)
    private ParcelSize parcelSize;

    @Column(name = "base_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseFee;

    @Column(name = "rate_per_kg", nullable = false, precision = 10, scale = 2)
    private BigDecimal ratePerKg;

    @Column(name = "size_multiplier", nullable = false, precision = 4, scale = 2)
    private BigDecimal sizeMultiplier;

    public PricingRule toEffectiveRule(DeliveryZone originZone, DeliveryZone destinationZone) {
        return PricingRule.builder()
                .originZone(originZone)
                .destinationZone(destinationZone)
                .baseFee(baseFee)
                .ratePerKg(ratePerKg)
                .parcelSize(parcelSize)
                .sizeMultiplier(sizeMultiplier)
                .build();
    }
}
