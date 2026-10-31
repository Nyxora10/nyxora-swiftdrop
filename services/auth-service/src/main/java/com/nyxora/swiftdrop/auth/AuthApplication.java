package com.nyxora.swiftdrop.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SwiftDrop Authentication & Identity Microservice.
 *
 * Responsibilities:
 * - User Registration & Role Assignment (Customer, Merchant, Courier, Admin)
 * - Credential Verification & BCrypt Hashing
 * - Stateless JWT Access Token & Stateful Refresh Token Lifecycle Management
 * - Google OAuth 2.0 Social Login Integration
 * - Merchant & Courier Approval Workflows
 */
@SpringBootApplication
public class AuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }
}

