package com.nyxora.swiftdrop.parcel.receipt;

import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.UUID;

/**
 * Service contract for generating digital PDF delivery receipts.
 */
public interface ParcelReceiptService {

    /**
     * Verifies user authorization and renders an official digital PDF receipt
     * with embedded Code-128 linear barcode.
     *
     * @param parcelId        target parcel UUID
     * @param requesterUserId authenticated user UUID requesting the download
     * @param authorities     granted Spring Security authorities (roles)
     * @return raw PDF document byte array
     * @throws IllegalArgumentException if the parcel does not exist
     * @throws org.springframework.security.access.AccessDeniedException if the requester is not authorized
     */
    byte[] generateReceiptPdf(UUID parcelId, UUID requesterUserId, Collection<? extends GrantedAuthority> authorities);
}

