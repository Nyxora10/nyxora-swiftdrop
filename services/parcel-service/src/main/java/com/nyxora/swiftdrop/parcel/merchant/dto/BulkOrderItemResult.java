package com.nyxora.swiftdrop.parcel.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Individual item ingestion outcome in a bulk batch upload")
public class BulkOrderItemResult {

    @Schema(description = "Zero or 1-based row index in batch payload", example = "1")
    @JsonProperty("rowNumber")
    private int rowNumber;

    @Schema(description = "Assigned tracking number if order creation succeeded", example = "SD-20261107-A4B7C9")
    @JsonProperty("trackingNumber")
    private String trackingNumber;

    @Schema(description = "Calculated fee with 15% discount applied", example = "765.00")
    @JsonProperty("estimatedFee")
    private BigDecimal estimatedFee;

    @Schema(description = "Execution outcome: SUCCESS or FAILED", example = "SUCCESS")
    @JsonProperty("status")
    private String status;

    @Schema(description = "External merchant reference if supplied", example = "ORD-2026-9921A")
    @JsonProperty("merchantOrderReference")
    private String merchantOrderReference;

    @Schema(description = "List of validation or processing error messages if status is FAILED")
    @JsonProperty("errors")
    private List<String> errors;
}
