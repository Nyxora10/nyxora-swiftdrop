package com.nyxora.swiftdrop.parcel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * Reads the trusted {@code X-User-Role} header injected by the API Gateway after
 * JWT verification and establishes a Spring Security authentication context.
 *
 * <p>This allows {@code @PreAuthorize} and {@code hasRole()} checks in
 * {@link SecurityConfig} to work without re-parsing JWTs inside this service.
 *
 * <p>Header format: {@code X-User-Role: ROLE_ADMIN}
 *
 * <p><b>Gateway trust:</b> when {@code app.security.gateway-shared-secret} is configured, the
 * identity headers are honoured only if the request also carries the matching
 * {@code X-Gateway-Secret}, which the gateway stamps on every request it forwards after
 * stripping caller-supplied copies. A caller reaching this service directly cannot forge
 * {@code X-User-*} / {@code X-Merchant-Id} identity without that secret.
 */
@Slf4j
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER   = "X-User-Id";
    public static final String USER_ROLE_HEADER = "X-User-Role";
    public static final String GATEWAY_SECRET_HEADER = "X-Gateway-Secret";

    private final byte[] gatewaySecret;

    public GatewayHeaderAuthFilter(@Value("${app.security.gateway-shared-secret:}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret == null ? new byte[0] : gatewaySecret.trim().getBytes(StandardCharsets.UTF_8);
        if (this.gatewaySecret.length == 0) {
            log.warn("[GatewayHeaderAuthFilter] app.security.gateway-shared-secret is not set; identity headers are "
                    + "trusted without gateway verification. Configure GATEWAY_SHARED_SECRET outside tests.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId   = request.getHeader(USER_ID_HEADER);
        String userRole = request.getHeader(USER_ROLE_HEADER);

        if (userId != null && userRole != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (isFromGateway(request)) {
                // e.g., userRole = "ROLE_ADMIN" → authority = "ROLE_ADMIN"
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(userRole);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                log.warn("[GatewayHeaderAuthFilter] Ignoring identity headers without a valid gateway secret: {} {}",
                        request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isFromGateway(HttpServletRequest request) {
        if (gatewaySecret.length == 0) {
            return true; // verification disabled (test profile only)
        }
        String provided = request.getHeader(GATEWAY_SECRET_HEADER);
        return provided != null
                && MessageDigest.isEqual(gatewaySecret, provided.trim().getBytes(StandardCharsets.UTF_8));
    }
}
