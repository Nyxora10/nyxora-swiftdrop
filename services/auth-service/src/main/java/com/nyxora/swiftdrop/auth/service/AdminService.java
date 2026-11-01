package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.AdminDecisionRequest;
import com.nyxora.swiftdrop.auth.dto.response.AdminDecisionResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserDetailResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserSummaryResponse;
import com.nyxora.swiftdrop.auth.dto.response.PageResponse;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service managing user lifecycle, verification, and administrative controls.
 */
public interface AdminService {

    PageResponse<AdminUserSummaryResponse> getUsers(UserStatus status, RoleName role, String search, Pageable pageable);

    AdminUserDetailResponse getUserDetails(UUID userId);

    AdminDecisionResponse approveUser(UUID userId);

    AdminDecisionResponse rejectUser(UUID userId, AdminDecisionRequest request);

    AdminDecisionResponse suspendUser(UUID currentAdminId, UUID targetUserId, AdminDecisionRequest request);
}

