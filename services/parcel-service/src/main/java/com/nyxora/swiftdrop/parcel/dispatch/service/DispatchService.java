package com.nyxora.swiftdrop.parcel.dispatch.service;

import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for Dispatcher operations (Member 5: Task 1.6).
 * Manages unassigned parcel queues and courier assignment with 20-parcel workload enforcement.
 */
public interface DispatchService {

    /**
     * Retrieves all unassigned parcels (in CREATED status), optionally filtered by origin delivery zone.
     *
     * @param originZoneId optional origin zone ID filter (null for all zones)
     * @return list of unassigned parcels
     */
    List<UnassignedParcelResponse> getUnassignedParcels(Integer originZoneId);

    /**
     * Assigns a parcel in CREATED status to a courier, validating that the target courier
     * has fewer than 20 active deliveries.
     *
     * @param parcelId    UUID of the parcel to assign
     * @param request     request containing target courier ID
     * @param adminUserId UUID of the authenticated administrator making the assignment
     * @return assignment confirmation details
     */
    AssignCourierResponse assignCourier(UUID parcelId, AssignCourierRequest request, UUID adminUserId);
}
