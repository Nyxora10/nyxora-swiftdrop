package com.nyxora.swiftdrop.ai_assistant_service.client;

import com.nyxora.swiftdrop.ai_assistant_service.dto.tracking.ParcelTrackingResponseDto;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Optional;

/**
 * REST client for communicating with the downstream SwiftDrop Parcel Service.
 * Uses Spring Boot's {@link RestClient} to fetch real-time parcel tracking data.
 */
@Service
public class ParcelServiceClient {

    private static final Logger log =
            LoggerFactory.getLogger(ParcelServiceClient.class);

    private final RestClient restClient;

    @Autowired
    public ParcelServiceClient(
            @Value("${services.parcel.url:http://localhost:8082}") String parcelServiceUrl) {

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);

        this.restClient = RestClient.builder()
                .baseUrl(parcelServiceUrl)
                .requestFactory(requestFactory)
                .build();

        log.info("ParcelServiceClient initialized with base URL: {}", parcelServiceUrl);
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
}

