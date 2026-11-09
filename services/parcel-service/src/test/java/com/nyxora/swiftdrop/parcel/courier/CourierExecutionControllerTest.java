package com.nyxora.swiftdrop.parcel.courier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.courier.dto.*;
import com.nyxora.swiftdrop.parcel.courier.storage.ProofStorageService;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.exception.GlobalExceptionHandler;
import com.nyxora.swiftdrop.parcel.statemachine.InvalidStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CourierExecutionController MockMvc Tests (Member 6)")
class CourierExecutionControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Mock
    private CourierExecutionService courierExecutionService;

    @Mock
    private ProofStorageService proofStorageService;

    @InjectMocks
    private CourierExecutionController courierExecutionController;

    private UUID courierId;
    private UUID parcelId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(courierExecutionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        courierId = UUID.randomUUID();
        parcelId = UUID.randomUUID();
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/runsheet with X-Courier-Id returns 200 OK")
    void getRunsheetWithCourierIdHeader() throws Exception {
        CourierRunsheetResponseDto response = CourierRunsheetResponseDto.builder()
                .courierId(courierId)
                .totalActiveParcels(0)
                .pickups(Collections.emptyList())
                .deliveries(Collections.emptyList())
                .generatedAt(Instant.now())
                .build();

        when(courierExecutionService.getCourierRunsheet(courierId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/parcels/courier/runsheet")
                        .header("X-Courier-Id", courierId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.totalActiveParcels").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/runsheet with X-User-Id fallback returns 200 OK")
    void getRunsheetWithUserIdHeaderFallback() throws Exception {
        CourierRunsheetResponseDto response = CourierRunsheetResponseDto.builder()
                .courierId(courierId)
                .totalActiveParcels(1)
                .pickups(Collections.emptyList())
                .deliveries(Collections.emptyList())
                .generatedAt(Instant.now())
                .build();

        when(courierExecutionService.getCourierRunsheet(courierId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/parcels/courier/runsheet")
                        .header("X-User-Id", courierId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveParcels").value(1));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/runsheet without header returns 400 Bad Request")
    void getRunsheetMissingHeader() throws Exception {
        mockMvc.perform(get("/api/v1/parcels/courier/runsheet"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Required courier identity header ('X-Courier-Id' or 'X-User-Id') is missing."));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/{id} returns 200 OK for assigned parcel")
    void getCourierParcelByIdSuccess() throws Exception {
        CourierParcelCardDto card = CourierParcelCardDto.builder()
                .id(parcelId)
                .trackingNumber("SD-20261109-001")
                .currentStatus(ParcelStatus.OUT_FOR_DELIVERY)
                .pickupAddress("Colombo 01")
                .dropoffAddress("Colombo 03")
                .recipientName("Nimal Silva")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("1.50"))
                .parcelSize(ParcelSize.SMALL)
                .build();

        when(courierExecutionService.getCourierParcelById(courierId, parcelId)).thenReturn(card);

        mockMvc.perform(get("/api/v1/parcels/courier/{id}", parcelId)
                        .header("X-Courier-Id", courierId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(parcelId.toString()))
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261109-001"));
    }

    @Test
    @DisplayName("GET /api/v1/parcels/courier/{id} returns 403 Forbidden when unauthorized")
    void getCourierParcelByIdForbidden() throws Exception {
        when(courierExecutionService.getCourierParcelById(courierId, parcelId))
                .thenThrow(new AccessDeniedException("Access denied: You are not authorized to view this parcel."));

        mockMvc.perform(get("/api/v1/parcels/courier/{id}", parcelId)
                        .header("X-Courier-Id", courierId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status returns 200 OK on valid status update")
    void updateParcelStatusSuccess() throws Exception {
        UpdateParcelStatusRequestDto request = UpdateParcelStatusRequestDto.builder()
                .targetStatus(ParcelStatus.PICKED_UP)
                .deliveryNote("Collected parcel")
                .build();

        ParcelStatusUpdateResponseDto response = ParcelStatusUpdateResponseDto.builder()
                .parcelId(parcelId)
                .trackingNumber("SD-20261109-001")
                .previousStatus(ParcelStatus.ASSIGNED)
                .currentStatus(ParcelStatus.PICKED_UP)
                .updatedAt(Instant.now())
                .message("Transitioned to PICKED_UP")
                .build();

        when(courierExecutionService.updateParcelStatus(eq(courierId), eq(parcelId), any(UpdateParcelStatusRequestDto.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/v1/parcels/{id}/status", parcelId)
                        .header("X-Courier-Id", courierId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.currentStatus").value("PICKED_UP"));
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status returns 409 Conflict on invalid state transition")
    void updateParcelStatusConflict() throws Exception {
        UpdateParcelStatusRequestDto request = UpdateParcelStatusRequestDto.builder()
                .targetStatus(ParcelStatus.DELIVERED)
                .build();

        when(courierExecutionService.updateParcelStatus(eq(courierId), eq(parcelId), any(UpdateParcelStatusRequestDto.class)))
                .thenThrow(new InvalidStateTransitionException(
                        ParcelStatus.CREATED, ParcelStatus.DELIVERED, "Direct delivery illegal"));

        mockMvc.perform(patch("/api/v1/parcels/{id}/status", parcelId)
                        .header("X-Courier-Id", courierId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Invalid State Transition"))
                .andExpect(jsonPath("$.fromStatus").value("CREATED"))
                .andExpect(jsonPath("$.toStatus").value("DELIVERED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/parcels/{id}/status returns 403 Forbidden when courier does not own parcel")
    void updateParcelStatusForbidden() throws Exception {
        UpdateParcelStatusRequestDto request = UpdateParcelStatusRequestDto.builder()
                .targetStatus(ParcelStatus.PICKED_UP)
                .build();

        when(courierExecutionService.updateParcelStatus(eq(courierId), eq(parcelId), any(UpdateParcelStatusRequestDto.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        mockMvc.perform(patch("/api/v1/parcels/{id}/status", parcelId)
                        .header("X-Courier-Id", courierId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/parcels/{id}/proof-upload returns 201 Created on valid upload")
    void uploadProofSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "proof.png", "image/png", "signature-bytes".getBytes());

        ProofUploadResponseDto uploadResponse = ProofUploadResponseDto.builder()
                .parcelId(parcelId)
                .filename("proof.png")
                .proofPhotoUrl("/api/v1/parcels/proofs/proof.png")
                .uploadedAt(Instant.now())
                .build();

        when(proofStorageService.storeProof(eq(parcelId), any())).thenReturn(uploadResponse);

        mockMvc.perform(multipart("/api/v1/parcels/{id}/proof-upload", parcelId)
                        .file(file)
                        .header("X-Courier-Id", courierId.toString()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parcelId").value(parcelId.toString()))
                .andExpect(jsonPath("$.filename").value("proof.png"))
                .andExpect(jsonPath("$.proofPhotoUrl").value("/api/v1/parcels/proofs/proof.png"));

        verify(courierExecutionService).getCourierParcelById(courierId, parcelId);
    }

    @Test
    @DisplayName("GET /api/v1/parcels/proofs/{filename} returns 200 OK with correct media type")
    void getProofImageSuccess() throws Exception {
        String filename = "sample-proof.jpg";
        Resource mockResource = new ByteArrayResource("image-binary-data".getBytes());

        when(proofStorageService.loadProofAsResource(filename)).thenReturn(mockResource);
        when(proofStorageService.determineContentType(filename)).thenReturn("image/jpeg");

        mockMvc.perform(get("/api/v1/parcels/proofs/{filename}", filename))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes("image-binary-data".getBytes()));
    }
}

