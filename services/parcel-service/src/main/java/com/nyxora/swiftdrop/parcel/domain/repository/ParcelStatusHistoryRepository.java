package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ParcelStatusHistory}.
 * Provides the full chronological delivery timeline for a given parcel.
 */
@Repository
public interface ParcelStatusHistoryRepository extends JpaRepository<ParcelStatusHistory, UUID> {

    /**
     * Returns the complete status history for a parcel, ordered oldest-first.
     * Used to render the public tracking timeline.
     */
    List<ParcelStatusHistory> findByParcel_IdOrderByCreatedAtAsc(UUID parcelId);

    /**
     * Counts how many times a parcel has entered a given status (e.g. FAILED).
     * Used by the State Machine to enforce delivery retry limits (max 3 failed attempts).
     */
    long countByParcel_IdAndStatus(UUID parcelId, ParcelStatus status);
}