package com.nyxora.swiftdrop.notification.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.notification.webhook.client.AuthInternalClient;
import com.nyxora.swiftdrop.notification.webhook.client.dto.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
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
}
