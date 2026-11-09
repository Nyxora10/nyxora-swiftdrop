package com.nyxora.swiftdrop.parcel.history.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Generic paginated response wrapper for order history queries.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated Order History Response Wrapper")
public class PagedOrderResponse<T> {

    @Schema(description = "List of order records on this page")
    private List<T> content;

    @Schema(description = "Current zero-indexed page number", example = "0")
    private int pageNumber;

    @Schema(description = "Number of items per page", example = "10")
    private int pageSize;

    @Schema(description = "Total number of matching orders across all pages", example = "42")
    private long totalElements;

    @Schema(description = "Total number of pages", example = "5")
    private int totalPages;

    @Schema(description = "True if this is the first page", example = "true")
    private boolean isFirst;

    @Schema(description = "True if this is the last page", example = "false")
    private boolean isLast;
}

