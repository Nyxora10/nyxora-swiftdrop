package com.nyxora.swiftdrop.auth.security;

import com.nyxora.swiftdrop.auth.entity.Merchant;
import com.nyxora.swiftdrop.auth.entity.Role;
import com.nyxora.swiftdrop.auth.entity.User;
import com.nyxora.swiftdrop.auth.entity.enums.AuthProvider;
import com.nyxora.swiftdrop.auth.entity.enums.RoleName;
import com.nyxora.swiftdrop.auth.entity.enums.UserStatus;
import com.nyxora.swiftdrop.auth.repository.MerchantRepository;
import com.nyxora.swiftdrop.auth.repository.RoleRepository;
import com.nyxora.swiftdrop.auth.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the real Spring Security filter chain (the controller slice tests run with
 * filters disabled) for the merchant-facing and internal auth-service endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Merchant endpoint security (full filter chain)")
class MerchantEndpointSecurityIntegrationTest {

    /** Matches app.internal.api-key in application-test.yml. */
    private static final String INTERNAL_KEY = "test-internal-api-key";
    private static final String WEBHOOK_SECRET = "whsec_integration_test_secret_0001";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MerchantRepository merchantRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private User merchantUser;
    private Merchant merchant;

    @BeforeEach
    void createMerchant() {
        Role merchantRole = roleRepository.findByName(RoleName.ROLE_MERCHANT).orElseThrow();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        merchantUser = userRepository.save(User.builder()
                .role(merchantRole)
                .email("sec-test-" + suffix + "@merchant.lk")
                .passwordHash("not-used")
                .fullName("Security Test Merchant")
                .phoneNumber("+94110000000")
                .status(UserStatus.ACTIVE)
                .authProvider(AuthProvider.LOCAL)
                .build());
        merchant = merchantRepository.save(Merchant.builder()
                .user(merchantUser)
                .businessName("Security Test Store " + suffix)
                .registrationNumber("PV-SEC-" + suffix)
                .webhookUrl("https://merchant.example/webhooks")
                .webhookSecret(WEBHOOK_SECRET)
                .oauthClientId("mch_sec_" + suffix)
                .build());
    }

    private String interactiveToken() {
        return jwtTokenProvider.generateAccessToken(UserPrincipal.create(merchantUser, null, merchant.getId()));
    }

    private String m2mToken() {
        return jwtTokenProvider.generateM2mToken(merchantUser, merchant);
    }

    private String webhookConfigUrl() {
        return "/api/v1/auth/internal/merchants/" + merchant.getId() + "/webhook-config";
    }

    @Nested
    @DisplayName("Internal webhook-config endpoint")
    class InternalEndpoint {

        @Test
        @DisplayName("rejects anonymous requests without returning the secret")
        void anonymous_isRejected() throws Exception {
            mockMvc.perform(get(webhookConfigUrl()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString(WEBHOOK_SECRET))));
        }

        @Test
        @DisplayName("rejects a wrong service key")
        void wrongKey_isRejected() throws Exception {
            mockMvc.perform(get(webhookConfigUrl()).header(InternalApiKeyFilter.HEADER, "guessed-key"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString(WEBHOOK_SECRET))));
        }

        @Test
        @DisplayName("rejects a valid merchant user JWT (users can never act as an internal service)")
        void merchantJwt_isRejected() throws Exception {
            mockMvc.perform(get(webhookConfigUrl()).header("Authorization", "Bearer " + interactiveToken()))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().string(not(containsString(WEBHOOK_SECRET))));
        }

        @Test
        @DisplayName("returns the config to a caller with the correct service key")
        void correctKey_returnsConfig() throws Exception {
            mockMvc.perform(get(webhookConfigUrl()).header(InternalApiKeyFilter.HEADER, INTERNAL_KEY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.merchantId").value(merchant.getId().toString()))
                    .andExpect(jsonPath("$.webhookSecret").value(WEBHOOK_SECRET));
        }
    }

    @Nested
    @DisplayName("M2M vs interactive merchant tokens")
    class TokenTypes {

        @Test
        @DisplayName("M2M token cannot read the merchant profile")
        void m2m_cannotReadProfile() throws Exception {
            mockMvc.perform(get("/api/v1/auth/merchant/profile").header("Authorization", "Bearer " + m2mToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("M2M token cannot change webhook configuration")
        void m2m_cannotUpdateWebhook() throws Exception {
            mockMvc.perform(put("/api/v1/auth/merchant/webhook")
                            .header("Authorization", "Bearer " + m2mToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://attacker.example/hook\",\"regenerateSecret\":true}"))
                    .andExpect(status().isForbidden());

            Merchant reloaded = merchantRepository.findById(merchant.getId()).orElseThrow();
            assertThat(reloaded.getWebhookUrl()).isEqualTo("https://merchant.example/webhooks");
            assertThat(reloaded.getWebhookSecret()).isEqualTo(WEBHOOK_SECRET);
        }

        @Test
        @DisplayName("M2M token cannot rotate OAuth client credentials")
        void m2m_cannotRotateCredentials() throws Exception {
            mockMvc.perform(post("/api/v1/auth/merchant/credentials/rotate").header("Authorization", "Bearer " + m2mToken()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("interactive merchant token can read the profile, which shows only the masked secret")
        void interactive_readsMaskedProfile() throws Exception {
            mockMvc.perform(get("/api/v1/auth/merchant/profile").header("Authorization", "Bearer " + interactiveToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.webhookSecret").value("whsec_****0001"))
                    .andExpect(jsonPath("$.newWebhookSecret").doesNotExist())
                    .andExpect(content().string(not(containsString(WEBHOOK_SECRET))));
        }
    }

    @Nested
    @DisplayName("One-time webhook secret reveal")
    class SecretReveal {

        @Test
        @DisplayName("regeneration returns the new secret once; later profile reads stay masked")
        void regenerate_revealsSecretOnlyOnce() throws Exception {
            String body = mockMvc.perform(put("/api/v1/auth/merchant/webhook")
                            .header("Authorization", "Bearer " + interactiveToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"regenerateSecret\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", containsString("no-store")))
                    .andExpect(jsonPath("$.newWebhookSecret").exists())
                    .andReturn().getResponse().getContentAsString();

            String revealed = JsonPath.read(body, "$.newWebhookSecret");
            String masked = JsonPath.read(body, "$.webhookSecret");
            String stored = merchantRepository.findById(merchant.getId()).orElseThrow().getWebhookSecret();

            assertThat(revealed).isEqualTo(stored).startsWith("whsec_").isNotEqualTo(WEBHOOK_SECRET);
            assertThat(masked).isEqualTo("whsec_****" + stored.substring(stored.length() - 4));

            mockMvc.perform(get("/api/v1/auth/merchant/profile").header("Authorization", "Bearer " + interactiveToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.newWebhookSecret").doesNotExist())
                    .andExpect(content().string(not(containsString(revealed))));
        }

        @Test
        @DisplayName("updating only the URL never returns a secret")
        void urlOnlyUpdate_doesNotRevealSecret() throws Exception {
            mockMvc.perform(put("/api/v1/auth/merchant/webhook")
                            .header("Authorization", "Bearer " + interactiveToken())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"webhookUrl\":\"https://merchant.example/new-hook\",\"regenerateSecret\":false}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.webhookUrl").value("https://merchant.example/new-hook"))
                    .andExpect(jsonPath("$.newWebhookSecret").doesNotExist())
                    .andExpect(content().string(not(containsString(WEBHOOK_SECRET))));
        }
    }
}
