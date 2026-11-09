package com.nyxora.swiftdrop.parcel.courier.dto;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for couriers to transition parcel delivery states
 * (e.g. PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateParcelStatusRequestDto {

    @NotNull(message = "Target status is required")
    private ParcelStatus targetStatus;

    private String deliveryNote;

    private String recipientSignatureName;

    private String proofPhotoUrl;

    private String failureReason;
}