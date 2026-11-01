package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.CustomerRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.GoogleAuthRequest;
import com.nyxora.swiftdrop.auth.dto.request.LoginRequest;
import com.nyxora.swiftdrop.auth.dto.response.AuthResponse;
import com.nyxora.swiftdrop.auth.exception.AccountRejectedException;
import com.nyxora.swiftdrop.auth.exception.AccountSuspendedException;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.AuthService;
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

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("POST /register/customer with valid payload returns HTTP 201 Created")
    void registerCustomer_ValidPayload_Returns201() throws Exception {
        CustomerRegisterRequest request = new CustomerRegisterRequest(
                "Kamal Silva", "kamal@swiftdrop.lk", "ValidPassword123!", "+94771234567"
        );

        AuthResponse authResponse = AuthResponse.of(
                "access_token_123", "refresh_token_123", 900000L,
                UUID.randomUUID(), "kamal@swiftdrop.lk", "ROLE_CUSTOMER", "Kamal Silva"
        );

        when(authService.registerCustomer(any(CustomerRegisterRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access_token_123"))
                .andExpect(jsonPath("$.email").value("kamal@swiftdrop.lk"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("POST /register/customer with invalid email returns HTTP 400 Bad Request with field error")
    void registerCustomer_InvalidEmail_Returns400() throws Exception {
        CustomerRegisterRequest request = new CustomerRegisterRequest(
                "Kamal Silva", "not-a-valid-email", "ValidPassword123!", "+94771234567"
        );

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /register/customer with short password (<8 chars) returns HTTP 400 Bad Request")
    void registerCustomer_ShortPassword_Returns400() throws Exception {
        CustomerRegisterRequest request = new CustomerRegisterRequest(
                "Kamal Silva", "kamal@swiftdrop.lk", "short", "+94771234567"
        );

        mockMvc.perform(post("/api/v1/auth/register/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("POST /login with valid payload returns HTTP 200 OK")
    void login_ValidPayload_Returns200() throws Exception {
        LoginRequest request = new LoginRequest("kamal@swiftdrop.lk", "ValidPassword123!");

        AuthResponse authResponse = AuthResponse.of(
                "access_token_456", "refresh_token_456", 900000L,
                UUID.randomUUID(), "kamal@swiftdrop.lk", "ROLE_CUSTOMER", "Kamal Silva"
        );

        when(authService.login(any(LoginRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access_token_456"))
                .andExpect(jsonPath("$.email").value("kamal@swiftdrop.lk"));
    }

    @Test
    @DisplayName("POST /login with blank email returns HTTP 400 Bad Request")
    void login_BlankEmail_Returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "ValidPassword123!");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("POST /google with valid token returns HTTP 200 OK and tokens")
    void googleLogin_Success_Returns200() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-google-id-token");

        AuthResponse authResponse = AuthResponse.of(
                "google_access_token_123", "google_refresh_token_123", 900000L,
                UUID.randomUUID(), "googleuser@gmail.com", "ROLE_CUSTOMER", "Google User"
        );

        when(authService.authenticateGoogle(any(GoogleAuthRequest.class))).thenReturn(authResponse);

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("google_access_token_123"))
                .andExpect(jsonPath("$.refreshToken").value("google_refresh_token_123"))
                .andExpect(jsonPath("$.email").value("googleuser@gmail.com"))
                .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"))
                .andExpect(jsonPath("$.fullName").value("Google User"));
    }

    @Test
    @DisplayName("POST /google with blank token returns HTTP 400 Bad Request with field error")
    void googleLogin_BlankToken_Returns400() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("");

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.idToken").exists());
    }

    @Test
    @DisplayName("POST /google with invalid/expired token returns HTTP 401 Unauthorized")
    void googleLogin_InvalidToken_Returns401() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("expired-token");

        when(authService.authenticateGoogle(any(GoogleAuthRequest.class)))
                .thenThrow(new BadCredentialsException("Google ID token is invalid or expired."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication Failed"))
                .andExpect(jsonPath("$.detail").value("Google ID token is invalid or expired."));
    }

    @Test
    @DisplayName("POST /google with suspended account returns HTTP 403 Forbidden")
    void googleLogin_SuspendedAccount_Returns403() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token-suspended-user");

        when(authService.authenticateGoogle(any(GoogleAuthRequest.class)))
                .thenThrow(new AccountSuspendedException("Your account has been suspended by an administrator. Please contact support."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account Suspended"))
                .andExpect(jsonPath("$.detail").value("Your account has been suspended by an administrator. Please contact support."));
    }

    @Test
    @DisplayName("POST /google with rejected account application returns HTTP 403 Forbidden")
    void googleLogin_RejectedAccount_Returns403() throws Exception {
        GoogleAuthRequest request = new GoogleAuthRequest("valid-token-rejected-user");

        when(authService.authenticateGoogle(any(GoogleAuthRequest.class)))
                .thenThrow(new AccountRejectedException("Your account application was rejected. Reason: License expired."));

        mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Account Application Rejected"))
                .andExpect(jsonPath("$.detail").value("Your account application was rejected. Reason: License expired."));
    }
}
