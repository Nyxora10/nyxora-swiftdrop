package com.nyxora.swiftdrop.notification.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.util.HmacSignatureUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ExtendWith(MockitoExtension.class)
class WebhookDispatcherServiceTest {

    @Mock
    private AuthInternalClient authClient;

    @Mock
    private WebhookDeliveryLogRepository repository;

    private UUID merchantId;
    private UUID parcelId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
        lenient().when(repository.save(any(WebhookDeliveryLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void rejectsMissingMerchant() {
        var result = service(false).dispatchWebhook(null, parcelId, "parcel.updated", "{}");

        assertThat(result.success()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("INVALID_MERCHANT_ID");
        verifyNoInteractions(authClient, repository);
    }

    @Test
    void rejectsPayloadThatCannotBeSerialized() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new IllegalStateException("serialization failed"));

        var result = service(mapper, false).dispatchWebhook(merchantId, parcelId, "parcel.updated", new Object());

        assertThat(result.errorCategory()).isEqualTo("PAYLOAD_SERIALIZATION_ERROR");
        verifyNoInteractions(authClient, repository);
    }

    @Test
    void retriesWhenMerchantConfigurationIsUnavailable() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.empty());

        var result = service(false).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("AUTH_CONFIG_UNAVAILABLE");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
    }

    @Test
    void permanentlyRejectsUnsafeUrl() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://127.0.0.1/hook", "secret", true)));

