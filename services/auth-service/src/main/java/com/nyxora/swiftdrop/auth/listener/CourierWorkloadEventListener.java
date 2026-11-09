package com.nyxora.swiftdrop.auth.listener;

import com.nyxora.swiftdrop.auth.config.RabbitMQConfig;
import com.nyxora.swiftdrop.auth.dto.event.ParcelWorkloadEvent;
import com.nyxora.swiftdrop.auth.entity.ProcessedWorkloadEvent;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.ProcessedWorkloadEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * RabbitMQ event consumer for cross-service courier workload synchronization (Member 5: Task 2.2).
 * Listens for parcel lifecycle events (assigned, delivered, failed) and atomically adjusts
 * the courier's active_delivery_count in auth_db.couriers without cross-database calls.
 *
 * <p>Guarantees:
 * <ul>
 *   <li>Idempotent execution: duplicate message deliveries are detected and ignored via {@link ProcessedWorkloadEvent}.</li>
 *   <li>Database-level uniqueness constraint handling for concurrent duplicate delivery races.</li>
 *   <li>Atomic state updates within a single database transaction.</li>
 *   <li>Zero count clamping on decrements.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourierWorkloadEventListener {

    private final CourierRepository courierRepository;
    private final ProcessedWorkloadEventRepository processedWorkloadEventRepository;

    @RabbitListener(queues = RabbitMQConfig.WORKLOAD_QUEUE_NAME)
    @Transactional
    public void handleParcelWorkloadEvent(ParcelWorkloadEvent event,
                                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if (event == null || event.courierId() == null || event.parcelId() == null) {
            log.warn("[CourierWorkloadEventListener] Received event with null fields, skipping. routingKey={}", routingKey);
            return;
        }

        UUID courierId = event.courierId();
        UUID parcelId = event.parcelId();
        String trackingNumber = event.trackingNumber();
        String eventKey = parcelId.toString() + ":" + routingKey;

        // 1. Idempotency check: skip if this exact event transition was already applied for this parcel
        if (processedWorkloadEventRepository.existsById(eventKey)) {
            log.info("[CourierWorkloadEventListener] Duplicate event detected for key={}, skipping to maintain idempotency", eventKey);
            return;
        }

        log.info("[CourierWorkloadEventListener] Processing workload event: routingKey={}, courierId={}, parcelId={}, tracking={}",
                routingKey, courierId, parcelId, trackingNumber);

        // 2. Atomically record processed event for deduplication (guaranteed unique by database PK constraint)
        try {
            processedWorkloadEventRepository.saveAndFlush(new ProcessedWorkloadEvent(eventKey, courierId, Instant.now()));
        } catch (DataIntegrityViolationException ex) {
            log.info("[CourierWorkloadEventListener] Concurrent duplicate event blocked by database uniqueness constraint: key={}", eventKey);
            return;
        }

        // 3. Atomically update courier workload count in auth_db
        if (RabbitMQConfig.ROUTING_KEY_PARCEL_ASSIGNED.equals(routingKey)) {
            int updated = courierRepository.incrementActiveDeliveryCount(courierId);
            if (updated == 0) {
                log.warn("[CourierWorkloadEventListener] Courier not found for increment: courierId={}", courierId);
            } else {
                log.info("[CourierWorkloadEventListener] Successfully incremented active deliveries for courierId={}", courierId);
            }
        } else if (RabbitMQConfig.ROUTING_KEY_PARCEL_DELIVERED.equals(routingKey)
                || RabbitMQConfig.ROUTING_KEY_PARCEL_FAILED.equals(routingKey)) {
            int updated = courierRepository.decrementActiveDeliveryCount(courierId);
            if (updated == 0) {
                log.warn("[CourierWorkloadEventListener] Courier not found for decrement: courierId={}", courierId);
            } else {
                log.info("[CourierWorkloadEventListener] Successfully decremented active deliveries for courierId={} on event={}",
                        courierId, routingKey);
            }
        } else {
            log.warn("[CourierWorkloadEventListener] Ignored unhandled routing key: {}", routingKey);
        }
    }
}
