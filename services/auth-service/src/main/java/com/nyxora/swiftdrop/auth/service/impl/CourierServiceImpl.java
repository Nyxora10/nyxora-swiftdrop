package com.nyxora.swiftdrop.auth.service.impl;

import com.nyxora.swiftdrop.auth.dto.request.CourierAvailabilityRequest;
import com.nyxora.swiftdrop.auth.dto.response.CourierProfileResponse;
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.service.CourierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implementation of CourierService providing courier profile retrieval
 * and shift availability toggling (Task 2.5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierServiceImpl implements CourierService {

    private final CourierRepository courierRepository;

    @Override
    @Transactional(readOnly = true)
    public CourierProfileResponse getCourierProfile(UUID userId) {
        log.info("[CourierService] Fetching courier profile for userId: {}", userId);

        Courier courier = courierRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Courier profile not found for userId: " + userId));

        return CourierProfileResponse.fromEntity(courier);
    }

    @Override
    @Transactional
    public CourierProfileResponse updateAvailability(UUID userId, CourierAvailabilityRequest request) {
        log.info("[CourierService] Updating availability for userId: {} → isAvailable={}, zoneId={}",
                userId, request.isAvailable(), request.assignedZoneId());

        Courier courier = courierRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Courier profile not found for userId: " + userId));

        courier.setIsAvailable(request.isAvailable());

        // Only update zone when explicitly provided in the request payload
        if (request.assignedZoneId() != null) {
            courier.setAssignedZoneId(request.assignedZoneId());
        }

        Courier saved = courierRepository.save(courier);
        log.info("[CourierService] Courier availability updated → courierId: {}, isAvailable: {}",
                saved.getId(), saved.getIsAvailable());

        return CourierProfileResponse.fromEntity(saved);
    }
}
