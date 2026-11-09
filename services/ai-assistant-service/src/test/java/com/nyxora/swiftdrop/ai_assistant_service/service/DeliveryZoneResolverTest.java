package com.nyxora.swiftdrop.ai_assistant_service.service;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

class DeliveryZoneResolverTest {

    private ParcelServiceClient parcelServiceClient;
    private DeliveryZoneResolver resolver;

    @BeforeEach
    void setUp() {
        parcelServiceClient = mock(ParcelServiceClient.class);
        resolver = new DeliveryZoneResolver(parcelServiceClient);
    }

    @Test
    @DisplayName("Should return MISSING_LOCATION when location query is null or blank")
    void shouldReturnMissingLocationWhenBlank() {
        ZoneResolutionResult nullResult = resolver.resolveZone(null);
        assertThat(nullResult.status()).isEqualTo(ZoneResolutionResult.Status.MISSING_LOCATION);

        ZoneResolutionResult blankResult = resolver.resolveZone("   ");
        assertThat(blankResult.status()).isEqualTo(ZoneResolutionResult.Status.MISSING_LOCATION);

        verifyNoInteractions(parcelServiceClient);
    }

    @Test
    @DisplayName("Should return RESOLVED when exactly one zone matches")
    void shouldReturnResolvedWhenSingleMatch() {
        PublicDeliveryZoneResponse kandyZone = new PublicDeliveryZoneResponse(2, "CP-KAN-01", "Kandy Metro", "Kandy");
        when(parcelServiceClient.getPublicZones("Kandy", null)).thenReturn(List.of(kandyZone));

        ZoneResolutionResult result = resolver.resolveZone("Kandy");

        assertThat(result.status()).isEqualTo(ZoneResolutionResult.Status.RESOLVED);
        assertThat(result.matchedZone()).isEqualTo(kandyZone);
        assertThat(result.matchedZone().id()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should return RESOLVED when multiple zones return but one is an exact match on name or code")
    void shouldReturnResolvedWhenExactMatchDisambiguates() {
        PublicDeliveryZoneResponse zone1 = new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo");
        PublicDeliveryZoneResponse zone2 = new PublicDeliveryZoneResponse(3, "WP-COL-02", "Colombo South", "Colombo");

        when(parcelServiceClient.getPublicZones("WP-COL-01", null)).thenReturn(List.of(zone1, zone2));

        ZoneResolutionResult result = resolver.resolveZone("WP-COL-01");

        assertThat(result.status()).isEqualTo(ZoneResolutionResult.Status.RESOLVED);
        assertThat(result.matchedZone().id()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should return AMBIGUOUS when multiple zones match without an exact match")
    void shouldReturnAmbiguousWhenMultipleMatches() {
        PublicDeliveryZoneResponse zone1 = new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo");
        PublicDeliveryZoneResponse zone2 = new PublicDeliveryZoneResponse(3, "WP-COL-02", "Colombo South", "Colombo");

        when(parcelServiceClient.getPublicZones("Colombo", null)).thenReturn(List.of(zone1, zone2));

        ZoneResolutionResult result = resolver.resolveZone("Colombo");

        assertThat(result.status()).isEqualTo(ZoneResolutionResult.Status.AMBIGUOUS);
        assertThat(result.candidateZones()).hasSize(2);
        assertThat(result.message()).contains("Multiple delivery zones match");
    }

    @Test
    @DisplayName("Should return NOT_FOUND when no zones match")
    void shouldReturnNotFoundWhenEmpty() {
        when(parcelServiceClient.getPublicZones(anyString(), isNull())).thenReturn(List.of());

        ZoneResolutionResult result = resolver.resolveZone("UnknownPlace");

        assertThat(result.status()).isEqualTo(ZoneResolutionResult.Status.NOT_FOUND);
        assertThat(result.message()).contains("No delivery zone found matching");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when ParcelServiceClient throws RestClientException")
    void shouldReturnUnavailableOnClientException() {
        when(parcelServiceClient.getPublicZones(anyString(), isNull()))
                .thenThrow(new RestClientException("Connection refused"));

        ZoneResolutionResult result = resolver.resolveZone("Colombo");

        assertThat(result.status()).isEqualTo(ZoneResolutionResult.Status.UNAVAILABLE);
        assertThat(result.message()).contains("temporarily unavailable");
    }
}

