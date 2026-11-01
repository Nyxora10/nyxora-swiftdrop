package com.nyxora.swiftdrop.gateway.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RouteValidator Unit Tests")
class RouteValidatorTest {

    private RouteValidator routeValidator;

    @BeforeEach
    void setUp() {
        routeValidator = new RouteValidator();
    }

    @Test
    @DisplayName("Public auth endpoints are recognized as not secured")
    void openEndpoints_ReturnNotSecured() {
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/auth/login").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/register/customer").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/register/merchant").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/register/courier").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/refresh").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/google").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/apple").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/facebook").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/parcels/track/SD-20261101-0001").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/actuator/health").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/actuator/info").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/v3/api-docs").build()));
        assertFalse(routeValidator.isSecured.test(MockServerHttpRequest.get("/swagger-ui/index.html").build()));
    }

    @Test
    @DisplayName("Protected endpoints require authentication")
    void protectedEndpoints_ReturnSecured() {
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/auth/me").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/auth/logout").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.post("/api/v1/parcels/book").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/parcels").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/couriers/available").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/admin/users").build()));
        assertTrue(routeValidator.isSecured.test(MockServerHttpRequest.get("/api/v1/notifications").build()));
    }
}
