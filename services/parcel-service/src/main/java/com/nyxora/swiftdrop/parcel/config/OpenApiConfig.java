package com.nyxora.swiftdrop.parcel.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger UI configuration for the Parcel Service.
 * Exposes interactive API documentation at {@code /swagger-ui.html}.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI parcelServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("SwiftDrop Parcel Service API")
                        .version("1.0.0")
                        .description("Parcel Domain, Dynamic Pricing Strategy Engine and Zone Administration. " +
                                "Member 3 — Logistics Domain Model & Pricing Lead.")
                        .contact(new Contact()
                                .name("Member 3 — AshenRandira")
                                .url("https://github.com/Nyxora10/nyxora-swiftdrop")))
                .addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("BearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the access token issued by the Auth Service (/api/v1/auth/login)")));
    }
}

