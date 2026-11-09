package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.request.CourierAvailabilityRequest;
import com.nyxora.swiftdrop.auth.dto.response.CourierProfileResponse;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

/**
 * Courier self-service endpoints — profile retrieval and shift availability toggle (Task 2.5).
 * All routes require an authenticated ROLE_COURIER JWT at the API Gateway perimeter.
 */
@RestController
@RequestMapping("/api/v1/auth/courier")
@RequiredArgsConstructor
@PreAuthorize("hasRole('COURIER')")
@Tag(
        name = "Courier Self-Service",
        description = "Courier profile retrieval and on/off-duty availability toggling. " +
                      "Protected by ROLE_COURIER. All requests must carry a valid Bearer JWT."
)
@SecurityRequirement(name = "BearerAuth")
public class CourierController {

    private final CourierService courierService;

    /**
     * Resolves the authenticated UserPrincipal from method argument, SecurityContext,
     * or request session attributes (fallback for filter-bypassed test environments).
     */
    private UserPrincipal resolvePrincipal(UserPrincipal userPrincipal, HttpServletRequest request) {
        if (userPrincipal != null) {
            return userPrincipal;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal)) {
            if (request != null && request.getUserPrincipal() instanceof Authentication userAuth) {
                auth = userAuth;
            } else if (request != null && request.getSession(false) != null) {
                Object secCtx = request.getSession(false).getAttribute("SPRING_SECURITY_CONTEXT");
                if (secCtx instanceof SecurityContext sc) {
                    auth = sc.getAuthentication();
                }
            }
        }
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            return principal;
        }
        throw new BadCredentialsException("Authentication required. Please provide a valid Bearer token.");
    }

    /**
     * Returns the authenticated courier's full profile including vehicle details,
     * current availability status, active delivery count, and assigned zone.
     */
    @GetMapping("/profile")
    @Operation(
            summary = "Get Courier Profile",
            description = "Returns vehicleType, licensePlate, isAvailable, activeDeliveryCount, " +
                          "and assignedZoneId for the authenticated courier."
    )
    public ResponseEntity<CourierProfileResponse> getCourierProfile(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            HttpServletRequest request) {

        UserPrincipal principal = resolvePrincipal(userPrincipal, request);
        CourierProfileResponse response = courierService.getCourierProfile(principal.getId());
        return ResponseEntity.ok(response);
    }

    /**
     * Toggles the authenticated courier's on/off-duty availability flag.
     * Optionally updates the courier's assigned delivery zone when {@code assignedZoneId} is provided.
     */
    @PatchMapping("/availability")
    @Operation(
            summary = "Toggle Courier Availability",
            description = "Sets isAvailable=true (on-duty) or false (off-duty). " +
                          "Optionally updates the courier's assignedZoneId when provided in the request body."
    )
    public ResponseEntity<CourierProfileResponse> updateAvailability(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CourierAvailabilityRequest request,
            HttpServletRequest httpRequest) {

        UserPrincipal principal = resolvePrincipal(userPrincipal, httpRequest);
        CourierProfileResponse response = courierService.updateAvailability(
                principal.getId(), request);
        return ResponseEntity.ok(response);
    }
}
