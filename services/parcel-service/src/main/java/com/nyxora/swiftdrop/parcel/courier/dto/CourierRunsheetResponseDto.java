package com.nyxora.swiftdrop.parcel.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response payload containing the courier's active daily deliveries
 * partitioned into pending Pickups and active Deliveries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourierRunsheetResponseDto {

    private UUID courierId;
    private int totalActiveParcels;
    private List<CourierParcelCardDto> pickups;
    private List<CourierParcelCardDto> deliveries;
    private Instant generatedAt;
}