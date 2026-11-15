package com.nyxora.swiftdrop.notification.webhook.consumer;

import com.nyxora.swiftdrop.notification.webhook.config.WebhookRabbitConfig;
import com.nyxora.swiftdrop.notification.webhook.dto.ParcelStatusUpdatedEvent;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPublisher;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookDispatcherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParcelStatusWebhookConsumer Unit Tests")
class ParcelStatusWebhookConsumerTest {

    @Mock
    private WebhookDispatcherService dispatcherService;

    @Mock
    private WebhookRetryPublisher retryPublisher;

    @Mock
    private WebhookDeliveryLogRepository logRepository;

    private ParcelStatusWebhookConsumer consumer;

    private UUID merchantId;
    private UUID parcelId;
    private UUID eventId;
    private ParcelStatusUpdatedEvent sampleEvent;
    private WebhookDeliveryLog sampleLog;

    @BeforeEach
    void setUp() {
        consumer = new ParcelStatusWebhookConsumer(dispatcherService, retryPublisher, logRepository);

        merchantId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
        eventId = UUID.randomUUID();

        sampleEvent = ParcelStatusUpdatedEvent.builder()
                .eventId(eventId)
                .eventType("PARCEL_STATUS_UPDATED")
                .parcelId(parcelId)
                .trackingNumber("SD-20261115-001")
                .previousStatus("IN_TRANSIT")
                .currentStatus("OUT_FOR_DELIVERY")
                .status("OUT_FOR_DELIVERY")
                .merchantId(merchantId)
                .courierId(UUID.randomUUID())
                .recipientName("Nimal Perera")
                .recipientPhone("+94771234567")
                .deliveryNote("Leave at front door")
                .timestamp(Instant.now())
                .build();

        sampleLog = new WebhookDeliveryLog();
        sampleLog.setId(UUID.randomUUID());
        sampleLog.setMerchantId(merchantId);
        sampleLog.setParcelId(parcelId);
        sampleLog.setEventType("PARCEL_STATUS_UPDATED");
        sampleLog.setPayload("{\"trackingNumber\":\"SD-20261115-001\"}");
        sampleLog.setRetryCount(0);
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
    }

    @Test
    @DisplayName("Should successfully dispatch merchant parcel event and not schedule retries on HTTP 200")
    void shouldSuccessfullyDispatchMerchantParcelEvent() {
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should skip retail parcel event when merchantId is null")
    void shouldSkipRetailEventWithNullMerchantId() {
        sampleEvent.setMerchantId(null);

        consumer.handleParcelStatusUpdated(sampleEvent);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
        verifyNoInteractions(logRepository);
    }

    @Test
    @DisplayName("Should forward correct merchantId, parcelId, and fallback routing key when eventType is blank")
    void shouldForwardFallbackEventTypeWhenBlank() {
        sampleEvent.setEventType("");
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(eq(merchantId), eq(parcelId), eq(WebhookRabbitConfig.WEBHOOK_PARCEL_EVENTS_ROUTING_KEY), eq(sampleEvent)))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, WebhookRabbitConfig.WEBHOOK_PARCEL_EVENTS_ROUTING_KEY, sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should schedule DLQ retry when dispatch fails with a retryable failure")
    void shouldScheduleRetryOnRetryableDispatchFailure() {
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.retryableFailure(503, "HTTP_SERVER_ERROR", "Service Unavailable", sampleLog));
        when(retryPublisher.scheduleRetry(sampleLog)).thenReturn(true);

        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verify(retryPublisher).scheduleRetry(sampleLog);
    }

