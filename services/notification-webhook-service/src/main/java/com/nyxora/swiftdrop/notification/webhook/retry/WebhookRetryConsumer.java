package com.nyxora.swiftdrop.notification.webhook.retry;

import com.nyxora.swiftdrop.notification.webhook.config.WebhookRabbitConfig;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookDispatcherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * RabbitMQ consumer for outbound merchant webhook backoff retries (Task 3.4).
 *
 * <p>
 * Listens on the retry processing queue
 * {@code swiftdrop.webhook.retry.process},
 * which receives messages after their TTL backoff expires in the 5 delay
 * queues.
 *
 * <p>
 * Reliability & Safety Guarantees:
 * <ul>
 * <li><b>Audit Preservation:</b> Reuses existing {@link WebhookDeliveryLog}
 * rows; never creates duplicates.</li>
 * <li><b>Idempotency & Stale Guard:</b> Discards messages for already
 * {@code DELIVERED} or {@code DEAD_LETTER}
 * logs, or messages whose retryCount is strictly less than the database's
 * current retryCount.</li>
 * <li><b>Security Verification:</b> Verifies that the message's merchantId and
 * parcelId match the database log.</li>
 * <li><b>Loop Prevention:</b> If {@link WebhookRetryPublisher} fails, throws
 * {@link AmqpRejectAndDontRequeueException}
 * to prevent endless immediate redelivery loops.</li>
 * </ul>
 */
@Component
public class WebhookRetryConsumer {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryConsumer.class);

    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookDispatcherService dispatcherService;
    private final WebhookRetryPublisher retryPublisher;

    @Autowired
    public WebhookRetryConsumer(
            WebhookDeliveryLogRepository logRepository,
            WebhookDispatcherService dispatcherService,
            WebhookRetryPublisher retryPublisher) {
        this.logRepository = logRepository;
        this.dispatcherService = dispatcherService;
        this.retryPublisher = retryPublisher;
    }

    /**
     * Consumes expired webhook retry messages and triggers the subsequent HTTP
     * delivery attempt.
     *
     * @param message the retry message received from the delay queue
     */
    @RabbitListener(queues = "${app.rabbitmq.webhook-retry-queue:" + WebhookRabbitConfig.RETRY_PROCESS_QUEUE + "}")
    public void handleRetryMessage(WebhookRetryMessage message) {
        // 1. Message Validation
        if (message == null || message.deliveryLogId() == null) {
            log.warn("Discarding malformed or null WebhookRetryMessage");
            return;
        }

        log.info("Received webhook retry message: deliveryLogId={}, attempt=#{}, merchantId={}",
                message.deliveryLogId(), message.retryCount(), message.merchantId());

        // 2. Load Existing Delivery Log from PostgreSQL
        Optional<WebhookDeliveryLog> logOpt = logRepository.findById(message.deliveryLogId());
        if (logOpt.isEmpty()) {
            log.warn("Delivery log not found for retry: deliveryLogId={}. Discarding message.",
                    message.deliveryLogId());
            return;
        }
        WebhookDeliveryLog deliveryLog = logOpt.get();

        // 3. Security Verification: Merchant and Parcel Ownership
        if (!deliveryLog.getMerchantId().equals(message.merchantId())
                || !deliveryLog.getParcelId().equals(message.parcelId())) {
            log.error(
                    "Context mismatch for deliveryLogId={}: message claims merchantId={}, parcelId={}, but log contains merchantId={}, parcelId={}. Discarding.",
                    deliveryLog.getId(), message.merchantId(), message.parcelId(),
                    deliveryLog.getMerchantId(), deliveryLog.getParcelId());
            return;
        }

        // 4. Finalized Status Guard (Idempotency)
        if (deliveryLog.getDeliveryStatus() == WebhookDeliveryStatus.DELIVERED) {
            log.info("Ignoring retry message for already DELIVERED webhook: deliveryLogId={}", deliveryLog.getId());
            return;
        }
        if (deliveryLog.getDeliveryStatus() == WebhookDeliveryStatus.DEAD_LETTER) {
            log.info("Ignoring retry message for already DEAD_LETTER webhook: deliveryLogId={}", deliveryLog.getId());
            return;
        }

        // 5. Active Status & Exact Sequence Validation (Duplicate & Stale Message Guard)
        if (deliveryLog.getDeliveryStatus() != WebhookDeliveryStatus.RETRYING) {
            log.warn("Discarding unexpected retry message for deliveryLogId={}: expected status RETRYING but found {}",
                    deliveryLog.getId(), deliveryLog.getDeliveryStatus());
            return;
        }

        if (deliveryLog.getRetryCount() == null || message.retryCount() != deliveryLog.getRetryCount()) {
            log.warn("Discarding out-of-sequence retry message for deliveryLogId={}: message retryCount={} does not match expected DB retryCount={}",
                    deliveryLog.getId(), message.retryCount(), deliveryLog.getRetryCount());
            return;
        }

        // 6. Execute Retry Dispatch via WebhookDispatcherService
        log.info("Executing webhook retry dispatch for deliveryLogId={}, attempt #{}",
                deliveryLog.getId(), message.retryCount());

        WebhookDispatchResult result = dispatcherService.dispatchRetryWebhook(deliveryLog);

        // 7. Evaluate Outcome
        if (result.success()) {
            log.info("Webhook retry attempt #{} SUCCEEDED for deliveryLogId={}, statusCode={}",
                    message.retryCount(), deliveryLog.getId(), result.httpStatusCode());
            // Marked DELIVERED and nextRetryAt cleared inside dispatchRetryWebhook
            return;
        }

        // Handle Dispatch Failure
        log.warn("Webhook retry attempt #{} FAILED for deliveryLogId={}, statusCode={}, errorCategory={}",
                message.retryCount(), deliveryLog.getId(), result.httpStatusCode(), result.errorCategory());

        if (!result.retryable()) {
            log.warn("Failure is non-retryable for deliveryLogId={}. Marking DEAD_LETTER.", deliveryLog.getId());
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            logRepository.save(deliveryLog);
            return;
        }

        // Failure is retryable: check retry policy
        int currentAttempt = deliveryLog.getRetryCount() != null ? deliveryLog.getRetryCount() : message.retryCount();

        if (WebhookRetryPolicy.canRetry(currentAttempt)) {
            try {
                log.info("Scheduling subsequent retry for deliveryLogId={}, currentAttempt={}",
                        deliveryLog.getId(), currentAttempt);
                retryPublisher.scheduleRetry(deliveryLog);
            } catch (Exception ex) {
                log.error("Failed to publish next retry message to RabbitMQ DLX for deliveryLogId={}: {}",
                        deliveryLog.getId(), ex.getMessage(), ex);
                // Reject without requeueing to avoid fast immediate retry storm on RabbitMQ
                // broker
                throw new AmqpRejectAndDontRequeueException(
                        "RabbitMQ publication failed for deliveryLogId=" + deliveryLog.getId(), ex);
            }
        } else {
            log.warn("All {} retries exhausted for deliveryLogId={}. Moving to DEAD_LETTER.",
                    WebhookRetryPolicy.MAX_RETRIES, deliveryLog.getId());
            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            logRepository.save(deliveryLog);
        }
    }
}
