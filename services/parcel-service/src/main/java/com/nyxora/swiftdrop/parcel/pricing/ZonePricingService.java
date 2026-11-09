package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import com.nyxora.swiftdrop.parcel.exception.ResourceConflictException;
import com.nyxora.swiftdrop.parcel.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.parcel.pricing.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for Zone and Pricing Rule administration operations.
 * Handles CRUD for {@link DeliveryZone} and {@link PricingRule} entities.
 * All mutation operations are transactional and admin-only.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZonePricingService {

    private final DeliveryZoneRepository zoneRepository;
    private final PricingRuleRepository pricingRuleRepository;

    // ─────────────────────────────────────────────
    //  DELIVERY ZONE OPERATIONS
    // ─────────────────────────────────────────────

    /** Returns all delivery zones (active and inactive). */
    @Transactional(readOnly = true)
    public List<DeliveryZone> getAllZones() {
        return zoneRepository.findAll();
    }

    /** Returns only zones currently open for booking. */
    @Transactional(readOnly = true)
    public List<DeliveryZone> getActiveZones() {
        return zoneRepository.findByIsActiveTrue();
    }

    /** Returns a single zone by its database ID. */
    @Transactional(readOnly = true)
    public DeliveryZone getZoneById(Integer id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone not found: " + id));
    }

    /**
     * Creates a new Sri Lankan delivery zone.
     *
     * @throws IllegalArgumentException if the zone code already exists
     */
    @Transactional
    public DeliveryZone createZone(CreateZoneRequest request) {
        if (zoneRepository.existsByZoneCode(request.getZoneCode())) {
            throw new ResourceConflictException("Zone code already exists: " + request.getZoneCode());
        }
        DeliveryZone zone = DeliveryZone.builder()
                .zoneCode(request.getZoneCode())
                .zoneName(request.getZoneName())
                .district(request.getDistrict())
                .baseSurcharge(request.getBaseSurcharge())
                .isActive(true)
                .build();
        return zoneRepository.save(zone);
    }

    /**
     * Partially updates an existing delivery zone.
     * Only fields present (non-null) in the request are applied.
     */
    @Transactional
    public DeliveryZone updateZone(Integer id, UpdateZoneRequest request) {
        DeliveryZone zone = getZoneById(id);
        if (request.getZoneName() == null
                && request.getDistrict() == null
                && request.getBaseSurcharge() == null
                && request.getIsActive() == null) {
            throw new IllegalArgumentException("At least one zone field must be provided for update");
        }
        if (request.getZoneName()      != null) zone.setZoneName(request.getZoneName());
        if (request.getDistrict()      != null) zone.setDistrict(request.getDistrict());
        if (request.getBaseSurcharge() != null) zone.setBaseSurcharge(request.getBaseSurcharge());
        if (request.getIsActive()      != null) zone.setIsActive(request.getIsActive());
        return zoneRepository.save(zone);
    }

    // ─────────────────────────────────────────────
    //  PRICING RULE OPERATIONS
    // ─────────────────────────────────────────────

    /** Returns all inter-zone pricing rules. Optionally filtered by origin zone. */
    @Transactional(readOnly = true)
    public List<PricingRule> getPricingRules(Integer originZoneId) {
        if (originZoneId != null) {
            return pricingRuleRepository.findByOriginZone_Id(originZoneId);
        }
        return pricingRuleRepository.findAll();
    }

    /** Returns a single pricing rule by its database ID. */
    @Transactional(readOnly = true)
    public PricingRule getPricingRuleById(Integer id) {
        return pricingRuleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pricing rule not found: " + id));
    }

    /**
     * Creates a new inter-zone pricing rule.
     *
     * @throws IllegalArgumentException if a rule already exists for the same zone pair + size
     */
    @Transactional
    public PricingRule createPricingRule(CreatePricingRuleRequest request) {
        if (request.getOriginZoneId().equals(request.getDestinationZoneId())) {
            throw new IllegalArgumentException("Origin and destination zones must be different");
        }
        DeliveryZone originZone = zoneRepository.findById(request.getOriginZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Origin zone not found: " + request.getOriginZoneId()));
        DeliveryZone destZone = zoneRepository.findById(request.getDestinationZoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination zone not found: " + request.getDestinationZoneId()));

        pricingRuleRepository.findByOriginZone_IdAndDestinationZone_IdAndParcelSize(
                request.getOriginZoneId(), request.getDestinationZoneId(), request.getParcelSize()
        ).ifPresent(existing -> {
            throw new ResourceConflictException(String.format(
                    "Pricing rule already exists for origin=%d, destination=%d, size=%s",
                    request.getOriginZoneId(), request.getDestinationZoneId(), request.getParcelSize()));
        });

        PricingRule rule = PricingRule.builder()
                .originZone(originZone)
                .destinationZone(destZone)
                .baseFee(request.getBaseFee())
                .ratePerKg(request.getRatePerKg())
                .parcelSize(request.getParcelSize())
                .sizeMultiplier(request.getSizeMultiplier())
                .build();
        return pricingRuleRepository.save(rule);
    }

    /**
     * Partially updates an existing pricing rule.
     * Only non-null fields in the request are applied.
     */
    @Transactional
    public PricingRule updatePricingRule(Integer id, UpdatePricingRuleRequest request) {
        PricingRule rule = getPricingRuleById(id);
        if (request.getBaseFee() == null
                && request.getRatePerKg() == null
                && request.getSizeMultiplier() == null) {
            throw new IllegalArgumentException("At least one pricing field must be provided for update");
        }
        if (request.getBaseFee()        != null) rule.setBaseFee(request.getBaseFee());
        if (request.getRatePerKg()      != null) rule.setRatePerKg(request.getRatePerKg());
        if (request.getSizeMultiplier() != null) rule.setSizeMultiplier(request.getSizeMultiplier());
        return pricingRuleRepository.save(rule);
    }
}
