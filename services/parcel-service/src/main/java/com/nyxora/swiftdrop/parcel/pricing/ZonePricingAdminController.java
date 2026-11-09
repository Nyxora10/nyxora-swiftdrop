package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.pricing.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Member 3 — Dedicated Admin Controller for Delivery Zone and Pricing Rule management.
 *
 * <p>All endpoints are protected by {@code ROLE_ADMIN} enforced at the API Gateway perimeter.
 * The gateway injects {@code X-User-Role: ROLE_ADMIN} which is validated by
 * {@link com.nyxora.swiftdrop.parcel.config.SecurityConfig}.
 *
 * <p>Base paths:
 * <ul>
 *   <li>{@code GET/POST/PUT /api/v1/zones/**}</li>
 *   <li>{@code GET/POST/PUT /api/v1/pricing/rules/**}</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Admin: Zone & Pricing Management", description = "ROLE_ADMIN — Delivery zone and inter-zone pricing rule CRUD operations")
@SecurityRequirement(name = "BearerAuth")
public class ZonePricingAdminController {

    private final ZonePricingService zonePricingService;

    // ══════════════════════════════════════════════════════════════════
    //  DELIVERY ZONE ENDPOINTS  (/api/v1/zones)
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/zones")
    @Operation(summary = "List all delivery zones",
               description = "Returns all Sri Lankan delivery zones (active and inactive). Requires ROLE_ADMIN.")
    public ResponseEntity<List<DeliveryZoneResponse>> getAllZones(
            @Parameter(description = "Filter to only return active zones")
            @RequestParam(required = false, defaultValue = "false") boolean activeOnly
    ) {
        List<DeliveryZone> zones = activeOnly
                ? zonePricingService.getActiveZones()
                : zonePricingService.getAllZones();
        return ResponseEntity.ok(zones.stream()
                .map(DeliveryZoneResponse::from)
                .toList());
    }

    @GetMapping("/api/v1/zones/{id}")
    @Operation(summary = "Get zone by ID",
               description = "Returns a single delivery zone by its database ID. Requires ROLE_ADMIN.")
    public ResponseEntity<DeliveryZoneResponse> getZoneById(@PathVariable Integer id) {
        return ResponseEntity.ok(DeliveryZoneResponse.from(zonePricingService.getZoneById(id)));
    }

    @PostMapping("/api/v1/zones")
    @Operation(summary = "Create a new delivery zone",
               description = "Creates a new Sri Lankan delivery zone. Zone codes must follow WP-COL-01 format. Requires ROLE_ADMIN.")
    public ResponseEntity<DeliveryZoneResponse> createZone(@Valid @RequestBody CreateZoneRequest request) {
        DeliveryZone created = zonePricingService.createZone(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(DeliveryZoneResponse.from(created));
    }

    @PutMapping("/api/v1/zones/{id}")
    @Operation(summary = "Update a delivery zone",
               description = "Partially updates an existing zone. Only provided fields are changed. Requires ROLE_ADMIN.")
    public ResponseEntity<DeliveryZoneResponse> updateZone(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateZoneRequest request
    ) {
        return ResponseEntity.ok(DeliveryZoneResponse.from(zonePricingService.updateZone(id, request)));
    }

    // ══════════════════════════════════════════════════════════════════
    //  PRICING RULE ENDPOINTS  (/api/v1/pricing/rules)
    // ══════════════════════════════════════════════════════════════════

    @GetMapping("/api/v1/pricing/rules")
    @Operation(summary = "List pricing rules",
               description = "Returns all inter-zone pricing rules. Optionally filter by origin zone ID. Requires ROLE_ADMIN.")
    public ResponseEntity<List<PricingRuleResponse>> getPricingRules(
            @Parameter(description = "Optional: filter by origin zone ID")
            @RequestParam(required = false) Integer originZoneId
    ) {
        return ResponseEntity.ok(zonePricingService.getPricingRules(originZoneId).stream()
                .map(PricingRuleResponse::from)
                .toList());
    }

    @GetMapping("/api/v1/pricing/rules/{id}")
    @Operation(summary = "Get pricing rule by ID",
               description = "Returns a single pricing rule by its database ID. Requires ROLE_ADMIN.")
    public ResponseEntity<PricingRuleResponse> getPricingRuleById(@PathVariable Integer id) {
        return ResponseEntity.ok(PricingRuleResponse.from(zonePricingService.getPricingRuleById(id)));
    }

    @PostMapping("/api/v1/pricing/rules")
    @Operation(summary = "Create a pricing rule",
               description = "Defines a new inter-zone base rate and per-kg rate. Unique per origin+destination+size. Requires ROLE_ADMIN.")
    public ResponseEntity<PricingRuleResponse> createPricingRule(
            @Valid @RequestBody CreatePricingRuleRequest request
    ) {
        PricingRule created = zonePricingService.createPricingRule(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PricingRuleResponse.from(created));
    }

    @PutMapping("/api/v1/pricing/rules/{id}")
    @Operation(summary = "Update a pricing rule",
               description = "Adjusts base fee, rate-per-kg, or size multiplier for an existing rule. Requires ROLE_ADMIN.")
    public ResponseEntity<PricingRuleResponse> updatePricingRule(
            @PathVariable Integer id,
            @Valid @RequestBody UpdatePricingRuleRequest request
    ) {
        return ResponseEntity.ok(PricingRuleResponse.from(zonePricingService.updatePricingRule(id, request)));
    }
}
