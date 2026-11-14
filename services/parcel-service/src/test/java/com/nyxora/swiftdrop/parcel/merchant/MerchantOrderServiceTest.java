package com.nyxora.swiftdrop.parcel.merchant;

import com.nyxora.swiftdrop.parcel.booking.TrackingNumberGenerator;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderRequest;
import com.nyxora.swiftdrop.parcel.merchant.dto.MerchantOrderResponse;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteRequest;
import com.nyxora.swiftdrop.parcel.pricing.ParcelQuoteResponse;
import com.nyxora.swiftdrop.parcel.pricing.PricingEngine;
import com.nyxora.swiftdrop.parcel.pricing.QuoteTier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MerchantOrderService Unit Tests")
class MerchantOrderServiceTest {

    @Mock
    private PricingEngine pricingEngine;

    @Mock
    private DeliveryZoneRepository deliveryZoneRepository;

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    @Mock
    private TrackingNumberGenerator trackingNumberGenerator;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private MerchantOrderServiceImpl merchantOrderService;

    private DeliveryZone colomboZone;
    private DeliveryZone kandyZone;
    private MerchantOrderRequest orderRequest;
    private UUID merchantId;
    private UUID senderUserId;

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        senderUserId = UUID.randomUUID();

        colomboZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .isActive(true)
                .baseSurcharge(new BigDecimal("50.00"))
                .build();

        kandyZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KDY-01")
                .zoneName("Kandy Metro")
                .isActive(true)
                .baseSurcharge(new BigDecimal("100.00"))
                .build();

        orderRequest = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Warehouse 4, Peliyagoda")
                .dropoffAddress("No. 12 Dalada Veediya, Kandy")
                .recipientName("Nimal Bandara")
                .recipientPhone("+94772345678")
                .weightKg(new BigDecimal("3.0"))
                .parcelSize(ParcelSize.MEDIUM)
                .merchantOrderReference("DARAZ-PK-9812")
                .build();
    }

    @Test
    @DisplayName("createMerchantOrder applies 15% discount, generates tracking number, and persists parcel")
    void createMerchantOrder_Success() {
        ParcelQuoteResponse quote = ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.MERCHANT)
                .totalFee(new BigDecimal("765.00"))
                .discountAmount(new BigDecimal("135.00"))
                .build();

        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(kandyZone));
        when(pricingEngine.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-20261107-DARAZ1");
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(i -> {
            Parcel p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        MerchantOrderResponse response = merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId);

        assertNotNull(response);
        assertEquals("SD-20261107-DARAZ1", response.getTrackingNumber());
        assertEquals(merchantId, response.getMerchantId());
        assertEquals(new BigDecimal("765.00"), response.getEstimatedFee());
        assertEquals(new BigDecimal("135.00"), response.getDiscountAmount());
        assertEquals(ParcelStatus.CREATED, response.getCurrentStatus());
        assertEquals("DARAZ-PK-9812", response.getMerchantOrderReference());

        verify(parcelRepository, times(1)).save(any(Parcel.class));
        verify(parcelStatusHistoryRepository, times(1)).save(any(ParcelStatusHistory.class));
    }

    @Test
    @DisplayName("createMerchantOrder succeeds when senderUserId is null (falls back to merchantId) and order reference is null")
    void createMerchantOrder_NullSenderUserIdAndNullReference_Success() {
        MerchantOrderRequest reqNoRef = MerchantOrderRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("Peliyagoda")
                .dropoffAddress("Kandy")
                .recipientName("Kamal")
                .recipientPhone("+94770001122")
                .weightKg(new BigDecimal("1.0"))
                .parcelSize(ParcelSize.SMALL)
                .merchantOrderReference(null)
                .build();

        ParcelQuoteResponse quote = ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.MERCHANT)
                .totalFee(new BigDecimal("425.00"))
                .discountAmount(new BigDecimal("75.00"))
                .build();

        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(kandyZone));
        when(pricingEngine.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-20261107-DARAZ2");
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(i -> {
            Parcel p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        MerchantOrderResponse response = merchantOrderService.createMerchantOrder(reqNoRef, merchantId, null);

        assertNotNull(response);
        assertEquals("SD-20261107-DARAZ2", response.getTrackingNumber());
        assertNull(response.getMerchantOrderReference());
    }

    @Test
    @DisplayName("createMerchantOrder catches RabbitMQ exception gracefully without failing order")
    void createMerchantOrder_RabbitMqFailure_DoesNotFailOrder() {
        ParcelQuoteResponse quote = ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.MERCHANT)
                .totalFee(new BigDecimal("765.00"))
                .discountAmount(new BigDecimal("135.00"))
                .build();

        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(kandyZone));
        when(pricingEngine.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(quote);
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn("SD-20261107-DARAZ3");
        when(parcelRepository.save(any(Parcel.class))).thenAnswer(i -> {
            Parcel p = i.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
        doThrow(new RuntimeException("RabbitMQ connection down")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        MerchantOrderResponse response = merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId);

        assertNotNull(response);
        assertEquals("SD-20261107-DARAZ3", response.getTrackingNumber());
    }

    @Test
    @DisplayName("createMerchantOrder throws IllegalArgumentException if origin zone not found")
    void createMerchantOrder_OriginZoneNotFound_ThrowsException() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId));
        assertTrue(ex.getMessage().contains("Origin delivery zone ID 1 not found"));
    }

    @Test
    @DisplayName("createMerchantOrder throws IllegalArgumentException if destination zone not found")
    void createMerchantOrder_DestinationZoneNotFound_ThrowsException() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId));
        assertTrue(ex.getMessage().contains("Destination delivery zone ID 2 not found"));
    }

    @Test
    @DisplayName("createMerchantOrder throws IllegalArgumentException if merchantId is null")
    void createMerchantOrder_NullMerchantId_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> merchantOrderService.createMerchantOrder(orderRequest, null, senderUserId));
    }

    @Test
    @DisplayName("createMerchantOrder throws IllegalArgumentException if origin zone is inactive")
    void createMerchantOrder_InactiveOriginZone_ThrowsException() {
        colomboZone.setIsActive(false);
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));

        assertThrows(IllegalArgumentException.class,
                () -> merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId));
    }

    @Test
    @DisplayName("createMerchantOrder throws IllegalArgumentException if destination zone is inactive")
    void createMerchantOrder_InactiveDestinationZone_ThrowsException() {
        kandyZone.setIsActive(false);
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(colomboZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(kandyZone));

        assertThrows(IllegalArgumentException.class,
                () -> merchantOrderService.createMerchantOrder(orderRequest, merchantId, senderUserId));
    }
}
