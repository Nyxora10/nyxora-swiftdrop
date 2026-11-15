package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;
import com.nyxora.swiftdrop.parcel.domain.repository.DefaultPricingRuleRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * GoF Strategy Pattern — Context / Orchestrator.
 *
 * <p>Coordinates the Dynamic Pricing Strategy selection and fee calculation.
 * Injects all {@link PricingStrategy} implementations from the Spring context
 * and selects the correct one at runtime based on the requested {@link QuoteTier}.
 *
 * <p>Demonstrates the Open/Closed Principle: new pricing tiers can be added by
 * implementing a new {@link PricingStrategy} bean — no modification to this class required.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricingEngine {

    private final List<PricingStrategy> strategies;
    private final PricingRuleRepository pricingRuleRepository;
    private final DefaultPricingRuleRepository defaultPricingRuleRepository;
    private final DeliveryZoneRepository deliveryZoneRepository;

    /**
     * Calculates a delivery fee quote for the given request.
     * Resolves an exact route override first and otherwise uses one of six defaults,
     * selects the matching strategy, and returns an itemized quote.
     *
     * @param request the parcel quote request
     * @return itemized fee breakdown
     * @throws IllegalStateException    if the required default pricing rule is missing
     * @throws IllegalStateException    if no strategy supports the requested tier
     */
    public ParcelQuoteResponse calculateQuote(ParcelQuoteRequest request) {

        log.debug("Calculating quote: origin={}, dest={}, weight={}kg, size={}, tier={}",
                request.getOriginZoneId(), request.getDestinationZoneId(),
                request.getWeightKg(), request.getParcelSize(), request.getTier());

        // Resolve destination zone for surcharge
        DeliveryZone destZone = deliveryZoneRepository.findById(request.getDestinationZoneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Destination zone not found: " + request.getDestinationZoneId()));

        if (!destZone.getIsActive()) {
            throw new IllegalArgumentException(
                    "Destination zone is not active: " + destZone.getZoneCode());
        }

        // Resolve origin zone (validate active)
        DeliveryZone originZone = deliveryZoneRepository.findById(request.getOriginZoneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Origin zone not found: " + request.getOriginZoneId()));

        if (!originZone.getIsActive()) {
            throw new IllegalArgumentException(
                    "Origin zone is not active: " + originZone.getZoneCode());
        }

        RouteType routeType = RouteType.between(
                request.getOriginZoneId(),
                request.getDestinationZoneId());

        // Exact route rules are optional overrides; one of six defaults covers every other pair.
        PricingRule rule = pricingRuleRepository
                .findByOriginZone_IdAndDestinationZone_IdAndParcelSize(
                        request.getOriginZoneId(),
                        request.getDestinationZoneId(),
                        request.getParcelSize())
                .orElseGet(() -> defaultPricingRuleRepository
                        .findByRouteTypeAndParcelSize(routeType, request.getParcelSize())
                        .map(defaultRule -> defaultRule.toEffectiveRule(originZone, destZone))
                        .orElseThrow(() -> new IllegalStateException(
                                String.format("No default pricing rule found for routeType=%s, size=%s",
                                        routeType, request.getParcelSize()))));

        // Select and apply the strategy
        PricingStrategy strategy = strategies.stream()
                .filter(s -> s.supports(request.getTier()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No pricing strategy registered for tier: " + request.getTier()));

        ParcelQuoteResponse quote = strategy.calculateFee(request, rule, destZone);
        log.debug("Quote calculated: totalFee={} using {}", quote.getTotalFee(), strategy.getClass().getSimpleName());

        return quote;
    }
}
