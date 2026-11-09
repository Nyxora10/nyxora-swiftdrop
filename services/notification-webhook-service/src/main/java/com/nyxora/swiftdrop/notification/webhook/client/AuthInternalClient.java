package com.nyxora.swiftdrop.notification.webhook.client;

import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.UUID;

/**
 * Internal REST client for retrieving merchant webhook endpoints and HMAC signing credentials from auth-service.
 *
 * <p>Uses Spring 6 / Spring Boot 3 {@link RestClient}.
 * Connects to internal endpoint: {@code GET /api/v1/auth/internal/merchants/{merchantId}/webhook-config}.</p>
 *
 * <p>Security & Caching:
 * <ul>
 *   <li>Prepared with {@code @Cacheable("merchantWebhookConfigs")} for 15-minute Caffeine caching (Task 3.3).</li>
 *   <li>Masks and guarantees zero logging of merchant secrets in info, warn, error, or debug logs.</li>
 *   <li>Never falls back to hardcoded production secrets.</li>
 * </ul>
 */
@Component
public class AuthInternalClient {

    private static final Logger log = LoggerFactory.getLogger(AuthInternalClient.class);

    private final RestClient restClient;

    @Autowired
    public AuthInternalClient(
            @Autowired(required = false) RestClient.Builder restClientBuilder,
            @Value("${app.services.auth-service-url:http://localhost:8081}") String authServiceBaseUrl) {
        RestClient.Builder builder = restClientBuilder != null ? restClientBuilder : RestClient.builder();
        this.restClient = builder
                .baseUrl(authServiceBaseUrl)
                .build();
    }

    /**
     * Alternative constructor facilitating unit and integration testing with custom RestClient instances.
     *
     * @param restClient pre-configured RestClient
     */
    public AuthInternalClient(RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * Fetches the webhook configuration for the specified merchant.
     *
     * <p>When caching is enabled, results are cached under cache name {@code "merchantWebhookConfigs"}
     * with merchantId as key. Missing configurations (404) return {@link Optional#empty()} and should
     * not cause application failure.</p>
     *
     * @param merchantId UUID of the merchant
     * @return Optional containing the configuration if found and active, otherwise empty
     */
    @Cacheable(value = "merchantWebhookConfigs", key = "#merchantId", unless = "#result == null || !#result.isPresent()")
    public Optional<MerchantWebhookConfigResponse> getMerchantWebhookConfig(UUID merchantId) {
        if (merchantId == null) {
            log.warn("Cannot fetch webhook configuration: merchantId is null");
            return Optional.empty();
        }

        log.debug("Fetching internal webhook configuration from auth-service for merchantId={}", merchantId);

        try {
            MerchantWebhookConfigResponse config = restClient.get()
                    .uri("/api/v1/auth/internal/merchants/{merchantId}/webhook-config", merchantId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.warn("Merchant webhook configuration not found or rejected by auth-service: merchantId={}, status={}",
                                merchantId, response.getStatusCode());
                    })
                    .body(MerchantWebhookConfigResponse.class);

            if (config == null) {
                log.warn("Received null webhook configuration response body for merchantId={}", merchantId);
                return Optional.empty();
            }

            if (!config.isEligibleForDispatch()) {
                log.info("Merchant configuration retrieved but ineligible for dispatch (inactive or missing url/secret): merchantId={}", merchantId);
                return Optional.empty();
            }

            log.info("Successfully resolved active webhook destination for merchantId={}", merchantId);
            return Optional.of(config);

        } catch (HttpClientErrorException.NotFound ex) {
            log.warn("Merchant webhook config endpoint returned 404 for merchantId={}", merchantId);
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            log.warn("Client error querying auth-service webhook config for merchantId={}: status={}", merchantId, ex.getStatusCode());
            return Optional.empty();
        } catch (HttpServerErrorException ex) {
            log.error("Auth-service 5xx server error while querying webhook config for merchantId={}: status={}", merchantId, ex.getStatusCode());
            return Optional.empty();
        } catch (ResourceAccessException ex) {
            log.error("Network I/O failure reaching auth-service at configured endpoint for merchantId={}: {}", merchantId, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.error("Unexpected error retrieving merchant webhook configuration for merchantId={}", merchantId, ex);
            return Optional.empty();
        }
    }
}

