package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderItemResult;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk ingestion with per-row partial success.
 *
 * <ul>
 *   <li><b>Validation per row:</b> {@link BulkOrderRequest} validates only the batch envelope
 *       (non-empty, at most 100). Each row's Bean Validation constraints are checked here, so one
 *       invalid row is reported as FAILED instead of rejecting the whole request with HTTP 400.</li>
 *   <li><b>One transaction per row:</b> each row runs in its own {@code REQUIRES_NEW} transaction.
 *       A row that fails, including on a database error after a partial write, rolls back only
 *       its own changes; rows already committed stay committed.</li>
 * </ul>
 */
@Slf4j
@Service
public class BulkOrderServiceImpl implements BulkOrderService {

    private static final int MAX_BATCH_SIZE = 100;

    private final MerchantOrderService merchantOrderService;
    private final Validator validator;
    private final TransactionTemplate rowTransaction;

    public BulkOrderServiceImpl(MerchantOrderService merchantOrderService,
                                Validator validator,
                                PlatformTransactionManager transactionManager) {
        this.merchantOrderService = merchantOrderService;
        this.validator = validator;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Deliberately not {@code @Transactional}: each row commits or rolls back on its own. */
    @Override
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

            List<String> validationErrors = validateRow(orderReq);
            if (!validationErrors.isEmpty()) {
                log.warn("[BulkOrderService] Row {} failed validation with {} error(s)", rowNumber, validationErrors.size());
                results.add(failed(rowNumber, orderReq, validationErrors));
                failureCount++;
                continue;
            }

            try {
                MerchantOrderResponse created = rowTransaction.execute(
                        status -> merchantOrderService.createMerchantOrder(orderReq, merchantId, senderUserId));
                results.add(BulkOrderItemResult.builder()
                        .rowNumber(rowNumber)
                        .trackingNumber(created.getTrackingNumber())
                        .estimatedFee(created.getEstimatedFee())
                        .status("SUCCESS")
                        .merchantOrderReference(orderReq.getMerchantOrderReference())
                        .errors(Collections.emptyList())
                        .build());
                successCount++;
            } catch (DataAccessException ex) {
                // Don't echo SQL/driver details back to the merchant.
                log.error("[BulkOrderService] Row {} failed with a database error; row rolled back", rowNumber, ex);
                results.add(failed(rowNumber, orderReq,
                        List.of("Order could not be saved due to a temporary storage error. Please retry this row.")));
                failureCount++;
            } catch (RuntimeException ex) {
                log.warn("[BulkOrderService] Row {} failed creation: {}", rowNumber, ex.getMessage());
                results.add(failed(rowNumber, orderReq,
                        List.of(ex.getMessage() != null ? ex.getMessage() : "Unknown error processing row")));
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

    private List<String> validateRow(MerchantOrderRequest orderReq) {
        if (orderReq == null) {
            return List.of("Order row is empty.");
        }
        Set<ConstraintViolation<MerchantOrderRequest>> violations = validator.validate(orderReq);
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .sorted()
                .toList();
    }

    private static BulkOrderItemResult failed(int rowNumber, MerchantOrderRequest orderReq, List<String> errors) {
        return BulkOrderItemResult.builder()
                .rowNumber(rowNumber)
                .trackingNumber(null)
                .estimatedFee(null)
                .status("FAILED")
                .merchantOrderReference(orderReq != null ? orderReq.getMerchantOrderReference() : null)
                .errors(errors)
                .build();
    }
}
