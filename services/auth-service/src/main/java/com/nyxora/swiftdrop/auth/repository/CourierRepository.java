package com.nyxora.swiftdrop.auth.repository;

import com.nyxora.swiftdrop.auth.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    Optional<Courier> findByUserId(UUID userId);

    Optional<Courier> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);

    List<Courier> findByIsAvailableTrue();

    @Query("""
        SELECT c FROM Courier c
        JOIN FETCH c.user u
        WHERE c.isAvailable = true
          AND u.status = com.nyxora.swiftdrop.auth.entity.enums.UserStatus.ACTIVE
          AND (:zoneId IS NULL OR c.assignedZoneId = :zoneId)
        ORDER BY c.activeDeliveryCount ASC
    """)
    List<Courier> findAvailableCouriersByZone(@Param("zoneId") Integer zoneId);

    // ── Member 5 (Task 2.2): Atomic workload counter updates ──

    @Modifying
    @Transactional
    @Query("UPDATE Courier c SET c.activeDeliveryCount = c.activeDeliveryCount + 1 WHERE c.id = :courierId")
    int incrementActiveDeliveryCount(@Param("courierId") UUID courierId);

    @Modifying
    @Transactional
    @Query("UPDATE Courier c SET c.activeDeliveryCount = CASE WHEN c.activeDeliveryCount > 0 THEN c.activeDeliveryCount - 1 ELSE 0 END WHERE c.id = :courierId")
    int decrementActiveDeliveryCount(@Param("courierId") UUID courierId);
}
