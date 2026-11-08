package com.nyxora.swiftdrop.parcel.receipt;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.UUID;

/**
 * Implementation of {@link ParcelReceiptService}.
 * Enforces ownership access controls before generating branded PDF delivery receipts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelReceiptServiceImpl implements ParcelReceiptService {

    private final ParcelRepository parcelRepository;
    private final PdfReceiptGenerator pdfReceiptGenerator;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateReceiptPdf(UUID parcelId, UUID requesterUserId, Collection<? extends GrantedAuthority> authorities) {
        if (parcelId == null) {
            throw new IllegalArgumentException("Parcel ID cannot be null.");
        }
        if (requesterUserId == null) {
            throw new IllegalArgumentException("Requester user ID cannot be null.");
        }

        log.debug("Verifying access for receipt download: parcelId={}, requesterUserId={}", parcelId, requesterUserId);

        Parcel parcel = parcelRepository.findById(parcelId)
                .orElseThrow(() -> new IllegalArgumentException("Parcel with ID " + parcelId + " was not found."));

        // Role-based ownership check
        boolean isAdmin = authorities != null && authorities.stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        if (!isAdmin) {
            boolean isSender = requesterUserId.equals(parcel.getSenderUserId());
            boolean isMerchant = parcel.getMerchantId() != null && requesterUserId.equals(parcel.getMerchantId());

            if (!isSender && !isMerchant) {
                log.warn("Unauthorized receipt download attempt: User {} is neither the sender {} nor merchant of parcel {}",
                        requesterUserId, parcel.getSenderUserId(), parcelId);
                throw new AccessDeniedException("You do not have permission to access the delivery receipt for this parcel.");
            }
        }

        return pdfReceiptGenerator.generateReceipt(parcel);
    }
}

