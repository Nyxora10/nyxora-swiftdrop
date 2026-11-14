package com.nyxora.swiftdrop.ai_assistant_service.dto;

/**
 * Publicly accessible delivery zone representation returned by the Parcel Service
 * from {@code GET /api/v1/public/zones}.
 *
 * @param id       Zone ID used in rate quote calculations
 * @param zoneCode Standard zone code (e.g. WP-COL-01)
 * @param zoneName Human-readable zone title (e.g. Colombo Central Metro)
 * @param district Administrative district (e.g. Colombo)
 */
public record PublicDeliveryZoneResponse(
        Integer id,
        String zoneCode,
        String zoneName,
        String district
) {}

