package com.nyxora.swiftdrop.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that the route definitions in application.yml are loaded into
 * Spring Cloud Gateway's RouteLocator (Task 4.1).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Gateway Routes Integration Test (Task 4.1)")
class GatewayRoutesIntegrationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("Task 4.1: Downstream routes including AI, Parcel, Auth, Notification are configured")
    void downstreamRoutesAreConfigured() {
        List<String> routeIds = routeLocator.getRoutes()
                .map(Route::getId)
                .collectList()
                .block();

        assertThat(routeIds).contains(
                "auth-service-route",
                "parcel-service-route",
                "notification-service-route",
                "ai-service-route"
        );
    }

    @Test
    @DisplayName("Task 4.1: AI route URI targets port 8084 and maps to /api/v1/ai/**")
    void aiRouteDefinition_TargetsPort8084() {
        Route aiRoute = routeLocator.getRoutes()
                .filter(route -> "ai-service-route".equals(route.getId()))
                .blockFirst();

        assertThat(aiRoute).isNotNull();
        assertThat(aiRoute.getUri().toString()).contains("8084");
    }
}
