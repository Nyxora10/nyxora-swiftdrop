package com.nyxora.swiftdrop.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Perimeter security filter enforcing stateless JWT verification and Token-to-Header identity relay.
 * Prevents unauthorized requests from reaching downstream microservices and strips external header spoofing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private final RouteValidator routeValidator;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().pathWithinApplication().value();

        // 1. Whitelist Check: Public Endpoints
        if (!routeValidator.isSecured.test(request)) {
            log.debug("[AuthenticationFilter] Public path accessed: {}", path);
            // Anti-spoofing: Remove any forged X-User headers on public paths
            ServerHttpRequest sanitizedRequest = request.mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-User-Email");
                    })
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

        log.debug("[AuthenticationFilter] Verified user [{}] with role [{}] for path [{}]", email, role, path);

        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(h -> {
                    // Anti-spoofing: clear any caller-supplied identity headers first
                    h.remove("X-User-Id");
                    h.remove("X-User-Role");
                    h.remove("X-User-Email");
                })
                .header("X-User-Id", userId != null ? userId : "")
                .header("X-User-Role", role != null ? role : "")
                .header("X-User-Email", email != null ? email : "")
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
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

