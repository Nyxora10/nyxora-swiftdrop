package com.nyxora.swiftdrop.parcel.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate courier delivery history response containing summary metrics
 * and chronological delivery records (Member 6: Delivery History).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierHistoryResponseDto {

    private UUID courierId;
    private int totalCompleted;
    private int deliveredCount;
    private int failedCount;
    private int returnedCount;
    private List<CourierDeliveryHistoryItemDto> history;
    private Instant generatedAt;
}