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
import java.util.List;
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

    // ---------------------------------------------------------------------------------------
    // Merchant identity propagation, M2M restrictions and internal-route blocking
    // ---------------------------------------------------------------------------------------

    private String merchantToken(UUID userId, UUID merchantId, boolean m2m) {
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim("role", "ROLE_MERCHANT")
                .claim("email", "store@merchant-a.lk")
                .claim("merchantId", merchantId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000));
        if (m2m) {
            builder.claim("token_type", "client_credentials").claim("scope", "parcels:create parcels:bulk");
        }
        return builder.signWith(secretKey).compact();
    }

    /** Runs the filter and returns the headers forwarded downstream, or null if the chain was not invoked. */
    private HttpHeaders forwardedHeaders(MockServerWebExchange exchange) {
        java.util.concurrent.atomic.AtomicReference<HttpHeaders> forwarded = new java.util.concurrent.atomic.AtomicReference<>();
        authenticationFilter.filter(exchange, ex -> {
            forwarded.set(ex.getRequest().getHeaders());
            return Mono.empty();
        }).block();
        return forwarded.get();
    }

    @Test
    @DisplayName("Merchant A cannot spoof Merchant B's X-Merchant-Id: the verified JWT claim always wins")
    void merchantIdHeader_IsReplacedWithVerifiedClaim() {
        UUID merchantA = UUID.randomUUID();
        UUID merchantB = UUID.randomUUID();
        String token = merchantToken(UUID.randomUUID(), merchantA, false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/parcels/merchant/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Merchant-Id", merchantB.toString())
                        .build());

        HttpHeaders headers = forwardedHeaders(exchange);

        assertNotNull(headers);
        assertEquals(List.of(merchantA.toString()), headers.get("X-Merchant-Id"));
        assertEquals("user", headers.getFirst("X-Token-Type"));
    }

    @Test
    @DisplayName("Client-supplied X-Merchant-Id is dropped when the token carries no merchant identity")
    void merchantIdHeader_IsStrippedForNonMerchantTokens() {
        String customerToken = generateToken(UUID.randomUUID().toString(), "ROLE_CUSTOMER", "c@swiftdrop.lk", 900000);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/parcels/customer/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken)
                        .header("X-Merchant-Id", UUID.randomUUID().toString())
                        .header("X-Courier-Id", UUID.randomUUID().toString())
                        .build());

        HttpHeaders headers = forwardedHeaders(exchange);

        assertNotNull(headers);
        assertNull(headers.getFirst("X-Merchant-Id"));
        assertNull(headers.getFirst("X-Courier-Id"));
    }

    @Test
    @DisplayName("Public routes also drop forged merchant and gateway-trust headers")
    void publicRoute_StripsMerchantAndGatewayHeaders() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/parcels/quote")
                        .header("X-Merchant-Id", UUID.randomUUID().toString())
                        .header("X-Gateway-Secret", "forged")
                        .build());

        HttpHeaders headers = forwardedHeaders(exchange);

        assertNotNull(headers);
        assertNull(headers.getFirst("X-Merchant-Id"));
        assertNull(headers.getFirst("X-Gateway-Secret"));
    }

    @Test
    @DisplayName("Configured gateway secret replaces any caller-supplied value")
    void gatewaySecret_IsStampedOnForwardedRequests() {
        authenticationFilter.setGatewaySharedSecret("real-gateway-secret");
        String token = merchantToken(UUID.randomUUID(), UUID.randomUUID(), false);

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/parcels/merchant/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .header("X-Gateway-Secret", "forged")
                        .build());

        HttpHeaders headers = forwardedHeaders(exchange);

        assertNotNull(headers);
        assertEquals(List.of("real-gateway-secret"), headers.get("X-Gateway-Secret"));
    }

    @Test
    @DisplayName("M2M token may create single and bulk orders")
    void m2mToken_AllowedOnOrderCreationRoutes() {
        UUID merchantId = UUID.randomUUID();
        String token = merchantToken(UUID.randomUUID(), merchantId, true);

        for (String path : List.of("/api/v1/parcels/merchant/orders", "/api/v1/parcels/bulk")) {
            MockServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());

            HttpHeaders headers = forwardedHeaders(exchange);

            assertNotNull(headers, "M2M should be allowed on " + path);
            assertEquals(merchantId.toString(), headers.getFirst("X-Merchant-Id"));
            assertEquals("client_credentials", headers.getFirst("X-Token-Type"));
        }
    }

    @Test
    @DisplayName("M2M token is rejected on interactive account-management routes")
    void m2mToken_RejectedOnAccountManagementRoutes() {
        String token = merchantToken(UUID.randomUUID(), UUID.randomUUID(), true);

        List<MockServerHttpRequest> requests = List.of(
                MockServerHttpRequest.put("/api/v1/auth/merchant/webhook").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                MockServerHttpRequest.post("/api/v1/auth/merchant/credentials/rotate").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build(),
                MockServerHttpRequest.get("/api/v1/auth/merchant/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token).build());

        for (MockServerHttpRequest request : requests) {
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            assertNull(forwardedHeaders(exchange), "M2M must not reach " + request.getPath());
            assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        }
    }

    @Test
    @DisplayName("Internal auth-service routes are not reachable through the gateway, even with a valid token")
    void internalRoutes_AreBlockedAtTheEdge() {
        String token = merchantToken(UUID.randomUUID(), UUID.randomUUID(), false);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/auth/internal/merchants/" + UUID.randomUUID() + "/webhook-config")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());

        assertNull(forwardedHeaders(exchange));
        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
    }
}

