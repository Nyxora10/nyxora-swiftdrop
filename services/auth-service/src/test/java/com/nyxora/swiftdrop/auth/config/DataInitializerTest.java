package com.nyxora.swiftdrop.auth.config;

import com.nyxora.swiftdrop.auth.entity.Courier;
import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.repository.CourierRepository;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DataInitializer Unit Tests")
class DataInitializerTest {

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CourierRepository courierRepository;

    @Mock
    private MerchantRepository merchantRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        environment = new MockEnvironment();
        when(passwordEncoder.encode(anyString())).thenAnswer(i -> "hashed:" + i.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        for (RoleName name : RoleName.values()) {
            when(roleRepository.findByName(name)).thenReturn(Optional.of(Role.builder().name(name).build()));
        }
    }

    private DataInitializer initializer(boolean demoData, String adminEmail, String adminPassword) {
        DataInitializer init = new DataInitializer(roleRepository, userRepository, courierRepository,
                merchantRepository, passwordEncoder, environment);
        ReflectionTestUtils.setField(init, "demoDataEnabled", demoData);
        ReflectionTestUtils.setField(init, "bootstrapAdminEmail", adminEmail);
        ReflectionTestUtils.setField(init, "bootstrapAdminPassword", adminPassword);
        return init;
    }

    @Test
    @DisplayName("demo seeding on an empty database creates roles, all demo users, the courier and the OAuth merchant")
    void run_demoDataOnEmptyDatabase_seedsEverything() {
        when(roleRepository.existsByName(any())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);

        initializer(true, "", "").run();

        verify(roleRepository, times(RoleName.values().length)).save(any(Role.class));
        ArgumentCaptor<User> users = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(4)).save(users.capture());
        assertEquals(List.of("admin@swiftdrop.lk", "customer.kasun@swiftdrop.lk",
                        "courier.kamal@swiftdrop.lk", DataInitializer.DEFAULT_MERCHANT_EMAIL),
                users.getAllValues().stream().map(User::getEmail).toList());
        verify(courierRepository).save(any(Courier.class));

