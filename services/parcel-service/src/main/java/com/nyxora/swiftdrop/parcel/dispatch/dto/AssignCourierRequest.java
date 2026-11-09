package com.nyxora.swiftdrop.parcel.dispatch.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for assigning a parcel to an active courier.
 */
public record AssignCourierRequest(
        @NotNull(message = "Courier ID is required.")
        UUID courierId
) {
}
