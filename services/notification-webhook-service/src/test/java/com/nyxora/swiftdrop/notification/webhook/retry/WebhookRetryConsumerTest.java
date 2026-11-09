package com.nyxora.swiftdrop.notification.webhook.retry;

import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookDispatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookRetryConsumer Unit Tests")
class WebhookRetryConsumerTest {

    @Mock
    private WebhookDeliveryLogRepository logRepository;

    @Mock
    private WebhookDispatcherService dispatcherService;

    @Mock
    private WebhookRetryPublisher retryPublisher;

    private WebhookRetryConsumer retryConsumer;

    private UUID logId;
    private UUID merchantId;
    private UUID parcelId;
    private WebhookDeliveryLog sampleLog;
    private WebhookRetryMessage sampleMessage;

    @BeforeEach
    void setUp() {
        retryConsumer = new WebhookRetryConsumer(logRepository, dispatcherService, retryPublisher);

        logId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        parcelId = UUID.randomUUID();

        sampleLog = new WebhookDeliveryLog();
        sampleLog.setId(logId);
        sampleLog.setMerchantId(merchantId);
        sampleLog.setParcelId(parcelId);
        sampleLog.setEventType("parcel.status.updated");
        sampleLog.setPayload("{\"trackingNumber\":\"SD-123456\"}");
        sampleLog.setRetryCount(1);
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);

        sampleMessage = new WebhookRetryMessage(
                logId,
                merchantId,
                parcelId,
                "parcel.status.updated",
                "{\"trackingNumber\":\"SD-123456\"}",
                1);
    }

    @Test
    @DisplayName("Should successfully process retry without rescheduling when HTTP 200 DELIVERED is received")
    void shouldHandleSuccessfulRetry() {
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));
        when(dispatcherService.dispatchRetryWebhook(sampleLog))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verify(dispatcherService).dispatchRetryWebhook(sampleLog);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should schedule subsequent retry when transient 5xx error occurs and retries remain")
    void shouldScheduleNextRetryOnRetryableFailure() {
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));
        when(dispatcherService.dispatchRetryWebhook(sampleLog)).thenReturn(
                WebhookDispatchResult.retryableFailure(503, "HTTP_SERVER_ERROR", "Service Unavailable", sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verify(dispatcherService).dispatchRetryWebhook(sampleLog);
        verify(retryPublisher).scheduleRetry(sampleLog);
    }

    @Test
    @DisplayName("Should transition to DEAD_LETTER when failure is permanently non-retryable (e.g. 400 Bad Request)")
    void shouldMarkDeadLetterOnNonRetryableFailure() {
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));
        when(dispatcherService.dispatchRetryWebhook(sampleLog)).thenReturn(
                WebhookDispatchResult.permanentFailure(400, "HTTP_CLIENT_ERROR", "Bad Request", sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verify(dispatcherService).dispatchRetryWebhook(sampleLog);
        verifyNoInteractions(retryPublisher);
        assertEquals(WebhookDeliveryStatus.DEAD_LETTER, sampleLog.getDeliveryStatus());
        assertNull(sampleLog.getNextRetryAt());
        verify(logRepository).save(sampleLog);
    }

    @Test
    @DisplayName("Should transition to DEAD_LETTER when maximum retry limit of 5 is exhausted")
    void shouldMarkDeadLetterWhenAllRetriesExhausted() {
        sampleLog.setRetryCount(5);
        WebhookRetryMessage finalAttemptMessage = new WebhookRetryMessage(
                logId, merchantId, parcelId, "parcel.status.updated", "{}", 5);

        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));
        when(dispatcherService.dispatchRetryWebhook(sampleLog)).thenReturn(
                WebhookDispatchResult.retryableFailure(504, "GATEWAY_TIMEOUT", "Gateway Timeout", sampleLog));

        retryConsumer.handleRetryMessage(finalAttemptMessage);

        verify(dispatcherService).dispatchRetryWebhook(sampleLog);
        verifyNoInteractions(retryPublisher);
        assertEquals(WebhookDeliveryStatus.DEAD_LETTER, sampleLog.getDeliveryStatus());
        assertNull(sampleLog.getNextRetryAt());
        verify(logRepository).save(sampleLog);
    }

    @Test
    @DisplayName("Should ignore retry message if delivery is already marked DELIVERED")
    void shouldIgnoreAlreadyDeliveredMessage() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should ignore retry message if delivery is already marked DEAD_LETTER")
    void shouldIgnoreAlreadyDeadLetterMessage() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard out-of-sequence or stale message when message retryCount does not match DB retryCount")
    void shouldDiscardStaleRetryMessage() {
        sampleLog.setRetryCount(3); // DB is already on attempt 3
        WebhookRetryMessage staleMessage = new WebhookRetryMessage(
                logId, merchantId, parcelId, "parcel.status.updated", "{}", 1); // message is attempt 1

        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));

        retryConsumer.handleRetryMessage(staleMessage);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard retry message if delivery status is not RETRYING (e.g. PENDING)")
    void shouldDiscardWhenStatusIsNotRetrying() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));

        retryConsumer.handleRetryMessage(sampleMessage);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard message if merchantId or parcelId does not match database record")
    void shouldDiscardMismatchedSecurityContext() {
        UUID wrongMerchantId = UUID.randomUUID();
        WebhookRetryMessage spoofedMessage = new WebhookRetryMessage(
                logId, wrongMerchantId, parcelId, "parcel.status.updated", "{}", 1);

        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));

        retryConsumer.handleRetryMessage(spoofedMessage);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should throw AmqpRejectAndDontRequeueException when retry publication fails to avoid loop")
    void shouldThrowAmqpRejectWhenPublisherFails() {
        when(logRepository.findById(logId)).thenReturn(Optional.of(sampleLog));
        when(dispatcherService.dispatchRetryWebhook(sampleLog)).thenReturn(
                WebhookDispatchResult.retryableFailure(500, "HTTP_SERVER_ERROR", "Internal Server Error", sampleLog));

        doThrow(new RuntimeException("RabbitMQ connection down")).when(retryPublisher).scheduleRetry(sampleLog);

        assertThrows(AmqpRejectAndDontRequeueException.class, () -> retryConsumer.handleRetryMessage(sampleMessage));
    }
}
