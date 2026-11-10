package com.nyxora.swiftdrop.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perimeter security filter enforcing stateless JWT verification and Token-to-Header identity relay.
 * Prevents unauthorized requests from reaching downstream microservices and strips external header spoofing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_ROLE = "X-User-Role";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_MERCHANT_ID = "X-Merchant-Id";
    public static final String HEADER_COURIER_ID = "X-Courier-Id";
    public static final String HEADER_TOKEN_TYPE = "X-Token-Type";
    /** Proves to downstream services that identity headers were set by this gateway. */
    public static final String HEADER_GATEWAY_SECRET = "X-Gateway-Secret";

    /** Every header downstream services treat as trusted identity. Always stripped from callers. */
    static final List<String> TRUSTED_HEADERS = List.of(
            HEADER_USER_ID, HEADER_USER_ROLE, HEADER_USER_EMAIL,
            HEADER_MERCHANT_ID, HEADER_COURIER_ID, HEADER_TOKEN_TYPE, HEADER_GATEWAY_SECRET);

    /** Service-to-service routes that must never be reachable from outside the cluster. */
    static final String INTERNAL_PATH_PREFIX = "/api/v1/auth/internal";

    static final String TOKEN_TYPE_M2M = "client_credentials";
    static final String TOKEN_TYPE_USER = "user";

    /**
     * Machine-to-machine (client_credentials) tokens may only call these method+path pairs:
     * single order creation and bulk ingestion. Everything else needs an interactive session.
     */
    static final List<String> M2M_ALLOWED_ROUTES = List.of(
            "POST /api/v1/parcels/merchant/orders",
            "POST /api/v1/parcels/bulk");

    private final RouteValidator routeValidator;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    private String gatewaySharedSecret;

    @Value("${app.gateway.shared-secret:}")
    public void setGatewaySharedSecret(String gatewaySharedSecret) {
        this.gatewaySharedSecret = gatewaySharedSecret;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        // 0. Internal service-to-service APIs are not part of the public surface.
        if (path.equals(INTERNAL_PATH_PREFIX) || path.startsWith(INTERNAL_PATH_PREFIX + "/")) {
            log.warn("[AuthenticationFilter] Blocked external access to internal path: {}", path);
            return onError(exchange, "Resource not found.", HttpStatus.NOT_FOUND);
        }

        // 1. Whitelist Check: Public Endpoints
        if (!routeValidator.isSecured.test(request)) {
            log.debug("[AuthenticationFilter] Public path accessed: {}", path);
            // Anti-spoofing: Remove any forged identity headers on public paths
            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(this::replaceTrustedHeaders)
                    .build();
            return chain.filter(exchange.mutate().request(sanitizedRequest).build());
        }

        // 2. Secured Endpoint: Extract Authorization Header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("[AuthenticationFilter] Missing or invalid Authorization header on secured path: {}", path);
            return onError(exchange, "Missing or invalid Authorization header. Expected 'Bearer <token>'.", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7).trim();

        // 3. Cryptographic Signature & Expiration Verification
        Claims claims;
        try {
            claims = jwtUtil.validateAndExtractClaims(token);
        } catch (ExpiredJwtException e) {
            log.warn("[AuthenticationFilter] Expired JWT presented on path: {}", path);
            return onError(exchange, "JWT token has expired. Please refresh your session.", HttpStatus.UNAUTHORIZED);
        } catch (JwtException e) {
            log.warn("[AuthenticationFilter] Invalid JWT presented on path {}: {}", path, e.getMessage());
            return onError(exchange, "Invalid JWT token: " + e.getMessage(), HttpStatus.UNAUTHORIZED);
        } catch (Exception e) {
            log.error("[AuthenticationFilter] Unexpected error parsing JWT on path {}: {}", path, e.getMessage());
            return onError(exchange, "Authentication failed.", HttpStatus.UNAUTHORIZED);
        }

        // 4. Token-to-Header Relay: Mutate request with verified identity claims
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        String email = claims.get("email", String.class);
        String merchantId = uuidClaim(claims, "merchantId");
        String courierId = uuidClaim(claims, "courierId");
        boolean m2m = TOKEN_TYPE_M2M.equals(claims.get("token_type", String.class));

        log.debug("[AuthenticationFilter] Verified user [{}] with role [{}] for path [{}]",
                PiiMaskingUtil.maskEmail(email), role, path);

        // 5. M2M tokens are restricted to explicitly authorised automation routes.
        if (m2m && !isM2mAllowed(request.getMethod(), path)) {
            log.warn("[AuthenticationFilter] M2M token rejected on non-automation route {} {}", request.getMethod(), path);
            return onError(exchange,
                    "Machine-to-machine tokens may only create orders. Sign in to the merchant portal for this action.",
                    HttpStatus.FORBIDDEN);
        }

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(h -> {
                    // Anti-spoofing: clear every caller-supplied identity header first,
                    // then set only values taken from the verified token.
                    replaceTrustedHeaders(h);
                    h.set(HEADER_USER_ID, userId != null ? userId : "");
                    h.set(HEADER_USER_ROLE, role != null ? role : "");
                    h.set(HEADER_USER_EMAIL, email != null ? email : "");
                    h.set(HEADER_TOKEN_TYPE, m2m ? TOKEN_TYPE_M2M : TOKEN_TYPE_USER);
                    if (merchantId != null) {
                        h.set(HEADER_MERCHANT_ID, merchantId);
                    }
                    if (courierId != null) {
                        h.set(HEADER_COURIER_ID, courierId);
                    }
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Removes all caller-supplied trusted headers and stamps the gateway secret, so downstream
     * services can tell gateway traffic from direct (possibly forged) calls.
     */
    private void replaceTrustedHeaders(HttpHeaders headers) {
        TRUSTED_HEADERS.forEach(headers::remove);
        if (gatewaySharedSecret != null && !gatewaySharedSecret.isBlank()) {
            headers.set(HEADER_GATEWAY_SECRET, gatewaySharedSecret);
        }
    }

    private static boolean isM2mAllowed(HttpMethod method, String path) {
        String route = (method != null ? method.name() : "") + " " + path;
        return M2M_ALLOWED_ROUTES.contains(route);
    }

    /** Returns the claim only if it is a well-formed UUID; never forwards arbitrary strings. */
    private static String uuidClaim(Claims claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString()).toString();
        } catch (IllegalArgumentException e) {
            log.warn("[AuthenticationFilter] Ignoring malformed {} claim", name);
            return null;
        }
    }

    /**
     * Serializes RFC 7807 ProblemDetail JSON response on authentication failure.
     */
    private Mono<Void> onError(ServerWebExchange exchange, String detail, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        Map<String, Object> errorBody = Map.of(
                "type", "about:blank",
                "title", status.getReasonPhrase(),
                "status", status.value(),
                "detail", detail,
                "instance", exchange.getRequest().getPath().pathWithinApplication().value()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":" + status.value() + ",\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // High precedence to intercept before routing and downstream dispatch
        return -1;
    }
}

