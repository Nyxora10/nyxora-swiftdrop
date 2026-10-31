package com.nyxora.swiftdrop.auth.repository;

import com.nyxora.swiftdrop.auth.entity.Courier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourierRepository extends JpaRepository<Courier, UUID> {

    Optional<Courier> findByUserId(UUID userId);

    Optional<Courier> findByLicensePlate(String licensePlate);

    boolean existsByLicensePlate(String licensePlate);

    List<Courier> findByIsAvailableTrue();
}
