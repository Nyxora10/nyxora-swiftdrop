package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.booking.TrackingNumberGenerator;
import com.nyxora.swiftdrop.parcel.booking.config.RabbitMqConfig;
import com.nyxora.swiftdrop.parcel.booking.event.ParcelCreatedEvent;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantOrderServiceImpl implements MerchantOrderService {

    private final PricingEngine pricingEngine;
    private final DeliveryZoneRepository deliveryZoneRepository;
    private final ParcelRepository parcelRepository;
    private final ParcelStatusHistoryRepository parcelStatusHistoryRepository;
    private final TrackingNumberGenerator trackingNumberGenerator;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Validation and pricing failures (IllegalArgument/IllegalState) are all raised before any
     * write, so they must not mark the caller's transaction rollback-only. Otherwise one bad row
     * in {@link BulkOrderServiceImpl} rolls back the entire batch with UnexpectedRollbackException.
     */
    @Override
    @Transactional(noRollbackFor = {IllegalArgumentException.class, IllegalStateException.class})
    public MerchantOrderResponse createMerchantOrder(MerchantOrderRequest request, UUID merchantId, UUID senderUserId) {
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant ID is required to create a B2B merchant order.");
        }

        log.info("[MerchantOrderService] Creating B2B order for merchantId={}, originZoneId={}, destZoneId={}, weight={}kg",
                merchantId, request.getOriginZoneId(), request.getDestinationZoneId(), request.getWeightKg());

        // 1. Validate active zones
        DeliveryZone originZone = deliveryZoneRepository.findById(request.getOriginZoneId())
                .orElseThrow(() -> new IllegalArgumentException("Origin delivery zone ID " + request.getOriginZoneId() + " not found."));
        if (Boolean.FALSE.equals(originZone.getIsActive())) {
            throw new IllegalArgumentException("Origin delivery zone '" + originZone.getZoneCode() + "' is inactive.");
        }

        DeliveryZone destZone = deliveryZoneRepository.findById(request.getDestinationZoneId())
                .orElseThrow(() -> new IllegalArgumentException("Destination delivery zone ID " + request.getDestinationZoneId() + " not found."));
        if (Boolean.FALSE.equals(destZone.getIsActive())) {
            throw new IllegalArgumentException("Destination delivery zone '" + destZone.getZoneCode() + "' is inactive.");
        }

        // 2. Compute discounted fee via GoF Merchant Strategy (15% corporate discount)
        ParcelQuoteRequest quoteRequest = ParcelQuoteRequest.builder()
                .originZoneId(request.getOriginZoneId())
                .destinationZoneId(request.getDestinationZoneId())
                .weightKg(request.getWeightKg())
                .parcelSize(request.getParcelSize())
                .tier(QuoteTier.MERCHANT)
                .build();
        ParcelQuoteResponse quote = pricingEngine.calculateQuote(quoteRequest);

        // 3. Generate unique collision-free tracking number (SD-YYYYMMDD-XXXXXX)
        String trackingNumber = trackingNumberGenerator.generateUniqueTrackingNumber();

        // 4. Persist Parcel entity
        Parcel parcel = Parcel.builder()
                .trackingNumber(trackingNumber)
                .senderUserId(senderUserId != null ? senderUserId : merchantId)
                .merchantId(merchantId)
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

        // 5. Create initial audit status history record
        ParcelStatusHistory statusHistory = ParcelStatusHistory.builder()
                .parcel(savedParcel)
                .status(ParcelStatus.CREATED)
                .updatedByUserId(senderUserId != null ? senderUserId : merchantId)
                .deliveryNote("Automated B2B order booked by merchant via API." +
                        (request.getMerchantOrderReference() != null ? " Ref: " + request.getMerchantOrderReference() : ""))
                .build();
        parcelStatusHistoryRepository.save(statusHistory);

        log.info("[MerchantOrderService] Saved parcel id={}, trackingNumber={}, fee=LKR {}, discount=LKR {}",
                savedParcel.getId(), savedParcel.getTrackingNumber(), savedParcel.getEstimatedFee(), quote.getDiscountAmount());

        // 6. Asynchronously publish parcel.created event to RabbitMQ
        publishParcelCreatedEvent(savedParcel, originZone.getId(), destZone.getId());

        // 7. Assemble response DTO
        return MerchantOrderResponse.builder()
                .parcelId(savedParcel.getId())
                .trackingNumber(savedParcel.getTrackingNumber())
                .merchantId(savedParcel.getMerchantId())
                .originZoneCode(originZone.getZoneCode())
                .destinationZoneCode(destZone.getZoneCode())
                .recipientName(savedParcel.getRecipientName())
                .weightKg(savedParcel.getWeightKg())
                .parcelSize(savedParcel.getParcelSize())
                .estimatedFee(savedParcel.getEstimatedFee())
                .discountAmount(quote.getDiscountAmount())
                .currentStatus(savedParcel.getCurrentStatus())
                .merchantOrderReference(request.getMerchantOrderReference())
                .createdAt(savedParcel.getCreatedAt() != null ? savedParcel.getCreatedAt() : Instant.now())
                .build();
    }

    private void publishParcelCreatedEvent(Parcel parcel, Integer originZoneId, Integer destZoneId) {
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
                    QuoteTier.MERCHANT.name(),
                    parcel.getEstimatedFee(),
                    parcel.getCurrentStatus().name(),
                    parcel.getCreatedAt() != null ? parcel.getCreatedAt() : Instant.now()
            );

            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PARCEL_EVENTS_EXCHANGE,
                    RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED,
                    event
            );
            log.debug("[MerchantOrderService] Published parcel.created domain event for trackingNumber={}", parcel.getTrackingNumber());
        } catch (Exception ex) {
            log.warn("[MerchantOrderService] Non-fatal: Failed to publish parcel.created event: {}", ex.getMessage());
        }
    }
}
