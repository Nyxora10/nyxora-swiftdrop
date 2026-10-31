package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for merchant partner registration (awaits admin approval).
 */
public record MerchantRegisterRequest(
        @NotBlank(message = "Contact person full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Account email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Contact phone number is required")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        @NotBlank(message = "Registered business name is required")
        @Size(max = 200, message = "Business name must not exceed 200 characters")
        String businessName,

        @NotBlank(message = "Business registration number / Tax ID is required")
        @Size(max = 100, message = "Registration number must not exceed 100 characters")
        String registrationNumber,

        @Size(max = 500, message = "Webhook URL must not exceed 500 characters")
        String webhookUrl
) {}

