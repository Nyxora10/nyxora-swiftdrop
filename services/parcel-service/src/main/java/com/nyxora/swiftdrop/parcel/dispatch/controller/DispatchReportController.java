package com.nyxora.swiftdrop.parcel.dispatch.controller;

import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierRequest;
import com.nyxora.swiftdrop.parcel.dispatch.dto.AssignCourierResponse;
import com.nyxora.swiftdrop.parcel.dispatch.dto.UnassignedParcelResponse;
import com.nyxora.swiftdrop.parcel.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for operations dispatch queue management and courier assignment (Member 5: Task 1.6).
 * Protected by perimeter RBAC requiring ROLE_ADMIN.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
@Tag(name = "Dispatch & Operations (Member 5)", description = "Unassigned parcels queue, courier workload monitoring, and parcel assignment")
@SecurityRequirement(name = "BearerAuth")
public class DispatchReportController {

    private final DispatchService dispatchService;

    @Operation(
            summary = "Get unassigned parcels queue",
            description = "Returns all parcels currently in CREATED status awaiting courier dispatch. Can be optionally filtered by originZoneId."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unassigned parcels retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Access denied — requires ROLE_ADMIN")
    })
    @GetMapping("/unassigned")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UnassignedParcelResponse>> getUnassignedParcels(
            @Parameter(description = "Optional origin delivery zone ID to filter unassigned parcels")
            @RequestParam(required = false) Integer originZoneId) {
        log.info("[DispatchReportController] GET /api/v1/parcels/unassigned (originZoneId: {})", originZoneId);
        List<UnassignedParcelResponse> parcels = dispatchService.getUnassignedParcels(originZoneId);
        return ResponseEntity.ok(parcels);
    }

    @Operation(
            summary = "Assign courier to parcel",
            description = "Assigns an unassigned parcel to a designated courier. Enforces the 20-parcel active delivery limit. Publishes parcel.assigned event to RabbitMQ."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Parcel successfully assigned to courier"),
            @ApiResponse(responseCode = "400", description = "Invalid parcel state — parcel is not in CREATED status"),
            @ApiResponse(responseCode = "403", description = "Access denied — requires ROLE_ADMIN"),
            @ApiResponse(responseCode = "404", description = "Parcel not found with the specified ID"),
            @ApiResponse(responseCode = "409", description = "Courier workload limit exceeded — courier already has 20 active parcels")
    })
    @PutMapping("/{parcelId}/assign")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AssignCourierResponse> assignCourier(
            @Parameter(description = "UUID of the parcel to assign", required = true)
            @PathVariable UUID parcelId,
            @Valid @RequestBody AssignCourierRequest request,
            @Parameter(hidden = true)
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {

        log.info("[DispatchReportController] PUT /api/v1/parcels/{}/assign to courierId={}", parcelId, request.courierId());

        UUID adminUserId = null;
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                adminUserId = UUID.fromString(userIdHeader.trim());
            } catch (IllegalArgumentException e) {
                log.warn("[DispatchReportController] Invalid X-User-Id header format: {}", userIdHeader);
            }
        }

        AssignCourierResponse response = dispatchService.assignCourier(parcelId, request, adminUserId);
        return ResponseEntity.ok(response);
    }
}
