package com.nyxora.swiftdrop.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.gateway.config.JwtProperties;
import com.nyxora.swiftdrop.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuthenticationFilter Unit Tests")
class AuthenticationFilterTest {

    private static final String TEST_SECRET = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private AuthenticationFilter authenticationFilter;
    private SecretKey secretKey;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(TEST_SECRET);
        JwtUtil jwtUtil = new JwtUtil(properties);
        RouteValidator routeValidator = new RouteValidator();
        ObjectMapper objectMapper = new ObjectMapper();

        authenticationFilter = new AuthenticationFilter(routeValidator, jwtUtil, objectMapper);
        secretKey = Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(TEST_SECRET));
    }

    private String generateToken(String sub, String role, String email, long expMs) {
        return generateToken(sub, role, email, null, null, expMs);
    }

    private String generateToken(String sub, String role, String email, String courierId, String merchantId, long expMs) {
        var builder = Jwts.builder()
                .subject(sub)
                .claim("role", role)
                .claim("email", email);

        if (courierId != null) {
            builder.claim("courierId", courierId);
        }
        if (merchantId != null) {
            builder.claim("merchantId", merchantId);
        }

        return builder
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expMs))
                .signWith(secretKey)
                .compact();
    }

    @Test
    @DisplayName("Public endpoint bypasses auth and strips caller-forged identity headers")
    void publicEndpoint_BypassesAuthAndStripsSpoofedHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-User-Id", "spoofed-uuid")
                .header("X-User-Role", "ROLE_ADMIN")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            assertNull(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Id"));
            assertNull(mutatedExchange.getRequest().getHeaders().getFirst("X-User-Role"));
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured endpoint without Authorization header returns 401 Unauthorized")
    void securedEndpoint_MissingAuthHeader_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/parcels/book").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> Mono.empty();

        authenticationFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured endpoint with non-Bearer Authorization header returns 401")
    void securedEndpoint_InvalidScheme_Returns401() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/parcels/book")
                .header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> Mono.empty();

        authenticationFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured endpoint with expired token returns 401")
    void securedEndpoint_ExpiredToken_Returns401() {
        String expiredToken = generateToken(UUID.randomUUID().toString(), "ROLE_CUSTOMER", "user@test.lk", -5000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        GatewayFilterChain chain = mutatedExchange -> Mono.empty();

        authenticationFilter.filter(exchange, chain).block();

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Secured endpoint with valid token injects verified identity headers downstream")
    void securedEndpoint_ValidToken_InjectsRelayHeaders() {
        UUID userId = UUID.randomUUID();
        String validToken = generateToken(userId.toString(), "ROLE_CUSTOMER", "kamal@swiftdrop.lk", 900000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/parcels/book")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + validToken)
                .header("X-User-Role", "ROLE_ADMIN") // malicious spoof attempt
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            HttpHeaders headers = mutatedExchange.getRequest().getHeaders();
            assertEquals(userId.toString(), headers.getFirst("X-User-Id"));
            assertEquals("ROLE_CUSTOMER", headers.getFirst("X-User-Role")); // spoof overwritten
            assertEquals("kamal@swiftdrop.lk", headers.getFirst("X-User-Email"));
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Internal auth endpoint is blocked at perimeter with 403 Forbidden")
    void internalAuthEndpoint_BlockedWithForbidden() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/internal/users/123").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Internal parcels endpoint is blocked at perimeter with 403 Forbidden even with valid token")
    void internalParcelsEndpoint_BlockedWithForbidden() {
        String token = generateToken(UUID.randomUUID().toString(), "ROLE_ADMIN", "admin@swiftdrop.lk", 900000);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/parcels/internal/sync")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Courier token injects verified X-Courier-Id header and strips spoofed courier header")
    void courierToken_InjectsXCourierIdHeader() {
        UUID userId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();
        String courierToken = generateToken(userId.toString(), "ROLE_COURIER", "courier@swiftdrop.lk",
                courierId.toString(), null, 900000);

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/parcels/courier/deliveries")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + courierToken)
                .header("X-Courier-Id", "spoofed-courier-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            HttpHeaders headers = mutatedExchange.getRequest().getHeaders();
            assertEquals(userId.toString(), headers.getFirst("X-User-Id"));
            assertEquals("ROLE_COURIER", headers.getFirst("X-User-Role"));
            assertEquals(courierId.toString(), headers.getFirst("X-Courier-Id"));
            assertEquals("", headers.getFirst("X-Merchant-Id"));
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Merchant token injects verified X-Merchant-Id header and strips spoofed merchant header")
    void merchantToken_InjectsXMerchantIdHeader() {
        UUID userId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        String merchantToken = generateToken(userId.toString(), "ROLE_MERCHANT", "merchant@swiftdrop.lk",
                null, merchantId.toString(), 900000);

        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/parcels/merchant/bulk-upload")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + merchantToken)
                .header("X-Merchant-Id", "spoofed-merchant-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            HttpHeaders headers = mutatedExchange.getRequest().getHeaders();
            assertEquals(userId.toString(), headers.getFirst("X-User-Id"));
            assertEquals("ROLE_MERCHANT", headers.getFirst("X-User-Role"));
            assertEquals("", headers.getFirst("X-Courier-Id"));
            assertEquals(merchantId.toString(), headers.getFirst("X-Merchant-Id"));
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("Public endpoint strips spoofed X-Courier-Id and X-Merchant-Id headers")
    void publicEndpoint_StripsSpoofedCourierAndMerchantHeaders() {
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/v1/auth/login")
                .header("X-Courier-Id", "spoofed-courier-uuid")
                .header("X-Merchant-Id", "spoofed-merchant-uuid")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = mutatedExchange -> {
            chainInvoked.set(true);
            assertNull(mutatedExchange.getRequest().getHeaders().getFirst("X-Courier-Id"));
            assertNull(mutatedExchange.getRequest().getHeaders().getFirst("X-Merchant-Id"));
            return Mono.empty();
        };

        authenticationFilter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }
}

