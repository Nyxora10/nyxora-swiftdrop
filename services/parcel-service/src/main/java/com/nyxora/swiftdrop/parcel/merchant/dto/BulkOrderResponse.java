package com.nyxora.swiftdrop.parcel.merchant.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Bulk Parcel Ingestion Batch Summary Response")
public class BulkOrderResponse {

    @Schema(description = "Total number of orders submitted in batch", example = "50")
    @JsonProperty("totalReceived")
    private int totalReceived;

    @Schema(description = "Number of successfully created orders", example = "48")
    @JsonProperty("successfulCount")
    private int successfulCount;

    @Schema(description = "Number of failed orders with validation issues", example = "2")
    @JsonProperty("failedCount")
    private int failedCount;

    @Schema(description = "Array of individual order processing results and tracking numbers")
    @JsonProperty("results")
    private List<BulkOrderItemResult> results;
}
