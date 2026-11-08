package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderItemResult;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BulkOrderServiceImpl implements BulkOrderService {

    private static final int MAX_BATCH_SIZE = 100;
    private final MerchantOrderService merchantOrderService;

    @Override
    @Transactional
    public BulkOrderResponse processBulkOrders(BulkOrderRequest request, UUID merchantId, UUID senderUserId) {
        if (request == null || request.getOrders() == null || request.getOrders().isEmpty()) {
            throw new IllegalArgumentException("Bulk order batch cannot be empty.");
        }

        if (request.getOrders().size() > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("Batch size (" + request.getOrders().size() +
                    ") exceeds maximum allowed limit of " + MAX_BATCH_SIZE + " orders per upload.");
        }

        log.info("[BulkOrderService] Processing batch of {} orders for merchantId={}",
                request.getOrders().size(), merchantId);

        List<BulkOrderItemResult> results = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < request.getOrders().size(); i++) {
            int rowNumber = i + 1;
            MerchantOrderRequest orderReq = request.getOrders().get(i);

            try {
                MerchantOrderResponse created = merchantOrderService.createMerchantOrder(orderReq, merchantId, senderUserId);
                results.add(BulkOrderItemResult.builder()
                        .rowNumber(rowNumber)
                        .trackingNumber(created.getTrackingNumber())
                        .estimatedFee(created.getEstimatedFee())
                        .status("SUCCESS")
                        .merchantOrderReference(orderReq.getMerchantOrderReference())
                        .errors(Collections.emptyList())
                        .build());
                successCount++;
            } catch (Exception ex) {
                log.warn("[BulkOrderService] Row {} failed validation or creation: {}", rowNumber, ex.getMessage());
                results.add(BulkOrderItemResult.builder()
                        .rowNumber(rowNumber)
                        .trackingNumber(null)
                        .estimatedFee(null)
                        .status("FAILED")
                        .merchantOrderReference(orderReq != null ? orderReq.getMerchantOrderReference() : null)
                        .errors(List.of(ex.getMessage() != null ? ex.getMessage() : "Unknown error processing row"))
                        .build());
                failureCount++;
            }
        }

        log.info("[BulkOrderService] Completed batch for merchantId={}: total={}, success={}, failed={}",
                merchantId, request.getOrders().size(), successCount, failureCount);

        return BulkOrderResponse.builder()
                .totalReceived(request.getOrders().size())
                .successfulCount(successCount)
                .failedCount(failureCount)
                .results(results)
                .build();
    }
}
