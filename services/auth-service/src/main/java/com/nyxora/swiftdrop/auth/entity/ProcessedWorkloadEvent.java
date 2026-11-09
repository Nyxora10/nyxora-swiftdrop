package com.nyxora.swiftdrop.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit log of processed parcel workload events to ensure strict idempotent processing (Member 5: Task 2.2).
 * Prevents duplicate RabbitMQ message redeliveries from double-incrementing or double-decrementing active_delivery_count.
 */
@Entity
@Table(name = "processed_workload_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedWorkloadEvent {

    @Id
    @Column(name = "event_key", length = 150)
    private String eventKey;

    @Column(name = "courier_id", nullable = false)
    private UUID courierId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
