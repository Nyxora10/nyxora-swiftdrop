package com.nyxora.swiftdrop.parcel.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk Parcel Batch Creation Payload (up to 100 delivery requests)")
public class BulkOrderRequest {

    @Schema(description = "Array of up to 100 delivery orders to create in a single batch", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "Bulk orders list cannot be empty.")
    @Size(max = 100, message = "Bulk order batch cannot exceed 100 orders.")
    // No @Valid here on purpose: rows are validated one by one in BulkOrderServiceImpl so an
    // invalid row is reported as FAILED instead of rejecting the whole batch with HTTP 400.
    @JsonProperty("orders")
    private List<MerchantOrderRequest> orders;
}
