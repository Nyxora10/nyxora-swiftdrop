package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.nyxora.swiftdrop.ai_assistant_service.dto.PublicDeliveryZoneResponse;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteRequest;
import com.nyxora.swiftdrop.ai_assistant_service.dto.quote.ParcelQuoteResponse;
import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Optional;

/**
 * REST client for communicating with the downstream SwiftDrop Parcel Service.
 * Uses Spring Boot's {@link RestClient} to fetch real-time parcel tracking,
 * query public delivery zones, and calculate delivery rate quotes.
 */
@Service
public class ParcelServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(ParcelServiceClient.class);

    private final RestClient restClient;

    @Autowired
    public ParcelServiceClient(
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);

        RestClient.Builder builder = (restClientBuilder != null ? restClientBuilder : RestClient.builder())
                .baseUrl(parcelServiceUrl)
                .requestFactory(requestFactory);

        this.restClient = builder.build();

        log.info("ParcelServiceClient initialized with base URL: {}", parcelServiceUrl);
    }

    public ParcelServiceClient(String parcelServiceUrl) {
        this(null, parcelServiceUrl);
    }

    /**
     * Package-private constructor for testing with a mock or pre-configured RestClient.
     */
    ParcelServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Retrieves parcel tracking information from the Parcel Service.
     *
     * @param trackingNumber the normalized tracking number
     * @return Optional containing {@link ParcelTrackingResponseDto}, or empty if not found
     */
    public Optional<ParcelTrackingResponseDto> getTracking(String trackingNumber) {
        log.debug("Invoking parcel-service tracking endpoint for code: {}",
                PiiMaskingUtil.maskText(trackingNumber));

        try {
            ParcelTrackingResponseDto response = restClient.get()
                    .uri("/api/v1/parcels/track/{trackingNumber}", trackingNumber)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ParcelTrackingResponseDto.class);

            return Optional.ofNullable(response);
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.BadRequest ex) {
            log.warn("Parcel not found or bad request for tracking number {}: {}",
                    PiiMaskingUtil.maskText(trackingNumber), ex.getStatusCode());
            return Optional.empty();
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 400) {
                log.warn("Parcel tracking returned HTTP {}: {}",
                        ex.getStatusCode().value(), ex.getResponseBodyAsString());
                return Optional.empty();
            }
            log.error("Downstream Parcel Service error: status={}, body={}",
                    ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
            throw ex;
        } catch (RestClientException ex) {
            log.error("Connection failure while calling Parcel Service: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Queries active Sri Lankan delivery zones matching optional search or district criteria.
     *
     * @param search   optional free-text query (matches zone name, code, or district)
     * @param district optional administrative district filter
     * @return list of matching active delivery zone DTOs
     */
    public List<PublicDeliveryZoneResponse> getPublicZones(String search, String district) {
        log.debug("Querying public delivery zones: search='{}', district='{}'", search, district);

        try {
            List<PublicDeliveryZoneResponse> zones = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path("/api/v1/public/zones");
                        if (search != null && !search.trim().isBlank()) {
                            uriBuilder.queryParam("search", search.trim());
                        }
                        if (district != null && !district.trim().isBlank()) {
                            uriBuilder.queryParam("district", district.trim());
                        }
                        return uriBuilder.build();
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PublicDeliveryZoneResponse>>() {});

            return zones != null ? zones : List.of();
        } catch (RestClientException ex) {
            log.error("Failed to query public delivery zones from Parcel Service: {}", ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * Calculates an itemized delivery rate quotation from the Parcel Service.
     *
     * @param request the validated quotation request
     * @return the itemized fee quote response
     */
    public ParcelQuoteResponse calculateQuote(ParcelQuoteRequest request) {
        log.debug("Requesting delivery quote: originZone={}, destZone={}, weight={}kg, size={}, tier={}",
                request.originZoneId(), request.destinationZoneId(), request.weightKg(), request.parcelSize(), request.tier());

        try {
            return restClient.post()
                    .uri("/api/v1/parcels/quote")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ParcelQuoteResponse.class);
        } catch (RestClientException ex) {
            log.error("Error executing parcel rate quote with Parcel Service: {}", ex.getMessage(), ex);
            throw ex;
        }
    }
}
