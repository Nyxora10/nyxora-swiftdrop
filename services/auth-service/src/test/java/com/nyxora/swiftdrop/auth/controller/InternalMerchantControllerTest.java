package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.response.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.auth.exception.ResourceNotFoundException;
import com.nyxora.swiftdrop.auth.security.CustomUserDetailsService;
import com.nyxora.swiftdrop.auth.security.JwtTokenProvider;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalMerchantController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("InternalMerchantController Tests")
class InternalMerchantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MerchantAuthService merchantAuthService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("GET /internal/merchants/{id}/webhook-config returns 200 with webhook settings")
    void getWebhookConfig_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        MerchantWebhookConfigResponse response = new MerchantWebhookConfigResponse(
                merchantId,
                "https://partner.daraz.lk/webhooks",
                "whsec_test_secret_123",
                true
        );

        when(merchantAuthService.getInternalWebhookConfig(merchantId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/auth/internal/merchants/{merchantId}/webhook-config", merchantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value(merchantId.toString()))
                .andExpect(jsonPath("$.webhookUrl").value("https://partner.daraz.lk/webhooks"))
                .andExpect(jsonPath("$.webhookSecret").value("whsec_test_secret_123"))
                .andExpect(jsonPath("$.isActive").value(true));
    }

    @Test
    @DisplayName("GET /internal/merchants/{id}/webhook-config returns 404 when merchant not found")
    void getWebhookConfig_NotFound_Returns404() throws Exception {
        UUID merchantId = UUID.randomUUID();
        when(merchantAuthService.getInternalWebhookConfig(merchantId))
                .thenThrow(new ResourceNotFoundException("Merchant not found with ID: " + merchantId));

        mockMvc.perform(get("/api/v1/auth/internal/merchants/{merchantId}/webhook-config", merchantId))
                .andExpect(status().isNotFound());
    }
}
