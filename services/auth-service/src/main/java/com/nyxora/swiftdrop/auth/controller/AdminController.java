package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.request.AdminDecisionRequest;
import com.nyxora.swiftdrop.auth.dto.response.AdminDecisionResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserDetailResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserSummaryResponse;
import com.nyxora.swiftdrop.auth.dto.response.PageResponse;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Administrative endpoints for user verification, review, and account lifecycle management.
 * Protected by perimeter gateway authorization and method-level @PreAuthorize("hasRole('ADMIN')").
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin User Lifecycle", description = "Administrative endpoints for partner verification, approvals, rejections, suspensions, and user audits.")
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @Operation(summary = "Search & Filter Users", description = "Paginated search across users with optional filtering by UserStatus, RoleName, and keyword search.")
    public ResponseEntity<PageResponse<AdminUserSummaryResponse>> getUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AdminUserSummaryResponse> response = adminService.getUsers(status, role, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "Get User Detailed Profile", description = "Retrieves full user profile including merchant business details or courier vehicle / driver license details.")
    public ResponseEntity<AdminUserDetailResponse> getUserDetails(@PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetails(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/approve")
    @Operation(summary = "Approve Partner Registration", description = "Transitions a user from PENDING_APPROVAL to ACTIVE, allowing login.")
    public ResponseEntity<AdminDecisionResponse> approveUser(@PathVariable UUID userId) {
        AdminDecisionResponse response = adminService.approveUser(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/reject")
    @Operation(summary = "Reject Partner Registration", description = "Rejects a partner registration with an audit reason, transitioning status to REJECTED.")
    public ResponseEntity<AdminDecisionResponse> rejectUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDecisionRequest request) {
        AdminDecisionResponse response = adminService.rejectUser(userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/suspend")
    @Operation(summary = "Suspend User Account", description = "Suspends an active user account with an audit reason, immediately revoking refresh tokens and preventing subsequent logins. Self-suspension is blocked.")
    public ResponseEntity<AdminDecisionResponse> suspendUser(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDecisionRequest request) {
        UUID currentAdminId = currentAdmin != null ? currentAdmin.getId() : null;
        AdminDecisionResponse response = adminService.suspendUser(currentAdminId, userId, request);
        return ResponseEntity.ok(response);
    }
}
