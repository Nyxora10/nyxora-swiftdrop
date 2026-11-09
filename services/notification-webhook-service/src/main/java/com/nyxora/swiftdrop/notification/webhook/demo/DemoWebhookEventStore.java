package com.nyxora.swiftdrop.notification.webhook.demo;

import com.nyxora.swiftdrop.notification.webhook.demo.dto.DemoWebhookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Thread-safe, bounded in-memory event store for demo webhook events.
 *
 * <p><strong>EPHEMERAL STORAGE NOTICE:</strong>
 * This store is temporary and strictly in-memory. It is specifically designed to facilitate
 * live examiner demonstrations and frontend webhook testing (Task 3.5). All stored events
 * are ephemeral and will disappear whenever the application restarts or is redeployed.</p>
 *
 * <p>Capacity & Memory Guardrails:
 * <ul>
 *   <li>Retains at most {@value #MAX_EVENTS_PER_MERCHANT} recent events per merchant.</li>
 *   <li>When merchant capacity is reached, new events replace the oldest events (FIFO ring buffer).</li>
 *   <li>Limits the total number of concurrently tracked merchants to {@value #MAX_TRACKED_MERCHANTS} using
 *       an LRU eviction policy to protect JVM heap memory from unbounded growth.</li>
 *   <li>Merchant event histories are completely isolated from one another.</li>
 * </ul>
 * </p>
 */
@Component
public class DemoWebhookEventStore {

    private static final Logger log = LoggerFactory.getLogger(DemoWebhookEventStore.class);

    public static final int MAX_EVENTS_PER_MERCHANT = 100;
    public static final int MAX_TRACKED_MERCHANTS = 500;

    /**
     * LRU map maintaining merchant event queues in access order with a hard bound on tracked merchants.
     */
    private final Map<UUID, Deque<DemoWebhookEvent>> merchantStore = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Deque<DemoWebhookEvent>> eldest) {
            boolean shouldRemove = size() > MAX_TRACKED_MERCHANTS;
            if (shouldRemove) {
                log.info("Evicting oldest merchant webhook demo history from memory: merchantId={}", eldest.getKey());
            }
            return shouldRemove;
        }
    };

    /**
     * Appends a verified demo webhook event to the merchant's in-memory ring buffer.
     * If the merchant's buffer reaches {@value #MAX_EVENTS_PER_MERCHANT}, the oldest event is evicted.
     *
     * @param merchantId the UUID of the merchant
     * @param event      the verified demo webhook event
     */
    public synchronized void addEvent(UUID merchantId, DemoWebhookEvent event) {
        if (merchantId == null || event == null) {
            log.warn("Cannot add demo webhook event: merchantId or event is null");
            return;
        }

        Deque<DemoWebhookEvent> queue = merchantStore.computeIfAbsent(merchantId, k -> new ArrayDeque<>());

        // Evict oldest if capacity is reached
        while (queue.size() >= MAX_EVENTS_PER_MERCHANT) {
            queue.pollLast(); // oldest event is at the tail
        }

        queue.addFirst(event); // newest event is at the head
        log.debug("Stored demo webhook event in-memory: eventId={}, merchantId={}, currentQueueSize={}",
                event.id(), merchantId, queue.size());
    }

    /**
     * Retrieves the list of recent verified events for the specified merchant, ordered newest first.
     *
     * @param merchantId the UUID of the merchant
     * @return unmodifiable list of demo events; empty list if no events exist
     */
    public synchronized List<DemoWebhookEvent> getRecentEvents(UUID merchantId) {
        if (merchantId == null) {
            return Collections.emptyList();
        }

        Deque<DemoWebhookEvent> queue = merchantStore.get(merchantId);
        if (queue == null || queue.isEmpty()) {
            return Collections.emptyList();
        }

        return List.copyOf(queue);
    }

    /**
     * Returns the count of currently stored events for the specified merchant.
     *
     * @param merchantId the UUID of the merchant
     * @return count of stored events
     */
    public synchronized int getEventCount(UUID merchantId) {
        if (merchantId == null) {
            return 0;
        }
        Deque<DemoWebhookEvent> queue = merchantStore.get(merchantId);
        return queue != null ? queue.size() : 0;
    }

    /**
     * Clears stored events for a specific merchant.
     *
     * @param merchantId the UUID of the merchant
     */
    public synchronized void clearEvents(UUID merchantId) {
        if (merchantId != null) {
            merchantStore.remove(merchantId);
            log.info("Cleared in-memory demo webhook events for merchantId={}", merchantId);
        }
    }

    /**
     * Clears all in-memory demo webhook events across all merchants.
     */
    public synchronized void clearAll() {
        merchantStore.clear();
        log.info("Cleared all in-memory demo webhook events across all merchants");
    }
}

