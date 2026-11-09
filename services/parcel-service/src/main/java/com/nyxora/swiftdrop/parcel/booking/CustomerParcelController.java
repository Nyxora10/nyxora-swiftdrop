package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.booking.dto.BookingResponse;
import com.nyxora.swiftdrop.parcel.booking.dto.CustomerBookingRequest;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.history.ParcelHistoryService;
import com.nyxora.swiftdrop.parcel.history.dto.MerchantOrderHistoryResponse;
import com.nyxora.swiftdrop.parcel.history.dto.OrderSummaryDto;
import com.nyxora.swiftdrop.parcel.history.dto.PagedOrderResponse;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;
import com.nyxora.swiftdrop.parcel.receipt.ParcelReceiptService;
import com.nyxora.swiftdrop.parcel.tracking.ParcelTrackingService;
import com.nyxora.swiftdrop.parcel.tracking.dto.ParcelTrackingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Member 4 — Dedicated Customer & Merchant Parcel Operations Controller.
 * Provides thin REST endpoints for rate estimation, delivery booking, public tracking,
 * PDF receipts, and paginated order history under {@code /api/v1/parcels/**}.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parcels")
@RequiredArgsConstructor
@Tag(name = "Customer: Parcel Delivery & Booking", description = "Member 4 — Parcel rate quotes, booking, tracking, and receipts")
public class CustomerParcelController {

    private final CustomerBookingService customerBookingService;
    private final ParcelTrackingService parcelTrackingService;
    private final ParcelReceiptService parcelReceiptService;
    private final ParcelHistoryService parcelHistoryService;

    /**
     * Public instant rate quote endpoint.
     * Computes the transparent fee breakdown based on origin zone, destination zone,
     * weight, size, and pricing tier using Member 3's Dynamic Pricing Engine.
     */
    @PostMapping("/quote")
    @Operation(
            summary = "Calculate delivery rate quote",
            description = "Public endpoint providing instant, itemized rate calculations across Sri Lankan zones."
    )
    public ResponseEntity<ParcelQuoteResponse> calculateQuote(
            @Valid @RequestBody ParcelQuoteRequest request
    ) {
        log.debug("Received delivery rate quote request for originZone={}, destZone={}, weight={}kg",
                request.getOriginZoneId(), request.getDestinationZoneId(), request.getWeightKg());
        return ResponseEntity.ok(customerBookingService.calculateQuote(request));
    }

    /**
     * Customer delivery booking endpoint.
     * Validates zones, generates a unique linear tracking number, persists the delivery record,
     * logs the initial lifecycle audit entry, and publishes a domain event to RabbitMQ.
     */
    @PostMapping("/book")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Book a new parcel delivery",
            description = "Protected endpoint for customers and merchants to book parcel deliveries. Requires ROLE_CUSTOMER, ROLE_MERCHANT, or ROLE_ADMIN."
    )
    public ResponseEntity<BookingResponse> bookParcel(
            @Parameter(description = "Authenticated user UUID injected by API Gateway", required = true)
            @RequestHeader("X-User-Id") UUID senderUserId,
            @Valid @RequestBody CustomerBookingRequest request
    ) {
        log.info("Processing parcel booking request for senderUserId={}", senderUserId);
        BookingResponse response = customerBookingService.bookParcel(request, senderUserId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Public parcel tracking endpoint.
     * Returns real-time lifecycle status and chronological milestone history with recipient PII masked.
     */
    @GetMapping("/track/{trackingNumber}")
    @Operation(
            summary = "Track a delivery by tracking number",
            description = "Public endpoint providing real-time lifecycle tracking and chronological milestone timeline with PII masking."
    )
    public ResponseEntity<ParcelTrackingResponse> trackParcel(
            @PathVariable String trackingNumber
    ) {
        log.debug("Received public tracking lookup request for trackingNumber={}", trackingNumber);
        return ResponseEntity.ok(parcelTrackingService.trackParcel(trackingNumber));
    }

    /**
     * Official digital delivery receipt download endpoint.
     * Streams an itemized PDF invoice with an embedded Code-128 linear tracking barcode.
     */
    @GetMapping(value = "/{id}/receipt/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('CUSTOMER', 'MERCHANT', 'ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Download official PDF delivery receipt with linear barcode",
            description = "Generates and streams an itemized digital PDF delivery receipt with an embedded Code-128 tracking barcode."
    )
    public ResponseEntity<byte[]> downloadReceiptPdf(
            @PathVariable UUID id,
            @Parameter(description = "Authenticated user UUID injected by API Gateway", required = true)
            @RequestHeader("X-User-Id") UUID senderUserId,
            Authentication authentication
    ) {
        log.info("Processing receipt PDF download request for parcelId={}, requesterUserId={}", id, senderUserId);
        byte[] pdfBytes = parcelReceiptService.generateReceiptPdf(id, senderUserId, authentication.getAuthorities());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"SwiftDrop_Receipt_" + id + ".pdf\"")
                .header(HttpHeaders.CACHE_CONTROL, "must-revalidate, post-check=0, pre-check=0")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    /**
     * Paginated customer order history endpoint.
     * Returns all parcels booked by the authenticated customer with optional status filtering.
     */
    @GetMapping("/customer/orders")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Get paginated order history for customer",
            description = "Returns paginated list of parcels booked by the authenticated customer, sorted newest first."
    )
    public ResponseEntity<PagedOrderResponse<OrderSummaryDto>> getCustomerOrders(
            @Parameter(description = "Authenticated customer UUID", required = true)
            @RequestHeader("X-User-Id") UUID customerUserId,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Fetching customer orders: userId={}, status={}, page={}, size={}", customerUserId, status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(parcelHistoryService.getCustomerOrders(customerUserId, status, pageable));
    }

    /**
     * Paginated merchant order history and status counter summary endpoint.
     * Returns lifetime counters (total, in-transit, delivered, failed) and filtered order records.
     */
    @GetMapping("/merchant/orders")
    @PreAuthorize("hasAnyRole('MERCHANT', 'ADMIN')")
    @SecurityRequirement(name = "BearerAuth")
    @Operation(
            summary = "Get paginated order history and KPI metrics for merchant",
            description = "Returns aggregate status counters and paginated shipment history for corporate merchant accounts."
    )
    public ResponseEntity<MerchantOrderHistoryResponse> getMerchantOrders(
            @Parameter(description = "Authenticated merchant user UUID", required = true)
            @RequestHeader("X-User-Id") UUID merchantUserId,
            @RequestParam(required = false) ParcelStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        log.debug("Fetching merchant orders: merchantId={}, status={}, page={}, size={}", merchantUserId, status, page, size);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(parcelHistoryService.getMerchantOrders(merchantUserId, status, pageable));
    }
}
