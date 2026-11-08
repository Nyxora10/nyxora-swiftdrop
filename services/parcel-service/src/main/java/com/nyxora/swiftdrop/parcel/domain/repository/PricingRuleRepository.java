package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PricingRule}.
 * Provides the inter-zone pricing matrix lookup capabilities used
 * by the Dynamic Pricing Strategy Engine.
 */
@Repository
public interface PricingRuleRepository extends JpaRepository<PricingRule, Integer> {

    /**
     * Primary lookup used by the Pricing Engine to find the applicable rule
     * for a given origin → destination zone pair and parcel size.
     */
    Optional<PricingRule> findByOriginZone_IdAndDestinationZone_IdAndParcelSize(
            Integer originZoneId,
            Integer destinationZoneId,
            ParcelSize parcelSize
    );

    /** Returns all rules for a specific origin zone (used by admin listing). */
    List<PricingRule> findByOriginZone_Id(Integer originZoneId);

    /** Returns all rules for a specific destination zone (used by admin listing). */
    List<PricingRule> findByDestinationZone_Id(Integer destinationZoneId);
}

