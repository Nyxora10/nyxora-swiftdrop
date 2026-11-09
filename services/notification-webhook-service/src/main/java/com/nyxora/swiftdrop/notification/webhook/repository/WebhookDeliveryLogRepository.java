package com.nyxora.swiftdrop.notification.webhook.repository;

import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link WebhookDeliveryLog} entities
 * stored in {@code notification_db.webhook_delivery_logs}.
 *
 * <h3>Supported Query Patterns</h3>
 * <ul>
 *   <li><b>Merchant Portal (Task 3.6):</b> Paginated delivery logs filtered by {@code merchantId},
 *       allowing merchants to inspect their own webhook dispatch history.</li>
 *   <li><b>Admin Portal (Task 3.6):</b> Paginated delivery logs across all merchants,
 *       via the inherited {@link JpaRepository#findAll(Pageable)} method.</li>
 *   <li><b>DLQ Retry Engine (Task 3.4):</b> Lookup logs in {@code RETRYING} status whose
 *       {@code nextRetryAt} timestamp has elapsed, enabling the retry scheduler to
 *       re-dispatch failed webhooks.</li>
 *   <li><b>Manual Retry (Task 3.6):</b> Lookup individual logs by ID via the inherited
 *       {@link JpaRepository#findById(Object)} method, supporting the
 *       {@code POST /api/v1/webhooks/logs/{id}/retry} endpoint.</li>
 * </ul>
 */
@Repository
public interface WebhookDeliveryLogRepository extends JpaRepository<WebhookDeliveryLog, UUID> {

    /**
     * Returns a paginated list of webhook delivery logs for a specific merchant.
     * Used by the Merchant Dashboard to display webhook dispatch history.
     *
     * @param merchantId the merchant's UUID
     * @param pageable   pagination and sorting parameters
     * @return a page of delivery logs belonging to the specified merchant
     */
    Page<WebhookDeliveryLog> findByMerchantId(UUID merchantId, Pageable pageable);

    /**
     * Returns a paginated list of webhook delivery logs filtered by delivery status.
     * Used by admin dashboards to view logs grouped by outcome.
     *
     * @param deliveryStatus the delivery status to filter by
     * @param pageable       pagination and sorting parameters
     * @return a page of delivery logs matching the specified status
     */
    Page<WebhookDeliveryLog> findByDeliveryStatus(WebhookDeliveryStatus deliveryStatus, Pageable pageable);

    /**
     * Returns a paginated list of webhook delivery logs for a specific merchant
     * filtered by delivery status. Supports merchant-facing status filtering.
     *
     * @param merchantId     the merchant's UUID
     * @param deliveryStatus the delivery status to filter by
     * @param pageable       pagination and sorting parameters
     * @return a page of delivery logs matching both merchant and status criteria
     */
    Page<WebhookDeliveryLog> findByMerchantIdAndDeliveryStatus(
            UUID merchantId, WebhookDeliveryStatus deliveryStatus, Pageable pageable);

    /**
     * Finds all delivery logs eligible for DLQ retry: those in the specified
     * delivery status whose next scheduled retry timestamp has already elapsed.
     *
     * <p>This query drives the exponential-backoff retry scheduler (Task 3.4).
     * The query leverages the composite index
     * {@code idx_webhook_logs_retry(delivery_status, next_retry_at)}
     * defined in {@code init-multi-db.sql}.</p>
     *
     * @param deliveryStatus the status to filter by (typically {@code RETRYING})
     * @param cutoff         the current timestamp; only logs with {@code nextRetryAt} before this are returned
     * @return list of delivery logs ready for retry dispatch
     */
    List<WebhookDeliveryLog> findByDeliveryStatusAndNextRetryAtBefore(
            WebhookDeliveryStatus deliveryStatus, Instant cutoff);

    /**
     * Finds a delivery log by its primary key while strictly scoping to the owning merchant.
     * Enforces tenant isolation at the database query level (Task 3.6).
     *
     * @param id         the delivery log UUID
     * @param merchantId the owning merchant UUID
     * @return Optional containing the log if found and owned by merchant; empty otherwise
     */
    Optional<WebhookDeliveryLog> findByIdAndMerchantId(UUID id, UUID merchantId);

    /**
     * Atomically transitions the delivery status and retry count of a log from an expected state to a new state.
     *
     * <p>Provides native, lock-free, concurrency-safe updates in PostgreSQL without requiring
     * schema modifications or separate distributed locks. If two concurrent manual retry requests
     * target the same log, only the first transaction modifies the row (returning {@code 1});
     * the second matches {@code 0} rows and is safely rejected.</p>
     *
     * @param id             the delivery log UUID
     * @param merchantId     the owning merchant UUID
     * @param expectedStatus the current status required for update (e.g. DEAD_LETTER)
     * @param newStatus      the target status (e.g. RETRYING)
     * @param newRetryCount  the retry sequence count to set (e.g. 5 for manual retry)
     * @param nextRetryAt    the timestamp of the next retry attempt (or null)
     * @return number of affected rows (1 if transition succeeded, 0 if state had changed)
     */
    @Modifying
    @Query("UPDATE WebhookDeliveryLog w SET w.deliveryStatus = :newStatus, w.retryCount = :newRetryCount, w.nextRetryAt = :nextRetryAt "
            + "WHERE w.id = :id AND w.merchantId = :merchantId AND w.deliveryStatus = :expectedStatus")
    int transitionStatusIfExpected(
            @Param("id") UUID id,
            @Param("merchantId") UUID merchantId,
            @Param("expectedStatus") WebhookDeliveryStatus expectedStatus,
            @Param("newStatus") WebhookDeliveryStatus newStatus,
            @Param("newRetryCount") Integer newRetryCount,
            @Param("nextRetryAt") Instant nextRetryAt);
}

