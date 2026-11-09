package com.nyxora.swiftdrop.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GatewayProperties;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @Autowired
    private GatewayProperties gatewayProperties;

    @Test
    void contextLoads() {
    }

    @Test
    void deduplicatesCorsHeadersAddedByDownstreamServices() {
        var dedupeFilter = gatewayProperties.getDefaultFilters().stream()
                .filter(filter -> "DedupeResponseHeader".equals(filter.getName()))
                .findFirst();

        assertThat(dedupeFilter).isPresent();
        assertThat(String.join(" ", dedupeFilter.orElseThrow().getArgs().values()))
                .contains("Access-Control-Allow-Origin")
                .contains("Access-Control-Allow-Credentials")
                .contains("RETAIN_FIRST");
    }
}


