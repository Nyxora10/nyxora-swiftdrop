package com.nyxora.swiftdrop.auth.dto.response;

import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;

import java.time.Instant;
import java.util.UUID;

/**
 * Comprehensive polymorphic user details for administrator review before approval/rejection.
 */
public record AdminUserDetailResponse(
        UUID id,
        String email,
        String fullName,
        String phoneNumber,
        RoleName role,
        UserStatus status,
        String statusReason,
        AuthProvider authProvider,
        Instant createdAt,
        CourierDetail courierProfile,
        MerchantDetail merchantProfile
) {
    public record CourierDetail(
            UUID courierId,
            VehicleType vehicleType,
            String licensePlate,
            Boolean isAvailable,
            Integer activeDeliveryCount,
            Integer assignedZoneId
    ) {}

    public record MerchantDetail(
            UUID merchantId,
            String businessName,
            String registrationNumber,
            String webhookUrl,
            String oauthClientId
    ) {}

    public static AdminUserDetailResponse fromEntity(User user, Courier courier, Merchant merchant) {
        CourierDetail courierDetail = courier != null ? new CourierDetail(
                courier.getId(),
                courier.getVehicleType(),
                courier.getLicensePlate(),
                courier.getIsAvailable(),
                courier.getActiveDeliveryCount(),
                courier.getAssignedZoneId()
        ) : null;

        MerchantDetail merchantDetail = merchant != null ? new MerchantDetail(
                merchant.getId(),
                merchant.getBusinessName(),
                merchant.getRegistrationNumber(),
                merchant.getWebhookUrl(),
                merchant.getOauthClientId()
        ) : null;

        return new AdminUserDetailResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhoneNumber(),
                user.getRole().getName(),
                user.getStatus(),
                user.getStatusReason(),
                user.getAuthProvider(),
                user.getCreatedAt(),
                courierDetail,
                merchantDetail
        );
    }
}

