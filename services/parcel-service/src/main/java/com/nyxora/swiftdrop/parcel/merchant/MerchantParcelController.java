package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
@Tag(name = "Merchant B2B Parcel Operations", description = "Automated machine-to-machine order creation and bulk ingestion engine")
@SecurityRequirement(name = "BearerAuth")
public class MerchantParcelController {

    private final MerchantOrderService merchantOrderService;
    private final BulkOrderService bulkOrderService;

    @PostMapping(
            value = "/merchant/orders",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(
            summary = "Create Automated Merchant Parcel Order",
            description = "Creates a single delivery order for an approved B2B merchant partner, automatically applying the 15% corporate discount and issuing a collision-free tracking number"
    )
    @ApiResponse(responseCode = "201", description = "Parcel booked successfully with 15% discount",
            content = @Content(schema = @Schema(implementation = MerchantOrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "Validation failure or inactive delivery zone")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_MERCHANT or ROLE_ADMIN")
    public ResponseEntity<MerchantOrderResponse> createMerchantOrder(
            @Parameter(hidden = true) @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody MerchantOrderRequest request
    ) {
        UUID merchantId = resolveMerchantId(merchantIdHeader);
        UUID senderUserId = userIdHeader != null ? UUID.fromString(userIdHeader) : merchantId;

        log.info("[MerchantParcelController] Received M2M order request from merchantId={}", merchantId);
        MerchantOrderResponse response = merchantOrderService.createMerchantOrder(request, merchantId, senderUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(
            value = "/bulk",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @Operation(
            summary = "Bulk Parcel Ingestion (up to 100 orders)",
            description = "Accepts a batch of up to 100 parcel delivery orders in a single transaction, computing discounted rates and compiling per-row tracking numbers and errors"
    )
    @ApiResponse(responseCode = "201", description = "Batch processed successfully",
            content = @Content(schema = @Schema(implementation = BulkOrderResponse.class)))
    @ApiResponse(responseCode = "400", description = "Batch empty, exceeds 100 orders, or malformed payload")
    @ApiResponse(responseCode = "403", description = "Forbidden - Requires ROLE_MERCHANT or ROLE_ADMIN")
    public ResponseEntity<BulkOrderResponse> createBulkOrders(
            @Parameter(hidden = true) @RequestHeader(value = "X-Merchant-Id", required = false) String merchantIdHeader,
            @Parameter(hidden = true) @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @Valid @RequestBody BulkOrderRequest request
    ) {
        UUID merchantId = resolveMerchantId(merchantIdHeader);
        UUID senderUserId = userIdHeader != null ? UUID.fromString(userIdHeader) : merchantId;

        log.info("[MerchantParcelController] Received bulk ingestion request for {} orders from merchantId={}",
                request.getOrders() != null ? request.getOrders().size() : 0, merchantId);

        BulkOrderResponse response = bulkOrderService.processBulkOrders(request, merchantId, senderUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * The merchant must come from the gateway-injected X-Merchant-Id (JWT merchantId claim).
     * Falling back to X-User-Id would stamp parcels with a user UUID that is not a merchant ID,
     * hiding them from the merchant's own order history and webhooks.
     */
    private UUID resolveMerchantId(String merchantIdHeader) {
        if (merchantIdHeader != null && !merchantIdHeader.isBlank()) {
            return UUID.fromString(merchantIdHeader.trim());
        }
        throw new IllegalArgumentException("Merchant context is missing. X-Merchant-Id header must be provided.");
    }
}
