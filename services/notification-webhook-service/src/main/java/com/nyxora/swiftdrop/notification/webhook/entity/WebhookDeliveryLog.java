package com.nyxora.swiftdrop.notification.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code webhook_delivery_logs} table in {@code notification_db}.
 *
 * <p>Records every outbound merchant webhook dispatch attempt, including the
 * signed JSON payload, HTTP response code, retry count, and current delivery
 * status. This table is the single source of truth for the webhook audit trail
 * and drives the DLQ exponential-backoff retry engine.</p>
 *
 * <h3>Schema Reference ({@code docker/postgres/init-multi-db.sql})</h3>
 * <pre>
 * CREATE TABLE IF NOT EXISTS webhook_delivery_logs (
 *     id               UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
 *     parcel_id        UUID NOT NULL,
 *     merchant_id      UUID NOT NULL,
 *     event_type       VARCHAR(50) NOT NULL,
 *     payload          JSONB NOT NULL,
 *     http_status_code INT,
 *     retry_count      INT DEFAULT 0,
 *     delivery_status  VARCHAR(30) NOT NULL DEFAULT 'PENDING',
 *     next_retry_at    TIMESTAMP WITH TIME ZONE,
 *     created_at       TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
 * );
 * </pre>
 *
 * <h3>Retry Semantics</h3>
 * <ul>
 *   <li>{@code retryCount} tracks the number of retry attempts <em>after</em> the
 *       initial delivery attempt. A value of {@code 0} means only the initial
 *       attempt has been made (or is pending).</li>
 *   <li>Up to 5 retries are permitted (delays: 10s, 30s, 2m, 10m, 30m).</li>
 *   <li>When {@code retryCount} reaches 5 and the delivery still fails,
 *       {@code deliveryStatus} is set to {@link WebhookDeliveryStatus#DEAD_LETTER}.</li>
 * </ul>
 */
@Entity
@Table(name = "webhook_delivery_logs")
public class WebhookDeliveryLog {

    /**
     * Maximum number of retry attempts permitted after the initial delivery attempt.
     */
    public static final int MAX_RETRY_COUNT = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "parcel_id", nullable = false, updatable = false)
    private UUID parcelId;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    private UUID merchantId;

    @Column(name = "event_type", nullable = false, length = 50, updatable = false)
    private String eventType;

    /**
     * The signed JSON payload dispatched to the merchant webhook endpoint.
     * Mapped to the PostgreSQL {@code JSONB} column using Hibernate 6 native JSON support.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    /**
     * Number of retry attempts performed after the initial delivery attempt.
     * Starts at 0 and increments with each DLQ retry, up to {@link #MAX_RETRY_COUNT}.
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 30)
    private WebhookDeliveryStatus deliveryStatus = WebhookDeliveryStatus.PENDING;

    /**
     * Timestamp of the next scheduled retry attempt via the DLQ.
     * {@code null} when no retry is pending (status is PENDING, DELIVERED, or DEAD_LETTER).
     */
    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    // ─── Lifecycle Callback ─────────────────────────────────────────────

    /**
     * Sets default values for fields that have database-level defaults,
     * ensuring consistency regardless of whether the entity is persisted
     * through JPA or inserted directly by the database.
     */
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.deliveryStatus == null) {
            this.deliveryStatus = WebhookDeliveryStatus.PENDING;
        }
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
    }

    // ─── Convenience Methods ────────────────────────────────────────────

    /**
     * Returns {@code true} if additional retry attempts are still permitted.
     */
    public boolean hasRetriesRemaining() {
        return this.retryCount < MAX_RETRY_COUNT;
    }

    // ─── Constructors ───────────────────────────────────────────────────

    public WebhookDeliveryLog() {
    }

    // ─── Getters and Setters ────────────────────────────────────────────

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getParcelId() {
        return parcelId;
    }

    public void setParcelId(UUID parcelId) {
        this.parcelId = parcelId;
    }

    public UUID getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(UUID merchantId) {
        this.merchantId = merchantId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }

    public void setHttpStatusCode(Integer httpStatusCode) {
        this.httpStatusCode = httpStatusCode;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public WebhookDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public void setDeliveryStatus(WebhookDeliveryStatus deliveryStatus) {
        this.deliveryStatus = deliveryStatus;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

