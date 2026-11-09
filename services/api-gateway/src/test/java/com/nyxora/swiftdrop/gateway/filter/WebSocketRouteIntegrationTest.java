package com.nyxora.swiftdrop.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("WebSocket Route Integration Tests")
class WebSocketRouteIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Request to /ws-swiftdrop/info bypasses perimeter JWT security without 401 Unauthorized")
    void wsInfoEndpoint_BypassesPerimeterSecurity_NotUnauthorized() {
        webTestClient.get()
                .uri("/ws-swiftdrop/info")
                .exchange()
                .expectStatus()
                .value(status -> {
                    // Verifies that perimeter authentication filter did not reject with 401 or 403
                    assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status,
                            "Public WebSocket handshake endpoint must not return 401 Unauthorized");
                    assertNotEquals(HttpStatus.FORBIDDEN.value(), status,
                            "Public WebSocket handshake endpoint must not return 403 Forbidden");
                });
    }

    @Test
    @DisplayName("Request to /ws-swiftdrop/websocket bypasses perimeter JWT security without 401 Unauthorized")
    void wsWebsocketEndpoint_BypassesPerimeterSecurity_NotUnauthorized() {
        webTestClient.get()
                .uri("/ws-swiftdrop/websocket")
                .exchange()
                .expectStatus()
                .value(status -> {
                    assertNotEquals(HttpStatus.UNAUTHORIZED.value(), status,
                            "Public WebSocket endpoint must not return 401 Unauthorized");
                    assertNotEquals(HttpStatus.FORBIDDEN.value(), status,
                            "Public WebSocket endpoint must not return 403 Forbidden");
                });
    }

    @Test
    @DisplayName("Secured endpoint without token returns 401 Unauthorized for comparison")
    void securedEndpoint_WithoutToken_Returns401() {
        webTestClient.post()
                .uri("/api/v1/parcels/book")
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }
}

