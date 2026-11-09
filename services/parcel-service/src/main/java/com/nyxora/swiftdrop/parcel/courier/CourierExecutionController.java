package com.nyxora.swiftdrop.parcel.courier;

import com.nyxora.swiftdrop.parcel.courier.dto.*;
import com.nyxora.swiftdrop.parcel.courier.storage.ProofStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller for courier operations, runsheet management, status execution,
 * and delivery proof storage (Tasks 1.7 & 1.8).
 */
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Courier Execution & Proof Storage",
        description = "Endpoints for courier runsheets, state transitions, and delivery proof uploads (Member 6: Task 1.7 / 1.8)")
public class CourierExecutionController {

    private final CourierExecutionService courierExecutionService;
    private final ProofStorageService proofStorageService;

    @Operation(summary = "Get Courier Runsheet",
            description = "Retrieves all active pickups and deliveries assigned to the authenticated courier.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Runsheet retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid courier identity header"),
            @ApiResponse(responseCode = "403", description = "Access denied: Missing ROLE_COURIER or ROLE_ADMIN")
    })
    @GetMapping("/courier/runsheet")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
    public ResponseEntity<CourierRunsheetResponseDto> getCourierRunsheet(
            @Parameter(description = "Authenticated courier UUID (propagated by API Gateway)")
            @RequestHeader(value = "X-Courier-Id", required = false) UUID xCourierId,
            @RequestHeader(value = "X-User-Id", required = false) UUID xUserId) {

        UUID courierId = resolveCourierId(xCourierId, xUserId);
        log.info("Fetching daily runsheet for courier: {}", courierId);
        CourierRunsheetResponseDto response = courierExecutionService.getCourierRunsheet(courierId);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get Assigned Courier Parcel",
            description = "Retrieves details of a specific parcel assigned to the courier. Enforces ownership authorization.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parcel details retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied: Parcel not assigned to this courier"),
            @ApiResponse(responseCode = "404", description = "Parcel not found")
    })
    @GetMapping("/courier/{id}")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
    public ResponseEntity<CourierParcelCardDto> getCourierParcelById(
            @PathVariable("id") UUID parcelId,
            @RequestHeader(value = "X-Courier-Id", required = false) UUID xCourierId,
            @RequestHeader(value = "X-User-Id", required = false) UUID xUserId) {

        UUID courierId = resolveCourierId(xCourierId, xUserId);
        log.info("Courier {} requesting details for parcel {}", courierId, parcelId);
        CourierParcelCardDto parcel = courierExecutionService.getCourierParcelById(courierId, parcelId);
        return ResponseEntity.ok(parcel);
    }

    @Operation(summary = "Update Parcel Delivery Status",
            description = "Transitions a parcel's status (PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, FAILED) with state machine validation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "403", description = "Access denied: Parcel not assigned to this courier"),
            @ApiResponse(responseCode = "409", description = "Invalid state transition or missing proof/failure guards")
    })
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
    public ResponseEntity<ParcelStatusUpdateResponseDto> updateParcelStatus(
            @PathVariable("id") UUID parcelId,
            @Valid @RequestBody UpdateParcelStatusRequestDto request,
            @RequestHeader(value = "X-Courier-Id", required = false) UUID xCourierId,
            @RequestHeader(value = "X-User-Id", required = false) UUID xUserId) {

        UUID courierId = resolveCourierId(xCourierId, xUserId);
        log.info("Courier {} updating status for parcel {} to {}", courierId, parcelId, request.getTargetStatus());
        ParcelStatusUpdateResponseDto response = courierExecutionService.updateParcelStatus(courierId, parcelId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Upload Delivery Proof Artifact",
            description = "Uploads a recipient signature or photo proof (PNG, JPEG, WEBP) to AWS S3 or local storage.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Proof artifact uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid file type or size exceeds 5MB"),
            @ApiResponse(responseCode = "403", description = "Access denied: Parcel not assigned to this courier")
    })
    @PostMapping(value = "/{id}/proof-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('COURIER', 'ADMIN')")
    public ResponseEntity<ProofUploadResponseDto> uploadDeliveryProof(
            @PathVariable("id") UUID parcelId,
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Courier-Id", required = false) UUID xCourierId,
            @RequestHeader(value = "X-User-Id", required = false) UUID xUserId) {

        UUID courierId = resolveCourierId(xCourierId, xUserId);

        // Security check: Verify the courier owns this parcel before accepting upload
        courierExecutionService.getCourierParcelById(courierId, parcelId);

        log.info("Courier {} uploading delivery proof for parcel {}", courierId, parcelId);
        ProofUploadResponseDto response = proofStorageService.storeProof(parcelId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Serve Delivery Proof Artifact",
            description = "Public static endpoint for viewing stored delivery proof images.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Proof image streamed successfully"),
            @ApiResponse(responseCode = "400", description = "Proof file not found or path invalid")
    })
    @GetMapping("/proofs/{filename:.+}")
    public ResponseEntity<Resource> getProofImage(@PathVariable("filename") String filename) {
        Resource resource = proofStorageService.loadProofAsResource(filename);
        String contentType = proofStorageService.determineContentType(filename);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private UUID resolveCourierId(UUID xCourierId, UUID xUserId) {
        if (xCourierId != null) {
            return xCourierId;
        }
        if (xUserId != null) {
            return xUserId;
        }
        throw new IllegalArgumentException("Required courier identity header ('X-Courier-Id' or 'X-User-Id') is missing.");
    }
}