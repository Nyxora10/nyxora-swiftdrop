package com.nyxora.swiftdrop.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for consuming cross-service courier workload events (Member 5: Task 2.2).
 * Binds auth.courier.workload.queue to swiftdrop.events.topic for parcel lifecycle events.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.topic";
    public static final String WORKLOAD_QUEUE_NAME = "auth.courier.workload.queue";

    public static final String ROUTING_KEY_PARCEL_ASSIGNED = "parcel.assigned";
    public static final String ROUTING_KEY_PARCEL_DELIVERED = "parcel.status.delivered";
    public static final String ROUTING_KEY_PARCEL_FAILED = "parcel.status.failed";

    @Bean
    public TopicExchange swiftdropTopicExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    @Bean
    public Queue courierWorkloadQueue() {
        return QueueBuilder.durable(WORKLOAD_QUEUE_NAME)
                .build();
    }

    @Bean
    public Binding bindingParcelAssigned(Queue courierWorkloadQueue, TopicExchange swiftdropTopicExchange) {
        return BindingBuilder.bind(courierWorkloadQueue)
                .to(swiftdropTopicExchange)
                .with(ROUTING_KEY_PARCEL_ASSIGNED);
    }

    @Bean
    public Binding bindingParcelDelivered(Queue courierWorkloadQueue, TopicExchange swiftdropTopicExchange) {
        return BindingBuilder.bind(courierWorkloadQueue)
                .to(swiftdropTopicExchange)
                .with(ROUTING_KEY_PARCEL_DELIVERED);
    }

    @Bean
    public Binding bindingParcelFailed(Queue courierWorkloadQueue, TopicExchange swiftdropTopicExchange) {
        return BindingBuilder.bind(courierWorkloadQueue)
                .to(swiftdropTopicExchange)
                .with(ROUTING_KEY_PARCEL_FAILED);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}

