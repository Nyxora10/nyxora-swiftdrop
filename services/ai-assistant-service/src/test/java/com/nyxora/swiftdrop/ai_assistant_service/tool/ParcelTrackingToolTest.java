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
        assertThat(sanitizedNote).contains("07****4567");
        assertThat(sanitizedNote).doesNotContain("driver@nyxora.com");
        assertThat(sanitizedNote).contains("d***r@nyxora.com");
    }

    @Test
    @DisplayName("Should sanitize international phone numbers and embedded emails in delivery notes")
    void shouldSanitizeInternationalPhoneAndEmailInDeliveryNote() {
        TrackingTimelineEventDto milestone = new TrackingTimelineEventDto(
                "OUT_FOR_DELIVERY",
                Instant.parse("2026-11-07T14:00:00Z"),
                "Courier assigned: Sunil (+94771234567, sunil.courier@nyxora.com). Delivering parcel safely."
        );

        ParcelTrackingResponseDto backendResponse = new ParcelTrackingResponseDto(
                "SD-20261107-7K9P2X",
                "OUT_FOR_DELIVERY",
                "WP-COL-01",
                "Colombo Central",
                "CP-KAN-01",
                "Kandy Metro",
                "K***l P***a",
                "+94*****4567",
                "*** Main Street, Kandy",
                new BigDecimal("2.50"),
                "MEDIUM",
                Instant.parse("2026-11-07T09:00:00Z"),
                List.of(milestone)
        );

        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.of(backendResponse));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        String note = result.timeline().get(0).deliveryNote();
        assertThat(note).doesNotContain("+94771234567");
        assertThat(note).contains("+94*****4567");
        assertThat(note).doesNotContain("sunil.courier@nyxora.com");
        assertThat(note).contains("s***r@nyxora.com");
    }

    @Test
    @DisplayName("Should provide fallback delivery note when note is null or blank")
    void shouldProvideFallbackDeliveryNoteWhenNoteIsNullOrEmpty() {
        TrackingTimelineEventDto nullNoteEvent = new TrackingTimelineEventDto(
                "DELIVERED",
                Instant.parse("2026-11-07T16:00:00Z"),
                null
        );
        TrackingTimelineEventDto blankNoteEvent = new TrackingTimelineEventDto(
                "RECEIVED",
                Instant.parse("2026-11-07T10:00:00Z"),
                "   "
        );

        ParcelTrackingResponseDto backendResponse = new ParcelTrackingResponseDto(
                "SD-20261107-7K9P2X",
                "DELIVERED",
                null,
                "WP-COL-01",
                null,
                "CP-KAN-01",
                null,
                null,
                null,
                new BigDecimal("1.00"),
                "SMALL",
                Instant.parse("2026-11-07T09:00:00Z"),
                List.of(nullNoteEvent, blankNoteEvent)
        );

        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.of(backendResponse));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.originZone()).isEqualTo("WP-COL-01"); // fallback to zone code when name is null
        assertThat(result.destinationZone()).isEqualTo("CP-KAN-01");
        assertThat(result.timeline().get(0).deliveryNote()).isEqualTo("Status updated to DELIVERED");
        assertThat(result.timeline().get(1).deliveryNote()).isEqualTo("Status updated to RECEIVED");
    }

    @Test
    @DisplayName("Should handle null timeline in parcel response gracefully")
    void shouldHandleNullTimelineGracefully() {
        ParcelTrackingResponseDto backendResponse = new ParcelTrackingResponseDto(
                "SD-20261107-7K9P2X",
                "CREATED",
                "Colombo",
                "WP-COL-01",
                "Kandy",
                "CP-KAN-01",
                null,
                null,
                null,
                new BigDecimal("1.00"),
                "SMALL",
                Instant.parse("2026-11-07T09:00:00Z"),
                null
        );

        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.of(backendResponse));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.timeline()).isEmpty();
    }

    @Test
    @DisplayName("Should return UNAVAILABLE when ParcelServiceClient is null")
    void shouldReturnUnavailableWhenClientIsNull() {
        ParcelTrackingTool toolWithoutClient = new ParcelTrackingTool(null);
        ParcelTrackingResult result = toolWithoutClient.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.UNAVAILABLE);
    }

    @Test
    @DisplayName("Should log and proceed when caller identity is present in UserContextHolder")
    void shouldLogAndProceedWhenCallerIdentityPresent() {
        com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder.setContext(
                new com.nyxora.swiftdrop.ai_assistant_service.security.UserIdentity("user-1", "CUSTOMER", "user@test.com")
        );
        try {
            when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.empty());
            ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");
            assertThat(result.status()).isEqualTo(ToolResultStatus.INVALID_INPUT);
        } finally {
            com.nyxora.swiftdrop.ai_assistant_service.security.UserContextHolder.clear();
        }
    }

    @Test
    @DisplayName("Should handle null status when delivery note is null")
    void shouldHandleNullStatusWhenDeliveryNoteNull() {
        ParcelTrackingResponseDto backendResponse = new ParcelTrackingResponseDto(
                "SD-20261107-7K9P2X",
                "PENDING",
                "Colombo",
                "WP-COL-01",
                "   ",
                "CP-KAN-01",
                null,
                null,
                null,
                new BigDecimal("1.00"),
                "SMALL",
                Instant.parse("2026-11-07T09:00:00Z"),
                List.of(new TrackingTimelineEventDto(null, Instant.now(), null))
        );

        when(parcelServiceClient.getTracking("SD-20261107-7K9P2X")).thenReturn(Optional.of(backendResponse));

        ParcelTrackingResult result = parcelTrackingTool.getParcelTracking("SD-20261107-7K9P2X");

        assertThat(result.status()).isEqualTo(ToolResultStatus.SUCCESS);
        assertThat(result.destinationZone()).isEqualTo("CP-KAN-01");
        assertThat(result.timeline().get(0).deliveryNote()).isEqualTo("Status updated to UNKNOWN");
    }
}
