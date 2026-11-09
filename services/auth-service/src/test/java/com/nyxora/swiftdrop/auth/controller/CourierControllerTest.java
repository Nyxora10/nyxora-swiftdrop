package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.CourierAvailabilityRequest;
import com.nyxora.swiftdrop.auth.dto.response.CourierProfileResponse;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.CourierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WebMVC slice tests for CourierController (Task 2.5).
 * Security filters are disabled; principal injection is handled via @WithUserDetails substitute.
 */
@WebMvcTest(CourierController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourierService courierService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UUID userId;
    private UUID courierId;
    private UserPrincipal courierPrincipal;
    private CourierProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        userId    = UUID.randomUUID();
        courierId = UUID.randomUUID();

        courierPrincipal = UserPrincipal.builder()
                .id(userId)
                .email("courier@swiftdrop.lk")
                .password("hashed")
                .fullName("Kamal Rider")
                .phoneNumber("+94771234567")
                .active(true)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_COURIER")))
                .courierId(courierId)
                .merchantId(null)
                .build();

        profileResponse = new CourierProfileResponse(
                courierId, "MOTORCYCLE", "ABC-1234", true, 2, 5
        );
    }

    // ─── GET /api/v1/auth/courier/profile ───────────────────────────────────

    @Test
    @DisplayName("GET /profile — returns 200 with courier profile for ROLE_COURIER principal")
    void getCourierProfile_success() throws Exception {
        when(courierService.getCourierProfile(userId)).thenReturn(profileResponse);

        mockMvc.perform(get("/api/v1/auth/courier/profile")
                        .with(SecurityMockMvcRequestPostProcessors.user(courierPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(courierId.toString()))
                .andExpect(jsonPath("$.vehicleType").value("MOTORCYCLE"))
                .andExpect(jsonPath("$.licensePlate").value("ABC-1234"))
                .andExpect(jsonPath("$.isAvailable").value(true))
                .andExpect(jsonPath("$.activeDeliveryCount").value(2))
                .andExpect(jsonPath("$.assignedZoneId").value(5));
    }

    // ─── PATCH /api/v1/auth/courier/availability ─────────────────────────────

    @Test
    @DisplayName("PATCH /availability — returns 200 with updated profile when going off-duty")
    void updateAvailability_goOffDuty() throws Exception {
        CourierAvailabilityRequest request = new CourierAvailabilityRequest(false, null);
        CourierProfileResponse offDutyResponse = new CourierProfileResponse(
                courierId, "MOTORCYCLE", "ABC-1234", false, 2, 5
        );

        when(courierService.updateAvailability(eq(userId), any(CourierAvailabilityRequest.class)))
                .thenReturn(offDutyResponse);

        mockMvc.perform(patch("/api/v1/auth/courier/availability")
                        .with(SecurityMockMvcRequestPostProcessors.user(courierPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isAvailable").value(false));
    }

    @Test
    @DisplayName("PATCH /availability — also updates assignedZoneId when provided")
    void updateAvailability_withZoneUpdate() throws Exception {
        CourierAvailabilityRequest request = new CourierAvailabilityRequest(true, 12);
        CourierProfileResponse zoneUpdatedResponse = new CourierProfileResponse(
                courierId, "MOTORCYCLE", "ABC-1234", true, 2, 12
        );

        when(courierService.updateAvailability(eq(userId), any(CourierAvailabilityRequest.class)))
                .thenReturn(zoneUpdatedResponse);

        mockMvc.perform(patch("/api/v1/auth/courier/availability")
                        .with(SecurityMockMvcRequestPostProcessors.user(courierPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedZoneId").value(12))
                .andExpect(jsonPath("$.isAvailable").value(true));
    }

    @Test
    @DisplayName("PATCH /availability — returns 400 when isAvailable is missing")
    void updateAvailability_missingIsAvailable_returns400() throws Exception {
        // Send an empty JSON body — @NotNull on isAvailable should trigger 400
        mockMvc.perform(patch("/api/v1/auth/courier/availability")
                        .with(SecurityMockMvcRequestPostProcessors.user(courierPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
