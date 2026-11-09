package com.nyxora.swiftdrop.parcel.dispatch.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for Transactional Outbox events.
 * Uses PESSIMISTIC_WRITE with SKIP LOCKED (lock.timeout = -2) to prevent concurrent
 * polling duplicate processing across multiple application instances.
 */
@Repository
public interface DispatchOutboxRepository extends JpaRepository<DispatchOutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2")})
    @Query("SELECT e FROM DispatchOutboxEvent e WHERE e.published = false ORDER BY e.createdAt ASC")
    List<DispatchOutboxEvent> findPendingEventsForProcessing();
}
