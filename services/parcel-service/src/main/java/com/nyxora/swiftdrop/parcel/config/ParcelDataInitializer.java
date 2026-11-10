package com.nyxora.swiftdrop.parcel.config;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Startup Data Initializer for parcel_db.
 * Primes:
 * 1. Default Sri Lankan Delivery Zones (Colombo, Gampaha, Kandy, Galle, Jaffna, Kurunegala)
 * 2. Complete Inter-Zone Pricing Rule Matrix across all parcel sizes (SMALL, MEDIUM, LARGE)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParcelDataInitializer implements CommandLineRunner {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final PricingRuleRepository pricingRuleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ParcelDataInitializer] Verifying parcel_db default zones and pricing rules...");
        List<DeliveryZone> zones = seedDeliveryZones();
        seedPricingRules(zones);
        log.info("[ParcelDataInitializer] Database verification complete.");
    }

    private List<DeliveryZone> seedDeliveryZones() {
        List<DeliveryZone> existing = deliveryZoneRepository.findAll();
        if (!existing.isEmpty()) {
            log.info("[ParcelDataInitializer] {} delivery zones already present.", existing.size());
            return existing;
        }

        List<DeliveryZone> toSeed = List.of(
                DeliveryZone.builder().zoneCode("WP-COL-01").zoneName("Colombo Central Metro").district("Colombo").baseSurcharge(new BigDecimal("50.00")).isActive(true).build(),
                DeliveryZone.builder().zoneCode("WP-GAM-01").zoneName("Gampaha Industrial Hub").district("Gampaha").baseSurcharge(new BigDecimal("75.00")).isActive(true).build(),
                DeliveryZone.builder().zoneCode("CP-KAN-01").zoneName("Kandy Central Highlands").district("Kandy").baseSurcharge(new BigDecimal("100.00")).isActive(true).build(),
                DeliveryZone.builder().zoneCode("SP-GAL-01").zoneName("Galle Southern Corridor").district("Galle").baseSurcharge(new BigDecimal("120.00")).isActive(true).build(),
                DeliveryZone.builder().zoneCode("NP-JAF-01").zoneName("Jaffna Northern Peninsula").district("Jaffna").baseSurcharge(new BigDecimal("200.00")).isActive(true).build(),
                DeliveryZone.builder().zoneCode("NW-KUR-01").zoneName("Kurunegala Wayamba Zone").district("Kurunegala").baseSurcharge(new BigDecimal("90.00")).isActive(true).build()
        );

        List<DeliveryZone> saved = deliveryZoneRepository.saveAll(toSeed);
        log.info("[ParcelDataInitializer] Successfully seeded {} default delivery zones.", saved.size());
        return saved;
    }

    private void seedPricingRules(List<DeliveryZone> zones) {
        if (pricingRuleRepository.count() > 0) {
            log.info("[ParcelDataInitializer] Pricing rules already populated.");
            return;
        }

        List<PricingRule> rulesToSeed = new ArrayList<>();
        BigDecimal standardRatePerKg = new BigDecimal("80.00");

        for (DeliveryZone origin : zones) {
            for (DeliveryZone dest : zones) {
                boolean isIntraZone = origin.getId().equals(dest.getId());
                BigDecimal baseFee = isIntraZone ? new BigDecimal("350.00") : new BigDecimal("450.00");

                for (ParcelSize size : ParcelSize.values()) {
                    BigDecimal multiplier = switch (size) {
                        case SMALL -> new BigDecimal("1.00");
                        case MEDIUM -> new BigDecimal("1.20");
                        case LARGE -> new BigDecimal("1.50");
                    };

                    rulesToSeed.add(PricingRule.builder()
                            .originZone(origin)
                            .destinationZone(dest)
                            .baseFee(baseFee)
                            .ratePerKg(standardRatePerKg)
                            .parcelSize(size)
                            .sizeMultiplier(multiplier)
                            .build());
                }
            }
        }

        pricingRuleRepository.saveAll(rulesToSeed);
        log.info("[ParcelDataInitializer] Successfully seeded {} inter-zone pricing matrix rules.", rulesToSeed.size());
    }
}

