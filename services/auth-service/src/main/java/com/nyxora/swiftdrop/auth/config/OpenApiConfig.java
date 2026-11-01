package com.nyxora.swiftdrop.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3.0 / Swagger configuration for SwiftDrop Auth Service.
 * Defines global API metadata and configures the Bearer JWT security scheme
 * for interactive Swagger UI authentication.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SwiftDrop Identity & Authentication Service",
                version = "v1.0",
                description = "Production-grade microservice powering island-wide identity management, "
                        + "multi-role registration (Customer, Merchant, Courier), stateless JWT authentication, "
                        + "social logins (Google OAuth 2.0, Apple Sign In, Facebook Login), and admin approval lifecycle.",
                contact = @Contact(
                        name = "SwiftDrop Engineering Team",
                        email = "dev@swiftdrop.lk"
                )
        ),
        servers = {
                @Server(url = "http://localhost:8081", description = "Auth Service Direct (Local)"),
                @Server(url = "http://localhost:8080", description = "API Gateway (Local)")
        }
)
@SecurityScheme(
        name = "BearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Enter the JWT access token issued by /api/v1/auth/login or social login endpoints."
)
public class OpenApiConfig {
}

