package com.nyxora.swiftdrop.parcel.history;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.history.dto.MerchantOrderHistoryResponse;
import com.nyxora.swiftdrop.parcel.history.dto.OrderSummaryDto;
import com.nyxora.swiftdrop.parcel.history.dto.PagedOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link ParcelHistoryService}.
 * Executes paginated queries and aggregates status counters for customer and merchant portals.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ParcelHistoryServiceImpl implements ParcelHistoryService {

    private final ParcelRepository parcelRepository;

    @Override
    @Transactional(readOnly = true)
    public PagedOrderResponse<OrderSummaryDto> getCustomerOrders(UUID customerUserId, ParcelStatus status, Pageable pageable) {
        if (customerUserId == null) {
            throw new IllegalArgumentException("Customer user ID cannot be null.");
        }

        log.debug("Fetching orders for customer {}: status={}, page={}, size={}",
                customerUserId, status, pageable.getPageNumber(), pageable.getPageSize());

        Page<Parcel> page = (status != null)
                ? parcelRepository.findBySenderUserIdAndCurrentStatus(customerUserId, status, pageable)
                : parcelRepository.findBySenderUserId(customerUserId, pageable);

        List<OrderSummaryDto> content = page.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        return PagedOrderResponse.<OrderSummaryDto>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MerchantOrderHistoryResponse getMerchantOrders(UUID merchantId, ParcelStatus status, Pageable pageable) {
        if (merchantId == null) {
            throw new IllegalArgumentException("Merchant ID cannot be null.");
        }

        log.debug("Fetching orders for merchant {}: status={}, page={}, size={}",
                merchantId, status, pageable.getPageNumber(), pageable.getPageSize());

        Page<Parcel> page = (status != null)
                ? parcelRepository.findByMerchantIdAndCurrentStatus(merchantId, status, pageable)
                : parcelRepository.findByMerchantId(merchantId, pageable);

        List<OrderSummaryDto> content = page.getContent().stream()
                .map(this::mapToSummaryDto)
                .toList();

        PagedOrderResponse<OrderSummaryDto> pagedOrders = PagedOrderResponse.<OrderSummaryDto>builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .isFirst(page.isFirst())
                .isLast(page.isLast())
                .build();

        long totalOrders = parcelRepository.countByMerchantId(merchantId);
        long inTransitCount = parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.IN_TRANSIT)
                + parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.OUT_FOR_DELIVERY);
        long deliveredCount = parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.DELIVERED);
        long failedCount = parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.FAILED);

        return MerchantOrderHistoryResponse.builder()
                .totalOrders(totalOrders)
                .inTransitCount(inTransitCount)
                .deliveredCount(deliveredCount)
                .failedCount(failedCount)
                .orders(pagedOrders)
                .build();
    }

    private OrderSummaryDto mapToSummaryDto(Parcel parcel) {
        return OrderSummaryDto.builder()
                .parcelId(parcel.getId())
                .trackingNumber(parcel.getTrackingNumber())
                .currentStatus(parcel.getCurrentStatus())
                .recipientName(parcel.getRecipientName())
                .recipientPhone(parcel.getRecipientPhone())
                .dropoffAddress(parcel.getDropoffAddress())
                .originZoneName(parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneName() : null)
                .destinationZoneName(parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() : null)
                .weightKg(parcel.getWeightKg())
                .parcelSize(parcel.getParcelSize())
                .estimatedFee(parcel.getEstimatedFee())
                .currency("LKR")
                .createdAt(parcel.getCreatedAt())
                .updatedAt(parcel.getUpdatedAt())
                .build();
    }
}

