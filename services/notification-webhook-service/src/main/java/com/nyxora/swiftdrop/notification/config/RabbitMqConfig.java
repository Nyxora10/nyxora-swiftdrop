package com.nyxora.swiftdrop.notification.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ AMQP Topology and Message Converter Configuration.
 *
 * Configures:
 * - Durable Topic Exchange: swiftdrop.events.topic
 * - Durable Notification Queue: notification.parcel.events
 * - Binding with routing key: parcel.# (matches all multi-token parcel status events)
 * - Jackson2JsonMessageConverter with JavaTimeModule support for ISO-8601 timestamps
 */
@Configuration
public class RabbitMqConfig {

    @Value("${app.rabbitmq.exchange:swiftdrop.events.topic}")
    private String exchangeName;

    @Value("${app.rabbitmq.notification-queue:notification.parcel.events}")
    private String queueName;

    @Value("${app.rabbitmq.parcel-routing-key:parcel.#}")
    private String routingKey;

    @Bean
    public TopicExchange parcelTopicExchange() {
        return ExchangeBuilder.topicExchange(exchangeName)
                .durable(true)
                .build();
    }

    @Bean
    public Queue notificationParcelQueue() {
        return QueueBuilder.durable(queueName)
                .build();
    }

    @Bean
    public Binding parcelQueueBinding(Queue notificationParcelQueue, TopicExchange parcelTopicExchange) {
        return BindingBuilder.bind(notificationParcelQueue)
                .to(parcelTopicExchange)
                .with(routingKey);
    }

    @Bean
    public MessageConverter jackson2JsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    public String getExchangeName() {
        return exchangeName;
    }

    public String getQueueName() {
        return queueName;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}

