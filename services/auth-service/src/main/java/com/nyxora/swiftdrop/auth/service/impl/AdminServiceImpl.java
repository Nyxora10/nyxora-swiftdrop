package com.nyxora.swiftdrop.auth.service.impl;

import com.nyxora.swiftdrop.auth.dto.request.AdminDecisionRequest;
import com.nyxora.swiftdrop.auth.dto.response.AdminDecisionResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserDetailResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserSummaryResponse;
import com.nyxora.swiftdrop.auth.dto.response.PageResponse;
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import com.nyxora.swiftdrop.auth.service.AdminService;
import com.nyxora.swiftdrop.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final CourierRepository courierRepository;
    private final MerchantRepository merchantRepository;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserSummaryResponse> getUsers(UserStatus status, RoleName role, String search, Pageable pageable) {
        String sanitizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        log.debug("[AdminService] Querying users: status={}, role={}, search={}", status, role, sanitizedSearch);

        Page<User> usersPage = userRepository.findAllWithFilters(status, role, sanitizedSearch, pageable);
        Page<AdminUserSummaryResponse> mappedPage = usersPage.map(AdminUserSummaryResponse::fromEntity);

        return PageResponse.fromPage(mappedPage);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetails(UUID userId) {
        log.debug("[AdminService] Fetching user details for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        Courier courier = null;
        if (user.getRole().getName() == RoleName.ROLE_COURIER) {
            courier = courierRepository.findByUserId(userId).orElse(null);
        }

        Merchant merchant = null;
        if (user.getRole().getName() == RoleName.ROLE_MERCHANT) {
            merchant = merchantRepository.findByUserId(userId).orElse(null);
        }

        return AdminUserDetailResponse.fromEntity(user, courier, merchant);
    }

    @Override
    @Transactional
    public AdminDecisionResponse approveUser(UUID userId) {
        log.info("[AdminService] Processing account approval for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getStatus() == UserStatus.ACTIVE) {
            throw new IllegalStateException("User account is already ACTIVE.");
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setStatusReason(null);

        // If courier is approved/reactivated, restore availability
        if (user.getRole().getName() == RoleName.ROLE_COURIER) {
            courierRepository.findByUserId(userId).ifPresent(courier -> {
                courier.setIsAvailable(true);
                courierRepository.save(courier);
                log.info("[AdminService] Restored courier availability for userId: {}", userId);
            });
        }

        User savedUser = userRepository.save(user);
        log.info("[AdminService] Successfully activated account for user: {}", savedUser.getEmail());

        return AdminDecisionResponse.of("User account approved and activated successfully.", savedUser);
    }

    @Override
    @Transactional
    public AdminDecisionResponse rejectUser(UUID userId, AdminDecisionRequest request) {
        log.info("[AdminService] Processing account rejection for userId: {}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));

        if (user.getStatus() != UserStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("Only accounts with PENDING_APPROVAL status can be rejected.");
        }

        user.setStatus(UserStatus.REJECTED);
        user.setStatusReason(request.reason().trim());

        User savedUser = userRepository.save(user);
        log.info("[AdminService] Rejected application for user: {} with reason: {}", savedUser.getEmail(), request.reason());

        return AdminDecisionResponse.of("User account application rejected.", savedUser);
    }

    @Override
    @Transactional
    public AdminDecisionResponse suspendUser(UUID currentAdminId, UUID targetUserId, AdminDecisionRequest request) {
        log.info("[AdminService] Processing account suspension for targetUserId: {} requested by admin: {}",
                targetUserId, currentAdminId);

        if (currentAdminId != null && currentAdminId.equals(targetUserId)) {
            throw new IllegalArgumentException("Administrators cannot suspend their own account.");
        }

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + targetUserId));

        if (user.getRole().getName() == RoleName.ROLE_ADMIN) {
            throw new IllegalArgumentException("Cannot suspend an administrator account.");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new IllegalStateException("User account is already SUSPENDED.");
        }

        user.setStatus(UserStatus.SUSPENDED);
        user.setStatusReason(request.reason().trim());

        // Side-effect 1: If courier, disable availability immediately
        if (user.getRole().getName() == RoleName.ROLE_COURIER) {
            courierRepository.findByUserId(targetUserId).ifPresent(courier -> {
                courier.setIsAvailable(false);
                courierRepository.save(courier);
                log.info("[AdminService] Disabled courier availability on suspension for userId: {}", targetUserId);
            });
        }

        // Side-effect 2: Invalidate all active refresh tokens immediately
        refreshTokenService.revokeAllByUserId(targetUserId);
        log.info("[AdminService] Revoked all active refresh tokens on suspension for userId: {}", targetUserId);

        User savedUser = userRepository.save(user);
        return AdminDecisionResponse.of("User account suspended successfully.", savedUser);
    }
}

