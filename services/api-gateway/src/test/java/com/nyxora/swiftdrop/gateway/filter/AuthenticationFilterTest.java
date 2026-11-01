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
import org.springframework.web.server.ServerWebExchange;
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
        return Jwts.builder()
                .subject(sub)
                .claim("role", role)
                .claim("email", email)
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
}

