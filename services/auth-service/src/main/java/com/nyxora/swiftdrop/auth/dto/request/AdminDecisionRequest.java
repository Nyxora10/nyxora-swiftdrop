package com.nyxora.swiftdrop.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload provided by an administrator when rejecting an application or suspending an active user.
 */
public record AdminDecisionRequest(
        @NotBlank(message = "Reason is required and cannot be blank")
        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason
) {}

