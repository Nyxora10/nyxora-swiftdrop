package com.nyxora.swiftdrop.parcel.config;

import com.nyxora.swiftdrop.parcel.domain.entity.DefaultPricingRule;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.RouteType;
import com.nyxora.swiftdrop.parcel.domain.repository.DefaultPricingRuleRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Verifies the 25 district hubs and the six fallback pricing rules at startup.
 * Existing exact-route pricing rules are preserved as optional overrides.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParcelDataInitializer implements CommandLineRunner {

    private final DeliveryZoneRepository deliveryZoneRepository;
    private final DefaultPricingRuleRepository defaultPricingRuleRepository;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[ParcelDataInitializer] Verifying 25 district hubs and default pricing...");
        seedDeliveryZones();
        seedDefaultPricingRules();
        log.info("[ParcelDataInitializer] Database verification complete.");
    }

    private void seedDeliveryZones() {
        List<ZoneSeed> districtHubs = List.of(
                new ZoneSeed("WP-COL-01", "Colombo Central Hub", "Colombo", "No. 120, Baseline Road, Colombo 09", "50.00"),
                new ZoneSeed("WP-GAM-01", "Gampaha District Hub", "Gampaha", "No. 45, Colombo Road, Gampaha", "75.00"),
                new ZoneSeed("WP-KAL-01", "Kalutara District Hub", "Kalutara", "No. 78, Galle Road, Kalutara South", "70.00"),
                new ZoneSeed("CP-KAN-01", "Kandy District Hub", "Kandy", "No. 32, William Gopallawa Mawatha, Kandy", "100.00"),
                new ZoneSeed("CP-MTL-01", "Matale District Hub", "Matale", "No. 18, Kandy-Jaffna Highway, Matale", "110.00"),
                new ZoneSeed("CP-NUW-01", "Nuwara Eliya District Hub", "Nuwara Eliya", "No. 25, Badulla Road, Nuwara Eliya", "140.00"),
                new ZoneSeed("SP-GAL-01", "Galle District Hub", "Galle", "No. 90, Wakwella Road, Galle", "120.00"),
                new ZoneSeed("SP-MTR-01", "Matara District Hub", "Matara", "No. 55, Akuressa Road, Matara", "130.00"),
                new ZoneSeed("SP-HAM-01", "Hambantota District Hub", "Hambantota", "No. 14, Main Street, Hambantota", "150.00"),
                new ZoneSeed("NP-JAF-01", "Jaffna District Hub", "Jaffna", "No. 64, KKS Road, Jaffna", "200.00"),
                new ZoneSeed("NP-KIL-01", "Kilinochchi District Hub", "Kilinochchi", "No. 22, A9 Road, Kilinochchi", "210.00"),
                new ZoneSeed("NP-MAN-01", "Mannar District Hub", "Mannar", "No. 11, Hospital Road, Mannar", "220.00"),
                new ZoneSeed("NP-MUL-01", "Mullaitivu District Hub", "Mullaitivu", "No. 16, Beach Road, Mullaitivu", "230.00"),
                new ZoneSeed("NP-VAV-01", "Vavuniya District Hub", "Vavuniya", "No. 80, Kandy Road, Vavuniya", "180.00"),
                new ZoneSeed("EP-BAT-01", "Batticaloa District Hub", "Batticaloa", "No. 36, Trinco Road, Batticaloa", "190.00"),
                new ZoneSeed("EP-AMP-01", "Ampara District Hub", "Ampara", "No. 27, D.S. Senanayake Road, Ampara", "200.00"),
                new ZoneSeed("EP-TRI-01", "Trincomalee District Hub", "Trincomalee", "No. 48, Kandy Road, Trincomalee", "195.00"),
                new ZoneSeed("NW-KUR-01", "Kurunegala District Hub", "Kurunegala", "No. 70, Dambulla Road, Kurunegala", "90.00"),
                new ZoneSeed("NW-PUT-01", "Puttalam District Hub", "Puttalam", "No. 29, Colombo Road, Puttalam", "120.00"),
                new ZoneSeed("NC-ANU-01", "Anuradhapura District Hub", "Anuradhapura", "No. 88, Maithripala Senanayake Mawatha, Anuradhapura", "160.00"),
                new ZoneSeed("NC-POL-01", "Polonnaruwa District Hub", "Polonnaruwa", "No. 21, Habarana Road, Kaduruwela", "170.00"),
                new ZoneSeed("UV-BAD-01", "Badulla District Hub", "Badulla", "No. 40, Passara Road, Badulla", "165.00"),
                new ZoneSeed("UV-MON-01", "Monaragala District Hub", "Monaragala", "No. 19, Wellawaya Road, Monaragala", "180.00"),
                new ZoneSeed("SB-RAT-01", "Ratnapura District Hub", "Ratnapura", "No. 66, Colombo Road, Ratnapura", "110.00"),
                new ZoneSeed("SB-KEG-01", "Kegalle District Hub", "Kegalle", "No. 34, Colombo-Kandy Road, Kegalle", "95.00")
        );

        int created = 0;
        int backfilled = 0;
        for (ZoneSeed seed : districtHubs) {
            DeliveryZone zone = deliveryZoneRepository.findByZoneCode(seed.zoneCode()).orElse(null);
            if (zone == null) {
                deliveryZoneRepository.save(seed.toEntity());
                created++;
            } else if (zone.getHubLocation() == null || zone.getHubLocation().isBlank()) {
                zone.setHubLocation(seed.hubLocation());
                deliveryZoneRepository.save(zone);
                backfilled++;
            }
        }

        log.info("[ParcelDataInitializer] Verified district hubs: {} created and {} addresses backfilled.",
                created, backfilled);
    }

    private void seedDefaultPricingRules() {
        List<DefaultRuleSeed> seeds = List.of(
                new DefaultRuleSeed(RouteType.SAME_ZONE, ParcelSize.SMALL, "350.00", "80.00", "1.00"),
                new DefaultRuleSeed(RouteType.SAME_ZONE, ParcelSize.MEDIUM, "350.00", "80.00", "1.20"),
                new DefaultRuleSeed(RouteType.SAME_ZONE, ParcelSize.LARGE, "350.00", "80.00", "1.50"),
                new DefaultRuleSeed(RouteType.DIFFERENT_ZONE, ParcelSize.SMALL, "450.00", "80.00", "1.00"),
                new DefaultRuleSeed(RouteType.DIFFERENT_ZONE, ParcelSize.MEDIUM, "450.00", "80.00", "1.20"),
                new DefaultRuleSeed(RouteType.DIFFERENT_ZONE, ParcelSize.LARGE, "450.00", "80.00", "1.50")
        );

        int created = 0;
        for (DefaultRuleSeed seed : seeds) {
            if (defaultPricingRuleRepository
                    .findByRouteTypeAndParcelSize(seed.routeType(), seed.parcelSize())
                    .isEmpty()) {
                defaultPricingRuleRepository.save(seed.toEntity());
                created++;
            }
        }
        log.info("[ParcelDataInitializer] Verified six fallback pricing rules: {} created.", created);
    }

    private record ZoneSeed(
            String zoneCode,
            String zoneName,
            String district,
            String hubLocation,
            String baseSurcharge
    ) {
        private DeliveryZone toEntity() {
            return DeliveryZone.builder()
                    .zoneCode(zoneCode)
                    .zoneName(zoneName)
                    .district(district)
                    .hubLocation(hubLocation)
                    .baseSurcharge(new BigDecimal(baseSurcharge))
                    .isActive(true)
                    .build();
        }
    }

    private record DefaultRuleSeed(
            RouteType routeType,
            ParcelSize parcelSize,
            String baseFee,
            String ratePerKg,
            String sizeMultiplier
    ) {
        private DefaultPricingRule toEntity() {
            return DefaultPricingRule.builder()
                    .routeType(routeType)
                    .parcelSize(parcelSize)
                    .baseFee(new BigDecimal(baseFee))
                    .ratePerKg(new BigDecimal(ratePerKg))
                    .sizeMultiplier(new BigDecimal(sizeMultiplier))
                    .build();
        }
    }
}
