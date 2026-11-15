package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.response.MerchantWebhookConfigResponse;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/internal/merchants")
@RequiredArgsConstructor
@Tag(name = "Internal Merchant API", description = "Private service-to-service endpoints for cross-microservice data resolution")
public class InternalMerchantController {

    private final MerchantAuthService merchantAuthService;

    @GetMapping("/{merchantId}/webhook-config")
    @Operation(
            summary = "Internal Webhook Config Lookup",
            description = "Enables notification-webhook-service to retrieve target webhook URL and HMAC signing secret without cross-database access"
    )
    @ApiResponse(responseCode = "200", description = "Webhook config resolved successfully")
    @ApiResponse(responseCode = "404", description = "Merchant not found")
    public ResponseEntity<MerchantWebhookConfigResponse> getWebhookConfig(@PathVariable UUID merchantId) {
        log.debug("[InternalMerchantController] Webhook config requested for merchant: {}", merchantId);
        return ResponseEntity.ok(merchantAuthService.getInternalWebhookConfig(merchantId));
    }
}
