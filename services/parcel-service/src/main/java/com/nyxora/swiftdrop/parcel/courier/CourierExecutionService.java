package com.nyxora.swiftdrop.parcel.courier;

import com.nyxora.swiftdrop.parcel.courier.dto.CourierParcelCardDto;
import com.nyxora.swiftdrop.parcel.courier.dto.CourierRunsheetResponseDto;
import com.nyxora.swiftdrop.parcel.courier.dto.ParcelStatusUpdateResponseDto;
import com.nyxora.swiftdrop.parcel.courier.dto.UpdateParcelStatusRequestDto;

import java.util.UUID;

/**
 * Service interface governing Courier daily runsheet queries and guarded
 * lifecycle state execution (Task 1.7).
 */
public interface CourierExecutionService {

    /**
     * Retrieves the daily runsheet for the specified courier, partitioned into
     * pending pickups and active deliveries.
     *
     * @param courierId Unique UUID of the authenticated courier
     * @return CourierRunsheetResponseDto containing partitioned active parcels
     */
    CourierRunsheetResponseDto getCourierRunsheet(UUID courierId);

    /**
     * Retrieves details for a specific parcel assigned to the courier.
     * Enforces courier resource ownership (HTTP 403 Forbidden if not assigned to this courier).
     *
     * @param courierId Unique UUID of the authenticated courier
     * @param parcelId  Unique UUID of the requested parcel
     * @return CourierParcelCardDto with full parcel details and failure count
     */
    CourierParcelCardDto getCourierParcelById(UUID courierId, UUID parcelId);

    /**
     * Transitions a parcel to a target lifecycle status with strict state machine validation,
     * courier ownership authorization, audit trail persistence, RabbitMQ domain event publishing,
     * and live WebSocket broadcasting.
     *
     * @param courierId Unique UUID of the authenticated courier
     * @param parcelId  Unique UUID of the parcel being updated
     * @param request   Update request payload containing target status, proofs, notes, or failure reasons
     * @return ParcelStatusUpdateResponseDto containing status change summary
     */
    ParcelStatusUpdateResponseDto updateParcelStatus(UUID courierId, UUID parcelId, UpdateParcelStatusRequestDto request);
}