package com.nyxora.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoleAuthorizationFilter Unit Tests")
class RoleAuthorizationFilterTest {

    private RoleAuthorizationFilter roleAuthorizationFilter;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        roleAuthorizationFilter = new RoleAuthorizationFilter(objectMapper);
    }

    @Test
    @DisplayName("Non-restricted path allows any role through (e.g. customer parcel booking)")
    void nonRestrictedPath_AllowsAnyRoleThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/parcels/book")
                .header("X-User-Role", "ROLE_CUSTOMER")
                .header("X-User-Id", "customer-uuid-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Admin path with ROLE_ADMIN passes through")
    void adminPath_WithAdminRole_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/admin/pending")
                .header("X-User-Role", "ROLE_ADMIN")
                .header("X-User-Id", "admin-uuid-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Admin path with ROLE_CUSTOMER is blocked with 403 Forbidden")
    void adminPath_WithCustomerRole_Returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.put("/api/v1/auth/admin/users/123/approve")
                .header("X-User-Role", "ROLE_CUSTOMER")
                .header("X-User-Id", "customer-uuid-123")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get(), "Downstream chain should not be invoked");
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertEquals(MediaType.APPLICATION_PROBLEM_JSON, exchange.getResponse().getHeaders().getContentType());
    }

    @Test
    @DisplayName("Courier path with ROLE_MERCHANT is blocked with 403 Forbidden")
    void courierPath_WithMerchantRole_Returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.patch("/api/v1/couriers/status/available")
                .header("X-User-Role", "ROLE_MERCHANT")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Courier path with ROLE_COURIER passes through")
    void courierPath_WithCourierRole_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.patch("/api/v1/couriers/status/available")
                .header("X-User-Role", "ROLE_COURIER")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Courier path with ROLE_ADMIN passes through (Admin super-user access)")
    void courierPath_WithAdminRole_PassesThrough() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/couriers/active")
                .header("X-User-Role", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Restricted path with missing X-User-Role header returns 403 Forbidden")
    void restrictedPath_MissingRoleHeader_Returns403() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/admin/pending").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        roleAuthorizationFilter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Filter order is 0 (executes immediately after AuthenticationFilter)")
    void filterOrder_IsZero() {
        assertEquals(0, roleAuthorizationFilter.getOrder());
    }
}
