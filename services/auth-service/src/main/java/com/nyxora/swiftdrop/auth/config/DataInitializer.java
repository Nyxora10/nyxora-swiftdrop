package com.nyxora.swiftdrop.auth.config;

import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup Data Initializer that primes the auth_db with:
 * 1. Default RBAC Roles (ROLE_CUSTOMER, ROLE_MERCHANT, ROLE_COURIER, ROLE_ADMIN)
 * 2. Default Platform Administrator Account
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String DEFAULT_ADMIN_EMAIL = "admin@swiftdrop.lk";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@SwiftDrop2026!";

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[DataInitializer] Verifying database seeding for auth_db...");
        seedRoles();
        seedDefaultAdmin();
        log.info("[DataInitializer] Database verification complete.");
    }

    private void seedRoles() {
        createRoleIfNotFound(RoleName.ROLE_CUSTOMER, "Standard retail delivery customer");
        createRoleIfNotFound(RoleName.ROLE_MERCHANT, "E-commerce partner with API and webhook integration");
        createRoleIfNotFound(RoleName.ROLE_COURIER, "Delivery courier and driver executing dispatch runs");
        createRoleIfNotFound(RoleName.ROLE_ADMIN, "Platform administrator and operations dispatcher");
    }

    private void createRoleIfNotFound(RoleName roleName, String description) {
        if (!roleRepository.existsByName(roleName)) {
            Role role = Role.builder()
                    .name(roleName)
                    .description(description)
                    .build();
            roleRepository.save(role);
            log.info("[DataInitializer] Seeded role: {}", roleName);
        }
    }

    private void seedDefaultAdmin() {
        if (!userRepository.existsByEmail(DEFAULT_ADMIN_EMAIL)) {
            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found during admin seeding"));

            User admin = User.builder()
                    .role(adminRole)
                    .email(DEFAULT_ADMIN_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEFAULT_ADMIN_PASSWORD))
                    .fullName("SwiftDrop Administrator")
                    .phoneNumber("+94770000000")
                    .status(UserStatus.ACTIVE)
                    .authProvider(AuthProvider.LOCAL)
                    .build();

            userRepository.save(admin);
            log.info("[DataInitializer] Successfully created default admin account: {}", DEFAULT_ADMIN_EMAIL);
        } else {
            log.info("[DataInitializer] Default admin account already exists.");
        }
    }
}

