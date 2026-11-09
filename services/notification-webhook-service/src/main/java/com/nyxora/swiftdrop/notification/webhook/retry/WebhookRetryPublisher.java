package com.nyxora.swiftdrop.notification.webhook.retry;

import com.nyxora.swiftdrop.notification.webhook.config.WebhookRabbitConfig;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service responsible for publishing failed merchant webhook dispatches to the
 * RabbitMQ Dead-Letter Exchange (swiftdrop.dlx) with per-attempt TTL delays (Task 3.4).
 *
 * <p>Key Invariants:
 * <ul>
 *   <li><b>Audit Continuity:</b> Preserves the existing {@link WebhookDeliveryLog} row identity;
 *       never inserts duplicate log rows for retry attempts.</li>
 *   <li><b>Durable Delivery:</b> Messages are published with {@link MessageDeliveryMode#PERSISTENT}.</li>
 *   <li><b>Exhaustion Boundary:</b> When {@code retryCount >= 5}, marks status as
 *       {@link WebhookDeliveryStatus#DEAD_LETTER} and halts further publishing.</li>
 *   <li><b>Zero Information Leakage:</b> Never serializes or logs webhook signing secrets.</li>
 * </ul>
 */
@Service
public class WebhookRetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(WebhookRetryPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final WebhookDeliveryLogRepository logRepository;

    @Autowired
    public WebhookRetryPublisher(
            RabbitTemplate rabbitTemplate,
            WebhookDeliveryLogRepository logRepository) {
        this.rabbitTemplate = rabbitTemplate;
        this.logRepository = logRepository;
    }

    /**
     * Schedules a backoff retry for an existing delivery log if retry attempts remain.
     *
     * @param deliveryLog the existing persisted delivery log
     * @return {@code true} if message was scheduled to a TTL queue;
     *         {@code false} if retries are exhausted and status was updated to DEAD_LETTER
     * @throws IllegalArgumentException if deliveryLog is null or missing an ID
     */
    public boolean scheduleRetry(WebhookDeliveryLog deliveryLog) {
        if (deliveryLog == null || deliveryLog.getId() == null) {
            throw new IllegalArgumentException("Cannot schedule retry for null or unpersisted delivery log");
        }

        int currentCount = deliveryLog.getRetryCount() != null ? deliveryLog.getRetryCount() : 0;

        // 1. Boundary Check: Are retry attempts exhausted?
        if (!WebhookRetryPolicy.canRetry(currentCount)) {
            log.warn("Exhausted all {} permitted retries for deliveryLogId={}, merchantId={}. Marking DEAD_LETTER.",
                    WebhookRetryPolicy.MAX_RETRIES, deliveryLog.getId(), deliveryLog.getMerchantId());

            deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
            deliveryLog.setNextRetryAt(null);
            logRepository.save(deliveryLog);
            return false;
        }

        // 2. Calculate next retry parameters
        int nextAttemptNumber = currentCount + 1;
        String routingKey = WebhookRetryPolicy.getRoutingKeyForNextRetry(currentCount);
        Instant nextRetryAt = WebhookRetryPolicy.calculateNextRetryAt(currentCount);

        log.info("Scheduling webhook retry #{} for deliveryLogId={}, merchantId={}, delayRoutingKey={}, nextRetryAt={}",
                nextAttemptNumber, deliveryLog.getId(), deliveryLog.getMerchantId(), routingKey, nextRetryAt);

        // 3. Construct serializable retry DTO preserving original payload byte-identity
        WebhookRetryMessage retryMessage = new WebhookRetryMessage(
                deliveryLog.getId(),
                deliveryLog.getMerchantId(),
                deliveryLog.getParcelId(),
                deliveryLog.getEventType(),
                deliveryLog.getPayload(),
                nextAttemptNumber
        );

        // 4. Publish to DLX exchange with PERSISTENT delivery mode first
        // If broker publishing fails, we do NOT advance the DB retryCount, avoiding phantom retry states.
        try {
            rabbitTemplate.convertAndSend(
                    WebhookRabbitConfig.DEAD_LETTER_EXCHANGE,
                    routingKey,
                    retryMessage,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setContentType(MediaType.APPLICATION_JSON_VALUE);
                        message.getMessageProperties().setHeader("x-swiftdrop-retry-count", nextAttemptNumber);
                        return message;
                    }
            );

            log.info("Successfully enqueued retry #{} to {} via key {}",
                    nextAttemptNumber, WebhookRabbitConfig.DEAD_LETTER_EXCHANGE, routingKey);

        } catch (Exception ex) {
            log.error("Failed to publish retry message to RabbitMQ for deliveryLogId={}, routingKey={}: {}",
                    deliveryLog.getId(), routingKey, ex.getMessage(), ex);
            // Re-throw so caller knows publication failed; DB is NOT updated with the unqueued attempt count!
            throw new IllegalStateException("RabbitMQ retry publication failed for deliveryLogId=" + deliveryLog.getId(), ex);
        }

        // 5. Update and persist delivery log ONLY AFTER successful broker dispatch
        deliveryLog.setRetryCount(nextAttemptNumber);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        deliveryLog.setNextRetryAt(nextRetryAt);
        logRepository.save(deliveryLog);

        return true;
    }

    /**
     * Publishes a manual retry message for an eligible failed webhook delivery (Task 3.6).
     *
     * <p>Design & Infinite-Loop Prevention:
     * <ul>
     *   <li>Retains {@code retryCount} at {@link WebhookRetryPolicy#MAX_RETRIES} (5) so that
     *       if this manual attempt fails, {@link WebhookRetryConsumer} immediately transitions
     *       the record back to {@code DEAD_LETTER} rather than scheduling an infinite cascading
     *       automatic retry sequence.</li>
     *   <li>Routes directly to {@link WebhookRabbitConfig#MAIN_TOPIC_EXCHANGE} via
     *       {@link WebhookRabbitConfig#RETRY_PROCESS_ROUTING_KEY} for immediate processing.</li>
     *   <li>Publishes with persistent delivery mode and custom audit headers.</li>
     * </ul>
     * </p>
     *
     * @param deliveryLog the existing delivery log entity
     * @return true if published successfully
     * @throws IllegalStateException if RabbitMQ publication fails
     */
    public boolean publishManualRetry(WebhookDeliveryLog deliveryLog) {
        if (deliveryLog == null || deliveryLog.getId() == null) {
            throw new IllegalArgumentException("Cannot publish manual retry: deliveryLog or ID is null");
        }

        int attemptNumber = deliveryLog.getRetryCount() != null ? deliveryLog.getRetryCount() : WebhookRetryPolicy.MAX_RETRIES;

        WebhookRetryMessage retryMessage = new WebhookRetryMessage(
                deliveryLog.getId(),
                deliveryLog.getMerchantId(),
                deliveryLog.getParcelId(),
                deliveryLog.getEventType(),
                deliveryLog.getPayload(),
                attemptNumber
        );

        try {
            rabbitTemplate.convertAndSend(
                    WebhookRabbitConfig.MAIN_TOPIC_EXCHANGE,
                    WebhookRabbitConfig.RETRY_PROCESS_ROUTING_KEY,
                    retryMessage,
                    message -> {
                        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        message.getMessageProperties().setContentType(MediaType.APPLICATION_JSON_VALUE);
                        message.getMessageProperties().setHeader("x-swiftdrop-retry-count", attemptNumber);
                        message.getMessageProperties().setHeader("x-swiftdrop-manual-retry", true);
                        return message;
                    }
            );

            log.info("Successfully enqueued manual retry for deliveryLogId={}, merchantId={} to {}",
                    deliveryLog.getId(), deliveryLog.getMerchantId(), WebhookRabbitConfig.RETRY_PROCESS_QUEUE);
            return true;

        } catch (Exception ex) {
            log.error("Failed to publish manual retry message to RabbitMQ for deliveryLogId={}: {}",
                    deliveryLog.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("RabbitMQ manual retry publication failed for deliveryLogId=" + deliveryLog.getId(), ex);
        }
    }
}

