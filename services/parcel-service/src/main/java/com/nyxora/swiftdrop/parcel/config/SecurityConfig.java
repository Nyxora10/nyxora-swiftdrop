package com.nyxora.swiftdrop.parcel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Parcel Service Security Configuration.
 *
 * <p>This service sits behind the API Gateway perimeter. All inbound requests have
 * already been JWT-verified by the Gateway's {@code AuthenticationFilter}.
 * The Gateway injects three trusted headers for downstream use:
 * <ul>
 *   <li>{@code X-User-Id}    — the authenticated user's UUID</li>
 *   <li>{@code X-User-Email} — the authenticated user's email</li>
 *   <li>{@code X-User-Role}  — the authenticated user's role (e.g., ROLE_CUSTOMER, ROLE_ADMIN)</li>
 * </ul>
 *
 * <p>This filter chain reads the {@code X-User-Role} header to enforce role-based
 * access control without re-verifying the JWT (stateless, no token re-parsing needed).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    GatewayHeaderAuthFilter gatewayHeaderAuthFilter) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(gatewayHeaderAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                // Swagger UI & OpenAPI docs — open for exploration
                .requestMatchers(
                    "/swagger-ui.html",
                    "/swagger-ui/**",
                    "/v3/api-docs",
                    "/v3/api-docs/**"
                ).permitAll()

                // Member 4: Public endpoints (instant rate quote calculation & tracking)
                .requestMatchers(HttpMethod.POST,   "/api/v1/parcels/quote").permitAll()
                .requestMatchers(HttpMethod.GET,    "/api/v1/parcels/track/**").permitAll()

                // Member 7: Public real-time STOMP WebSocket & SockJS fallback endpoints (Task 1.11 / 4.4)
                .requestMatchers("/ws-swiftdrop/**").permitAll()

                // Member 4: Customer & Merchant delivery operations
                .requestMatchers(HttpMethod.POST,   "/api/v1/parcels/book").hasAnyRole("CUSTOMER", "MERCHANT", "ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/parcels/*/receipt/pdf").hasAnyRole("CUSTOMER", "MERCHANT", "ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/parcels/customer/**").hasAnyRole("CUSTOMER", "ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/parcels/merchant/**").hasAnyRole("MERCHANT", "ADMIN")

                // Member 3: Admin zone and pricing management — ROLE_ADMIN only
                .requestMatchers(HttpMethod.GET,    "/api/v1/zones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/v1/zones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/zones/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET,    "/api/v1/pricing/rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST,   "/api/v1/pricing/rules/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT,    "/api/v1/pricing/rules/**").hasRole("ADMIN")

                // All other requests require authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}
