package com.nyxora.swiftdrop.notification.webhook.service;

import com.nyxora.swiftdrop.notification.webhook.dto.ManualRetryResponse;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookLogsPageResponse;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.exception.ConcurrentRetryException;
import com.nyxora.swiftdrop.notification.webhook.exception.DeliveryLogNotFoundException;
import com.nyxora.swiftdrop.notification.webhook.exception.InvalidDeliveryStatusException;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPolicy;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookLogService Business Logic Unit Tests")
class WebhookLogServiceTest {

    @Mock
    private WebhookDeliveryLogRepository logRepository;

    @Mock
    private WebhookRetryPublisher retryPublisher;

    private WebhookLogService logService;

    private UUID merchantId;
    private UUID deliveryLogId;
    private WebhookDeliveryLog sampleLog;

    @BeforeEach
    void setUp() {
        logService = new WebhookLogService(logRepository, retryPublisher);

        merchantId = UUID.randomUUID();
        deliveryLogId = UUID.randomUUID();

        sampleLog = new WebhookDeliveryLog();
        sampleLog.setId(deliveryLogId);
        sampleLog.setMerchantId(merchantId);
        sampleLog.setParcelId(UUID.randomUUID());
        sampleLog.setEventType("parcel.status.updated");
        sampleLog.setPayload("{\"trackingNumber\":\"SD-123\"}");
        sampleLog.setRetryCount(5);
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
        sampleLog.setCreatedAt(Instant.now());
    }

    // ─── Query Tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Should query logs by merchant ID and status when status filter is provided")
    void shouldQueryLogsWithStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<WebhookDeliveryLog> page = new PageImpl<>(List.of(sampleLog), pageable, 1);

        when(logRepository.findByMerchantIdAndDeliveryStatus(merchantId, WebhookDeliveryStatus.DEAD_LETTER, pageable))
                .thenReturn(page);

        WebhookLogsPageResponse response = logService.getMerchantLogs(
                merchantId, WebhookDeliveryStatus.DEAD_LETTER, pageable);

