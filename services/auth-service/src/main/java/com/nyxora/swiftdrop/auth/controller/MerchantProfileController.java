package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.request.MerchantWebhookUpdateRequest;
import com.nyxora.swiftdrop.auth.dto.response.MerchantProfileResponse;
import com.nyxora.swiftdrop.auth.dto.response.RotateCredentialsResponse;
import com.nyxora.swiftdrop.auth.security.UserPrincipal;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/merchant")
@RequiredArgsConstructor
@Tag(name = "Merchant Self-Service API", description = "Merchant portal credentials and webhook configuration management")
@SecurityRequirement(name = "BearerAuth")
public class MerchantProfileController {

    private final MerchantAuthService merchantAuthService;

    @GetMapping("/profile")
    @PreAuthorize("hasRole('ROLE_MERCHANT')")
    @Operation(summary = "Get Merchant Profile & Config", description = "Retrieves business details, OAuth client ID, and masked webhook secret for authenticated merchant")
    @ApiResponse(responseCode = "200", description = "Merchant profile returned successfully")
    @ApiResponse(responseCode = "401", description = "Unauthorized")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_MERCHANT")
    public ResponseEntity<MerchantProfileResponse> getProfile(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = resolveUserId(principal);
        log.debug("[MerchantProfileController] Profile requested by user: {}", userId);
        return ResponseEntity.ok(merchantAuthService.getMerchantProfile(userId));
    }

    @PutMapping("/webhook")
    @PreAuthorize("hasRole('ROLE_MERCHANT')")
    @Operation(summary = "Update Webhook Configuration", description = "Updates outbound webhook endpoint URL and optionally regenerates the shared HMAC secret")
    @ApiResponse(responseCode = "200", description = "Webhook configuration updated successfully")
    @ApiResponse(responseCode = "400", description = "Invalid URL format")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_MERCHANT")
    public ResponseEntity<MerchantProfileResponse> updateWebhook(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody MerchantWebhookUpdateRequest request
    ) {
        UUID userId = resolveUserId(principal);
        log.info("[MerchantProfileController] Updating webhook for merchant user: {}", userId);
        return ResponseEntity.ok(merchantAuthService.updateWebhook(userId, request));
    }

    @PostMapping("/credentials/rotate")
    @PreAuthorize("hasRole('ROLE_MERCHANT')")
    @Operation(summary = "Rotate OAuth 2.0 Client Secret", description = "Generates and saves a new hashed secret, returning the plaintext secret once")
    @ApiResponse(responseCode = "200", description = "Client secret rotated successfully")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_MERCHANT")
    public ResponseEntity<RotateCredentialsResponse> rotateCredentials(@AuthenticationPrincipal UserPrincipal principal) {
        UUID userId = resolveUserId(principal);
        log.info("[MerchantProfileController] Rotating OAuth client secret for merchant user: {}", userId);
        return ResponseEntity.ok(merchantAuthService.rotateCredentials(userId));
    }

    private UUID resolveUserId(UserPrincipal principal) {
        if (principal != null) {
            return principal.getId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal p) {
            return p.getId();
        }
        throw new BadCredentialsException("Authenticated merchant principal not found.");
    }
}
