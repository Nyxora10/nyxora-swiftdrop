package com.nyxora.swiftdrop.auth.dto.request;

import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload for courier/rider driver registration (awaits admin approval).
 */
public record CourierRegisterRequest(
        @NotBlank(message = "Courier full name is required")
        @Size(max = 150, message = "Full name must not exceed 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email format is invalid")
        @Size(max = 255, message = "Email must not exceed 255 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
        String password,

        @NotBlank(message = "Phone number is required")
        @Size(max = 20, message = "Phone number must not exceed 20 characters")
        String phoneNumber,

        @NotNull(message = "Vehicle type is required (MOTORCYCLE, THREE_WHEELER, VAN, TRUCK)")
        VehicleType vehicleType,

        @NotBlank(message = "Vehicle license plate number is required")
        @Size(max = 50, message = "License plate must not exceed 50 characters")
        String licensePlate
) {}

