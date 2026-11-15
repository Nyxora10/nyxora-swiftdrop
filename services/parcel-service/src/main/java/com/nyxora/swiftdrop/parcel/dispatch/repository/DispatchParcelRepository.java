package com.nyxora.swiftdrop.parcel.dispatch.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Member 5 Dedicated Repository for Operations Dispatch Queue, Courier Workload Balancing,
 * and Operational KPI Analytics (Tasks 1.6, 1.10 & 6.4).
 * Completely isolated from other members' repositories to prevent merge conflicts.
 */
@Repository
public interface DispatchParcelRepository extends JpaRepository<Parcel, UUID> {

    @Query("SELECT p FROM Parcel p WHERE p.currentStatus = :status AND p.assignedCourierId IS NULL")
    List<Parcel> findUnassignedParcels(@Param("status") ParcelStatus status);

    @Query("SELECT p FROM Parcel p WHERE p.currentStatus = :status AND p.assignedCourierId IS NULL AND p.originZone.id = :originZoneId")
    List<Parcel> findUnassignedParcelsByOriginZone(@Param("status") ParcelStatus status, @Param("originZoneId") Integer originZoneId);

    long countByAssignedCourierIdAndCurrentStatusIn(UUID courierId, Collection<ParcelStatus> currentStatuses);

    @Query("SELECT p.currentStatus, COUNT(p) FROM Parcel p " +
           "WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate " +
           "GROUP BY p.currentStatus")
    List<Object[]> countParcelsByStatusBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("SELECT p.id, p.createdAt, MIN(h.createdAt) " +
           "FROM Parcel p " +
           "JOIN ParcelStatusHistory h ON h.parcel = p " +
           "WHERE p.currentStatus = com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus.DELIVERED " +
           "  AND h.status = com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus.DELIVERED " +
           "  AND p.createdAt >= :startDate AND p.createdAt <= :endDate " +
           "GROUP BY p.id, p.createdAt")
    List<Object[]> getDeliveredParcelTimestampsBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("SELECT h.parcel.id, h.failureReason, h.createdAt " +
           "FROM ParcelStatusHistory h " +
           "JOIN h.parcel p " +
           "WHERE p.currentStatus = com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus.FAILED " +
           "  AND h.status = com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus.FAILED " +
           "  AND p.createdAt >= :startDate AND p.createdAt <= :endDate " +
           "ORDER BY h.createdAt DESC")
    List<Object[]> getFailedParcelHistoryBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("SELECT p.createdAt, p.currentStatus " +
           "FROM Parcel p " +
           "WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate")
    List<Object[]> getDailyParcelStatusBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);

    @Query("SELECT z.zoneCode, z.zoneName, z.district, COUNT(p), " +
           "SUM(CASE WHEN p.currentStatus = com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus.DELIVERED THEN 1L ELSE 0L END) " +
           "FROM Parcel p JOIN p.destinationZone z " +
           "WHERE p.createdAt >= :startDate AND p.createdAt <= :endDate " +
           "GROUP BY z.zoneCode, z.zoneName, z.district")
    List<Object[]> getZonePerformanceBetween(@Param("startDate") Instant startDate, @Param("endDate") Instant endDate);
}
