package com.nyxora.swiftdrop.gateway.filter;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Predicate;

/**
 * Route validator determining whether an incoming request targets a public or secured resource.
 * Secure by default: Any route not explicitly whitelisted requires authenticated Bearer tokens.
 */
@Component
public class RouteValidator {

    /**
     * Whitelist of public endpoints that bypass perimeter JWT validation.
     */
    public static final List<String> OPEN_API_ENDPOINTS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/parcels/track",
            "/actuator/health",
            "/actuator/info"
    );

    /**
     * Predicate evaluating whether an HTTP request requires authentication.
     */
    public Predicate<ServerHttpRequest> isSecured = request -> {
        String path = request.getPath().pathWithinApplication().value();
        return OPEN_API_ENDPOINTS.stream().noneMatch(path::startsWith);
    };
}

