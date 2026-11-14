package com.nyxora.swiftdrop.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Service-to-service authentication for {@code /api/v1/auth/internal/**}.
 *
 * <p>Internal endpoints return merchant webhook signing secrets, so they must never be
 * reachable anonymously. Trusted services (notification-webhook-service) send the shared
 * key in {@value #HEADER}; when it matches, the request is authenticated as
 * {@value #ROLE_INTERNAL_SERVICE}, the only authority allowed on internal routes.
 * No user account or JWT can carry that role.</p>
 *
 * <p>Fails closed: when no key is configured, every internal request is rejected.</p>
 */
@Slf4j
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Api-Key";
    public static final String INTERNAL_PATH_PREFIX = "/api/v1/auth/internal/";
    public static final String ROLE_INTERNAL_SERVICE = "ROLE_INTERNAL_SERVICE";

    private final byte[] expectedKey;

    public InternalApiKeyFilter(@Value("${app.internal.api-key:}") String apiKey) {
        this.expectedKey = apiKey == null ? new byte[0] : apiKey.trim().getBytes(StandardCharsets.UTF_8);
        if (this.expectedKey.length == 0) {
            log.warn("[InternalApiKeyFilter] app.internal.api-key is not configured; all internal endpoints will be rejected.");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        // Internal routes are never authorised by a user JWT, even one set by an earlier filter.
        SecurityContextHolder.clearContext();

        if (isValidKey(request.getHeader(HEADER))) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "internal-service", null, List.of(new SimpleGrantedAuthority(ROLE_INTERNAL_SERVICE)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } else {
            log.warn("[InternalApiKeyFilter] Rejected internal request without a valid service key: {} {}",
                    request.getMethod(), request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private boolean isValidKey(String provided) {
        if (expectedKey.length == 0 || provided == null) {
            return false;
        }
        // Constant-time comparison avoids leaking the key through response timing.
        return MessageDigest.isEqual(expectedKey, provided.trim().getBytes(StandardCharsets.UTF_8));
    }
}
