package com.nyxora.swiftdrop.parcel.dispatch.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Member 5 Dedicated Repository for Operations Dispatch Queue & Courier Workload Balancing (Task 1.6).
 * Completely isolated from other members' repositories to prevent merge conflicts.
 */
@Repository
public interface DispatchParcelRepository extends JpaRepository<Parcel, UUID> {

    @Query("SELECT p FROM Parcel p WHERE p.currentStatus = :status AND p.assignedCourierId IS NULL")
    List<Parcel> findUnassignedParcels(@Param("status") ParcelStatus status);

    @Query("SELECT p FROM Parcel p WHERE p.currentStatus = :status AND p.assignedCourierId IS NULL AND p.originZone.id = :originZoneId")
    List<Parcel> findUnassignedParcelsByOriginZone(@Param("status") ParcelStatus status, @Param("originZoneId") Integer originZoneId);

    long countByAssignedCourierIdAndCurrentStatusIn(UUID courierId, Collection<ParcelStatus> currentStatuses);
}