        assertNotNull(response);
        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());
        assertEquals(deliveryLogId, response.content().get(0).id());
        verify(logRepository).findByMerchantIdAndDeliveryStatus(merchantId, WebhookDeliveryStatus.DEAD_LETTER, pageable);
        verify(logRepository, never()).findByMerchantId(any(), any());
    }

    @Test
    @DisplayName("Should query logs by merchant ID without status filter when status is null")
    void shouldQueryLogsWithoutStatusFilter() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<WebhookDeliveryLog> page = new PageImpl<>(List.of(sampleLog), pageable, 1);

        when(logRepository.findByMerchantId(merchantId, pageable)).thenReturn(page);

        WebhookLogsPageResponse response = logService.getMerchantLogs(merchantId, null, pageable);

        assertNotNull(response);
        assertEquals(1, response.totalElements());
        verify(logRepository).findByMerchantId(merchantId, pageable);
        verify(logRepository, never()).findByMerchantIdAndDeliveryStatus(any(), any(), any());
    }

    // ─── Manual Retry Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Should accept manual retry for eligible DEAD_LETTER log and publish to RabbitMQ")
    void shouldAcceptManualRetryForDeadLetterLog() {
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));
        when(logRepository.transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER),
                eq(WebhookDeliveryStatus.RETRYING), eq(WebhookRetryPolicy.MAX_RETRIES), any(Instant.class)))
                .thenReturn(1);
        when(retryPublisher.publishManualRetry(sampleLog)).thenReturn(true);

        ManualRetryResponse response = logService.retryFailedDelivery(deliveryLogId, merchantId);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());
        assertEquals(deliveryLogId, response.deliveryLogId());
        assertEquals(merchantId, response.merchantId());
        assertEquals(WebhookDeliveryStatus.DEAD_LETTER, response.previousStatus());
        assertEquals(WebhookDeliveryStatus.RETRYING, response.newStatus());

        verify(retryPublisher).publishManualRetry(sampleLog);
        verify(logRepository, never()).save(any()); // Original entity reused without creating second row
    }

    @Test
    @DisplayName("Should reject manual retry with 404 when delivery log does not exist or belongs to another merchant")
    void shouldThrowNotFoundWhenLogDoesNotExistOrBelongsToOtherMerchant() {
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.empty());

        assertThrows(DeliveryLogNotFoundException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should reject manual retry when delivery log is in DELIVERED status")
    void shouldRejectManualRetryForDeliveredLog() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));

        InvalidDeliveryStatusException ex = assertThrows(InvalidDeliveryStatusException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        assertEquals(WebhookDeliveryStatus.DELIVERED, ex.getCurrentStatus());
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should reject manual retry when delivery log is currently in RETRYING status")
    void shouldRejectManualRetryForRetryingLog() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));

        InvalidDeliveryStatusException ex = assertThrows(InvalidDeliveryStatusException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        assertEquals(WebhookDeliveryStatus.RETRYING, ex.getCurrentStatus());
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should reject manual retry when delivery log is still in PENDING status")
    void shouldRejectManualRetryForPendingLog() {
        sampleLog.setDeliveryStatus(WebhookDeliveryStatus.PENDING);
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));

        InvalidDeliveryStatusException ex = assertThrows(InvalidDeliveryStatusException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        assertEquals(WebhookDeliveryStatus.PENDING, ex.getCurrentStatus());
        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should reject manual retry when concurrent request won the race (rowsUpdated == 0)")
    void shouldThrowConcurrentRetryExceptionWhenRaceLost() {
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));
        when(logRepository.transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER),
                eq(WebhookDeliveryStatus.RETRYING), eq(WebhookRetryPolicy.MAX_RETRIES), any(Instant.class)))
                .thenReturn(0);

        assertThrows(ConcurrentRetryException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        verifyNoInteractions(retryPublisher);
    }

    @Test
    @DisplayName("Should execute compensating state rollback to DEAD_LETTER when RabbitMQ publish fails")
    void shouldRollbackStateWhenBrokerPublicationFails() {
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));
        when(logRepository.transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER),
                eq(WebhookDeliveryStatus.RETRYING), eq(WebhookRetryPolicy.MAX_RETRIES), any(Instant.class)))
                .thenReturn(1);
        doThrow(new IllegalStateException("RabbitMQ down")).when(retryPublisher).publishManualRetry(sampleLog);

        assertThrows(IllegalStateException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        // Verify compensating rollback: status reset from RETRYING back to DEAD_LETTER
        verify(logRepository).transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.RETRYING),
                eq(WebhookDeliveryStatus.DEAD_LETTER), eq(5), isNull());
    }

    @Test
    @DisplayName("Should preserve initial retryCount across compensating rollback when initial count was less than 5")
    void shouldPreserveOriginalRetryCountOnRollback() {
        sampleLog.setRetryCount(3);
        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));
        when(logRepository.transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER),
                eq(WebhookDeliveryStatus.RETRYING), eq(WebhookRetryPolicy.MAX_RETRIES), any(Instant.class)))
                .thenReturn(1);
        doThrow(new IllegalStateException("RabbitMQ cluster unavailable")).when(retryPublisher).publishManualRetry(sampleLog);

        assertThrows(IllegalStateException.class,
                () -> logService.retryFailedDelivery(deliveryLogId, merchantId));

        // Verify compensating rollback resets to original retry count 3
        verify(logRepository).transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.RETRYING),
                eq(WebhookDeliveryStatus.DEAD_LETTER), eq(3), isNull());
    }

    @Test
    @DisplayName("Should commit transaction prior to publishing when PlatformTransactionManager is provided")
    void shouldCommitTransactionBeforePublishingWhenTransactionManagerPresent() {
        PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
        TransactionStatus txStatus = mock(TransactionStatus.class);
        when(txManager.getTransaction(any(TransactionDefinition.class))).thenReturn(txStatus);

        WebhookLogService txLogService = new WebhookLogService(logRepository, retryPublisher, txManager);

        when(logRepository.findByIdAndMerchantId(deliveryLogId, merchantId))
                .thenReturn(Optional.of(sampleLog));
        when(logRepository.transitionStatusIfExpected(
                eq(deliveryLogId), eq(merchantId), eq(WebhookDeliveryStatus.DEAD_LETTER),
                eq(WebhookDeliveryStatus.RETRYING), eq(WebhookRetryPolicy.MAX_RETRIES), any(Instant.class)))
                .thenReturn(1);
        when(retryPublisher.publishManualRetry(sampleLog)).thenReturn(true);

        ManualRetryResponse response = txLogService.retryFailedDelivery(deliveryLogId, merchantId);

        assertNotNull(response);
        assertEquals("ACCEPTED", response.status());

        // InOrder verifier: verify that commit happens strictly BEFORE publishManualRetry
        InOrder inOrder = inOrder(txManager, retryPublisher);
        inOrder.verify(txManager).commit(txStatus);
        inOrder.verify(retryPublisher).publishManualRetry(sampleLog);
    }
}
