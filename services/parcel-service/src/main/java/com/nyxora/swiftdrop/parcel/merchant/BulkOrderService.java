package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;

import java.util.UUID;

public interface BulkOrderService {

    /**
     * Ingests a batch of up to 100 parcel orders in a transactional boundary,
     * assigning tracking numbers, computing corporate discounts, and compiling per-row results.
     */
    BulkOrderResponse processBulkOrders(BulkOrderRequest request, UUID merchantId, UUID senderUserId);
}
