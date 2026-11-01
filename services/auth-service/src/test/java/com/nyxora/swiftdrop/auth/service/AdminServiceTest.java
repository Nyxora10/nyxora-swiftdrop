package com.nyxora.swiftdrop.auth.service;

import com.nyxora.swiftdrop.auth.dto.request.AdminDecisionRequest;
import com.nyxora.swiftdrop.auth.dto.response.AdminDecisionResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserDetailResponse;
import com.nyxora.swiftdrop.auth.dto.response.AdminUserSummaryResponse;
import com.nyxora.swiftdrop.auth.dto.response.PageResponse;
import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import com.nyxora.swiftdrop.auth.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AdminServiceImpl adminService;

    private Role courierRole;
    private Role adminRole;
    private Role merchantRole;

    @BeforeEach
    void setUp() {
        courierRole = Role.builder().id(3).name(RoleName.ROLE_COURIER).build();
        adminRole = Role.builder().id(4).name(RoleName.ROLE_ADMIN).build();
        merchantRole = Role.builder().id(2).name(RoleName.ROLE_MERCHANT).build();
    }

    @Test
    @DisplayName("getUsers returns paginated list of users with dynamic filters")
    void getUsers_Success() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("test@rider.lk")
                .fullName("Test Rider")
                .role(courierRole)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 10), 1);
        when(userRepository.findAllWithFilters(eq(UserStatus.PENDING_APPROVAL), eq(RoleName.ROLE_COURIER), eq("rider"), any(Pageable.class)))
                .thenReturn(page);

        PageResponse<AdminUserSummaryResponse> response = adminService.getUsers(
                UserStatus.PENDING_APPROVAL, RoleName.ROLE_COURIER, "rider", PageRequest.of(0, 10)
        );

        assertNotNull(response);
        assertEquals(1, response.content().size());
        assertEquals("test@rider.lk", response.content().get(0).email());
        assertEquals(UserStatus.PENDING_APPROVAL, response.content().get(0).status());
    }

    @Test
    @DisplayName("getUserDetails returns polymorphic courier details when user is a courier")
    void getUserDetails_Courier_Success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("saman@rider.lk")
                .fullName("Saman Kumara")
                .role(courierRole)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        Courier courier = Courier.builder()
                .id(UUID.randomUUID())
                .user(user)
                .vehicleType(VehicleType.MOTORCYCLE)
                .licensePlate("WP-BBI-1234")
                .isAvailable(true)
                .activeDeliveryCount(0)
                .assignedZoneId(1)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));

        AdminUserDetailResponse response = adminService.getUserDetails(userId);

        assertNotNull(response);
        assertEquals(userId, response.id());
        assertNotNull(response.courierProfile());
        assertEquals("WP-BBI-1234", response.courierProfile().licensePlate());
        assertNull(response.merchantProfile());
    }

    @Test
    @DisplayName("getUserDetails throws ResourceNotFoundException when user does not exist")
    void getUserDetails_NotFound_ThrowsException() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> adminService.getUserDetails(userId));
    }

    @Test
    @DisplayName("approveUser transitions PENDING_APPROVAL to ACTIVE and clears statusReason")
    void approveUser_PendingMerchant_Success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("store@daraz.lk")
                .role(merchantRole)
                .status(UserStatus.PENDING_APPROVAL)
                .statusReason("Awaiting review")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDecisionResponse response = adminService.approveUser(userId);

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, response.status());
        assertNull(response.statusReason());
        assertEquals(userId, response.userId());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("approveUser on suspended courier restores courier availability to true")
    void approveUser_SuspendedCourier_RestoresAvailability() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("rider@swiftdrop.lk")
                .role(courierRole)
                .status(UserStatus.SUSPENDED)
                .statusReason("Previous suspension")
                .build();

        Courier courier = Courier.builder()
                .id(UUID.randomUUID())
                .user(user)
                .isAvailable(false)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(courierRepository.findByUserId(userId)).thenReturn(Optional.of(courier));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDecisionResponse response = adminService.approveUser(userId);

        assertNotNull(response);
        assertEquals(UserStatus.ACTIVE, response.status());
        assertTrue(courier.getIsAvailable());
        verify(courierRepository, times(1)).save(courier);
    }

    @Test
    @DisplayName("approveUser throws IllegalStateException when user is already ACTIVE")
    void approveUser_AlreadyActive_ThrowsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .role(merchantRole)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class, () -> adminService.approveUser(userId));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejectUser transitions PENDING_APPROVAL to REJECTED with reason")
    void rejectUser_Success() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("bad@applicant.lk")
                .role(courierRole)
                .status(UserStatus.PENDING_APPROVAL)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDecisionRequest request = new AdminDecisionRequest("Invalid driving license");
        AdminDecisionResponse response = adminService.rejectUser(userId, request);

        assertNotNull(response);
        assertEquals(UserStatus.REJECTED, response.status());
        assertEquals("Invalid driving license", response.statusReason());
        verify(userRepository, times(1)).save(user);
    }

    @Test
    @DisplayName("rejectUser throws IllegalStateException when user is not PENDING_APPROVAL")
    void rejectUser_NotPending_ThrowsException() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        AdminDecisionRequest request = new AdminDecisionRequest("Invalid document");
        assertThrows(IllegalStateException.class, () -> adminService.rejectUser(userId, request));
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("suspendUser sets courier unavailable and revokes refresh tokens")
    void suspendUser_Courier_Success() {
        UUID adminId = UUID.randomUUID();
        UUID targetCourierId = UUID.randomUUID();

        User courierUser = User.builder()
                .id(targetCourierId)
                .email("violator@rider.lk")
                .role(courierRole)
                .status(UserStatus.ACTIVE)
                .build();

        Courier courier = Courier.builder()
                .id(UUID.randomUUID())
                .user(courierUser)
                .isAvailable(true)
                .build();

        when(userRepository.findById(targetCourierId)).thenReturn(Optional.of(courierUser));
        when(courierRepository.findByUserId(targetCourierId)).thenReturn(Optional.of(courier));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminDecisionRequest request = new AdminDecisionRequest("Damaged packages repeatedly");
        AdminDecisionResponse response = adminService.suspendUser(adminId, targetCourierId, request);

        assertNotNull(response);
        assertEquals(UserStatus.SUSPENDED, response.status());
        assertEquals("Damaged packages repeatedly", response.statusReason());
        assertFalse(courier.getIsAvailable());
        verify(courierRepository, times(1)).save(courier);
        verify(refreshTokenService, times(1)).revokeAllByUserId(targetCourierId);
    }

    @Test
    @DisplayName("suspendUser throws IllegalArgumentException when admin attempts self-suspension")
    void suspendUser_SelfSuspension_ThrowsException() {
        UUID adminId = UUID.randomUUID();
        AdminDecisionRequest request = new AdminDecisionRequest("Accidental self-suspend");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.suspendUser(adminId, adminId, request)
        );
        assertTrue(ex.getMessage().contains("own account"));
    }

    @Test
    @DisplayName("suspendUser throws IllegalArgumentException when target is an administrator")
    void suspendUser_TargetIsAdmin_ThrowsException() {
        UUID adminId = UUID.randomUUID();
        UUID targetAdminId = UUID.randomUUID();

        User adminUser = User.builder()
                .id(targetAdminId)
                .role(adminRole)
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findById(targetAdminId)).thenReturn(Optional.of(adminUser));
        AdminDecisionRequest request = new AdminDecisionRequest("Dispute");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> adminService.suspendUser(adminId, targetAdminId, request)
        );
        assertTrue(ex.getMessage().contains("administrator account"));
    }
}

