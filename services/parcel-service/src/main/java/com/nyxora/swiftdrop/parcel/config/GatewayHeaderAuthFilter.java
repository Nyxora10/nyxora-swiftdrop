package com.nyxora.swiftdrop.parcel.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Reads the trusted {@code X-User-Role} header injected by the API Gateway after
 * JWT verification and establishes a Spring Security authentication context.
 *
 * <p>This allows {@code @PreAuthorize} and {@code hasRole()} checks in
 * {@link SecurityConfig} to work without re-parsing JWTs inside this service.
 *
 * <p>Header format: {@code X-User-Role: ROLE_ADMIN}
 */
@Component
public class GatewayHeaderAuthFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER   = "X-User-Id";
    private static final String USER_ROLE_HEADER = "X-User-Role";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String userId   = request.getHeader(USER_ID_HEADER);
        String userRole = request.getHeader(USER_ROLE_HEADER);

        if (userId != null && userRole != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            // e.g., userRole = "ROLE_ADMIN" → authority = "ROLE_ADMIN"
            SimpleGrantedAuthority authority = new SimpleGrantedAuthority(userRole);
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.singletonList(authority));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}

