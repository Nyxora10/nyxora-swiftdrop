package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.OAuthTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.OAuthTokenResponse;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OAuthTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OAuthTokenController Tests")
class OAuthTokenControllerTest {

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

    @Test
    @DisplayName("POST /oauth/token with application/x-www-form-urlencoded returns 200 and Bearer token")
    void getTokenForm_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        OAuthTokenResponse response = OAuthTokenResponse.of("mocked.jwt.token", 900000L, merchantId);

        when(merchantAuthService.generateM2mToken(any(OAuthTokenRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/oauth/token")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("client_id", "mer_daraz_client_01")
                        .param("client_secret", "sec_daraz_secret_2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(900))
                .andExpect(jsonPath("$.scope").value("merchant:orders"))
                .andExpect(jsonPath("$.merchant_id").value(merchantId.toString()));
    }

    @Test
    @DisplayName("POST /oauth/token with application/json returns 200 and Bearer token")
    void getTokenJson_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        OAuthTokenResponse response = OAuthTokenResponse.of("mocked.jwt.token", 900000L, merchantId);

        when(merchantAuthService.generateM2mToken(any(OAuthTokenRequest.class))).thenReturn(response);

        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "mer_daraz_client_01", "sec_daraz_secret_2026");

        mockMvc.perform(post("/api/v1/auth/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("mocked.jwt.token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.merchant_id").value(merchantId.toString()));
    }

    @Test
    @DisplayName("POST /oauth/token with invalid client credentials returns 401")
    void getToken_InvalidCredentials_Returns401() throws Exception {
        when(merchantAuthService.generateM2mToken(any(OAuthTokenRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid client_id or client_secret."));

        OAuthTokenRequest request = new OAuthTokenRequest("client_credentials", "bad_client", "wrong_secret");

        mockMvc.perform(post("/api/v1/auth/oauth/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
