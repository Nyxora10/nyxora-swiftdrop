package com.nyxora.swiftdrop.parcel.tracking;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.tracking.dto.ParcelTrackingResponse;
import com.nyxora.swiftdrop.parcel.tracking.dto.TrackingTimelineEventDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of {@link ParcelTrackingService}.
 * Retrieves real-time delivery lifecycle statuses and formats the tracking timeline
 * while sanitizing recipient Personally Identifiable Information (PII) using {@link PiiMaskingUtil}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelTrackingServiceImpl implements ParcelTrackingService {

    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    @Override
    @Transactional(readOnly = true)
    public ParcelTrackingResponse trackParcel(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number cannot be null or blank.");
        }

        String normalizedCode = trackingNumber.trim().toUpperCase();
        log.debug("Processing public tracking request for code: {}", normalizedCode);

        Parcel parcel = parcelRepository.findByTrackingNumber(normalizedCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Parcel with tracking number '" + normalizedCode + "' was not found."));

        List<ParcelStatusHistory> history =
                parcelStatusHistoryRepository.findByParcel_IdOrderByCreatedAtAsc(parcel.getId());

        List<TrackingTimelineEventDto> timeline = history.stream()
                .map(event -> TrackingTimelineEventDto.builder()
                        .status(event.getStatus())
                        .timestamp(event.getCreatedAt())
                        .deliveryNote(event.getDeliveryNote())
                        .build())
                .toList();

        String maskedPhone = PiiMaskingUtil.maskPhoneNumber(parcel.getRecipientPhone());
        String maskedAddress = PiiMaskingUtil.maskAddress(parcel.getDropoffAddress());
        String maskedName = maskRecipientName(parcel.getRecipientName());

        return ParcelTrackingResponse.builder()
                .trackingNumber(parcel.getTrackingNumber())
                .currentStatus(parcel.getCurrentStatus())
                .originZoneCode(parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneCode() : null)
                .originZoneName(parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneName() : null)
                .destinationZoneCode(parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneCode() : null)
                .destinationZoneName(parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : null)
                .maskedRecipientName(maskedName)
                .maskedRecipientPhone(maskedPhone)
                .maskedDropoffAddress(maskedAddress)
                .weightKg(parcel.getWeightKg())
                .parcelSize(parcel.getParcelSize())
                .createdAt(parcel.getCreatedAt())
                .timeline(timeline)
                .build();
    }

    /**
     * Masks recipient full names by preserving the outer characters of each name token.
     * e.g., "Kamal Perera" &rarr; "K***l P***a"
     */
    private String maskRecipientName(String fullName) {
        if (fullName == null) {
            return null;
        }
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }

        String[] parts = trimmed.split("\\s+");
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            int len = part.length();

            if (len == 1) {
                sb.append('*');
            } else if (len == 2) {
                sb.append(part.charAt(0)).append('*');
            } else {
                sb.append(part.charAt(0)).append("***").append(part.charAt(len - 1));
            }

            if (i < parts.length - 1) {
                sb.append(' ');
            }
        }

        return sb.toString();
    }
}

