package com.nyxora.swiftdrop.ai_assistant_service.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Perimeter security filter enforcing client authentication on MCP endpoints (/mcp/**).
 * Validates cryptographic JWT tokens or trusted gateway identity headers,
 * populating UserContextHolder and rejecting untrusted callers.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class McpSecurityFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(McpSecurityFilter.class);

    private final SecretKey signingKey;

    public McpSecurityFilter(@Value("${jwt.secret:defaultSecretKeyForTestingAndLocalDevelopmentOnly1234567890!}") String jwtSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret);
        } catch (Exception e) {
            keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only enforce authentication on MCP endpoints
        return !path.startsWith("/mcp");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        try {
            UserIdentity identity = resolveIdentity(request);

            if (identity == null) {
                log.warn("Unauthorized access attempt to MCP endpoint: {}", request.getRequestURI());
                sendUnauthorizedError(response, "Unauthorized MCP access. Valid token or trusted identity required.");
                return;
            }

            UserContextHolder.setContext(identity);
            filterChain.doFilter(request, response);

        } finally {
            UserContextHolder.clear();
        }
    }

    private UserIdentity resolveIdentity(HttpServletRequest request) {
        // 1. Check Authorization Bearer token
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(signingKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.getSubject();
                String role = claims.get("role", String.class);
                String email = claims.get("email", String.class);

                if (userId != null && !userId.isBlank()) {
                    return new UserIdentity(userId, role != null ? role : "USER", email);
                }
            } catch (JwtException ex) {
                log.warn("Invalid JWT presented to MCP server: {}", ex.getMessage());
                return null;
            }
        }

        // 2. Check trusted gateway identity header (when deployed behind SwiftDrop API Gateway)
        String gatewayUserId = request.getHeader("X-User-Id");
        if (gatewayUserId != null && !gatewayUserId.isBlank()) {
            String gatewayRole = request.getHeader("X-User-Role");
            String gatewayEmail = request.getHeader("X-User-Email");
            return new UserIdentity(
                    gatewayUserId.trim(),
                    gatewayRole != null ? gatewayRole.trim() : "USER",
                    gatewayEmail != null ? gatewayEmail.trim() : null
            );
        }

        return null;
    }

    private void sendUnauthorizedError(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\": \"" + message + "\"}");
    }
}

