package com.nyxora.swiftdrop.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.auth.dto.request.CustomerRegisterRequest;
import com.nyxora.swiftdrop.auth.dto.request.LoginRequest;
import com.nyxora.swiftdrop.auth.dto.response.AuthResponse;
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
}

