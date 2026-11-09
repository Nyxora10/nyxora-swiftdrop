package com.nyxora.swiftdrop.parcel.domain.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
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
}

