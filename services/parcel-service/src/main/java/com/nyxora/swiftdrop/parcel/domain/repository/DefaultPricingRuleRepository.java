package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.DefaultPricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DefaultPricingRuleRepository extends JpaRepository<DefaultPricingRule, Integer> {

    Optional<DefaultPricingRule> findByRouteTypeAndParcelSize(RouteType routeType, ParcelSize parcelSize);
}
