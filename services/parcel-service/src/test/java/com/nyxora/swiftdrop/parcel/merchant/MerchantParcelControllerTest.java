package com.nyxora.swiftdrop.parcel.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderItemResult;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("MerchantParcelController MockMvc Tests")
class MerchantParcelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MerchantOrderService merchantOrderService;

    @MockBean
    private BulkOrderService bulkOrderService;

    @Test
    @DisplayName("POST /merchant/orders returns 201 with 15% discount for authenticated merchant")
    void createMerchantOrder_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        MerchantOrderRequest request = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 1, Colombo")
                .dropoffAddress("Customer Home, Kandy")
                .recipientName("Nimal Bandara")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .merchantOrderReference("DARAZ-ORD-101")
                .build();

        MerchantOrderResponse response = MerchantOrderResponse.builder()
                .parcelId(UUID.randomUUID())
                .trackingNumber("SD-20261107-DARAZ1")
                .merchantId(merchantId)
                .originZoneCode("WP-COL-01")
                .destinationZoneCode("CP-KDY-01")
                .recipientName("Nimal Bandara")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("765.00"))
                .discountAmount(new BigDecimal("135.00"))
                .currentStatus(ParcelStatus.CREATED)
                .merchantOrderReference("DARAZ-ORD-101")
                .createdAt(Instant.now())
                .build();

        when(merchantOrderService.createMerchantOrder(any(MerchantOrderRequest.class), eq(merchantId), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/parcels/merchant/orders")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261107-DARAZ1"))
                .andExpect(jsonPath("$.estimatedFee").value(765.00))
                .andExpect(jsonPath("$.discountAmount").value(135.00))
                .andExpect(jsonPath("$.currentStatus").value("CREATED"))
                .andExpect(jsonPath("$.merchantOrderReference").value("DARAZ-ORD-101"));
    }

    @Test
    @DisplayName("POST /merchant/orders returns 403 Forbidden when caller is ROLE_CUSTOMER")
    void createMerchantOrder_CustomerRole_Returns403() throws Exception {
        MerchantOrderRequest request = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Colombo")
                .dropoffAddress("Kandy")
                .recipientName("Nimal")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .build();

        mockMvc.perform(post("/api/v1/parcels/merchant/orders")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .header("X-User-Role", "ROLE_CUSTOMER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /bulk returns 201 with summary for authenticated merchant")
    void createBulkOrders_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        MerchantOrderRequest orderReq = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Peliyagoda Warehouse")
                .dropoffAddress("Kandy Hub")
                .recipientName("Kamal Perera")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference("BULK-01")
                .build();

        BulkOrderRequest request = new BulkOrderRequest(List.of(orderReq));

        BulkOrderItemResult itemResult = BulkOrderItemResult.builder()
                .rowNumber(1)
                .trackingNumber("SD-20261107-BULK01")
                .estimatedFee(new BigDecimal("425.00"))
                .status("SUCCESS")
                .merchantOrderReference("BULK-01")
                .errors(Collections.emptyList())
                .build();

        BulkOrderResponse response = BulkOrderResponse.builder()
                .totalReceived(1)
                .successfulCount(1)
                .failedCount(0)
                .results(List.of(itemResult))
                .build();

        when(bulkOrderService.processBulkOrders(any(BulkOrderRequest.class), eq(merchantId), eq(userId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/parcels/bulk")
                        .header("X-User-Id", userId.toString())
                        .header("X-User-Role", "ROLE_MERCHANT")
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalReceived").value(1))
                .andExpect(jsonPath("$.successfulCount").value(1))
                .andExpect(jsonPath("$.results[0].trackingNumber").value("SD-20261107-BULK01"))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"));
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    @DisplayName("POST /merchant/orders succeeds with only X-Merchant-Id header")
    void createMerchantOrder_OnlyMerchantIdHeader_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantOrderRequest request = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 1, Colombo")
                .dropoffAddress("Customer Home, Kandy")
                .recipientName("Nimal Bandara")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .build();

        MerchantOrderResponse response = MerchantOrderResponse.builder()
                .trackingNumber("SD-20261107-MCHONLY")
                .estimatedFee(new BigDecimal("765.00"))
                .currentStatus(ParcelStatus.CREATED)
                .build();

        when(merchantOrderService.createMerchantOrder(any(MerchantOrderRequest.class), eq(merchantId), eq(merchantId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/parcels/merchant/orders")
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.trackingNumber").value("SD-20261107-MCHONLY"));
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    @DisplayName("POST /merchant/orders rejects requests carrying only X-User-Id (no merchant claim)")
    void createMerchantOrder_OnlyUserIdHeader_Returns400() throws Exception {
        MerchantOrderRequest request = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 1, Colombo")
                .dropoffAddress("Customer Home, Kandy")
                .recipientName("Nimal Bandara")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .build();

        mockMvc.perform(post("/api/v1/parcels/merchant/orders")
                        .header("X-User-Id", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verify(merchantOrderService, never()).createMerchantOrder(any(), any(), any());
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    @DisplayName("POST /merchant/orders returns 400 when merchant identification headers are missing")
    void createMerchantOrder_MissingHeaders_Returns400() throws Exception {
        MerchantOrderRequest request = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 1, Colombo")
                .dropoffAddress("Customer Home, Kandy")
                .recipientName("Nimal Bandara")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("2.5"))
                .parcelSize(ParcelSize.MEDIUM)
                .build();

        mockMvc.perform(post("/api/v1/parcels/merchant/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    @DisplayName("POST /bulk returns 400 when merchant identification headers are missing")
    void createBulkOrders_MissingHeaders_Returns400() throws Exception {
        MerchantOrderRequest orderReq = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Peliyagoda")
                .dropoffAddress("Kandy")
                .recipientName("Kamal")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .build();
        BulkOrderRequest request = new BulkOrderRequest(List.of(orderReq));

        mockMvc.perform(post("/api/v1/parcels/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "MERCHANT")
    @DisplayName("POST /bulk succeeds with only X-Merchant-Id header")
    void createBulkOrders_OnlyMerchantIdHeader_Success() throws Exception {
        UUID merchantId = UUID.randomUUID();

        MerchantOrderRequest orderReq = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Peliyagoda")
                .dropoffAddress("Kandy")
                .recipientName("Kamal")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .build();

        BulkOrderRequest request = new BulkOrderRequest(List.of(orderReq));
        BulkOrderResponse response = BulkOrderResponse.builder()
                .totalReceived(1)
                .successfulCount(1)
                .failedCount(0)
                .results(Collections.emptyList())
                .build();

        when(bulkOrderService.processBulkOrders(any(BulkOrderRequest.class), eq(merchantId), eq(merchantId)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/parcels/bulk")
                        .header("X-Merchant-Id", merchantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalReceived").value(1));
    }
}
