package com.nyxora.swiftdrop.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SwiftDrop Notification & Webhook Microservice.
 *
 * Core Responsibilities:
 * - Asynchronous AMQP parcel event consumption from RabbitMQ (swiftdrop.events.topic)
 * - Customer-facing notification dispatching (HTML email via Thymeleaf, Mock SMS)
 * - Merchant webhook delivery (Member 8 boundary)
 */
@SpringBootApplication
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

