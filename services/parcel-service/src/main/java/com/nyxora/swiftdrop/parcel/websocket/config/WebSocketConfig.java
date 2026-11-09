package com.nyxora.swiftdrop.parcel.websocket.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Spring STOMP WebSocket Message Broker Configuration.
 *
 * Exposes the real-time tracking WebSocket transport for customer clients:
 * - Public STOMP Endpoint: {@code /ws-swiftdrop} with SockJS fallback and permissive CORS patterns.
 * - Outbound Broker Destination: {@code /topic} (e.g. {@code /topic/parcels/{trackingNumber}}).
 * - Inbound Application Destination Prefix: {@code /app}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${app.websocket.endpoint:/ws-swiftdrop}")
    private String endpoint = "/ws-swiftdrop";

    @Value("${app.websocket.allowed-origins:*}")
    private String allowedOrigins = "*";

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback enabled for browser STOMP clients
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();

        // Native WebSocket transport for clients without SockJS wrapper
        registry.addEndpoint(endpoint)
                .setAllowedOriginPatterns(allowedOrigins);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory message broker prefix for topic subscriptions
        registry.enableSimpleBroker("/topic");

        // Application destination prefix for client-to-server messages
        registry.setApplicationDestinationPrefixes("/app");
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}

