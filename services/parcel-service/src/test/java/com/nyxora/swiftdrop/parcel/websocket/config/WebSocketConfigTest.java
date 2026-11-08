package com.nyxora.swiftdrop.parcel.websocket.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private StompEndpointRegistry endpointRegistry;

    @Mock
    private StompWebSocketEndpointRegistration registration;

    @Mock
    private MessageBrokerRegistry brokerRegistry;

    private WebSocketConfig webSocketConfig;

    @BeforeEach
    void setUp() {
        webSocketConfig = new WebSocketConfig();
    }

    @Test
    @DisplayName("Should register /ws-swiftdrop endpoint with SockJS and CORS origin patterns")
    void shouldRegisterStompEndpointsWithSockJs() {
        when(endpointRegistry.addEndpoint(eq("/ws-swiftdrop"))).thenReturn(registration);
        when(registration.setAllowedOriginPatterns(any(String[].class))).thenReturn(registration);

        webSocketConfig.registerStompEndpoints(endpointRegistry);

        // Verifies endpoint was registered for both SockJS and native WebSocket fallback
        verify(endpointRegistry, times(2)).addEndpoint("/ws-swiftdrop");
        verify(registration, times(2)).setAllowedOriginPatterns("*");
        verify(registration, times(1)).withSockJS();
    }

    @Test
    @DisplayName("Should configure simple message broker with /topic and application prefix /app")
    void shouldConfigureMessageBroker() {
        webSocketConfig.configureMessageBroker(brokerRegistry);

        verify(brokerRegistry).enableSimpleBroker("/topic");
        verify(brokerRegistry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    @DisplayName("Should allow customizing endpoint and allowed origin patterns via accessors")
    void shouldSupportCustomProperties() {
        webSocketConfig.setEndpoint("/custom-ws");
        webSocketConfig.setAllowedOrigins("https://swiftdrop.lk");

        assertEquals("/custom-ws", webSocketConfig.getEndpoint());
        assertEquals("https://swiftdrop.lk", webSocketConfig.getAllowedOrigins());
    }
}

