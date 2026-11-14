package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteResponse;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.TrackingTimelineEventDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://localhost:8082")
                .messageConverters(converters -> {
                    converters.removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper));
                });
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
        assertThat(result.get().createdAt()).isEqualTo(Instant.parse("2026-11-07T09:00:00Z"));
        assertThat(result.get().timeline()).hasSize(1);
        assertThat(result.get().timeline().get(0).status()).isEqualTo("BOOKED");
        assertThat(result.get().timeline().get(0).timestamp()).isEqualTo(Instant.parse("2026-11-07T09:00:00Z"));
        assertThat(result.get().timeline().get(0).deliveryNote()).isEqualTo("Booked");
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

    @Test
    @DisplayName("Should query public zones with search query")
    void shouldQueryPublicZonesWithSearch() throws Exception {
        List<PublicDeliveryZoneResponse> expected = List.of(
                new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo")
        );

        mockServer.expect(requestTo("http://localhost:8082/api/v1/public/zones?search=Colombo"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        List<PublicDeliveryZoneResponse> zones = parcelServiceClient.getPublicZones("Colombo", null);

        assertThat(zones).hasSize(1);
        assertThat(zones.get(0).zoneName()).isEqualTo("Colombo Central Metro");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should calculate delivery quote successfully")
    void shouldCalculateQuoteSuccessfully() throws Exception {
        ParcelQuoteRequest request = new ParcelQuoteRequest(1, 2, new BigDecimal("2.50"), "MEDIUM", "STANDARD");
        ParcelQuoteResponse expectedResponse = new ParcelQuoteResponse(
                "STANDARD",
                new BigDecimal("350.00"),
                new BigDecimal("125.00"),
                new BigDecimal("1.20"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("620.00")
        );

        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/quote"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

        ParcelQuoteResponse response = parcelServiceClient.calculateQuote(request);

        assertThat(response.totalFee()).isEqualByComparingTo("620.00");
        assertThat(response.appliedTier()).isEqualTo("STANDARD");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should query public zones with district filter only")
    void shouldQueryPublicZonesWithDistrictOnly() throws Exception {
        List<PublicDeliveryZoneResponse> expected = List.of(
                new PublicDeliveryZoneResponse(2, "CP-KAN-01", "Kandy Metro", "Kandy")
        );

        mockServer.expect(requestTo("http://localhost:8082/api/v1/public/zones?district=Kandy"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        List<PublicDeliveryZoneResponse> zones = parcelServiceClient.getPublicZones(null, "Kandy");

        assertThat(zones).hasSize(1);
        assertThat(zones.get(0).zoneName()).isEqualTo("Kandy Metro");
        mockServer.verify();
    }

    @Test
    @DisplayName("Should query public zones with both search and district filters")
    void shouldQueryPublicZonesWithSearchAndDistrict() throws Exception {
        List<PublicDeliveryZoneResponse> expected = List.of(
                new PublicDeliveryZoneResponse(1, "WP-COL-01", "Colombo Central Metro", "Colombo")
        );

        mockServer.expect(requestTo("http://localhost:8082/api/v1/public/zones?search=Central&district=Colombo"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        List<PublicDeliveryZoneResponse> zones = parcelServiceClient.getPublicZones("Central", "Colombo");

        assertThat(zones).hasSize(1);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should query public zones with no filters")
    void shouldQueryPublicZonesWithNoFilters() throws Exception {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/public/zones"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        List<PublicDeliveryZoneResponse> zones = parcelServiceClient.getPublicZones(null, null);

        assertThat(zones).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw RestClientException when getPublicZones encounters connection failure")
    void shouldThrowExceptionWhenGetPublicZonesFails() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/public/zones"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> parcelServiceClient.getPublicZones(null, null))
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw RestClientException when calculateQuote encounters connection failure")
    void shouldThrowExceptionWhenCalculateQuoteFails() {
        ParcelQuoteRequest request = new ParcelQuoteRequest(1, 2, new BigDecimal("2.50"), "MEDIUM", "STANDARD");

        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/quote"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> parcelServiceClient.calculateQuote(request))
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }

    @Test
    @DisplayName("Should instantiate with base URL constructor")
    void shouldInstantiateWithUrlConstructor() {
        ParcelServiceClient client = new ParcelServiceClient("http://localhost:8082");
        assertThat(client).isNotNull();
    }

    @Test
    @DisplayName("Should return empty when tracking returns 400 Bad Request")
    void shouldReturnEmptyWhenTrackingReturnsBadRequest() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-20261107-INVALID"))
                .andRespond(withBadRequest());

        Optional<ParcelTrackingResponseDto> result = parcelServiceClient.getTracking("SD-20261107-INVALID");

        assertThat(result).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw RestClientException when tracking returns 500 server error")
    void shouldThrowExceptionWhenTrackingReturnsServerError() {
        mockServer.expect(requestTo("http://localhost:8082/api/v1/parcels/track/SD-20261107-FAIL"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> parcelServiceClient.getTracking("SD-20261107-FAIL"))
                .isInstanceOf(RestClientException.class);
        mockServer.verify();
    }
}
