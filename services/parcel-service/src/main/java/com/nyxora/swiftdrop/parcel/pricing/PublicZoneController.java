package com.nyxora.swiftdrop.parcel.pricing;

import com.nyxora.swiftdrop.parcel.pricing.dto.PublicDeliveryZoneResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public, unauthenticated controller providing read-only active delivery zone lookups.
 * Allows customers, third-party integrations, and the AI Assistant to discover valid zone IDs
 * for dynamic rate quote requests without administrative privileges.
 */
@RestController
@RequestMapping("/api/v1/public/zones")
@RequiredArgsConstructor
@Tag(name = "Public: Delivery Zones", description = "Public endpoints for exploring active Sri Lankan logistics delivery zones")
public class PublicZoneController {

    private final PublicZoneQueryService publicZoneQueryService;

    @GetMapping
    @Operation(
            summary = "Lookup active delivery zones",
            description = "Returns active delivery zones matching optional search terms or district filters. Operates anonymously without JWT authentication."
    )
    public ResponseEntity<List<PublicDeliveryZoneResponse>> getPublicZones(
            @Parameter(description = "Optional search query matched against zone name, zone code, or district (case-insensitive)")
            @RequestParam(required = false) String search,

            @Parameter(description = "Optional filter by Sri Lankan administrative district (case-insensitive)")
            @RequestParam(required = false) String district
    ) {
        List<PublicDeliveryZoneResponse> zones = publicZoneQueryService.getActiveZones(search, district);
        return ResponseEntity.ok(zones);
    }
}

