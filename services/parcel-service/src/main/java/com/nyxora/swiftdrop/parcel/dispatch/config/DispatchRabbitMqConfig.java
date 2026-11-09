package com.nyxora.swiftdrop.parcel.dispatch.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RabbitMQ AMQP Topology Configuration for Member 5's Dispatch Engine.
 * Declares the system-wide topic exchange swiftdrop.events.topic and routing keys.
 */
@Configuration
@EnableScheduling
public class DispatchRabbitMqConfig {

    public static final String EXCHANGE_NAME = "swiftdrop.events.topic";
    public static final String ROUTING_KEY_PARCEL_ASSIGNED = "parcel.assigned";

    @Bean
    public TopicExchange swiftdropEventsTopicExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }
}
