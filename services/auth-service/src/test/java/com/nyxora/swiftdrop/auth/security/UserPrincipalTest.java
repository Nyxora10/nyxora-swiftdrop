package com.nyxora.swiftdrop.auth.security;

import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UserPrincipal Unit Tests")
class UserPrincipalTest {

    private User createSampleUser(UserStatus status) {
        Role role = Role.builder().id(1).name(RoleName.ROLE_COURIER).description("Courier").build();
        return User.builder()
                .id(UUID.randomUUID())
                .email("driver@swiftdrop.lk")
                .fullName("Driver Kamal")
                .phoneNumber("+94770001122")
                .passwordHash("password-hash")
                .role(role)
                .status(status)
                .build();
    }

    @Test
    @DisplayName("UserPrincipal create without courier/merchant enrichment")
    void create_WithoutEnrichment() {
        User user = createSampleUser(UserStatus.ACTIVE);
        UserPrincipal principal = UserPrincipal.create(user);

        assertEquals(user.getId(), principal.getId());
        assertEquals("driver@swiftdrop.lk", principal.getUsername());
        assertEquals("driver@swiftdrop.lk", principal.getEmail());
        assertEquals("password-hash", principal.getPassword());
        assertEquals("Driver Kamal", principal.getFullName());
        assertEquals("+94770001122", principal.getPhoneNumber());
        assertTrue(principal.isActive());
        assertTrue(principal.isEnabled());
        assertTrue(principal.isAccountNonLocked());
        assertTrue(principal.isAccountNonExpired());
        assertTrue(principal.isCredentialsNonExpired());
        assertNull(principal.getCourierId());
        assertNull(principal.getMerchantId());
        assertEquals(1, principal.getAuthorities().size());
        assertEquals("ROLE_COURIER", principal.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("UserPrincipal create with courier and merchant enrichment")
    void create_WithEnrichment() {
        User user = createSampleUser(UserStatus.ACTIVE);
        UUID courierId = UUID.randomUUID();
        UUID merchantId = UUID.randomUUID();
        UserPrincipal principal = UserPrincipal.create(user, courierId, merchantId);

        assertEquals(courierId, principal.getCourierId());
        assertEquals(merchantId, principal.getMerchantId());
    }

    @Test
    @DisplayName("UserPrincipal inactive status sets isEnabled and isAccountNonLocked to false")
    void isEnabled_InactiveUser_ReturnsFalse() {
        User user = createSampleUser(UserStatus.PENDING_APPROVAL);
        UserPrincipal principal = UserPrincipal.create(user);

        assertFalse(principal.isActive());
        assertFalse(principal.isEnabled());
        assertFalse(principal.isAccountNonLocked());
    }

    @Test
    @DisplayName("UserPrincipal equals and hashCode based on ID")
    void equalsAndHashCode() {
        UUID id = UUID.randomUUID();
        UserPrincipal p1 = UserPrincipal.builder().id(id).email("a@a.com").build();
        UserPrincipal p2 = UserPrincipal.builder().id(id).email("b@b.com").build();
        UserPrincipal p3 = UserPrincipal.builder().id(UUID.randomUUID()).email("a@a.com").build();

        assertEquals(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
        assertNotEquals(p1, p3);
        assertNotEquals(p1, null);
    }
}
