package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.MerchantWebhookUpdateRequest;
import com.nyxora.swiftdrop.auth.dto.response.MerchantProfileResponse;
import com.nyxora.swiftdrop.auth.dto.response.RotateCredentialsResponse;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("MerchantProfileController Tests")
class MerchantProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MerchantAuthService merchantAuthService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private UUID userId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        principal = createMockMerchantPrincipal(userId);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserPrincipal createMockMerchantPrincipal(UUID userId) {
        return UserPrincipal.builder()
                .id(userId)
                .email("store@daraz.lk")
                .fullName("Daraz Store")
                .active(true)
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_MERCHANT")))
                .build();
    }

    @Test
    @DisplayName("GET /merchant/profile returns 200 with masked secret")
    void getProfile_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantProfileResponse response = new MerchantProfileResponse(
                merchantId,
                "Daraz Sri Lanka",
                "PV-DARAZ-001",
                "mer_daraz_client_01",
                "https://partner.daraz.lk/webhooks",
                "whsec_****"
        );

        when(merchantAuthService.getMerchantProfile(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/merchant/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessName").value("Daraz Sri Lanka"))
                .andExpect(jsonPath("$.oauthClientId").value("mer_daraz_client_01"))
                .andExpect(jsonPath("$.webhookSecret").value("whsec_****"));
    }

    @Test
    @DisplayName("PUT /merchant/webhook updates webhook and returns 200")
    void updateWebhook_Success() throws Exception {
        MerchantWebhookUpdateRequest request = new MerchantWebhookUpdateRequest("https://partner.daraz.lk/new-webhooks", false);
        MerchantProfileResponse response = new MerchantProfileResponse(
                UUID.randomUUID(),
                "Daraz Sri Lanka",
                "PV-DARAZ-001",
                "mer_daraz_client_01",
                "https://partner.daraz.lk/new-webhooks",
                "whsec_****"
        );

        when(merchantAuthService.updateWebhook(eq(userId), any(MerchantWebhookUpdateRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/auth/merchant/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.webhookUrl").value("https://partner.daraz.lk/new-webhooks"));
    }

    @Test
    @DisplayName("POST /merchant/credentials/rotate rotates secret and returns 200")
    void rotateCredentials_Success() throws Exception {
        RotateCredentialsResponse response = new RotateCredentialsResponse(
                "mer_daraz_client_01",
                "cs_live_new_secret_456",
                "Client secret rotated successfully."
        );

        when(merchantAuthService.rotateCredentials(userId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/merchant/credentials/rotate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.oauthClientId").value("mer_daraz_client_01"))
                .andExpect(jsonPath("$.oauthClientSecret").value("cs_live_new_secret_456"));
    }
}
