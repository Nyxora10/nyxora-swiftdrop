package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DeliveryZone}.
 * Provides CRUD operations plus custom finders for zone lookups.
 */
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Integer> {

    /** Retrieve all zones currently open for booking (isActive = true). */
    List<DeliveryZone> findByIsActiveTrue();

    /** Retrieve all active zones sorted by district and zone name. */
    List<DeliveryZone> findByIsActiveTrueOrderByDistrictAscZoneNameAsc();

    /** Retrieve all active zones within a specific district (case-insensitive) sorted by zone name. */
    List<DeliveryZone> findByIsActiveTrueAndDistrictIgnoreCaseOrderByZoneNameAsc(String district);

    /**
     * Search active delivery zones matching zone name, zone code, or district case-insensitively.
     */
    @Query("""
        SELECT z FROM DeliveryZone z
        WHERE z.isActive = true
          AND (LOWER(z.zoneName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(z.zoneCode) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(z.district) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY z.district ASC, z.zoneName ASC
    """)
    List<DeliveryZone> searchActiveZones(@Param("search") String search);

    /**
     * Search active delivery zones within a specific district matching zone name or zone code case-insensitively.
     */
    @Query("""
        SELECT z FROM DeliveryZone z
        WHERE z.isActive = true
          AND LOWER(z.district) = LOWER(:district)
          AND (LOWER(z.zoneName) LIKE LOWER(CONCAT('%', :search, '%'))
               OR LOWER(z.zoneCode) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY z.district ASC, z.zoneName ASC
    """)
    List<DeliveryZone> searchActiveZonesInDistrict(
        @Param("search") String search,
        @Param("district") String district
    );

    /** Lookup a zone by its unique code (e.g., "WP-COL-01"). */
    Optional<DeliveryZone> findByZoneCode(String zoneCode);

    /** Check for zone code conflict before creating a new zone. */
    boolean existsByZoneCode(String zoneCode);
}
