package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.CourierAvailabilityRequest;
import com.nyxora.swiftdrop.auth.dto.response.CourierProfileResponse;
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.service.impl.CourierServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CourierServiceImpl (Task 2.5).
 * Covers profile retrieval, availability toggling, and zone update logic.
 */
@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    @Mock
    private CourierRepository courierRepository;

    @InjectMocks
    private CourierServiceImpl courierService;

    private UUID userId;
    private UUID courierId;
    private Courier courier;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        courierId = UUID.randomUUID();

        courier = Courier.builder()
                .id(courierId)
                .vehicleType(VehicleType.MOTORCYCLE)
                .licensePlate("ABC-1234")
                .isAvailable(true)
                .activeDeliveryCount(2)
                .assignedZoneId(5)
                .build();
    }

    // ─── getCourierProfile ───────────────────────────────────────────────────

    @Test
    @DisplayName("getCourierProfile — returns profile DTO for a known courier userId")
    void getCourierProfile_success() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));

        CourierProfileResponse response = courierService.getCourierProfile(userId);

        assertThat(response.id()).isEqualTo(courierId);
        assertThat(response.vehicleType()).isEqualTo("MOTORCYCLE");
        assertThat(response.licensePlate()).isEqualTo("ABC-1234");
        assertThat(response.isAvailable()).isTrue();
        assertThat(response.activeDeliveryCount()).isEqualTo(2);
        assertThat(response.assignedZoneId()).isEqualTo(5);

        verify(courierRepository).findByUserId(userId);
    }

    @Test
    @DisplayName("getCourierProfile — throws ResourceNotFoundException for unknown userId")
    void getCourierProfile_notFound() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.getCourierProfile(userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(courierRepository).findByUserId(userId);
        verifyNoMoreInteractions(courierRepository);
    }

    // ─── updateAvailability ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateAvailability — sets isAvailable=false (go off-duty)")
    void updateAvailability_goOffDuty() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(courierRepository.save(courier)).thenReturn(courier);

        CourierAvailabilityRequest request = new CourierAvailabilityRequest(false, null);
        CourierProfileResponse response = courierService.updateAvailability(userId, request);

        assertThat(courier.getIsAvailable()).isFalse();
        assertThat(response.isAvailable()).isFalse();
        // Zone should remain unchanged
        assertThat(response.assignedZoneId()).isEqualTo(5);

        verify(courierRepository).save(courier);
    }

    @Test
    @DisplayName("updateAvailability — sets isAvailable=true (go on-duty)")
    void updateAvailability_goOnDuty() {
        courier.setIsAvailable(false);
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(courierRepository.save(courier)).thenReturn(courier);

        CourierAvailabilityRequest request = new CourierAvailabilityRequest(true, null);
        CourierProfileResponse response = courierService.updateAvailability(userId, request);

        assertThat(response.isAvailable()).isTrue();
        verify(courierRepository).save(courier);
    }

    @Test
    @DisplayName("updateAvailability — also updates assignedZoneId when provided")
    void updateAvailability_withZoneUpdate() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(courierRepository.save(courier)).thenReturn(courier);

        CourierAvailabilityRequest request = new CourierAvailabilityRequest(false, 12);
        CourierProfileResponse response = courierService.updateAvailability(userId, request);

        assertThat(courier.getAssignedZoneId()).isEqualTo(12);
        assertThat(response.assignedZoneId()).isEqualTo(12);
        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("updateAvailability — does NOT change zone when assignedZoneId is null")
    void updateAvailability_zoneUnchangedWhenNull() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(courierRepository.save(courier)).thenReturn(courier);

        CourierAvailabilityRequest request = new CourierAvailabilityRequest(false, null);
        courierService.updateAvailability(userId, request);

        // Original zone (5) must be preserved
        assertThat(courier.getAssignedZoneId()).isEqualTo(5);
    }

    @Test
    @DisplayName("updateAvailability — throws ResourceNotFoundException for unknown userId")
    void updateAvailability_notFound() {
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> courierService.updateAvailability(userId,
                new CourierAvailabilityRequest(false, null)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(userId.toString());

        verify(courierRepository, never()).save(any());
    }
}
