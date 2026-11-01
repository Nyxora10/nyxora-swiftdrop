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
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public ResponseEntity<PageResponse<AdminUserSummaryResponse>> getUsers(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AdminUserSummaryResponse> response = adminService.getUsers(status, role, search, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserDetailResponse> getUserDetails(@PathVariable UUID userId) {
        AdminUserDetailResponse response = adminService.getUserDetails(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/approve")
    public ResponseEntity<AdminDecisionResponse> approveUser(@PathVariable UUID userId) {
        AdminDecisionResponse response = adminService.approveUser(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/reject")
    public ResponseEntity<AdminDecisionResponse> rejectUser(
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDecisionRequest request) {
        AdminDecisionResponse response = adminService.rejectUser(userId, request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/users/{userId}/suspend")
    public ResponseEntity<AdminDecisionResponse> suspendUser(
            @AuthenticationPrincipal UserPrincipal currentAdmin,
            @PathVariable UUID userId,
            @Valid @RequestBody AdminDecisionRequest request) {
        UUID currentAdminId = currentAdmin != null ? currentAdmin.getId() : null;
        AdminDecisionResponse response = adminService.suspendUser(currentAdminId, userId, request);
        return ResponseEntity.ok(response);
    }
}

