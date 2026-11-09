package com.nyxora.swiftdrop.parcel.booking.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ AMQP Topology Configuration for SwiftDrop Parcel Service.
 * Defines domain topic exchanges and JSON message conversion for asynchronous
 * inter-service communication across auth, parcel, and notification services.
 */
@Configuration
public class RabbitMqConfig {

    public static final String PARCEL_EVENTS_EXCHANGE = "swiftdrop.parcel.events";
    public static final String ROUTING_KEY_PARCEL_CREATED = "parcel.created";

    /** Shared enterprise topic exchange consumed by auth-service and notification-service */
    public static final String SWIFTDROP_EVENTS_TOPIC = "swiftdrop.events.topic";
    public static final String ROUTING_KEY_STATUS_UPDATED = "parcel.status.updated";
    public static final String ROUTING_KEY_STATUS_DELIVERED = "parcel.status.delivered";
    public static final String ROUTING_KEY_STATUS_FAILED = "parcel.status.failed";

    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(PARCEL_EVENTS_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange swiftdropTopicExchange() {
        return new TopicExchange(SWIFTDROP_EVENTS_TOPIC, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}