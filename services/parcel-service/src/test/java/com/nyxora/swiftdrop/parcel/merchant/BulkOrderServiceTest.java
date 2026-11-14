package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BulkOrderService Unit Tests")
class BulkOrderServiceTest {

    @Mock
    private MerchantOrderService merchantOrderService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BulkOrderServiceImpl bulkOrderService;

    private UUID merchantId;
    private UUID senderUserId;

    @BeforeEach
    void setUp() {
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        bulkOrderService = new BulkOrderServiceImpl(merchantOrderService, validator, transactionManager);
        merchantId = UUID.randomUUID();
        senderUserId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Invalid rows are reported per row and never reach order creation; valid rows still succeed")
    void processBulkOrders_InvalidRows_ReportedWithoutRejectingBatch() {
        MerchantOrderRequest missingPhone = createSampleOrder("REF-BAD-1");
        missingPhone.setRecipientPhone(null);
        MerchantOrderRequest badWeight = createSampleOrder("REF-BAD-2");
        badWeight.setWeightKg(new BigDecimal("0.0"));
        MerchantOrderRequest valid = createSampleOrder("REF-OK");

        when(merchantOrderService.createMerchantOrder(eq(valid), eq(merchantId), eq(senderUserId)))
                .thenReturn(MerchantOrderResponse.builder().trackingNumber("SD-OK").build());

        List<MerchantOrderRequest> rows = new ArrayList<>(List.of(missingPhone, valid, badWeight));
        rows.add(null);
        BulkOrderResponse response = bulkOrderService.processBulkOrders(new BulkOrderRequest(rows), merchantId, senderUserId);

        assertEquals(4, response.getTotalReceived());
        assertEquals(1, response.getSuccessfulCount());
        assertEquals(3, response.getFailedCount());
        assertEquals("FAILED", response.getResults().get(0).getStatus());
        assertTrue(response.getResults().get(0).getErrors().stream().anyMatch(e -> e.startsWith("recipientPhone:")));
        assertEquals("SUCCESS", response.getResults().get(1).getStatus());
        assertTrue(response.getResults().get(2).getErrors().stream().anyMatch(e -> e.startsWith("weightKg:")));
        assertEquals(List.of("Order row is empty."), response.getResults().get(3).getErrors());
        verify(merchantOrderService, times(1)).createMerchantOrder(any(), any(), any());
    }

    private MerchantOrderRequest createSampleOrder(String ref) {
        return MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 1, Colombo")
                .dropoffAddress("Customer Home, Kandy")
                .recipientName("Customer " + ref)
                .recipientPhone("+94770001122")
                .weightKg(new BigDecimal("1.5"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference(ref)
                .build();
    }

    @Test
    @DisplayName("processBulkOrders processes all valid items and returns summary")
    void processBulkOrders_Success() {
        List<MerchantOrderRequest> list = List.of(
                createSampleOrder("REF-001"),
                createSampleOrder("REF-002")
        );
        BulkOrderRequest request = new BulkOrderRequest(list);

        MerchantOrderResponse response1 = MerchantOrderResponse.builder()
                .trackingNumber("SD-20261107-000001")
                .estimatedFee(new BigDecimal("450.00"))
                .currentStatus(ParcelStatus.CREATED)
                .merchantOrderReference("REF-001")
                .build();

        MerchantOrderResponse response2 = MerchantOrderResponse.builder()
                .trackingNumber("SD-20261107-000002")
                .estimatedFee(new BigDecimal("450.00"))
                .currentStatus(ParcelStatus.CREATED)
                .merchantOrderReference("REF-002")
                .build();

        when(merchantOrderService.createMerchantOrder(eq(list.get(0)), eq(merchantId), eq(senderUserId))).thenReturn(response1);
        when(merchantOrderService.createMerchantOrder(eq(list.get(1)), eq(merchantId), eq(senderUserId))).thenReturn(response2);

        BulkOrderResponse response = bulkOrderService.processBulkOrders(request, merchantId, senderUserId);

        assertNotNull(response);
        assertEquals(2, response.getTotalReceived());
        assertEquals(2, response.getSuccessfulCount());
        assertEquals(0, response.getFailedCount());
        assertEquals(2, response.getResults().size());
        assertEquals("SD-20261107-000001", response.getResults().get(0).getTrackingNumber());
        assertEquals("SUCCESS", response.getResults().get(0).getStatus());
    }

    @Test
    @DisplayName("processBulkOrders enforces strict 100-parcel batch maximum limit")
    void processBulkOrders_Exceeds100_ThrowsException() {
        List<MerchantOrderRequest> list = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            list.add(createSampleOrder("REF-" + i));
        }
        BulkOrderRequest request = new BulkOrderRequest(list);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bulkOrderService.processBulkOrders(request, merchantId, senderUserId));

        assertTrue(ex.getMessage().contains("exceeds maximum allowed limit of 100"));
        verifyNoInteractions(merchantOrderService);
    }

    @Test
    @DisplayName("processBulkOrders records row-level errors without halting the entire batch")
    void processBulkOrders_RowFailure_CompilesError() {
        MerchantOrderRequest validReq = createSampleOrder("REF-GOOD");
        MerchantOrderRequest invalidReq = createSampleOrder("REF-BAD");
        BulkOrderRequest request = new BulkOrderRequest(List.of(validReq, invalidReq));

        MerchantOrderResponse successResp = MerchantOrderResponse.builder()
                .trackingNumber("SD-20261107-GOOD1")
                .estimatedFee(new BigDecimal("500.00"))
                .currentStatus(ParcelStatus.CREATED)
                .merchantOrderReference("REF-GOOD")
                .build();

        when(merchantOrderService.createMerchantOrder(eq(validReq), eq(merchantId), eq(senderUserId))).thenReturn(successResp);
        when(merchantOrderService.createMerchantOrder(eq(invalidReq), eq(merchantId), eq(senderUserId)))
                .thenThrow(new IllegalArgumentException("Destination delivery zone ID 999 not found."));

        BulkOrderResponse response = bulkOrderService.processBulkOrders(request, merchantId, senderUserId);

        assertNotNull(response);
        assertEquals(2, response.getTotalReceived());
        assertEquals(1, response.getSuccessfulCount());
        assertEquals(1, response.getFailedCount());
        assertEquals("SUCCESS", response.getResults().get(0).getStatus());
        assertEquals("FAILED", response.getResults().get(1).getStatus());
        assertTrue(response.getResults().get(1).getErrors().get(0).contains("zone ID 999 not found"));
    }

    @Test
    @DisplayName("processBulkOrders throws IllegalArgumentException when request is null")
    void processBulkOrders_NullRequest_ThrowsException() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bulkOrderService.processBulkOrders(null, merchantId, senderUserId));
        assertEquals("Bulk order batch cannot be empty.", ex.getMessage());
    }

    @Test
    @DisplayName("processBulkOrders throws IllegalArgumentException when orders list is null")
    void processBulkOrders_NullOrdersList_ThrowsException() {
        BulkOrderRequest request = new BulkOrderRequest(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bulkOrderService.processBulkOrders(request, merchantId, senderUserId));
        assertEquals("Bulk order batch cannot be empty.", ex.getMessage());
    }

    @Test
    @DisplayName("processBulkOrders throws IllegalArgumentException when orders list is empty")
    void processBulkOrders_EmptyOrdersList_ThrowsException() {
        BulkOrderRequest request = new BulkOrderRequest(Collections.emptyList());
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bulkOrderService.processBulkOrders(request, merchantId, senderUserId));
        assertEquals("Bulk order batch cannot be empty.", ex.getMessage());
    }

    @Test
    @DisplayName("processBulkOrders handles row exception with null message and null reference gracefully")
    void processBulkOrders_ExceptionWithNullMessage_HandlesGracefully() {
        MerchantOrderRequest orderReq = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse")
                .dropoffAddress("Home")
                .recipientName("Test")
                .recipientPhone("+94770001122")
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference(null)
                .build();

        BulkOrderRequest request = new BulkOrderRequest(List.of(orderReq));
        when(merchantOrderService.createMerchantOrder(eq(orderReq), eq(merchantId), eq(senderUserId)))
                .thenThrow(new NullPointerException());

        BulkOrderResponse response = bulkOrderService.processBulkOrders(request, merchantId, senderUserId);

        assertNotNull(response);
        assertEquals(1, response.getTotalReceived());
        assertEquals(0, response.getSuccessfulCount());
        assertEquals(1, response.getFailedCount());
        assertEquals("FAILED", response.getResults().get(0).getStatus());
        assertNull(response.getResults().get(0).getMerchantOrderReference());
        assertEquals("Unknown error processing row", response.getResults().get(0).getErrors().get(0));
    }
}
