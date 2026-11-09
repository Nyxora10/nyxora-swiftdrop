package com.nyxora.swiftdrop.notification.webhook.integration;

import com.nyxora.swiftdrop.notification.webhook.config.WebhookRabbitConfig;
import com.nyxora.swiftdrop.notification.webhook.dto.WebhookDispatchResult;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryLog;
import com.nyxora.swiftdrop.notification.webhook.entity.WebhookDeliveryStatus;
import com.nyxora.swiftdrop.notification.webhook.repository.WebhookDeliveryLogRepository;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPolicy;
import com.nyxora.swiftdrop.notification.webhook.retry.WebhookRetryPublisher;
import com.nyxora.swiftdrop.notification.webhook.retry.dto.WebhookRetryMessage;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookDispatcherService;
import com.nyxora.swiftdrop.notification.webhook.service.WebhookLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * End-to-End RabbitMQ Integration Tests using Testcontainers (Task 8.3).
 *
 * <p>Validates the live AMQP topology, dead-letter routing, exponential TTL delay queues,
 * direct manual-retry routing, and poison-message quarantine against an ephemeral RabbitMQ container.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Task 8.3: RabbitMQ Integration Testing with Testcontainers")
class WebhookRabbitMqIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WebhookRabbitMqIntegrationTest.class);

    @Container
    static final RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.11-management-alpine");

    @DynamicPropertySource
    static void configureRabbit(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
        // Explicitly enable listener auto-startup so consumer processes queue messages
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "true");
    }

    @Autowired
    private ConnectionFactory connectionFactory;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private WebhookDeliveryLogRepository logRepository;

    @Autowired
    private WebhookRetryPublisher retryPublisher;

    @Autowired
    private WebhookLogService logService;

    @MockBean
    private WebhookDispatcherService dispatcherService;

    @MockBean
    private JavaMailSender mailSender;

    private RabbitAdmin rabbitAdmin;

    @BeforeEach
    void setUp() {
        rabbitAdmin = new RabbitAdmin(connectionFactory);

        // Purge queues to guarantee test isolation
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_PROCESS_QUEUE, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_PARKING_LOT_QUEUE, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_QUEUE_10S, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_QUEUE_30S, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_QUEUE_2M, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_QUEUE_10M, false);
        rabbitAdmin.purgeQueue(WebhookRabbitConfig.RETRY_QUEUE_30M, false);

        logRepository.deleteAll();
    }

    // ─── Scenario 1: Topology Verification ───────────────────────────────

    @Test
    @DisplayName("1. Real Topology: Broker successfully declares all exchanges, queues, and DLX bindings")
    void shouldVerifyRealRabbitMqTopologyProvisioning() {
        // Assert all 7 webhook queues exist on live broker
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_PROCESS_QUEUE),
                "Processing queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_PARKING_LOT_QUEUE),
                "Parking lot queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10S),
                "10s TTL queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_30S),
                "30s TTL queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_2M),
                "2m TTL queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10M),
                "10m TTL queue should exist");
        assertNotNull(rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_30M),
                "30m TTL queue should exist");
    }

    // ─── Scenario 2: Automatic Retry Routing ─────────────────────────────

    @Test
    @DisplayName("2. Automatic Retry: Initial failure schedules message to 10s TTL delay queue on swiftdrop.dlx")
    void shouldRouteAutomaticRetryTo10sDelayQueue() {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.status.updated");
        deliveryLog.setPayload("{\"trackingNumber\":\"SWIFT-AUTO-01\"}");
        deliveryLog.setRetryCount(0);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        deliveryLog = logRepository.save(deliveryLog);

        boolean scheduled = retryPublisher.scheduleRetry(deliveryLog);
        assertTrue(scheduled, "Retry publisher should accept retry attempt 1");

        // Verify message landed in swiftdrop.webhook.retry.10s
        await().atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    QueueInformation info = rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10S);
                    assertNotNull(info);
                    assertEquals(1, info.getMessageCount(), "10s delay queue should hold 1 message");
                });

        // Verify entity updated in DB
        WebhookDeliveryLog reloaded = logRepository.findById(deliveryLog.getId()).orElseThrow();
        assertEquals(1, reloaded.getRetryCount());
        assertEquals(WebhookDeliveryStatus.RETRYING, reloaded.getDeliveryStatus());
        assertNotNull(reloaded.getNextRetryAt());
    }

    // ─── Scenario 3: Manual Retry Bypass ─────────────────────────────────

    @Test
    @DisplayName("3. Manual Retry: Routes directly to swiftdrop.webhook.retry.process bypassing delay queues")
    void shouldRouteManualRetryDirectlyToProcessQueue() {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.status.updated");
        deliveryLog.setPayload("{\"trackingNumber\":\"SWIFT-MANUAL-01\"}");
        deliveryLog.setRetryCount(5);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.DEAD_LETTER);
        final UUID logId = logRepository.save(deliveryLog).getId();

        // Configure dispatcher to simulate successful HTTP 200 response
        when(dispatcherService.dispatchRetryWebhook(any(WebhookDeliveryLog.class)))
                .thenAnswer(inv -> {
                    WebhookDeliveryLog logArg = inv.getArgument(0);
                    logArg.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
                    logArg.setHttpStatusCode(200);
                    logArg.setNextRetryAt(null);
                    return WebhookDispatchResult.successful(200, logRepository.save(logArg));
                });

        // Trigger manual retry via service (DEAD_LETTER -> RETRYING -> Publish -> Consumer -> DELIVERED)
        logService.retryFailedDelivery(logId, merchantId);

        // Await consumer execution and status update to DELIVERED
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    WebhookDeliveryLog reloaded = logRepository.findById(logId).orElseThrow();
                    assertEquals(WebhookDeliveryStatus.DELIVERED, reloaded.getDeliveryStatus());
                    assertEquals(200, reloaded.getHttpStatusCode());
                });

        // Verify delay queues were completely bypassed (all have 0 messages)
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10S).getMessageCount());
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_30S).getMessageCount());
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_2M).getMessageCount());
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10M).getMessageCount());
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_30M).getMessageCount());
    }

    // ─── Scenario 4: Real 10s TTL Expiration & DLX Routing ───────────────

    @Test
    @DisplayName("4. Real TTL: Message expires after 10s, dead-letters to process queue, and consumer processes it")
    void shouldExpireTtlAndDeadLetterToProcessQueue() {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.status.updated");
        deliveryLog.setPayload("{\"trackingNumber\":\"SWIFT-TTL-01\"}");
        deliveryLog.setRetryCount(1);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        final UUID logId = logRepository.save(deliveryLog).getId();

        // Mock dispatcher to succeed when woken up
        when(dispatcherService.dispatchRetryWebhook(any(WebhookDeliveryLog.class)))
                .thenAnswer(inv -> {
                    WebhookDeliveryLog logArg = inv.getArgument(0);
                    logArg.setDeliveryStatus(WebhookDeliveryStatus.DELIVERED);
                    logArg.setHttpStatusCode(200);
                    logArg.setNextRetryAt(null);
                    return WebhookDispatchResult.successful(200, logRepository.save(logArg));
                });

        // Publish directly to 10s TTL queue via swiftdrop.dlx with routing key webhook.retry.10s
        WebhookRetryMessage retryMessage = new WebhookRetryMessage(
                logId,
                merchantId,
                parcelId,
                "parcel.status.updated",
                deliveryLog.getPayload(),
                1
        );

        rabbitTemplate.convertAndSend(
                WebhookRabbitConfig.DEAD_LETTER_EXCHANGE,
                WebhookRabbitConfig.ROUTING_KEY_10S,
                retryMessage,
                m -> {
                    m.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    m.getMessageProperties().setContentType(MediaType.APPLICATION_JSON_VALUE);
                    m.getMessageProperties().setHeader("x-swiftdrop-retry-count", 1);
                    return m;
                }
        );

        // 1. Immediately verify message entered 10s delay queue
        await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
            QueueInformation qInfo = rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10S);
            assertNotNull(qInfo);
            assertTrue(qInfo.getMessageCount() >= 1, "Message must be held in 10s delay queue");
        });

        log.info("Holding for real 10-second RabbitMQ TTL expiration...");

        // 2. Bounded wait: After ~10 seconds TTL expires, RabbitMQ routes to process queue -> consumer marks DELIVERED
        await().atMost(Duration.ofSeconds(20))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    WebhookDeliveryLog reloaded = logRepository.findById(logId).orElseThrow();
                    assertEquals(WebhookDeliveryStatus.DELIVERED, reloaded.getDeliveryStatus(),
                            "Delivery should transition to DELIVERED after TTL expiration");
                });

        verify(dispatcherService).dispatchRetryWebhook(any(WebhookDeliveryLog.class));
    }

    // ─── Scenario 5: Retry Limit Exhaustion ───────────────────────────────

    @Test
    @DisplayName("5. Exhaustion: Final attempt failure (retryCount=5) transitions entity to DEAD_LETTER without requeueing")
    void shouldTransitionToDeadLetterWhenRetriesExhausted() {
        UUID merchantId = UUID.randomUUID();
        UUID parcelId = UUID.randomUUID();

        WebhookDeliveryLog deliveryLog = new WebhookDeliveryLog();
        deliveryLog.setMerchantId(merchantId);
        deliveryLog.setParcelId(parcelId);
        deliveryLog.setEventType("parcel.status.updated");
        deliveryLog.setPayload("{\"trackingNumber\":\"SWIFT-EXHAUST-01\"}");
        deliveryLog.setRetryCount(5);
        deliveryLog.setDeliveryStatus(WebhookDeliveryStatus.RETRYING);
        final UUID logId = logRepository.save(deliveryLog).getId();

        // Simulate 503 Server Error
        when(dispatcherService.dispatchRetryWebhook(any(WebhookDeliveryLog.class)))
                .thenReturn(WebhookDispatchResult.retryableFailure(
                        503, "HTTP_SERVER_ERROR", "Service Unavailable", deliveryLog));

        // Publish attempt 5 directly to process queue
        WebhookRetryMessage finalAttemptMsg = new WebhookRetryMessage(
                logId, merchantId, parcelId, "parcel.status.updated", deliveryLog.getPayload(), 5);

        rabbitTemplate.convertAndSend(
                WebhookRabbitConfig.MAIN_TOPIC_EXCHANGE,
                WebhookRabbitConfig.RETRY_PROCESS_ROUTING_KEY,
                finalAttemptMsg
        );

        // Await consumer processing: should exhaust and mark DEAD_LETTER
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    WebhookDeliveryLog reloaded = logRepository.findById(logId).orElseThrow();
                    assertEquals(WebhookDeliveryStatus.DEAD_LETTER, reloaded.getDeliveryStatus());
                    assertNull(reloaded.getNextRetryAt());
                });

        // Verify no message was scheduled to any delay queue
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_10S).getMessageCount());
        assertEquals(0, rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_QUEUE_30S).getMessageCount());
    }

    // ─── Scenario 6: Poison Message Quarantine ───────────────────────────

    @Test
    @DisplayName("6. Poison Message: Unparseable payload to process queue is rejected and dead-lettered into parking-lot")
    void shouldRoutePoisonMessageToParkingLotQueue() {
        // Send a poison message (non-deserializable text) directly to process queue
        rabbitTemplate.convertAndSend(
                WebhookRabbitConfig.MAIN_TOPIC_EXCHANGE,
                WebhookRabbitConfig.RETRY_PROCESS_ROUTING_KEY,
                "POISON_NON_JSON_CORRUPT_PAYLOAD",
                m -> {
                    m.getMessageProperties().setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    return m;
                }
        );

        // Await dead-lettering into swiftdrop.webhook.retry.parking-lot
        await().atMost(Duration.ofSeconds(6))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    QueueInformation parkingLotInfo = rabbitAdmin.getQueueInfo(WebhookRabbitConfig.RETRY_PARKING_LOT_QUEUE);
                    assertNotNull(parkingLotInfo);
                    assertTrue(parkingLotInfo.getMessageCount() >= 1,
                            "Parking-lot queue must receive the dead-lettered poison message");
                });
    }
}