        var result = service(false).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("SSRF_BLOCKED");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
    }

    @Test
    void deliversSuccessfullyAndPersistsStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withSuccess());
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{\"ok\":true}");

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        server.verify();
    }

    @Test
    void classifiesRateLimitAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("HTTP_CLIENT_TIMEOUT_OR_RATE_LIMIT");
        server.verify();
    }

    @Test
    void classifiesBadRequestAsPermanent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withBadRequest());
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isFalse();
        assertThat(result.errorCategory()).isEqualTo("HTTP_CLIENT_ERROR");
        server.verify();
    }

    @Test
    void classifiesServerErrorAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withServerError());
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("HTTP_SERVER_ERROR");
        server.verify();
    }

    @Test
    void treatsInactiveConfigurationAsRetryable() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "https://example.com/hook", "secret", false)));

        var result = service(false).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.errorCategory()).isEqualTo("AUTH_CONFIG_UNAVAILABLE");
        assertThat(result.retryable()).isTrue();
    }

    @Test
    void rejectsRetryWithMissingPayload() {
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setId(UUID.randomUUID());
        deliveryLog.setPayload(" ");

        var result = service(false).dispatchRetryWebhook(deliveryLog);

        assertThat(result.errorCategory()).isEqualTo("MISSING_PAYLOAD");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void rejectsRetryWhenLogIdIsUnknown() {
        UUID logId = UUID.randomUUID();
        when(repository.findById(logId)).thenReturn(Optional.empty());

        var result = service(false).dispatchRetryWebhook(logId);

        assertThat(result.errorCategory()).isEqualTo("LOG_NOT_FOUND");
    }

    @Test
    void rejectsNullOrUnpersistedRetryLog() {
        assertThatThrownBy(() -> service(false).dispatchRetryWebhook((WebhookDeliveryLog) null))
                .isInstanceOf(IllegalArgumentException.class);

        WebhookDeliveryLog unpersisted = new WebhookDeliveryLog();
        assertThatThrownBy(() -> service(false).dispatchRetryWebhook(unpersisted))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMalformedWebhookUrls() {
        assertUnsafeUrl("not-a-url");
        assertUnsafeUrl("ftp://example.com/hook");
        assertUnsafeUrl("https:///hook");
    }

    private void assertUnsafeUrl(String url) {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, url, "secret", true)));

        var result = service(false).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.errorCategory()).isEqualTo("SSRF_BLOCKED");
        reset(authClient);
    }

    @Test
    void classifiesNetworkFailureAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(request -> {
                    throw new org.springframework.web.client.ResourceAccessException("connection timed out");
                });
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("CONNECTION_TIMEOUT");
        server.verify();
    }

    @Test
    void classifiesUnexpectedHttpFailureAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(request -> {
                    throw new IllegalStateException("unexpected client failure");
                });
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("UNEXPECTED_ERROR");
        server.verify();
    }

    @Test
    void allowsSafePublicHttpsUrlAndDispatchesSuccessfully() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("https://api.merchant.com/webhook"))
                .andRespond(withSuccess());

        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "https://api.merchant.com/webhook", "secret", true)));

        WebhookDispatcherService.DnsResolver publicResolver = host -> new InetAddress[]{
                InetAddress.getByAddress("api.merchant.com", new byte[]{(byte) 93, (byte) 184, (byte) 216, (byte) 34})
        };

        var result = service(client, false, publicResolver)
                .dispatchWebhook(merchantId, parcelId, "parcel.updated", "{\"status\":\"OK\"}");

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        server.verify();
    }

    @Test
    void permanentlyRejectsProhibitedAddresses() throws Exception {
        List<String> prohibitedIps = List.of(
                "127.0.0.1",
                "127.0.0.2",
                "10.0.0.1",
                "172.16.0.1",
                "192.168.1.1",
                "169.254.169.254",
                "169.254.1.1",
                "0.0.0.0",
                "::1",
                "fe80::1",
                "fc00::1",
                "fd00::1"
        );

        for (String ip : prohibitedIps) {
            InetAddress prohibitedAddr = InetAddress.getByName(ip);
            WebhookDispatcherService.DnsResolver resolver = host -> new InetAddress[]{prohibitedAddr};

            when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                    new MerchantWebhookConfigResponse(merchantId, "https://target.host/webhook", "secret", true)));

            var result = service(false, resolver)
                    .dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

            assertThat(result.retryable()).as("IP " + ip + " should be non-retryable").isFalse();
            assertThat(result.errorCategory()).as("IP " + ip + " should be SSRF_BLOCKED").isEqualTo("SSRF_BLOCKED");
            assertThat(result.deliveryLog().getDeliveryStatus()).as("IP " + ip + " should be DEAD_LETTER")
                    .isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);

            reset(authClient);
        }
    }

    @Test
    void classifiesDnsResolutionFailureAsRetryableWhenAttemptsRemain() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "https://unresolvable.merchant.com/webhook", "secret", true)));

        WebhookDispatcherService.DnsResolver failingResolver = host -> {
            throw new UnknownHostException("Name resolution failed for " + host);
        };

        var result = service(false, failingResolver)
                .dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("DNS_RESOLUTION_FAILURE");
        assertThat(result.errorMessage()).contains("unresolvable.merchant.com");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
    }

    @Test
    void classifiesDnsResolutionFailureAsDeadLetterWhenRetriesExhausted() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "https://unresolvable.merchant.com/webhook", "secret", true)));

        WebhookDispatcherService.DnsResolver failingResolver = host -> {
            throw new UnknownHostException("Name resolution failed for " + host);
        };

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setId(UUID.randomUUID());
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.updated");
        deliveryLog.setPayload("{\"status\":\"IN_TRANSIT\"}");
        deliveryLog.setRetryCount(5); // Final attempt (exhausted)
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);

        var result = service(false, failingResolver).dispatchRetryWebhook(deliveryLog);

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("DNS_RESOLUTION_FAILURE");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
        assertThat(result.deliveryLog().getNextRetryAt()).isNull();
    }

    private WebhookDispatcherService service(boolean allowLocalhost) {
        return service(new ObjectMapper(), allowLocalhost);
    }

    private WebhookDispatcherService service(ObjectMapper mapper, boolean allowLocalhost) {
        return new WebhookDispatcherService(authClient, repository, mapper,
                RestClient.builder().build(), allowLocalhost);
    }

    private WebhookDispatcherService service(RestClient client, boolean allowLocalhost) {
        return new WebhookDispatcherService(authClient, repository, new ObjectMapper(), client, allowLocalhost);
    }

    private WebhookDispatcherService service(RestClient client, boolean allowLocalhost, WebhookDispatcherService.DnsResolver dnsResolver) {
        return new WebhookDispatcherService(authClient, repository, new ObjectMapper(), client, allowLocalhost, dnsResolver);
    }

    private WebhookDispatcherService service(boolean allowLocalhost, WebhookDispatcherService.DnsResolver dnsResolver) {
        return new WebhookDispatcherService(authClient, repository, new ObjectMapper(), RestClient.builder().build(), allowLocalhost, dnsResolver);
    }

    @Test
    void verifiesConstructorsAndNullFallbacks() {
        WebhookDispatcherService svc1 = new WebhookDispatcherService(authClient, repository, null, true);
        assertThat(svc1).isNotNull();

        WebhookDispatcherService svc2 = new WebhookDispatcherService(authClient, repository, new ObjectMapper(), true);
        assertThat(svc2).isNotNull();

        WebhookDispatcherService svc3 = new WebhookDispatcherService(authClient, repository, null, null, false, null);
        assertThat(svc3).isNotNull();

        WebhookDispatcherService svc4 = new WebhookDispatcherService(authClient, repository, new ObjectMapper(), RestClient.builder().build(), false, InetAddress::getAllByName);
        assertThat(svc4).isNotNull();
    }

    @Test
    void dispatchesWebhookWithNonStringPayloadAndNullMetadata() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withSuccess());

        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        Map<String, Object> mapPayload = Map.of("key", "value", "id", 123);
        var result = service(client, true).dispatchWebhook(merchantId, null, null, mapPayload);

        assertThat(result.success()).isTrue();
        assertThat(result.deliveryLog().getParcelId()).isNotNull();
        assertThat(result.deliveryLog().getEventType()).isEqualTo("unknown.event");
        assertThat(result.deliveryLog().getPayload()).contains("\"key\":\"value\"");
        server.verify();
    }

    @Test
    void rejectsRetryWithNullPayload() {
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setId(UUID.randomUUID());
        deliveryLog.setPayload(null);

        var result = service(false).dispatchRetryWebhook(deliveryLog);

        assertThat(result.errorCategory()).isEqualTo("MISSING_PAYLOAD");
        assertThat(result.retryable()).isFalse();
    }

    @Test
    void dispatchRetryWebhookByIdHandlesNullAndFound() {
        assertThatThrownBy(() -> service(false).dispatchRetryWebhook((UUID) null))
                .isInstanceOf(IllegalArgumentException.class);

        UUID logId = UUID.randomUUID();
        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setId(logId);
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.delivered");
        deliveryLog.setPayload("{\"status\":\"DELIVERED\"}");
        deliveryLog.setRetryCount(1);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);

        when(repository.findById(logId)).thenReturn(Optional.of(deliveryLog));
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withSuccess());

        var result = service(client, true).dispatchRetryWebhook(logId);

        assertThat(result.success()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(200);
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DELIVERED);
        server.verify();
    }

    @Test
    void permanentlyRejectsWhenSignatureComputationFails() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "valid-secret", true)));

        try (var mockedStatic = mockStatic(HmacSignatureUtil.class)) {
            mockedStatic.when(() -> HmacSignatureUtil.generateSignatureHeader(any(byte[].class), any(String.class)))
                    .thenThrow(new IllegalArgumentException("Simulated signature failure"));

            var result = service(true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

            assertThat(result.success()).isFalse();
            assertThat(result.retryable()).isFalse();
            assertThat(result.errorCategory()).isEqualTo("SIGNATURE_COMPUTATION_ERROR");
            assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
            assertThat(result.deliveryLog().getNextRetryAt()).isNull();
        }
    }

    @Test
    void classifiesRequestTimeoutAsRetryable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        server.expect(requestTo("http://localhost/hook"))
                .andRespond(withStatus(HttpStatus.REQUEST_TIMEOUT));
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

        var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

        assertThat(result.retryable()).isTrue();
        assertThat(result.httpStatusCode()).isEqualTo(408);
        assertThat(result.errorCategory()).isEqualTo("HTTP_CLIENT_TIMEOUT_OR_RATE_LIMIT");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
        server.verify();
    }

    @Test
    void classifiesUnauthorizedAndNotFoundAsPermanentClientErrors() {
        HttpStatus[] clientErrors = new HttpStatus[]{HttpStatus.UNAUTHORIZED, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND};
        for (HttpStatus status : clientErrors) {
            RestClient.Builder builder = RestClient.builder();
            MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
            RestClient client = builder.build();
            server.expect(requestTo("http://localhost/hook"))
                    .andRespond(withStatus(status));
            when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                    new MerchantWebhookConfigResponse(merchantId, "http://localhost/hook", "secret", true)));

            var result = service(client, true).dispatchWebhook(merchantId, parcelId, "parcel.updated", "{}");

            assertThat(result.retryable()).isFalse();
            assertThat(result.httpStatusCode()).isEqualTo(status.value());
            assertThat(result.errorCategory()).isEqualTo("HTTP_CLIENT_ERROR");
            assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
            assertThat(result.deliveryLog().getNextRetryAt()).isNull();
            server.verify();
            reset(authClient);
        }
    }

    @Test
    void classifiesDnsResolutionFailureWhenRetryCountIsNull() {
        when(authClient.getMerchantWebhookConfig(merchantId)).thenReturn(Optional.of(
                new MerchantWebhookConfigResponse(merchantId, "https://unresolvable.merchant.com/webhook", "secret", true)));

        WebhookDispatcherService.DnsResolver failingResolver = host -> {
            throw new UnknownHostException("Host not found " + host);
        };

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setId(UUID.randomUUID());
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.updated");
        deliveryLog.setPayload("{\"status\":\"IN_TRANSIT\"}");
        deliveryLog.setRetryCount(null);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.PENDING);

        var result = service(false, failingResolver).dispatchRetryWebhook(deliveryLog);

        assertThat(result.retryable()).isTrue();
        assertThat(result.errorCategory()).isEqualTo("DNS_RESOLUTION_FAILURE");
        assertThat(result.deliveryLog().getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.RETRYING);
    }

    @Test
    void exercisesUrlSecurityValidationBranches() throws Exception {
        WebhookDispatcherService svcProd = service(false);
        WebhookDispatcherService svcDev = service(true);

        assertThat(validateUrlSecurity(svcProd, null).status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcProd, "").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcProd, "   ").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcProd, "https://invalid uri with spaces.com").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcProd, "://no-scheme").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);

        assertThat(validateUrlSecurity(svcDev, "ftp://localhost/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcDev, "http://localhost/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SAFE);
        assertThat(validateUrlSecurity(svcDev, "https://localhost/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SAFE);

        assertThat(validateUrlSecurity(svcProd, "http://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(validateUrlSecurity(svcProd, "https:///hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);

        WebhookDispatcherService.DnsResolver runtimeExceptionResolver = host -> {
            throw new RuntimeException("DNS service offline");
        };
        WebhookDispatcherService svcRuntimeEx = service(false, runtimeExceptionResolver);
        assertThat(validateUrlSecurity(svcRuntimeEx, "https://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.DNS_FAILURE);

        WebhookDispatcherService.DnsResolver nullResolver = host -> null;
        WebhookDispatcherService svcNull = service(false, nullResolver);
        assertThat(validateUrlSecurity(svcNull, "https://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.DNS_FAILURE);

        WebhookDispatcherService.DnsResolver emptyResolver = host -> new InetAddress[0];
        WebhookDispatcherService svcEmpty = service(false, emptyResolver);
        assertThat(validateUrlSecurity(svcEmpty, "https://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.DNS_FAILURE);

        WebhookDispatcherService.DnsResolver prohibitedResolver = host -> new InetAddress[]{InetAddress.getByName("10.0.0.1")};
        WebhookDispatcherService svcProhibited = service(false, prohibitedResolver);
        assertThat(validateUrlSecurity(svcProhibited, "https://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);

        WebhookDispatcherService.DnsResolver safeResolver = host -> new InetAddress[]{
                InetAddress.getByAddress(new byte[]{(byte) 93, (byte) 184, (byte) 216, 34})
        };
        WebhookDispatcherService svcSafe = service(false, safeResolver);
        assertThat(validateUrlSecurity(svcSafe, "https://example.com/hook").status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SAFE);
    }

    @Test
    void exercisesIsLocalhostBranches() {
        WebhookDispatcherService svc = service(false);
        assertThat(isLocalhost(svc, "localhost")).isTrue();
        assertThat(isLocalhost(svc, "LOCALHOST")).isTrue();
        assertThat(isLocalhost(svc, "127.0.0.1")).isTrue();
        assertThat(isLocalhost(svc, "::1")).isTrue();
        assertThat(isLocalhost(svc, "[::1]")).isTrue();
        assertThat(isLocalhost(svc, "127.0.0.2")).isFalse();
        assertThat(isLocalhost(svc, "example.com")).isFalse();
    }

    @Test
    void exercisesIsCloudMetadataIpBranches() {
        WebhookDispatcherService svc = service(false);
        assertThat(isCloudMetadataIp(svc, null)).isFalse();
        assertThat(isCloudMetadataIp(svc, "")).isFalse();
        assertThat(isCloudMetadataIp(svc, "   ")).isFalse();
        assertThat(isCloudMetadataIp(svc, "169.254.169.254")).isTrue();
        assertThat(isCloudMetadataIp(svc, "169.254.170.2")).isTrue();
        assertThat(isCloudMetadataIp(svc, "fd00:ec2::254")).isTrue();
        assertThat(isCloudMetadataIp(svc, "FD00:EC2::254")).isTrue();
        assertThat(isCloudMetadataIp(svc, "93.184.216.34")).isFalse();
    }

    @Test
    void exercisesIsSafeUrlBranches() {
        WebhookDispatcherService.DnsResolver safeResolver = host -> new InetAddress[]{
                InetAddress.getByAddress(new byte[]{(byte) 93, (byte) 184, (byte) 216, 34})
        };
        WebhookDispatcherService svcSafe = service(false, safeResolver);
        assertThat(isSafeUrl(svcSafe, "https://example.com/hook")).isTrue();
        assertThat(isSafeUrl(svcSafe, "http://127.0.0.1/hook")).isFalse();
    }

    @Test
    void exercisesIsProhibitedAddressAllBranches() throws Exception {
        WebhookDispatcherService svc = service(false);

        assertThat(isProhibitedAddress(svc, null)).isTrue();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 224, 0, 0, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByName("ff02::1"))).isTrue();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{0, 1, 2, 3}))).isTrue();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{100, 64, 0, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{100, 127, (byte) 255, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{100, 63, (byte) 255, 1}))).isFalse();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{100, (byte) 128, 0, 1}))).isFalse();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{101, 64, 0, 1}))).isFalse();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 192, 0, 0, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 192, 0, 1, 1}))).isFalse();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 192, 1, 0, 1}))).isFalse();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 192, 0, 2, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 192, 0, 3, 1}))).isFalse();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 198, 51, 100, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 198, 50, 100, 1}))).isFalse();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 198, 51, 99, 1}))).isFalse();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 203, 0, 113, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 203, 1, 113, 1}))).isFalse();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 203, 0, 112, 1}))).isFalse();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 240, 0, 0, 1}))).isTrue();
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 250, 1, 2, 3}))).isTrue();

        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(new byte[]{(byte) 93, (byte) 184, (byte) 216, 34}))).isFalse();

        byte[] ula = new byte[16];
        ula[0] = (byte) 0xFD;
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(ula))).isTrue();

        byte[] mappedV4Prohibited = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 10, 0, 0, 1};
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(mappedV4Prohibited))).isTrue();

        byte[] mappedV4Safe = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 93, (byte) 184, (byte) 216, 34};
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(mappedV4Safe))).isFalse();

        byte[] publicV6 = new byte[16];
        publicV6[0] = 0x20;
        publicV6[1] = 0x01;
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(publicV6))).isFalse();

        byte[] nonMapped1 = new byte[16];
        nonMapped1[10] = 0x00;
        nonMapped1[11] = (byte) 0xFF;
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(nonMapped1))).isFalse();

        byte[] nonMapped2 = new byte[16];
        nonMapped2[10] = (byte) 0xFF;
        nonMapped2[11] = 0x00;
        assertThat(isProhibitedAddress(svc, InetAddress.getByAddress(nonMapped2))).isFalse();

        InetAddress mockMetadata = mock(InetAddress.class);
        when(mockMetadata.getHostAddress()).thenReturn("169.254.170.2");
        assertThat(isProhibitedAddress(svc, mockMetadata)).isTrue();

        InetAddress mockNullRawBytes = mock(InetAddress.class);
        when(mockNullRawBytes.getAddress()).thenReturn(null);
        assertThat(isProhibitedAddress(svc, mockNullRawBytes)).isTrue();

        InetAddress mockLoopbackFallback = mock(InetAddress.class);
        when(mockLoopbackFallback.getAddress()).thenReturn(new byte[]{127, 0, 0, 1});
        assertThat(isProhibitedAddress(svc, mockLoopbackFallback)).isTrue();

        InetAddress mockLinkLocalFallback = mock(InetAddress.class);
        when(mockLinkLocalFallback.getAddress()).thenReturn(new byte[]{(byte) 169, (byte) 254, 1, 1});
        assertThat(isProhibitedAddress(svc, mockLinkLocalFallback)).isTrue();

        InetAddress mockOddLength = mock(InetAddress.class);
        when(mockOddLength.getAddress()).thenReturn(new byte[]{1, 2, 3});
        assertThat(isProhibitedAddress(svc, mockOddLength)).isFalse();
    }

    @Test
    void verifiesEnumAndRecordDefinitions() {
        assertThat(WebhookDispatcherService.UrlSecurityStatus.values()).hasSize(3);
        assertThat(WebhookDispatcherService.UrlSecurityStatus.valueOf("SAFE"))
                .isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SAFE);
        assertThat(WebhookDispatcherService.UrlSecurityStatus.valueOf("SSRF_BLOCKED"))
                .isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(WebhookDispatcherService.UrlSecurityStatus.valueOf("DNS_FAILURE"))
                .isEqualTo(WebhookDispatcherService.UrlSecurityStatus.DNS_FAILURE);

        WebhookDispatcherService.UrlSecurityCheckResult safe = WebhookDispatcherService.UrlSecurityCheckResult.safe();
        assertThat(safe.status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SAFE);
        assertThat(safe.detail()).isNull();

        WebhookDispatcherService.UrlSecurityCheckResult blocked = WebhookDispatcherService.UrlSecurityCheckResult.blocked("bad host");
        assertThat(blocked.status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.SSRF_BLOCKED);
        assertThat(blocked.detail()).isEqualTo("bad host");

        WebhookDispatcherService.UrlSecurityCheckResult dnsFail = WebhookDispatcherService.UrlSecurityCheckResult.dnsFailure("unknown.host");
        assertThat(dnsFail.status()).isEqualTo(WebhookDispatcherService.UrlSecurityStatus.DNS_FAILURE);
        assertThat(dnsFail.detail()).isEqualTo("unknown.host");
    }

    private WebhookDispatcherService.UrlSecurityCheckResult validateUrlSecurity(WebhookDispatcherService svc, String url) {
        try {
            Method m = WebhookDispatcherService.class.getDeclaredMethod("validateUrlSecurity", String.class);
            m.setAccessible(true);
            return (WebhookDispatcherService.UrlSecurityCheckResult) m.invoke(svc, url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isLocalhost(WebhookDispatcherService svc, String host) {
        try {
            Method m = WebhookDispatcherService.class.getDeclaredMethod("isLocalhost", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(svc, host);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isProhibitedAddress(WebhookDispatcherService svc, InetAddress address) {
        try {
            Method m = WebhookDispatcherService.class.getDeclaredMethod("isProhibitedAddress", InetAddress.class);
            m.setAccessible(true);
            return (boolean) m.invoke(svc, address);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isCloudMetadataIp(WebhookDispatcherService svc, String ip) {
        try {
            Method m = WebhookDispatcherService.class.getDeclaredMethod("isCloudMetadataIp", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(svc, ip);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private boolean isSafeUrl(WebhookDispatcherService svc, String url) {
        try {
            Method m = WebhookDispatcherService.class.getDeclaredMethod("isSafeUrl", String.class);
            m.setAccessible(true);
            return (boolean) m.invoke(svc, url);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
