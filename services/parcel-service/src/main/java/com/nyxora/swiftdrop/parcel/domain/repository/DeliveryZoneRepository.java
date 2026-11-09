package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DeliveryZone}.
 * Provides CRUD operations plus custom finders for zone lookups.
 */
@Repository
public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Integer> {

    /** Retrieve all zones currently open for booking (isActive = true). */
    List<DeliveryZone> findByIsActiveTrue();

    /** Lookup a zone by its unique code (e.g., "WP-COL-01"). */
    Optional<DeliveryZone> findByZoneCode(String zoneCode);

    /** Check for zone code conflict before creating a new zone. */
    boolean existsByZoneCode(String zoneCode);
}

