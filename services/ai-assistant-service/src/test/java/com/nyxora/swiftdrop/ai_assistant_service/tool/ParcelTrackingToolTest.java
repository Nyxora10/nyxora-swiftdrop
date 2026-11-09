package com.nyxora.swiftdrop.ai_assistant_service.tool;

import com.nyxora.swiftdrop.ai_assistant_service.client.ParcelServiceClient;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.TrackingTimelineEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class ParcelTrackingToolTest {

    private ParcelServiceClient parcelServiceClient;
    private ParcelTrackingTool parcelTrackingTool;

    @BeforeEach
    void setUp() {
        parcelServiceClient = mock(ParcelServiceClient.class);
        parcelTrackingTool = new ParcelTrackingTool(parcelServiceClient);
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when tracking number is null or blank")
    void shouldReturnInvalidInputForBlankTrackingNumber() {
        ParcelTrackingResult nullResult = parcelTrackingTool.getParcelTracking(null);
        assertThat(nullResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(nullResult.message()).contains("must not be empty");

        ParcelTrackingResult blankResult = parcelTrackingTool.getParcelTracking("   ");
        assertThat(blankResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(blankResult.message()).contains("must not be empty");

        verifyNoInteractions(parcelServiceClient);
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when tracking number format is invalid")
    void shouldReturnInvalidInputForMalformedTrackingNumber() {
        ParcelTrackingResult invalidResult = parcelTrackingTool.getParcelTracking("INVALID-123");
        assertThat(invalidResult.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(invalidResult.message()).contains("Invalid tracking number format");

        verifyNoInteractions(parcelServiceClient);
    }

    @Test
    @DisplayName("Should return INVALID_INPUT when parcel is not found in parcel service")
    void shouldReturnInvalidInputWhenParcelNotFound() {
        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.empty());

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("sd-20261107-7k9p2x");

        assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        assertThat(result.message()).contains("was not found");
        verify(parcelServiceClient).getTracking("SD-20261107-7K9P2X");
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when downstream parcel service throws RestClientException")
    void shouldReturnUnavailableWhenDownstreamThrowsException() {
        when(parcelServiceClient.getTracking(anyString()))
                .thenThrow(new RestClientException("Connection refused"));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SWIFT-98765");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
        assertThat(result.message()).contains("temporarily unavailable");
    }

    @Test
    @DisplayName("Should return SUCCESS with sanitized delivery notes and no recipient PII")
    void shouldReturnSuccessWithSanitizedTimelineAndNoRecipientPii() {
        TrackingTimelineEventDto milestone1 = new TrackingTimelineEventDto(
                "BOOKED",
                Instant.parse("2026-11-07T09:00:00Z"),
                "Booked online by sender."
        );
        TrackingTimelineEventDto milestone2 = new TrackingTimelineEventDto(
                "IN_TRANSIT",
                Instant.parse("2026-11-07T11:30:00Z"),
                "Dispatched to courier. Call driver on 0771234567 or email driver@nyxora.com. Dropped at No. 45/2 Peradeniya Road."
        );

        ParcelTrackingResponseDto backendResponse = new ParcelTrackingResponseDto(
                "SD-20261107-7K9P2X",
                "IN_TRANSIT",
                "WP-COL-01",
                "Colombo Central",
                "CP-KAN-01",
                "Kandy Metro",
                "K***l P***a",
                "07*****4567",
                "No. ***, Kandy Road, Peradeniya",
                new BigDecimal("2.50"),
                "MEDIUM",
                Instant.parse("2026-11-07T09:00:00Z"),
                List.of(milestone1, milestone2)
        );

        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.of(backendResponse));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.trackingNumber()).isEqualTo("SD-20261107-7K9P2X");
        assertThat(result.currentStatus()).isEqualTo("IN_TRANSIT");
        assertThat(result.originZone()).isEqualTo("Colombo Central");
        assertThat(result.destinationZone()).isEqualTo("Kandy Metro");

        // Verify timeline milestones are returned
        assertThat(result.timeline()).hasSize(2);
        assertThat(result.timeline().get(0).status()).isEqualTo("BOOKED");
        assertThat(result.timeline().get(0).deliveryNote()).isEqualTo("Booked online by sender.");

        // Verify delivery note sanitization
        String sanitizedNote = result.timeline().get(1).deliveryNote();
        assertThat(sanitizedNote).doesNotContain("0771234567");
        assertThat(sanitizedNote).contains("07*****4567");
        assertThat(sanitizedNote).doesNotContain("driver@nyxora.com");
        assertThat(sanitizedNote).contains("d***r@nyxora.com");
    }
}
