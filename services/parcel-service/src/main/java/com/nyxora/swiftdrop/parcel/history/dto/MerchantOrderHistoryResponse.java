package com.nyxora.swiftdrop.parcel.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Merchant order history response enriched with aggregate KPI counters
 * for dashboard visualization and bulk shipment monitoring.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Merchant Order History with KPI Counters")
public class MerchantOrderHistoryResponse {

    @Schema(description = "Total lifetime orders booked by this merchant", example = "120")
    private long totalOrders;

    @Schema(description = "Number of orders currently in transit or out for delivery", example = "15")
    private long inTransitCount;

    @Schema(description = "Number of successfully completed deliveries", example = "100")
    private long deliveredCount;

    @Schema(description = "Number of failed or returned deliveries", example = "5")
    private long failedCount;

    @Schema(description = "Paginated list of orders matching the active filter")
    private PagedOrderResponse<OrderSummaryDto> orders;
}

