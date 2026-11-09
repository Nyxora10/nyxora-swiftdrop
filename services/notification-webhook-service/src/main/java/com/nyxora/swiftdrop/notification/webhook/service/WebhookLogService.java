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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Service orchestrating paginated delivery log queries and manual retry requests (Task 3.6).
 *
 * <p><strong>Key Invariants & Transaction Guarantees:</strong>
 * <ul>
 *   <li>Enforces tenant isolation by always qualifying queries with the authenticated {@code merchantId}.</li>
 *   <li>Only terminal failure records ({@link WebhookDeliveryStatus#DEAD_LETTER}) are eligible for manual retry.</li>
 *   <li>Active, pending, or successful dispatches (PENDING, RETRYING, DELIVERED) are rejected.</li>
 *   <li>Uses atomic conditional database updates to prevent concurrent retry requests from creating duplicate deliveries.</li>
 *   <li><b>Race-Condition Elimination:</b> Commits the database update (DEAD_LETTER -&gt; RETRYING) in a dedicated
 *       transaction <em>before</em> publishing the retry message to RabbitMQ. This guarantees that
 *       {@code WebhookRetryConsumer} never observes stale uncommitted {@code DEAD_LETTER} status.</li>
 *   <li>Maintains existing HMAC payload identity and ensures a single retry attempt is scheduled without triggering cascading loops.</li>
 *   <li>Executes a compensating transaction resetting state back to {@code DEAD_LETTER} if RabbitMQ publication fails.</li>
 * </ul>
 * </p>
 */
@Service
public class WebhookLogService {

    private static final Logger log = LoggerFactory.getLogger(WebhookLogService.class);

    private final WebhookDeliveryLogRepository logRepository;
    private final WebhookRetryPublisher retryPublisher;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public WebhookLogService(
            WebhookDeliveryLogRepository logRepository,
            WebhookRetryPublisher retryPublisher,
            @Autowired(required = false) PlatformTransactionManager transactionManager) {
        this.logRepository = logRepository;
        this.retryPublisher = retryPublisher;
        this.transactionTemplate = transactionManager != null ? new TransactionTemplate(transactionManager) : null;
    }

    public WebhookLogService(
            WebhookDeliveryLogRepository logRepository,
            WebhookRetryPublisher retryPublisher) {
        this(logRepository, retryPublisher, null);
    }

    /**
     * Retrieves paginated delivery logs for a specific merchant with optional status filtering.
     *
     * @param merchantId the verified merchant UUID
     * @param status     optional status filter (null for all statuses)
     * @param pageable   pagination parameters (page, size, sort)
     * @return paginated response DTO
     */
    @Transactional(readOnly = true)
    public WebhookLogsPageResponse getMerchantLogs(UUID merchantId, WebhookDeliveryStatus status, Pageable pageable) {
        if (merchantId == null) {
            throw new IllegalArgumentException("merchantId must not be null");
        }

        log.debug("Querying webhook delivery logs for merchantId={}, statusFilter={}, page={}",
                merchantId, status, pageable != null ? pageable.getPageNumber() : 0);

        Page<WebhookDeliveryLog> page;
        if (status != null) {
            page = logRepository.findByMerchantIdAndDeliveryStatus(merchantId, status, pageable);
        } else {
            page = logRepository.findByMerchantId(merchantId, pageable);
        }

        return WebhookLogsPageResponse.fromPage(page);
    }

    /**
     * Executes a manual retry for an eligible failed webhook delivery log.
     *
     * <p>Concurrency, Race-Free Ordering &amp; Reliability Contract:
     * <ol>
     *   <li>Verifies log existence and ownership by {@code merchantId}.</li>
     *   <li>Ensures log is currently in {@link WebhookDeliveryStatus#DEAD_LETTER}.</li>
     *   <li>Executes an atomic conditional update {@code DEAD_LETTER -> RETRYING} setting {@code retryCount = 5}.
     *       If another concurrent thread won the race, zero rows are affected and a {@link ConcurrentRetryException} is thrown.</li>
     *   <li>COMMITS the database transaction prior to RabbitMQ publication so that {@code WebhookRetryConsumer}
     *       never observes a stale {@code DEAD_LETTER} status when reading from PostgreSQL.</li>
     *   <li>Publishes the manual retry message to RabbitMQ topic exchange with {@code retryCount = 5}. If publication fails,
     *       executes a compensating transaction resetting status back to {@code DEAD_LETTER} to prevent stranding.</li>
     * </ol>
     * </p>
     *
     * @param deliveryLogId the delivery log UUID
     * @param merchantId    the verified merchant UUID
     * @return response DTO indicating retry acceptance
     */
    public ManualRetryResponse retryFailedDelivery(UUID deliveryLogId, UUID merchantId) {
        if (deliveryLogId == null || merchantId == null) {
            throw new IllegalArgumentException("deliveryLogId and merchantId must not be null");
        }

        log.info("Processing manual webhook retry request for deliveryLogId={}, merchantId={}",
                deliveryLogId, merchantId);

        Instant now = Instant.now();
        AtomicInteger previousRetryCountHolder = new AtomicInteger(WebhookRetryPolicy.MAX_RETRIES);

        // 1. Transactionally transition state in DB and COMMIT before publishing to RabbitMQ.
        // This eliminates the race condition where WebhookRetryConsumer reads uncommitted DEAD_LETTER status.
        WebhookDeliveryLog deliveryLog = executeInTransaction(() -> {
            Optional<WebhookDeliveryLog> logOpt = logRepository.findByIdAndMerchantId(deliveryLogId, merchantId);
            if (logOpt.isEmpty()) {
                log.warn("Manual retry rejected: delivery log not found or cross-merchant attempt for deliveryLogId={}, merchantId={}",
                        deliveryLogId, merchantId);
                throw new DeliveryLogNotFoundException("Webhook delivery log not found: " + deliveryLogId);
            }

            WebhookDeliveryLog entity = logOpt.get();

            if (entity.getDeliveryStatus() != WebhookDeliveryStatus.DEAD_LETTER) {
                log.warn("Manual retry rejected: deliveryLogId={} is in ineligible status {}",
                        deliveryLogId, entity.getDeliveryStatus());
                throw new InvalidDeliveryStatusException(
                        "Only deliveries in DEAD_LETTER status are eligible for manual retry. Current status: " + entity.getDeliveryStatus(),
                        entity.getDeliveryStatus()
                );
            }

            int prevCount = entity.getRetryCount() != null ? entity.getRetryCount() : WebhookRetryPolicy.MAX_RETRIES;
            previousRetryCountHolder.set(prevCount);

            int rowsUpdated = logRepository.transitionStatusIfExpected(
                    deliveryLogId,
                    merchantId,
                    WebhookDeliveryStatus.DEAD_LETTER,
                    WebhookDeliveryStatus.RETRYING,
                    WebhookRetryPolicy.MAX_RETRIES,
                    now
            );

            if (rowsUpdated == 0) {
                log.warn("Manual retry rejected: concurrent retry already active for deliveryLogId={}", deliveryLogId);
                throw new ConcurrentRetryException(
                        "A concurrent retry request was already processed for deliveryLogId=" + deliveryLogId
                );
            }

            entity.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
            entity.setRetryCount(WebhookRetryPolicy.MAX_RETRIES);
            entity.setNextRetryAt(now);

            return entity;
        });

        int previousRetryCount = previousRetryCountHolder.get();

        // 2. Publish to RabbitMQ topic exchange AFTER database transaction has committed.
        // WebhookRetryConsumer will now observe committed status RETRYING and retryCount=5.
        try {
            retryPublisher.publishManualRetry(deliveryLog);
            log.info("Manual retry successfully published to RabbitMQ for deliveryLogId={}, merchantId={}",
                    deliveryLogId, merchantId);

        } catch (Exception ex) {
            log.error("RabbitMQ manual retry publishing failed for deliveryLogId={}. Executing compensating state rollback: {}",
                    deliveryLogId, ex.getMessage(), ex);

            // Roll back database state to DEAD_LETTER in a committed compensating transaction
            try {
                executeInTransaction(() -> {
                    logRepository.transitionStatusIfExpected(
                            deliveryLogId,
                            merchantId,
                            WebhookDeliveryStatus.RETRYING,
                            WebhookDeliveryStatus.DEAD_LETTER,
                            previousRetryCount,
                            null
                    );
                    return null;
                });
            } catch (Exception rollbackEx) {
                log.error("Failed to execute compensating rollback for deliveryLogId={}: {}",
                        deliveryLogId, rollbackEx.getMessage(), rollbackEx);
            }

            throw new IllegalStateException("Failed to enqueue manual retry to message broker for deliveryLogId=" + deliveryLogId, ex);
        }

        return ManualRetryResponse.accepted(
                deliveryLogId,
                merchantId,
                WebhookDeliveryStatus.DEAD_LETTER,
                WebhookDeliveryStatus.RETRYING,
                "Manual webhook retry request accepted and enqueued for dispatch"
        );
    }

    private <T> T executeInTransaction(Supplier<T> action) {
        if (transactionTemplate != null) {
            return transactionTemplate.execute(status -> action.get());
        } else {
            return action.get();
        }
    }
}
