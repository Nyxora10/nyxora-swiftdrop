package com.nyxora.swiftdrop.parcel.history;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.history.dto.MerchantOrderHistoryResponse;
import com.nyxora.swiftdrop.parcel.history.dto.OrderSummaryDto;
import com.nyxora.swiftdrop.parcel.history.dto.PagedOrderResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Service contract for customer and merchant paginated order history queries.
 */
public interface ParcelHistoryService {

    /**
     * Retrieves a paginated list of parcels booked by a customer, optionally filtered by status.
     *
     * @param customerUserId authenticated customer user UUID
     * @param status         optional lifecycle status filter
     * @param pageable       pagination and sorting parameters
     * @return paginated list of order summaries
     */
    PagedOrderResponse<OrderSummaryDto> getCustomerOrders(UUID customerUserId, ParcelStatus status, Pageable pageable);

    /**
     * Retrieves paginated orders and operational KPI status counters for a merchant account.
     *
     * @param merchantId merchant account UUID
     * @param status     optional lifecycle status filter
     * @param pageable   pagination and sorting parameters
     * @return merchant order history response with KPI counters
     */
    MerchantOrderHistoryResponse getMerchantOrders(UUID merchantId, ParcelStatus status, Pageable pageable);
}

