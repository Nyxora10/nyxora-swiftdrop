package com.nyxora.swiftdrop.notification.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class RabbitMqConfigTest {

    private RabbitMqConfig rabbitMqConfig;

    @BeforeEach
    void setUp() {
        rabbitMqConfig = new RabbitMqConfig();
        ReflectionTestUtils.setField(rabbitMqConfig, "exchangeName", "swiftdrop.events.topic");
        ReflectionTestUtils.setField(rabbitMqConfig, "queueName", "notification.parcel.events");
        ReflectionTestUtils.setField(rabbitMqConfig, "routingKey", "parcel.#");
    }

    @Test
    @DisplayName("Should create durable TopicExchange with configured name")
    void shouldCreateTopicExchange() {
        TopicExchange exchange = rabbitMqConfig.parcelTopicExchange();

        assertNotNull(exchange);
        assertEquals("swiftdrop.events.topic", exchange.getName());
        assertTrue(exchange.isDurable());
        assertEquals("topic", exchange.getType());
    }

    @Test
    @DisplayName("Should create durable Queue with configured name")
    void shouldCreateNotificationQueue() {
        Queue queue = rabbitMqConfig.notificationParcelQueue();

        assertNotNull(queue);
        assertEquals("notification.parcel.events", queue.getName());
        assertTrue(queue.isDurable());
    }

    @Test
    @DisplayName("Should bind queue to topic exchange with routing key pattern parcel.#")
    void shouldCreateQueueBinding() {
        TopicExchange exchange = rabbitMqConfig.parcelTopicExchange();
        Queue queue = rabbitMqConfig.notificationParcelQueue();

        Binding binding = rabbitMqConfig.parcelQueueBinding(queue, exchange);

        assertNotNull(binding);
        assertEquals("notification.parcel.events", binding.getDestination());
        assertEquals("swiftdrop.events.topic", binding.getExchange());
        assertEquals("parcel.#", binding.getRoutingKey());
        assertEquals(Binding.DestinationType.QUEUE, binding.getDestinationType());
    }

    @Test
    @DisplayName("Should create Jackson2JsonMessageConverter bean")
    void shouldCreateJackson2JsonMessageConverter() {
        MessageConverter converter = rabbitMqConfig.jackson2JsonMessageConverter();

        assertNotNull(converter);
        assertInstanceOf(Jackson2JsonMessageConverter.class, converter);
    }

    @Test
    @DisplayName("Should configure RabbitTemplate with Jackson2JsonMessageConverter")
    void shouldConfigureRabbitTemplate() {
        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        MessageConverter messageConverter = rabbitMqConfig.jackson2JsonMessageConverter();

        RabbitTemplate rabbitTemplate = rabbitMqConfig.rabbitTemplate(connectionFactory, messageConverter);

        assertNotNull(rabbitTemplate);
        assertEquals(messageConverter, rabbitTemplate.getMessageConverter());
    }

    @Test
    @DisplayName("Should return configured property values via getters")
    void shouldReturnPropertyValues() {
        assertEquals("swiftdrop.events.topic", rabbitMqConfig.getExchangeName());
        assertEquals("notification.parcel.events", rabbitMqConfig.getQueueName());
        assertEquals("parcel.#", rabbitMqConfig.getRoutingKey());
    }
}

