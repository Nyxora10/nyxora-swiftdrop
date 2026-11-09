package com.nyxora.swiftdrop.notification.webhook.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ AMQP Topology configuration for the Webhook Dead-Letter Queue (DLQ)
 * 5-tier exponential backoff retry mechanism (Task 3.4).
 *
 * <p>Architecture:
 * <ol>
 *   <li><b>Dead-Letter Exchange (swiftdrop.dlx):</b> Direct exchange receiving failed webhook messages
 *       routed by specific backoff tier (10s, 30s, 2m, 10m, 30m).</li>
 *   <li><b>5 Durable TTL Delay Queues:</b> Hold messages without active consumers. When a message's
 *       TTL expires, RabbitMQ automatically dead-letters it to the main topic exchange
 *       {@code swiftdrop.events.topic} with routing key {@code webhook.retry.dispatch}.</li>
 *   <li><b>Retry Processing Queue (swiftdrop.webhook.retry.process):</b> Durable queue bound to
 *       {@code swiftdrop.events.topic} with key {@code webhook.retry.dispatch}. Consumed by the webhook
 *       retry dispatcher when messages wake up after their delay.</li>
 * </ol>
 *
 * <p>Zero-Conflict Contract:
 * <ul>
 *   <li>Uses routing key prefix {@code webhook.retry.*}, ensuring zero overlap with Member 7's
 *       {@code notification.parcel.events} consumer (which binds to {@code parcel.#}).</li>
 *   <li>Keeps retry queues entirely independent from parcel notification listeners.</li>
 * </ul>
 */
@Configuration
public class WebhookRabbitConfig {

    public static final String MAIN_TOPIC_EXCHANGE = "swiftdrop.events.topic";
    public static final String DEAD_LETTER_EXCHANGE = "swiftdrop.dlx";

    public static final String RETRY_PROCESS_QUEUE = "swiftdrop.webhook.retry.process";
    public static final String RETRY_PROCESS_ROUTING_KEY = "webhook.retry.dispatch";

    public static final String RETRY_PARKING_LOT_QUEUE = "swiftdrop.webhook.retry.parking-lot";
    public static final String RETRY_PARKING_LOT_ROUTING_KEY = "webhook.retry.parking-lot";

    public static final String RETRY_QUEUE_10S = "swiftdrop.webhook.retry.10s";
    public static final String RETRY_QUEUE_30S = "swiftdrop.webhook.retry.30s";
    public static final String RETRY_QUEUE_2M = "swiftdrop.webhook.retry.2m";
    public static final String RETRY_QUEUE_10M = "swiftdrop.webhook.retry.10m";
    public static final String RETRY_QUEUE_30M = "swiftdrop.webhook.retry.30m";

    public static final String ROUTING_KEY_10S = "webhook.retry.10s";
    public static final String ROUTING_KEY_30S = "webhook.retry.30s";
    public static final String ROUTING_KEY_2M = "webhook.retry.2m";
    public static final String ROUTING_KEY_10M = "webhook.retry.10m";
    public static final String ROUTING_KEY_30M = "webhook.retry.30m";

    @Value("${app.rabbitmq.topic-exchange:" + MAIN_TOPIC_EXCHANGE + "}")
    private String topicExchangeName;

    @Value("${app.rabbitmq.dlx-exchange:" + DEAD_LETTER_EXCHANGE + "}")
    private String dlxExchangeName;

    // ─── Exchanges ──────────────────────────────────────────────────────

    @Bean
    public TopicExchange webhookEventsTopicExchange() {
        return ExchangeBuilder.topicExchange(topicExchangeName)
                .durable(true)
                .build();
    }

    @Bean
    public DirectExchange webhookDeadLetterExchange() {
        return ExchangeBuilder.directExchange(dlxExchangeName)
                .durable(true)
                .build();
    }

    // ─── Retry Processing Queue (Woken Up After TTL) ────────────────────

    @Bean
    public Queue webhookRetryProcessQueue() {
        return QueueBuilder.durable(RETRY_PROCESS_QUEUE)
                .deadLetterExchange(dlxExchangeName)
                .deadLetterRoutingKey(RETRY_PARKING_LOT_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding webhookRetryProcessBinding(
            Queue webhookRetryProcessQueue,
            TopicExchange webhookEventsTopicExchange) {
        return BindingBuilder.bind(webhookRetryProcessQueue)
                .to(webhookEventsTopicExchange)
                .with(RETRY_PROCESS_ROUTING_KEY);
    }

    // ─── Quarantine / Parking-Lot Queue for Poison or Failed Messages ─────

    @Bean
    public Queue webhookRetryParkingLotQueue() {
        return QueueBuilder.durable(RETRY_PARKING_LOT_QUEUE)
                .build();
    }

    @Bean
    public Binding webhookRetryParkingLotBinding(
            Queue webhookRetryParkingLotQueue,
            DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(webhookRetryParkingLotQueue)
                .to(webhookDeadLetterExchange)
                .with(RETRY_PARKING_LOT_ROUTING_KEY);
    }

    // ─── 5-Tier TTL Delay Queues (Dead-Letter back to RETRY_PROCESS_QUEUE) ─

    @Bean
    public Queue retryQueue10s() {
        return QueueBuilder.durable(RETRY_QUEUE_10S)
                .ttl(10_000) // 10 seconds
                .deadLetterExchange(topicExchangeName)
                .deadLetterRoutingKey(RETRY_PROCESS_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding10s(Queue retryQueue10s, DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(retryQueue10s)
                .to(webhookDeadLetterExchange)
                .with(ROUTING_KEY_10S);
    }

    @Bean
    public Queue retryQueue30s() {
        return QueueBuilder.durable(RETRY_QUEUE_30S)
                .ttl(30_000) // 30 seconds
                .deadLetterExchange(topicExchangeName)
                .deadLetterRoutingKey(RETRY_PROCESS_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding30s(Queue retryQueue30s, DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(retryQueue30s)
                .to(webhookDeadLetterExchange)
                .with(ROUTING_KEY_30S);
    }

    @Bean
    public Queue retryQueue2m() {
        return QueueBuilder.durable(RETRY_QUEUE_2M)
                .ttl(120_000) // 2 minutes
                .deadLetterExchange(topicExchangeName)
                .deadLetterRoutingKey(RETRY_PROCESS_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding2m(Queue retryQueue2m, DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(retryQueue2m)
                .to(webhookDeadLetterExchange)
                .with(ROUTING_KEY_2M);
    }

    @Bean
    public Queue retryQueue10m() {
        return QueueBuilder.durable(RETRY_QUEUE_10M)
                .ttl(600_000) // 10 minutes
                .deadLetterExchange(topicExchangeName)
                .deadLetterRoutingKey(RETRY_PROCESS_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding10m(Queue retryQueue10m, DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(retryQueue10m)
                .to(webhookDeadLetterExchange)
                .with(ROUTING_KEY_10M);
    }

    @Bean
    public Queue retryQueue30m() {
        return QueueBuilder.durable(RETRY_QUEUE_30M)
                .ttl(1_800_000) // 30 minutes
                .deadLetterExchange(topicExchangeName)
                .deadLetterRoutingKey(RETRY_PROCESS_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retryBinding30m(Queue retryQueue30m, DirectExchange webhookDeadLetterExchange) {
        return BindingBuilder.bind(retryQueue30m)
                .to(webhookDeadLetterExchange)
                .with(ROUTING_KEY_30M);
    }
}

