package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.TrackingTimelineEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ParcelServiceClientTest {

    private MockRestServiceServer mockServer;
    private ParcelServiceClient parcelServiceClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost:8082");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        parcelServiceClient = new ParcelServiceClient(builder.build());
    }

    @Test
    @DisplayName("Should successfully return parcel tracking response when parcel exists")
    void shouldReturnParcelTrackingWhenFound() throws Exception {
        ParcelTrackingResponseDto expectedDto = new ParcelTrackingResponseDto(
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
                List.of(new TrackingTimelineEventDto("BOOKED", Instant.parse("2026-11-07T09:00:00Z"), "Booked"))
        );

        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-20261107-7K9P2X"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expectedDto), MediaType.APPLICATION_JSON));

        Optional<ParcelTrackingResponseDto> result = parcelServiceClient.getTracking("SD-20261107-7K9P2X");

        assertThat(result).isPresent();
        assertThat(result.get().trackingNumber()).isEqualTo("SD-20261107-7K9P2X");
        assertThat(result.get().currentStatus()).isEqualTo("IN_TRANSIT");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty optional when parcel service returns 404 Not Found")
    void shouldReturnEmptyOptionalWhenNotFound() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-UNKNOWN"))
                .andRespond(withResourceNotFound());

        Optional<ParcelTrackingResponseDto> result = parcelServiceClient.getTracking("SD-UNKNOWN");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should return empty optional when parcel service returns 400 Bad Request")
    void shouldReturnEmptyOptionalWhenBadRequest() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-NOTFOUND"))
                .andRespond(withBadRequest());

        Optional<ParcelTrackingResponseDto> result = parcelServiceClient.getTracking("SD-NOTFOUND");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw exception when parcel service returns 500 Internal Server Error")
    void shouldThrowExceptionWhenServerError() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-ERROR"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> parcelServiceClient.getTracking("SD-ERROR"))
                .isInstanceOf(HttpServerErrorException.class);
        mockServer.verify();
    }
}

