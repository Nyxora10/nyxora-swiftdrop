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
 * Defines the main parcel domain topic exchange and sets up JSON serialization
 * for asynchronous inter-service domain event messaging.
 */
@Configuration
public class RabbitMqConfig {

    public static final String PARCEL_EVENTS_EXCHANGE = "swiftdrop.parcel.events";
    public static final String ROUTING_KEY_PARCEL_CREATED = "parcel.created";

    @Bean
    public TopicExchange parcelEventsExchange() {
        return new TopicExchange(PARCEL_EVENTS_EXCHANGE, true, false);
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