    @Test
    @DisplayName("Should NOT schedule retry when dispatch fails with permanent non-retryable error")
    void shouldNotScheduleRetryOnNonRetryableDispatchFailure() {
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.permanentFailure(400, "HTTP_CLIENT_ERROR", "Bad Request", sampleLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard duplicate event when previous delivery log already exists for this event ID")
    void shouldDiscardDuplicateEventByEventId() {
        WebhookDeliveryLog existingLog = new WebhookDeliveryLog();
        existingLog.setId(UUID.randomUUID());
        existingLog.setParcelId(parcelId);
        existingLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        existingLog.setPayload("{\"eventId\":\"" + eventId + "\",\"currentStatus\":\"OUT_FOR_DELIVERY\"}");

        when(logRepository.findByParcelId(parcelId)).thenReturn(List.of(existingLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should process independently when different eventIds have the same parcelId and status")
    void shouldProcessIndependentlyWhenDifferentEventIdsHaveSameStatus() {
        UUID oldEventId = UUID.randomUUID();
        WebhookDeliveryLog existingLog = new WebhookDeliveryLog();
        existingLog.setId(UUID.randomUUID());
        existingLog.setParcelId(parcelId);
        existingLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        existingLog.setPayload("{\"eventId\":\"" + oldEventId + "\",\"currentStatus\":\"OUT_FOR_DELIVERY\"}");

        when(logRepository.findByParcelId(parcelId)).thenReturn(List.of(existingLog));
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        // sampleEvent has eventId != oldEventId
        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should not discard legitimate OUT_FOR_DELIVERY -> FAILED -> OUT_FOR_DELIVERY sequence")
    void shouldNotDiscardLegitimateReattemptSequence() {
        UUID firstAttemptEventId = UUID.randomUUID();
        WebhookDeliveryLog firstAttemptLog = new WebhookDeliveryLog();
        firstAttemptLog.setId(UUID.randomUUID());
        firstAttemptLog.setParcelId(parcelId);
        firstAttemptLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        firstAttemptLog.setPayload("{\"eventId\":\"" + firstAttemptEventId + "\",\"currentStatus\":\"OUT_FOR_DELIVERY\"}");

        UUID failedAttemptEventId = UUID.randomUUID();
        WebhookDeliveryLog failedAttemptLog = new WebhookDeliveryLog();
        failedAttemptLog.setId(UUID.randomUUID());
        failedAttemptLog.setParcelId(parcelId);
        failedAttemptLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        failedAttemptLog.setPayload("{\"eventId\":\"" + failedAttemptEventId + "\",\"currentStatus\":\"FAILED\"}");

        when(logRepository.findByParcelId(parcelId)).thenReturn(List.of(firstAttemptLog, failedAttemptLog));
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        // Re-attempt event has new eventId and currentStatus = OUT_FOR_DELIVERY
        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should safely process event with missing eventId when timestamp is distinct")
    void shouldSafelyProcessEventWithMissingEventId() {
        sampleEvent.setEventId(null);
        Instant now = Instant.parse("2026-11-15T18:00:00Z");
        sampleEvent.setTimestamp(now);

        WebhookDeliveryLog existingLog = new WebhookDeliveryLog();
        existingLog.setId(UUID.randomUUID());
        existingLog.setParcelId(parcelId);
        existingLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        // Previous log has older timestamp
        existingLog.setPayload("{\"currentStatus\":\"OUT_FOR_DELIVERY\",\"timestamp\":\"2026-11-14T10:00:00Z\"}");

        when(logRepository.findByParcelId(parcelId)).thenReturn(List.of(existingLog));
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.successful(200, sampleLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verify(dispatcherService).dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard duplicate event with missing eventId when both status and timestamp match exactly")
    void shouldDiscardDuplicateWhenMissingEventIdAndExactStatusAndTimestampMatch() {
        sampleEvent.setEventId(null);
        Instant exactTimestamp = Instant.parse("2026-11-15T18:00:00Z");
        sampleEvent.setTimestamp(exactTimestamp);

        WebhookDeliveryLog existingLog = new WebhookDeliveryLog();
        existingLog.setId(UUID.randomUUID());
        existingLog.setParcelId(parcelId);
        existingLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        existingLog.setPayload("{\"currentStatus\":\"OUT_FOR_DELIVERY\",\"timestamp\":\"2026-11-15T18:00:00Z\"}");

        when(logRepository.findByParcelId(parcelId)).thenReturn(List.of(existingLog));

        consumer.handleParcelStatusUpdated(sampleEvent);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should discard null or malformed message missing parcelId")
    void shouldDiscardNullOrMalformedMessage() {
        consumer.handleParcelStatusUpdated(null);

        ParcelStatusUpdatedEvent invalidEvent = new ParcelStatusUpdatedEvent();
        invalidEvent.setMerchantId(merchantId);
        consumer.handleParcelStatusUpdated(invalidEvent);

        verifyNoInteractions(dispatcherService);
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should reject and not requeue when unexpected dispatcher exception occurs")
    void shouldRejectAndNotRequeueOnDispatcherException() {
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenThrow(new RuntimeException("Critical database failure"));

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.handleParcelStatusUpdated(sampleEvent)
        );

        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should mark deliveryLog as DEAD_LETTER and reject without requeue when retry publisher throws exception")
    void shouldRejectAndNotRequeueOnRetryPublisherException() {
        when(logRepository.findByParcelId(parcelId)).thenReturn(Collections.emptyList());
        when(dispatcherService.dispatchWebhook(merchantId, parcelId, "PARCEL_STATUS_UPDATED", sampleEvent))
                .thenReturn(WebhookDispatchResult.retryableFailure(503, "HTTP_SERVER_ERROR", "Service Unavailable", sampleLog));
        doThrow(new IllegalStateException("RabbitMQ broker unreachable"))
                .when(retryPublisher).scheduleRetry(sampleLog);

        assertThrows(AmqpRejectAndDontRequeueException.class, () ->
                consumer.handleParcelStatusUpdated(sampleEvent)
        );

        assertThat(sampleLog.getDeliveryStatus()).isEqualTo(WebhookDeliveryStatus.DEAD_LETTER);
        verify(logRepository).save(sampleLog);
    }
}
