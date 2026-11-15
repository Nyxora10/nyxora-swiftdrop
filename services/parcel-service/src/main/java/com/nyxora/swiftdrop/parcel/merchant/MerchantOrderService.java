package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;

import java.util.UUID;

public interface MerchantOrderService {

    /**
     * Creates an automated merchant parcel booking applying the 15% corporate discount,
     * assigns a unique tracking number, creates the audit entry, and publishes parcel.created event.
     */
    MerchantOrderResponse createMerchantOrder(MerchantOrderRequest request, UUID merchantId, UUID senderUserId);
}
