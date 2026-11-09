package com.nyxora.swiftdrop.auth.listener;

import com.nyxora.swiftdrop.auth.config.RabbitMQConfig;
import com.nyxora.swiftdrop.auth.dto.event.ParcelWorkloadEvent;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RabbitMQ event consumer for cross-service courier workload synchronization (Member 5: Task 2.2).
 * Listens for parcel lifecycle events (assigned, delivered, failed) and atomically adjusts
 * the courier's active_delivery_count in auth_db.couriers without cross-database calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CourierWorkloadEventListener {

    private final CourierRepository courierRepository;

    @RabbitListener(queues = RabbitMQConfig.WORKLOAD_QUEUE_NAME)
    public void handleParcelWorkloadEvent(ParcelWorkloadEvent event,
                                          @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        if (event == null || event.courierId() == null) {
            log.warn("[CourierWorkloadEventListener] Received event with null courierId, skipping. routingKey={}", routingKey);
            return;
        }

        UUID courierId = event.courierId();
        String trackingNumber = event.trackingNumber();

        log.info("[CourierWorkloadEventListener] Processing workload event: routingKey={}, courierId={}, tracking={}",
                routingKey, courierId, trackingNumber);

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
