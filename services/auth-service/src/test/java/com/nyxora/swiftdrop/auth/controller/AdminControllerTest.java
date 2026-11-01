package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.AdminDecisionRequest;
import com.nyxora.swiftdrop.auth.dto.response.AdminDecisionResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserDetailResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserSummaryResponse;
import com.nyxora.swiftdrop.auth.dto.response.PageResponse;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.AdminService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("AdminController MockMvc Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /api/v1/auth/admin/users returns HTTP 200 OK with paginated users")
    void getUsers_Returns200WithPage() throws Exception {
        AdminUserSummaryResponse userSummary = new AdminUserSummaryResponse(
                UUID.randomUUID(), "courier@swiftdrop.lk", "Kamal Rider", "+94771234567",
                RoleName.ROLE_COURIER, UserStatus.PENDING_APPROVAL, null,
                AuthProvider.LOCAL, Instant.now()
        );

        PageResponse<AdminUserSummaryResponse> pageResponse = new PageResponse<>(
                List.of(userSummary), 0, 10, 1, 1, true, true
        );

        when(adminService.getUsers(any(), any(), any(), any(Pageable.class))).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/auth/admin/users")
                        .param("status", "PENDING_APPROVAL")
                        .param("page", "0")
                        .param("size", "10")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("courier@swiftdrop.lk"))
                .andExpect(jsonPath("$.content[0].status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/auth/admin/users/{userId} returns HTTP 200 OK with polymorphic detail")
    void getUserDetails_Returns200WithPolymorphicData() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminUserDetailResponse.CourierDetail courierDetail = new AdminUserDetailResponse.CourierDetail(
                UUID.randomUUID(), VehicleType.MOTORCYCLE, "WP-BBI-1234", true, 0, 1
        );

        AdminUserDetailResponse response = new AdminUserDetailResponse(
                userId, "courier@swiftdrop.lk", "Kamal Rider", "+94771234567",
                RoleName.ROLE_COURIER, UserStatus.PENDING_APPROVAL, null,
                AuthProvider.LOCAL, Instant.now(), courierDetail, null
        );

        when(adminService.getUserDetails(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/admin/users/{userId}", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.courierProfile.licensePlate").value("WP-BBI-1234"))
                .andExpect(jsonPath("$.merchantProfile").doesNotExist());
    }

    @Test
    @DisplayName("PUT /api/v1/auth/admin/users/{userId}/approve returns HTTP 200 OK")
    void approveUser_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminDecisionResponse response = new AdminDecisionResponse(
                "User account approved and activated successfully.",
                userId, "merchant@swiftdrop.lk", RoleName.ROLE_MERCHANT,
                UserStatus.ACTIVE, null
        );

        when(adminService.approveUser(userId)).thenReturn(response);

        mockMvc.perform(put("/api/v1/auth/admin/users/{userId}/approve", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/admin/users/{userId}/reject with valid payload returns HTTP 200 OK")
    void rejectUser_ValidPayload_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminDecisionRequest request = new AdminDecisionRequest("Invalid vehicle registration documents.");
        AdminDecisionResponse response = new AdminDecisionResponse(
                "User account application rejected.",
                userId, "rider@swiftdrop.lk", RoleName.ROLE_COURIER,
                UserStatus.REJECTED, request.reason()
        );

        when(adminService.rejectUser(eq(userId), any(AdminDecisionRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/auth/admin/users/{userId}/reject", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.statusReason").value("Invalid vehicle registration documents."));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/admin/users/{userId}/reject with blank reason returns HTTP 400 Bad Request")
    void rejectUser_BlankReason_Returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminDecisionRequest request = new AdminDecisionRequest("");

        mockMvc.perform(put("/api/v1/auth/admin/users/{userId}/reject", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/admin/users/{userId}/suspend returns HTTP 200 OK")
    void suspendUser_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AdminDecisionRequest request = new AdminDecisionRequest("Multiple delivery policy violations.");
        AdminDecisionResponse response = new AdminDecisionResponse(
                "User account suspended successfully.",
                userId, "courier@swiftdrop.lk", RoleName.ROLE_COURIER,
                UserStatus.SUSPENDED, request.reason()
        );

        when(adminService.suspendUser(any(), eq(userId), any(AdminDecisionRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/auth/admin/users/{userId}/suspend", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"))
                .andExpect(jsonPath("$.statusReason").value("Multiple delivery policy violations."));
    }

    @Test
    @DisplayName("PUT /api/v1/auth/admin/users/{userId}/approve when already ACTIVE returns HTTP 409 Conflict")
    void approveUser_AlreadyActive_Returns409Conflict() throws Exception {
        UUID userId = UUID.randomUUID();
        when(adminService.approveUser(userId)).thenThrow(new IllegalStateException("User account is already ACTIVE."));

        mockMvc.perform(put("/api/v1/auth/admin/users/{userId}/approve", userId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.detail").value("User account is already ACTIVE."));
    }
}

