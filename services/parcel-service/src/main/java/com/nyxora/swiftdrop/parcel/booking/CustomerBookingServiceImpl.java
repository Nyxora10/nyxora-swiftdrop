package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.booking.config.RabbitMqConfig;
import com.nyxora.swiftdrop.parcel.booking.dto.BookingResponse;
import com.nyxora.swiftdrop.parcel.booking.dto.CustomerBookingRequest;
import com.nyxora.swiftdrop.parcel.booking.event.ParcelCreatedEvent;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;
import com.nyxora.swiftdrop.parcel.pricing.PricingEngine;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Implementation of {@link CustomerBookingService}.
 * Coordinates zone validation, dynamic pricing calculation, unique tracking number generation,
 * entity persistence, and asynchronous domain event notification.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerBookingServiceImpl implements CustomerBookingService {

    private final PricingEngine pricingEngine;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository parcelStatusHistoryRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public ParcelQuoteResponse calculateQuote(ParcelQuoteRequest request) {
        log.debug("CustomerBookingService calculating quote for originZone={}, destZone={}, weight={}kg, tier={}",
                request.getOriginZoneId(), request.getDestinationZoneId(), request.getWeightKg(), request.getTier());
        return pricingEngine.calculateQuote(request);
    }

    @Override
    @Transactional
    public BookingResponse bookParcel(CustomerBookingRequest request, UUID senderUserId) {
        if (senderUserId == null) {
            throw new IllegalArgumentException("Authenticated sender user ID must be provided.");
        }

        log.info("Initiating parcel delivery booking for senderUserId={}, originZoneId={}, destZoneId={}, weight={}kg",
                senderUserId, request.getOriginZoneId(), request.getDestinationZoneId(), request.getWeightKg());

        // 1. Validate zones
        DeliveryZone originZone = deliveryZoneRepository.findById(request.getOriginZoneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Origin delivery zone with ID " + request.getOriginZoneId() + " does not exist."));
        if (Boolean.FALSE.equals(originZone.getIsActive())) {
            throw new IllegalArgumentException("Origin delivery zone " + originZone.getZoneCode()
                    + " (" + originZone.getZoneName() + ") is currently inactive.");
        }

        DeliveryZone destZone = deliveryZoneRepository.findById(request.getDestinationZoneId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Destination delivery zone with ID " + request.getDestinationZoneId() + " does not exist."));
        if (Boolean.FALSE.equals(destZone.getIsActive())) {
            throw new IllegalArgumentException("Destination delivery zone " + destZone.getZoneCode()
                    + " (" + destZone.getZoneName() + ") is currently inactive.");
        }

        // 2. Calculate fee using PricingEngine
        QuoteTier tier = request.getTier() != null ? request.getTier() : QuoteTier.STANDARD;
        ParcelQuoteRequest quoteRequest = ParcelQuoteRequest.builder()
                .originZoneId(request.getOriginZoneId())
                .destinationZoneId(request.getDestinationZoneId())
                .weightKg(request.getWeightKg())
                .parcelSize(request.getParcelSize())
                .tier(tier)
                .build();
        ParcelQuoteResponse quote = pricingEngine.calculateQuote(quoteRequest);

        // 3. Generate collision-free tracking number
        String trackingNumber = trackingNumberGenerator.generateUniqueTrackingNumber();

        // 4. Persist Parcel entity
        Parcel parcel = Parcel.builder()
                .trackingNumber(trackingNumber)
                .senderUserId(senderUserId)
                .merchantId(request.getMerchantId())
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress(request.getPickupAddress().trim())
                .dropoffAddress(request.getDropoffAddress().trim())
                .recipientName(request.getRecipientName().trim())
                .recipientPhone(request.getRecipientPhone().trim())
                .weightKg(request.getWeightKg())
                .parcelSize(request.getParcelSize())
                .estimatedFee(quote.getTotalFee())
                .currentStatus(ParcelStatus.CREATED)
                .build();

        Parcel savedParcel = parcelRepository.save(parcel);

        // 5. Create initial ParcelStatusHistory audit entry
        ParcelStatusHistory statusHistory = ParcelStatusHistory.builder()
                .parcel(savedParcel)
                .status(ParcelStatus.CREATED)
                .updatedByUserId(senderUserId)
                .deliveryNote("Parcel delivery booking initiated by customer.")
                .build();
        parcelStatusHistoryRepository.save(statusHistory);

        log.info("Successfully booked parcel id={}, trackingNumber={}, fee=LKR {}",
                savedParcel.getId(), savedParcel.getTrackingNumber(), savedParcel.getEstimatedFee());

        // 6. Asynchronously publish parcel.created domain event to RabbitMQ
        publishParcelCreatedEvent(savedParcel, originZone.getId(), destZone.getId(), tier);

        // 7. Assemble response
        return BookingResponse.builder()
                .parcelId(savedParcel.getId())
                .trackingNumber(savedParcel.getTrackingNumber())
                .currentStatus(savedParcel.getCurrentStatus())
                .senderUserId(savedParcel.getSenderUserId())
                .merchantId(savedParcel.getMerchantId())
                .originZoneId(originZone.getId())
                .originZoneCode(originZone.getZoneCode())
                .originZoneName(originZone.getZoneName())
                .destinationZoneId(destZone.getId())
                .destinationZoneCode(destZone.getZoneCode())
                .destinationZoneName(destZone.getZoneName())
                .pickupAddress(savedParcel.getPickupAddress())
                .dropoffAddress(savedParcel.getDropoffAddress())
                .recipientName(savedParcel.getRecipientName())
                .recipientPhone(savedParcel.getRecipientPhone())
                .weightKg(savedParcel.getWeightKg())
                .parcelSize(savedParcel.getParcelSize())
                .appliedTier(tier)
                .estimatedFee(savedParcel.getEstimatedFee())
                .currency("LKR")
                .createdAt(savedParcel.getCreatedAt() != null ? savedParcel.getCreatedAt() : Instant.now())
                .build();
    }

    private void publishParcelCreatedEvent(Parcel parcel, Integer originZoneId, Integer destZoneId, QuoteTier tier) {
        try {
            ParcelCreatedEvent event = new ParcelCreatedEvent(
                    parcel.getId(),
                    parcel.getTrackingNumber(),
                    parcel.getSenderUserId(),
                    parcel.getMerchantId(),
                    originZoneId,
                    destZoneId,
                    parcel.getPickupAddress(),
                    parcel.getDropoffAddress(),
                    parcel.getRecipientName(),
                    parcel.getRecipientPhone(),
                    parcel.getWeightKg(),
                    parcel.getParcelSize().name(),
                    tier.name(),
                    parcel.getEstimatedFee(),
                    parcel.getCurrentStatus().name(),
                    parcel.getCreatedAt() != null ? parcel.getCreatedAt() : Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PARCEL_EVENTS_EXCHANGE,
                    RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED,
                    event
            );
            log.debug("Published parcel.created domain event to RabbitMQ for trackingNumber={}", parcel.getTrackingNumber());
        } catch (Exception ex) {
            log.warn("Non-fatal: Failed to publish parcel.created event to RabbitMQ (broker may be unreachable): {}",
                    ex.getMessage());
        }
    }
}

