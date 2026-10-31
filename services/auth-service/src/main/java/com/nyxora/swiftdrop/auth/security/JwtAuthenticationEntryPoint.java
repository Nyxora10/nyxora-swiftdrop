package com.nyxora.swiftdrop.auth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles unauthenticated access attempts by returning an RFC 7807 compliant Problem Detail JSON payload.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        log.warn("[JwtAuthenticationEntryPoint] Unauthorized access attempt to {}: {}",
                request.getRequestURI(), authException.getMessage());

        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        Map<String, Object> problemDetail = new LinkedHashMap<>();
        problemDetail.put("type", "https://swiftdrop.lk/errors/unauthorized");
        problemDetail.put("title", "Unauthorized");
        problemDetail.put("status", HttpServletResponse.SC_UNAUTHORIZED);
        problemDetail.put("detail", authException.getMessage() != null
                ? authException.getMessage()
                : "Full authentication is required to access this resource.");
        problemDetail.put("instance", request.getRequestURI());
        problemDetail.put("timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
    }
}

