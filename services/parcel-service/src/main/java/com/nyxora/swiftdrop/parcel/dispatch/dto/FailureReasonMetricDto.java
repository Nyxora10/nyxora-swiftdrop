package com.nyxora.swiftdrop.parcel.dispatch.dto;

import lombok.Builder;

/**
 * Breakdown of delivery failure causes and their proportional distribution.
 */
@Builder
public record FailureReasonMetricDto(
        String failureReason,
        long count,
        double percentage
) {
}