        ArgumentCaptor<Merchant> merchant = ArgumentCaptor.forClass(Merchant.class);
        verify(merchantRepository).save(merchant.capture());
        assertEquals(DataInitializer.DEFAULT_MERCHANT_CLIENT_ID, merchant.getValue().getOauthClientId());
        assertEquals("hashed:" + DataInitializer.DEFAULT_MERCHANT_CLIENT_SECRET,
                merchant.getValue().getOauthClientSecretHash());
        assertEquals(DataInitializer.DEFAULT_MERCHANT_WEBHOOK_SECRET, merchant.getValue().getWebhookSecret());
    }

    @Test
    @DisplayName("demo seeding backfills OAuth credentials for an existing demo merchant that has none")
    void run_existingMerchantWithoutCredentials_backfillsThem() {
        User merchantUser = User.builder().id(UUID.randomUUID()).email(DataInitializer.DEFAULT_MERCHANT_EMAIL).build();
        Merchant legacy = Merchant.builder().id(UUID.randomUUID()).user(merchantUser).build();
        when(roleRepository.existsByName(any())).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        when(userRepository.findByEmail(DataInitializer.DEFAULT_MERCHANT_EMAIL)).thenReturn(Optional.of(merchantUser));
        when(merchantRepository.findByUserId(merchantUser.getId())).thenReturn(Optional.of(legacy));

        initializer(true, null, null).run();

        verify(roleRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(courierRepository, never()).save(any());
        verify(merchantRepository).save(legacy);
        assertEquals(DataInitializer.DEFAULT_MERCHANT_CLIENT_ID, legacy.getOauthClientId());
        assertEquals("hashed:" + DataInitializer.DEFAULT_MERCHANT_CLIENT_SECRET, legacy.getOauthClientSecretHash());
        assertEquals(DataInitializer.DEFAULT_MERCHANT_WEBHOOK_SECRET, legacy.getWebhookSecret());
    }

    @Test
    @DisplayName("demo seeding leaves an existing demo merchant's credentials untouched")
    void run_existingMerchantWithCredentials_isNotChanged() {
        User merchantUser = User.builder().id(UUID.randomUUID()).email(DataInitializer.DEFAULT_MERCHANT_EMAIL).build();
        Merchant current = Merchant.builder().id(UUID.randomUUID()).user(merchantUser)
                .oauthClientId("custom_client").oauthClientSecretHash("existing-hash").build();
        when(roleRepository.existsByName(any())).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        when(userRepository.findByEmail(DataInitializer.DEFAULT_MERCHANT_EMAIL)).thenReturn(Optional.of(merchantUser));
        when(merchantRepository.findByUserId(merchantUser.getId())).thenReturn(Optional.of(current));

        initializer(true, null, null).run();

        verify(merchantRepository, never()).save(any());
        assertEquals("custom_client", current.getOauthClientId());
        assertEquals("existing-hash", current.getOauthClientSecretHash());
    }

    @Test
    @DisplayName("the prod profile never seeds demo accounts, even when the demo flag is set")
    void run_prodProfileWithDemoFlag_seedsRolesOnly() {
        environment.setActiveProfiles("prod");
        when(roleRepository.existsByName(any())).thenReturn(true);

        initializer(true, "", "").run();

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
        verifyNoInteractions(merchantRepository, courierRepository);
    }

    @Test
    @DisplayName("the prod profile without the demo flag also seeds roles only")
    void run_prodProfileWithoutDemoFlag_seedsRolesOnly() {
        environment.setActiveProfiles("prod");
        when(roleRepository.existsByName(any())).thenReturn(true);

        initializer(false, null, null).run();

        verify(userRepository, never()).save(any());
        verifyNoInteractions(merchantRepository, courierRepository);
    }

    @Test
    @DisplayName("without demo data, a bootstrap admin is created from the configured email and password")
    void run_bootstrapAdminConfigured_createsAdmin() {
        when(roleRepository.existsByName(any())).thenReturn(true);
        when(userRepository.existsByEmail("ops@swiftdrop.lk")).thenReturn(false);

        initializer(false, "  ops@swiftdrop.lk  ", "Str0ng!Pass").run();

        ArgumentCaptor<User> admin = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(admin.capture());
        assertEquals("ops@swiftdrop.lk", admin.getValue().getEmail());
        assertEquals("hashed:Str0ng!Pass", admin.getValue().getPasswordHash());
        assertEquals(RoleName.ROLE_ADMIN, admin.getValue().getRole().getName());
        verifyNoInteractions(merchantRepository, courierRepository);
    }

    @Test
    @DisplayName("a bootstrap admin that already exists is not created again")
    void run_bootstrapAdminAlreadyExists_skipsCreation() {
        when(roleRepository.existsByName(any())).thenReturn(true);
        when(userRepository.existsByEmail("ops@swiftdrop.lk")).thenReturn(true);

        initializer(false, "ops@swiftdrop.lk", "Str0ng!Pass").run();

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("without demo data and with only a bootstrap email, no admin is created")
    void run_bootstrapPasswordMissing_seedsRolesOnly() {
        when(roleRepository.existsByName(any())).thenReturn(true);

        initializer(false, "ops@swiftdrop.lk", " ").run();

        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("seeding fails fast when the merchant role is missing")
    void run_merchantRoleMissing_throwsIllegalState() {
        when(roleRepository.existsByName(any())).thenReturn(true);
        when(userRepository.existsByEmail(anyString())).thenReturn(true);
        when(userRepository.existsByEmail(DataInitializer.DEFAULT_MERCHANT_EMAIL)).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_MERCHANT)).thenReturn(Optional.empty());

        DataInitializer init = initializer(true, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, init::run);
        assertTrue(ex.getMessage().contains("ROLE_MERCHANT"));
    }
}
