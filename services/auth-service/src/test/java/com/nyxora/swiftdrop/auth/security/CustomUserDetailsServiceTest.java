package com.nyxora.swiftdrop.auth.security;

import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService Unit Tests")
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService = new CustomUserDetailsService(userRepository);
    }

    private User createSampleUser(UUID id, String email) {
        Role role = Role.builder().id(1).name(RoleName.ROLE_CUSTOMER).description("Customer").build();
        return User.builder()
                .id(id)
                .email(email)
                .fullName("Sample User")
                .phoneNumber("+94771112233")
                .passwordHash("hashed")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("loadUserByUsername returns UserDetails when user exists")
    void loadUserByUsername_UserExists_ReturnsUserDetails() {
        UUID id = UUID.randomUUID();
        String email = "existing@swiftdrop.lk";
        User user = createSampleUser(id, email);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername(email);
        assertNotNull(details);
        assertEquals(email, details.getUsername());
        assertTrue(details.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException when user does not exist")
    void loadUserByUsername_UserNotFound_ThrowsException() {
        String email = "unknown@swiftdrop.lk";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserByUsername(email));
    }

    @Test
    @DisplayName("loadUserById returns UserDetails when user exists")
    void loadUserById_UserExists_ReturnsUserDetails() {
        UUID id = UUID.randomUUID();
        User user = createSampleUser(id, "byid@swiftdrop.lk");

        when(userRepository.findById(id)).thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserById(id);
        assertNotNull(details);
        assertEquals("byid@swiftdrop.lk", details.getUsername());
    }

    @Test
    @DisplayName("loadUserById throws UsernameNotFoundException when user does not exist")
    void loadUserById_UserNotFound_ThrowsException() {
        UUID id = UUID.randomUUID();
        when(userRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userDetailsService.loadUserById(id));
    }
}
