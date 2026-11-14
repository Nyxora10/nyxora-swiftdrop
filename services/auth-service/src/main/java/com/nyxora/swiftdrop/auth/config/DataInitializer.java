package com.nyxora.swiftdrop.auth.config;

import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.entity.enums.VehicleType;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Startup Data Initializer that primes the auth_db with:
 * 1. Default RBAC Roles (ROLE_CUSTOMER, ROLE_MERCHANT, ROLE_COURIER, ROLE_ADMIN)
 * 2. Default Platform Administrator Account
 * 3. Default Demo Customer Account
 * 4. Default Demo Courier Account (Task 2.5)
 * 5. Default Demo Merchant Account
 *
 * <p>Items 2-5 use fixed, publicly known credentials and are created only when
 * {@code app.seed.demo-data=true} (env {@code SEED_DEMO_DATA}) and the {@code prod} profile is
 * not active. Without demo data, an admin can be bootstrapped from
 * {@code ADMIN_BOOTSTRAP_EMAIL} / {@code ADMIN_BOOTSTRAP_PASSWORD}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CourierRepository courierRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    private static final String DEFAULT_ADMIN_EMAIL = "admin@swiftdrop.lk";
    private static final String DEFAULT_ADMIN_PASSWORD = "Admin@SwiftDrop2026!";

    private static final String DEFAULT_CUSTOMER_EMAIL = "customer.kasun@swiftdrop.lk";
    private static final String DEFAULT_CUSTOMER_PASSWORD = "Customer@123";

    private static final String DEFAULT_COURIER_EMAIL = "courier.kamal@swiftdrop.lk";
    private static final String DEFAULT_COURIER_PASSWORD = "Courier@123";

    public static final String DEFAULT_MERCHANT_EMAIL = "merchant.daraz@swiftdrop.lk";
    public static final String DEFAULT_MERCHANT_PASSWORD = "Merchant@123";
    // Member 2 (Task 2.3): demo OAuth 2.0 client_credentials for headless M2M order creation
    public static final String DEFAULT_MERCHANT_CLIENT_ID = "mch_daraz_client_01";
    public static final String DEFAULT_MERCHANT_CLIENT_SECRET = "sec_daraz_secret_2026";
    public static final String DEFAULT_MERCHANT_WEBHOOK_SECRET = "whsec_daraz_hmac_secret_2026";

    @Value("${app.seed.demo-data:false}")
    private boolean demoDataEnabled;

    @Value("${app.seed.admin-email:}")
    private String bootstrapAdminEmail;

    @Value("${app.seed.admin-password:}")
    private String bootstrapAdminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[DataInitializer] Verifying database seeding for auth_db...");
        seedRoles();

        if (isDemoSeedingAllowed()) {
            log.warn("[DataInitializer] Seeding DEMO accounts with well-known credentials. "
                    + "Set SEED_DEMO_DATA=false or use the 'prod' profile outside local development.");
            seedDefaultAdmin(DEFAULT_ADMIN_EMAIL, DEFAULT_ADMIN_PASSWORD);
            seedDefaultCustomer();
            seedDefaultCourier();
            seedDefaultMerchant();
        } else if (StringUtils.hasText(bootstrapAdminEmail) && StringUtils.hasText(bootstrapAdminPassword)) {
            seedDefaultAdmin(bootstrapAdminEmail.trim(), bootstrapAdminPassword);
        } else {
            log.info("[DataInitializer] Demo seeding disabled and no ADMIN_BOOTSTRAP_EMAIL/PASSWORD set; only roles were seeded.");
        }
        log.info("[DataInitializer] Database verification complete.");
    }

    /** Demo accounts are never created under the 'prod' profile, even if the flag is set. */
    private boolean isDemoSeedingAllowed() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            if (demoDataEnabled) {
                log.warn("[DataInitializer] app.seed.demo-data ignored: demo accounts are never seeded in the 'prod' profile.");
            }
            return false;
        }
        return demoDataEnabled;
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

    private void seedDefaultAdmin(String adminEmail, String adminPassword) {
        if (!userRepository.existsByEmail(adminEmail)) {
            Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN)
                    .orElseThrow(() -> new IllegalStateException("ROLE_ADMIN not found during admin seeding"));

            User admin = User.builder()
                    .role(adminRole)
                    .email(adminEmail)
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .fullName("SwiftDrop Administrator")
                    .phoneNumber("+94770000000")
                    .status(UserStatus.ACTIVE)
                    .authProvider(AuthProvider.LOCAL)
                    .build();

            userRepository.save(admin);
            log.info("[DataInitializer] Successfully created admin account: {}", adminEmail);
        } else {
            log.info("[DataInitializer] Default admin account already exists.");
        }
    }

    private void seedDefaultCustomer() {
        if (!userRepository.existsByEmail(DEFAULT_CUSTOMER_EMAIL)) {
            Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
                    .orElseThrow(() -> new IllegalStateException("ROLE_CUSTOMER not found during customer seeding"));

            User customer = User.builder()
                    .role(customerRole)
                    .email(DEFAULT_CUSTOMER_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEFAULT_CUSTOMER_PASSWORD))
                    .fullName("Kasun Perera")
                    .phoneNumber("+94771234567")
                    .status(UserStatus.ACTIVE)
                    .authProvider(AuthProvider.LOCAL)
                    .build();

            userRepository.save(customer);
            log.info("[DataInitializer] Successfully created default customer account: {}", DEFAULT_CUSTOMER_EMAIL);
        } else {
            log.info("[DataInitializer] Default customer account already exists.");
        }
    }

    private void seedDefaultCourier() {
        if (!userRepository.existsByEmail(DEFAULT_COURIER_EMAIL)) {
            Role courierRole = roleRepository.findByName(RoleName.ROLE_COURIER)
                    .orElseThrow(() -> new IllegalStateException("ROLE_COURIER not found during courier seeding"));

            User courierUser = User.builder()
                    .role(courierRole)
                    .email(DEFAULT_COURIER_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEFAULT_COURIER_PASSWORD))
                    .fullName("Kamal Perera")
                    .phoneNumber("+94777654321")
                    .status(UserStatus.ACTIVE)
                    .authProvider(AuthProvider.LOCAL)
                    .build();

            User savedUser = userRepository.save(courierUser);

            Courier courier = Courier.builder()
                    .user(savedUser)
                    .vehicleType(VehicleType.MOTORCYCLE)
                    .licensePlate("WP-BAM-1234")
                    .assignedZoneId(1)
                    .isAvailable(true)
                    .activeDeliveryCount(0)
                    .build();

            courierRepository.save(courier);
            log.info("[DataInitializer] Successfully created default courier account: {}", DEFAULT_COURIER_EMAIL);
        } else {
            log.info("[DataInitializer] Default courier account already exists.");
        }
    }

    private void seedDefaultMerchant() {
        if (!userRepository.existsByEmail(DEFAULT_MERCHANT_EMAIL)) {
            Role merchantRole = roleRepository.findByName(RoleName.ROLE_MERCHANT)
                    .orElseThrow(() -> new IllegalStateException("ROLE_MERCHANT not found during merchant seeding"));

            User merchantUser = User.builder()
                    .role(merchantRole)
                    .email(DEFAULT_MERCHANT_EMAIL)
                    .passwordHash(passwordEncoder.encode(DEFAULT_MERCHANT_PASSWORD))
                    .fullName("Daraz Logistics Hub")
                    .phoneNumber("+94112345678")
                    .status(UserStatus.ACTIVE)
                    .authProvider(AuthProvider.LOCAL)
                    .build();

            User savedUser = userRepository.save(merchantUser);

            Merchant merchant = Merchant.builder()
                    .user(savedUser)
                    .businessName("Daraz Sri Lanka PVT LTD")
                    .registrationNumber("PV-123456")
                    .webhookUrl("http://localhost:8083/api/v1/webhooks/demo-receiver")
                    .webhookSecret(DEFAULT_MERCHANT_WEBHOOK_SECRET)
                    .oauthClientId(DEFAULT_MERCHANT_CLIENT_ID)
                    .oauthClientSecretHash(passwordEncoder.encode(DEFAULT_MERCHANT_CLIENT_SECRET))
                    .build();

            merchantRepository.save(merchant);
            log.info("[DataInitializer] Successfully created default merchant account: {} (client_id: {})",
                    DEFAULT_MERCHANT_EMAIL, DEFAULT_MERCHANT_CLIENT_ID);
        } else {
            backfillDemoMerchantCredentials();
            log.info("[DataInitializer] Default merchant account already exists.");
        }
    }

    /**
     * Databases seeded by an earlier build may hold the demo merchant without OAuth
     * client credentials, which breaks the client_credentials grant. Fill them in once.
     */
    private void backfillDemoMerchantCredentials() {
        userRepository.findByEmail(DEFAULT_MERCHANT_EMAIL)
                .flatMap(user -> merchantRepository.findByUserId(user.getId()))
                .filter(merchant -> merchant.getOauthClientSecretHash() == null)
                .ifPresent(merchant -> {
                    merchant.setOauthClientId(DEFAULT_MERCHANT_CLIENT_ID);
                    merchant.setOauthClientSecretHash(passwordEncoder.encode(DEFAULT_MERCHANT_CLIENT_SECRET));
                    merchant.setWebhookSecret(DEFAULT_MERCHANT_WEBHOOK_SECRET);
                    merchantRepository.save(merchant);
                    log.info("[DataInitializer] Backfilled OAuth client credentials for demo merchant (client_id: {})",
                            DEFAULT_MERCHANT_CLIENT_ID);
                });
    }
}
