package com.nyxora.swiftdrop.parcel.merchant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end merchant workflow through the real controllers, services and H2 database,
 * with headers shaped exactly as the API Gateway forwards them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Merchant order workflow (create -> dashboard history, bulk partial success)")
class MerchantOrderFlowIntegrationTest {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeliveryZoneRepository deliveryZoneRepository;
    @Autowired private PricingRuleRepository pricingRuleRepository;
    @Autowired private ParcelRepository parcelRepository;

    private DeliveryZone origin;
    private DeliveryZone destination;

    /** A merchant's user account UUID and merchant entity UUID are different values. */
    private record Merchant(UUID userId, UUID merchantId) {
        static Merchant random() {
            return new Merchant(UUID.randomUUID(), UUID.randomUUID());
        }
    }

    @BeforeEach
    void seedZonesAndPricing() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        origin = deliveryZoneRepository.save(DeliveryZone.builder()
                .zoneCode("FL-COL-" + suffix).zoneName("Colombo Flow").district("Colombo")
                .baseSurcharge(new BigDecimal("50.00")).isActive(true).build());
        destination = deliveryZoneRepository.save(DeliveryZone.builder()
                .zoneCode("FL-KDY-" + suffix).zoneName("Kandy Flow").district("Kandy")
                .baseSurcharge(new BigDecimal("100.00")).isActive(true).build());
        pricingRuleRepository.save(PricingRule.builder()
                .originZone(origin).destinationZone(destination).parcelSize(ParcelSize.SMALL)
                .baseFee(new BigDecimal("400.00")).ratePerKg(new BigDecimal("60.00"))
                .sizeMultiplier(new BigDecimal("1.00")).build());
    }

    private MerchantOrderRequest order(String reference) {
        return MerchantOrderRequest.builder()
                .originZoneId(origin.getId())
                .destinationZoneId(destination.getId())
                .pickupAddress("Peliyagoda Central Warehouse")
                .dropoffAddress("42 Peradeniya Road, Kandy")
                .recipientName("Flow Recipient")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference(reference)
                .build();
    }

    private MockHttpServletRequestBuilder asMerchant(MockHttpServletRequestBuilder builder, Merchant merchant) {
        return builder
                .header("X-User-Id", merchant.userId().toString())
                .header("X-User-Role", "ROLE_MERCHANT")
                .header("X-Merchant-Id", merchant.merchantId().toString());
    }

    @Test
    @DisplayName("an order created by a merchant appears in that merchant's dashboard history and KPIs")
    void createdOrder_appearsInOwnHistory() throws Exception {
        Merchant merchant = Merchant.random();

        String created = mockMvc.perform(asMerchant(post("/api/v1/parcels/merchant/orders"), merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order("FLOW-1"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.merchantId").value(merchant.merchantId().toString()))
                .andReturn().getResponse().getContentAsString();
        String trackingNumber = JsonPath.read(created, "$.trackingNumber");

        mockMvc.perform(asMerchant(get("/api/v1/parcels/merchant/orders"), merchant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(1))
                .andExpect(jsonPath("$.orders.content.length()").value(1))
                .andExpect(jsonPath("$.orders.content[0].trackingNumber").value(trackingNumber));
    }

    @Test
    @DisplayName("Merchant B cannot see Merchant A's orders")
    void otherMerchant_cannotSeeOrders() throws Exception {
        Merchant merchantA = Merchant.random();
        Merchant merchantB = Merchant.random();

        mockMvc.perform(asMerchant(post("/api/v1/parcels/merchant/orders"), merchantA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order("FLOW-A"))))
                .andExpect(status().isCreated());

        mockMvc.perform(asMerchant(get("/api/v1/parcels/merchant/orders"), merchantB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOrders").value(0))
                .andExpect(jsonPath("$.orders.content.length()").value(0));
    }

    @Test
    @DisplayName("history keyed by the user account UUID (the old bug) finds none of the merchant's orders")
    void historyByUserUuid_doesNotMatchMerchantOrders() throws Exception {
        Merchant merchant = Merchant.random();

        mockMvc.perform(asMerchant(post("/api/v1/parcels/merchant/orders"), merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order("FLOW-U"))))
                .andExpect(status().isCreated());

        assertThat(parcelRepository.countByMerchantId(merchant.merchantId())).isEqualTo(1);
        assertThat(parcelRepository.countByMerchantId(merchant.userId())).isZero();
    }

    @Test
    @DisplayName("bulk upload of 10 rows with 2 invalid: HTTP 201, 8 saved, 2 FAILED with validation errors")
    void bulkMixedBatch_savesValidRowsAndReportsInvalidRows() throws Exception {
        Merchant merchant = Merchant.random();
        List<MerchantOrderRequest> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(order("BULK-" + i));
        }
        rows.get(2).setRecipientPhone(null);            // row 3: missing mandatory field
        rows.get(6).setWeightKg(new BigDecimal("0.0")); // row 7: invalid value

        mockMvc.perform(asMerchant(post("/api/v1/parcels/bulk"), merchant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BulkOrderRequest(rows))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalReceived").value(10))
                .andExpect(jsonPath("$.successfulCount").value(8))
                .andExpect(jsonPath("$.failedCount").value(2))
                .andExpect(jsonPath("$.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.results[2].rowNumber").value(3))
                .andExpect(jsonPath("$.results[2].errors[0]").value(org.hamcrest.Matchers.startsWith("recipientPhone:")))
                .andExpect(jsonPath("$.results[6].status").value("FAILED"))
                .andExpect(jsonPath("$.results[6].errors[0]").value(org.hamcrest.Matchers.startsWith("weightKg:")))
                .andExpect(jsonPath("$.results[0].status").value("SUCCESS"));

        assertThat(parcelRepository.countByMerchantId(merchant.merchantId())).isEqualTo(8);
    }

    @Test
    @DisplayName("bulk envelope is still validated: an empty batch is rejected with 422 (project validation status)")
    void bulkEmptyBatch_isRejected() throws Exception {
        mockMvc.perform(asMerchant(post("/api/v1/parcels/bulk"), Merchant.random())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orders\":[]}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
