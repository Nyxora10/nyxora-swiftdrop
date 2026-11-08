package com.nyxora.swiftdrop.parcel.dispatch.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse;
import com.nyxora.swiftdrop.parcel.dispatch.exception.CourierWorkloadExceededException;
import com.nyxora.swiftdrop.parcel.dispatch.exception.InvalidParcelStateException;
import com.nyxora.swiftdrop.parcel.dispatch.service.DispatchService;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("DispatchReportController MockMvc Tests (Member 5: Task 1.6)")
class DispatchReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DispatchService dispatchService;

    private static final String ADMIN_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String CUSTOMER_USER_ID = "00000000-0000-0000-0000-000000000002";

    @Test
    @DisplayName("GET /api/v1/parcels/unassigned requires authentication - returns 403 when unauthenticated")
    void getUnassignedParcels_unauthenticated_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/unassigned"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/unassigned with ROLE_CUSTOMER returns 403 Forbidden")
    void getUnassignedParcels_withCustomerRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/unassigned")
                        .header("X-User-Id", CUSTOMER_USER_ID)
                        .header("X-User-Role", "ROLE_CUSTOMER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/parcels/unassigned with ROLE_ADMIN returns 200 OK and list of unassigned parcels")
    void getUnassignedParcels_withAdminRole_returns200() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UnassignedParcelResponse parcelDto = new UnassignedParcelResponse(
                parcelId,
                "SD-20261107-123456",
                "No. 12, Main St, Colombo",
                "No. 45, Temple Rd, Kandy",
                "Nimal Jayasinghe",
                "+94771112233",
                BigDecimal.valueOf(2.50),
                ParcelSize.MEDIUM,
                BigDecimal.valueOf(850.00),
                ParcelStatus.CREATED,
                1, "WP-COL-01", "Colombo Central",
                5, "CP-KDY-01", "Kandy Central",
                Instant.now()
        );

        when(dispatchService.getUnassignedParcels(null)).thenReturn(List.of(parcelDto));

        mockMvc.perform(get("/api/v1/parcels/unassigned")
                        .header("X-User-Id", ADMIN_USER_ID)
                        .header("X-User-Role", "ROLE_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].trackingNumber").value("SD-20261107-123456"))
                .andExpect(jsonPath("$[0].originZoneCode").value("WP-COL-01"))
                .andExpect(jsonPath("$[0].currentStatus").value("CREATED"));
    }

    @Test
    @DisplayName("PUT /api/v1/parcels/{id}/assign with valid courier returns 200 OK")
    void assignCourier_withAdminRole_returns200() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        AssignCourierRequest request = new AssignCourierRequest(courierId);
        AssignCourierResponse response = new AssignCourierResponse(
                parcelId,
                "SD-20261107-123456",
                courierId,
                "ASSIGNED",
                Instant.now(),
                "Parcel SD-20261107-123456 successfully assigned to courier."
        );

        when(dispatchService.assignCourier(eq(parcelId), any(AssignCourierRequest.class), eq(UUID.fromString(ADMIN_USER_ID))))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/parcels/" + parcelId + "/assign")
                        .header("X-User-Id", ADMIN_USER_ID)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.assignedCourierId").value(courierId.toString()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    @DisplayName("PUT /api/v1/parcels/{id}/assign when courier workload is exceeded returns 409 Conflict")
    void assignCourier_whenWorkloadExceeded_returns409Conflict() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        AssignCourierRequest request = new AssignCourierRequest(courierId);

        when(dispatchService.assignCourier(eq(parcelId), any(AssignCourierRequest.class), any()))
                .thenThrow(new CourierWorkloadExceededException(courierId, 20L));

        mockMvc.perform(put("/api/v1/parcels/" + parcelId + "/assign")
                        .header("X-User-Id", ADMIN_USER_ID)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Courier Workload Exceeded"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    @DisplayName("PUT /api/v1/parcels/{id}/assign when parcel is not in CREATED status returns 400 Bad Request")
    void assignCourier_whenInvalidParcelState_returns400BadRequest() throws Exception {
        UUID parcelId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        AssignCourierRequest request = new AssignCourierRequest(courierId);

        when(dispatchService.assignCourier(eq(parcelId), any(AssignCourierRequest.class), any()))
                .thenThrow(new InvalidParcelStateException("SD-20261107-123456", ParcelStatus.IN_TRANSIT));

        mockMvc.perform(put("/api/v1/parcels/" + parcelId + "/assign")
                        .header("X-User-Id", ADMIN_USER_ID)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid Parcel State"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("PUT /api/v1/parcels/{id}/assign with empty payload returns 422 Unprocessable Entity")
    void assignCourier_withInvalidPayload_returns422() throws Exception {
        UUID parcelId = UUID.randomUUID();

        mockMvc.perform(put("/api/v1/parcels/" + parcelId + "/assign")
                        .header("X-User-Id", ADMIN_USER_ID)
                        .header("X-User-Role", "ROLE_ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
