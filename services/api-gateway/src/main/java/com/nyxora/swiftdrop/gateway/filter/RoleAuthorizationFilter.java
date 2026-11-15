package com.nyxora.swiftdrop.gateway.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Coarse-grained role-based authorization filter that enforces URL-prefix-to-role rules
 * at the API Gateway perimeter.
 *
 * <p>Runs after {@link AuthenticationFilter} (Order -1) and reads the verified
 * {@code X-User-Role} header to determine if the caller is allowed to access
 * restricted URL prefixes.</p>
 *
 * <h3>Design: Defense-in-Depth</h3>
 * <ul>
 *     <li><b>This filter (coarse-grained):</b> blocks entire URL prefixes by role type.
 *         Example: only ROLE_ADMIN can access {@code /admin/**}.</li>
 *     <li><b>Downstream services (fine-grained):</b> enforce resource ownership.
 *         Example: Customer A cannot view Customer B's parcels.</li>
 * </ul>
 *
 * <p>Paths not matching any entry in {@code ROLE_REQUIREMENTS} are allowed through
 * for any authenticated user (e.g. {@code /api/v1/parcels/**} is open to all roles).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RoleAuthorizationFilter implements GlobalFilter, Ordered {

    private final ObjectMapper objectMapper;

    /**
     * Path-prefix-to-allowed-roles mapping (Task 4.2 expanded perimeter RBAC matrix).
     *
     * <p>Uses {@code Map.ofEntries} to support 10+ entries. If a request path contains
     * any of these prefixes, the caller's role must be in the corresponding allowed list.</p>
     *
     * <ul>
     *   <li>ROLE_COURIER / ROLE_ADMIN: /api/v1/parcels/courier/**, /api/v1/auth/courier/**</li>
     *   <li>ROLE_MERCHANT / ROLE_ADMIN: /api/v1/parcels/merchant/**, /api/v1/auth/merchant/**</li>
     *   <li>ROLE_ADMIN only: /admin/, /api/v1/auth/admin, /api/v1/parcels/unassigned,
     *       /api/v1/parcels/reports/**, /api/v1/zones/**, /api/v1/pricing/**</li>
     * </ul>
     */
    static final Map<String, List<String>> ROLE_REQUIREMENTS = Map.ofEntries(
            // Admin-only management endpoints
            Map.entry("/admin/",                     List.of("ROLE_ADMIN")),
            Map.entry("/api/v1/auth/admin",          List.of("ROLE_ADMIN")),
            Map.entry("/api/v1/parcels/unassigned",  List.of("ROLE_ADMIN")),
            Map.entry("/api/v1/parcels/reports/",    List.of("ROLE_ADMIN")),
            Map.entry("/api/v1/zones/",              List.of("ROLE_ADMIN")),
            Map.entry("/api/v1/pricing/",            List.of("ROLE_ADMIN")),

            // Courier endpoints (Task 2.5 profile/shift & parcel runsheet operations)
            Map.entry("/api/v1/parcels/courier/",    List.of("ROLE_COURIER", "ROLE_ADMIN")),
            Map.entry("/api/v1/auth/courier/",       List.of("ROLE_COURIER", "ROLE_ADMIN")),

            // Merchant endpoints (Task 2.5 credentials & parcel merchant operations)
            Map.entry("/api/v1/parcels/merchant/",   List.of("ROLE_MERCHANT", "ROLE_ADMIN")),
            Map.entry("/api/v1/auth/merchant/",      List.of("ROLE_MERCHANT", "ROLE_ADMIN")),

            // Backward compatibility
            Map.entry("/couriers/",                  List.of("ROLE_COURIER", "ROLE_ADMIN")),
            Map.entry("/merchants/",                 List.of("ROLE_MERCHANT", "ROLE_ADMIN"))
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        String userRole = exchange.getRequest().getHeaders().getFirst("X-User-Role");

        // Dynamic path variable pattern: /api/v1/parcels/{id}/assign (Dispatcher only -> ROLE_ADMIN)
        if (path.startsWith("/api/v1/parcels/") && path.endsWith("/assign")) {
            if (!"ROLE_ADMIN".equals(userRole)) {
                log.warn("[RoleAuthorizationFilter] Access denied on [{}] — role [{}] requires ROLE_ADMIN",
                        path, userRole);
                return onForbidden(exchange,
                        "Access denied: insufficient role privileges for this resource.");
            }
            log.debug("[RoleAuthorizationFilter] Access granted on [{}] for role [{}]", path, userRole);
            return chain.filter(exchange);
        }

        // Check each restricted prefix against the request path
        for (Map.Entry<String, List<String>> entry : ROLE_REQUIREMENTS.entrySet()) {
            String prefix = entry.getKey();
            List<String> allowedRoles = entry.getValue();

            if (path.contains(prefix)) {
                // If X-User-Role is missing or role is not in the allowed list → block with 403
                if (userRole == null || !allowedRoles.contains(userRole)) {
                    log.warn("[RoleAuthorizationFilter] Access denied on [{}] — role [{}] not in {}",
                            path, userRole, allowedRoles);
                    return onForbidden(exchange,
                            "Access denied: insufficient role privileges for this resource.");
                }

                // Role matched — log and allow through
                log.debug("[RoleAuthorizationFilter] Access granted on [{}] for role [{}]",
                        path, userRole);
                return chain.filter(exchange);
            }
        }

        // Path didn't match any restricted prefix, or role was authorized → forward
        return chain.filter(exchange);
    }

    /**
     * Serializes an RFC 7807 ProblemDetail JSON response for authorization failures.
     */
    private Mono<Void> onForbidden(ServerWebExchange exchange, String detail) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);

        Map<String, Object> errorBody = Map.of(
                "type", "about:blank",
                "title", HttpStatus.FORBIDDEN.getReasonPhrase(),
                "status", HttpStatus.FORBIDDEN.value(),
                "detail", detail,
                "instance", exchange.getRequest().getPath().pathWithinApplication().value()
        );

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(errorBody);
        } catch (JsonProcessingException e) {
            bytes = ("{\"status\":403,\"detail\":\"" + detail + "\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        // Runs immediately after AuthenticationFilter (-1)
        return 0;
    }
}

