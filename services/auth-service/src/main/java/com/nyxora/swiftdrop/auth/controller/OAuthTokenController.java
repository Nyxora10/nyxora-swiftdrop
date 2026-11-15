package com.nyxora.swiftdrop.auth.controller;

import com.nyxora.swiftdrop.auth.dto.request.OAuthTokenRequest;
import com.nyxora.swiftdrop.auth.dto.response.OAuthTokenResponse;
import com.nyxora.swiftdrop.auth.service.MerchantAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "OAuth 2.0 M2M Authentication", description = "Machine-to-Machine client_credentials grant for partner e-commerce platforms")
public class OAuthTokenController {

    private final MerchantAuthService merchantAuthService;

    @PostMapping(
            value = "/oauth/token",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "OAuth 2.0 Token (Form-URLEncoded)",
            description = "Issues a stateless Bearer JWT for machine-to-machine integrations using standard client_credentials grant"
    )
    @ApiResponse(responseCode = "200", description = "Access token generated successfully",
            content = @Content(schema = @Schema(implementation = OAuthTokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Unsupported grant type or missing parameters")
    @ApiResponse(responseCode = "401", description = "Invalid client credentials or inactive merchant")
    public ResponseEntity<OAuthTokenResponse> getTokenForm(
            @RequestParam("grant_type") String grantType,
            @RequestParam("client_id") String clientId,
            @RequestParam("client_secret") String clientSecret
    ) {
        log.info("[OAuthController] M2M token request via form-urlencoded for client_id: {}", clientId);
        OAuthTokenRequest request = new OAuthTokenRequest(grantType, clientId, clientSecret);
        return ResponseEntity.ok(merchantAuthService.generateM2mToken(request));
    }

    @PostMapping(
            value = "/oauth/token",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(
            summary = "OAuth 2.0 Token (JSON)",
            description = "Issues a stateless Bearer JWT for machine-to-machine integrations accepting JSON payload"
    )
    @ApiResponse(responseCode = "200", description = "Access token generated successfully",
            content = @Content(schema = @Schema(implementation = OAuthTokenResponse.class)))
    @ApiResponse(responseCode = "400", description = "Invalid payload or unsupported grant type")
    @ApiResponse(responseCode = "401", description = "Invalid client credentials or inactive merchant")
    public ResponseEntity<OAuthTokenResponse> getTokenJson(
            @Valid @RequestBody OAuthTokenRequest request
    ) {
        log.info("[OAuthController] M2M token request via JSON for client_id: {}", request.clientId());
        return ResponseEntity.ok(merchantAuthService.generateM2mToken(request));
    }
}
