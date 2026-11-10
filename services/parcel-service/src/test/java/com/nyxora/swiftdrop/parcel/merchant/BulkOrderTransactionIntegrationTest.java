package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.PricingRule;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.PricingRuleRepository;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.BulkOrderResponse;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link BulkOrderService} against a real Spring transaction manager (H2),
 * which the Mockito unit tests cannot do.
 *
 * <p>Regression guard: a single invalid row must be reported as FAILED in the response
 * while the valid rows of the same batch are still committed. Previously the inner
 * {@code @Transactional} call marked the shared transaction rollback-only, so the
 * whole batch failed with {@code UnexpectedRollbackException} (HTTP 500).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class BulkOrderTransactionIntegrationTest {

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private BulkOrderService bulkOrderService;

    @Autowired
    private DeliveryZoneRepository deliveryZoneRepository;

    @Autowired
    private PricingRuleRepository pricingRuleRepository;

    @Autowired
    private ParcelRepository parcelRepository;

    private DeliveryZone colombo;
    private DeliveryZone kandy;

    @BeforeEach
    void seedZonesAndPricing() {
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        colombo = deliveryZoneRepository.save(DeliveryZone.builder()
                .zoneCode("IT-COL-" + suffix).zoneName("Colombo IT").district("Colombo")
                .baseSurcharge(new BigDecimal("50.00")).isActive(true).build());
        kandy = deliveryZoneRepository.save(DeliveryZone.builder()
                .zoneCode("IT-KDY-" + suffix).zoneName("Kandy IT").district("Kandy")
                .baseSurcharge(new BigDecimal("100.00")).isActive(true).build());
        pricingRuleRepository.save(PricingRule.builder()
                .originZone(colombo).destinationZone(kandy).parcelSize(ParcelSize.SMALL)
                .baseFee(new BigDecimal("400.00")).ratePerKg(new BigDecimal("60.00"))
                .sizeMultiplier(new BigDecimal("1.00")).build());
    }

    @Test
    @DisplayName("Mixed batch commits valid rows and reports invalid rows without rolling back the batch")
    void mixedBatch_commitsValidRows_andReportsInvalidRows() {
        UUID merchantId = UUID.randomUUID();
        MerchantOrderRequest valid = order(colombo.getId(), kandy.getId(), "REF-OK");
        MerchantOrderRequest sameZone = order(colombo.getId(), colombo.getId(), "REF-BAD");

        BulkOrderResponse response = bulkOrderService.processBulkOrders(
                BulkOrderRequest.builder().orders(List.of(valid, sameZone)).build(),
                merchantId, merchantId);

        assertThat(response.getSuccessfulCount()).isEqualTo(1);
        assertThat(response.getFailedCount()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getResults().get(1).getStatus()).isEqualTo("FAILED");
        assertThat(response.getResults().get(1).getErrors()).isNotEmpty();
        assertThat(parcelRepository.countByMerchantId(merchantId)).isEqualTo(1);
    }

    private MerchantOrderRequest order(Integer originZoneId, Integer destinationZoneId, String reference) {
        return MerchantOrderRequest.builder()
                .originZoneId(originZoneId)
                .destinationZoneId(destinationZoneId)
                .pickupAddress("Peliyagoda Central Warehouse")
                .dropoffAddress("42 Peradeniya Road, Kandy")
                .recipientName("Test Recipient")
                .recipientPhone("+94771234567")
                .weightKg(new BigDecimal("2.0"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference(reference)
                .build();
    }
}
