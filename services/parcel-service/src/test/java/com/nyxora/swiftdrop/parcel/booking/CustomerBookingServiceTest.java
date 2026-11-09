package com.nyxora.swiftdrop.parcel.booking;

import com.nyxora.swiftdrop.parcel.booking.config.RabbitMqConfig;
import com.nyxora.swiftdrop.parcel.booking.dto.BookingResponse;
import com.nyxora.swiftdrop.parcel.booking.dto.CustomerBookingRequest;
import com.nyxora.swiftdrop.parcel.booking.event.ParcelCreatedEvent;
import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerBookingService Unit Tests")
class CustomerBookingServiceTest {

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
    private CustomerBookingServiceImpl customerBookingService;

    private DeliveryZone originZone;
    private DeliveryZone destZone;
    private CustomerBookingRequest validRequest;
    private UUID senderUserId;
    private ParcelQuoteResponse mockQuote;

    @BeforeEach
    void setUp() {
        senderUserId = UUID.randomUUID();

        originZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .district("Colombo")
                .baseSurcharge(BigDecimal.ZERO)
                .isActive(true)
                .build();

        destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy Metro")
                .district("Kandy")
                .baseSurcharge(new BigDecimal("50.00"))
                .isActive(true)
                .build();

        validRequest = CustomerBookingRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .pickupAddress("No. 45, Galle Road, Colombo 03")
                .dropoffAddress("No. 128, Kandy Road, Peradeniya")
                .recipientName("Kamal Perera")
                .recipientPhone("0771234567")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .tier(QuoteTier.STANDARD)
                .build();

        mockQuote = ParcelQuoteResponse.builder()
                .appliedTier(QuoteTier.STANDARD)
                .baseFee(new BigDecimal("300.00"))
                .weightCharge(new BigDecimal("100.00"))
                .sizeMultiplier(new BigDecimal("1.20"))
                .zoneSurcharge(new BigDecimal("50.00"))
                .discountAmount(BigDecimal.ZERO)
                .expressSurcharge(BigDecimal.ZERO)
                .totalFee(new BigDecimal("530.00"))
                .build();
    }

    @Test
    @DisplayName("calculateQuote delegates directly to PricingEngine")
    void calculateQuote_delegatesToPricingEngine() {
        ParcelQuoteRequest request = ParcelQuoteRequest.builder()
                .originZoneId(1)
                .destinationZoneId(2)
                .weightKg(new BigDecimal("1.50"))
                .parcelSize(ParcelSize.SMALL)
                .tier(QuoteTier.STANDARD)
                .build();

        when(pricingEngine.calculateQuote(request)).thenReturn(mockQuote);

        ParcelQuoteResponse response = customerBookingService.calculateQuote(request);

        assertThat(response).isNotNull();
        assertThat(response.getTotalFee()).isEqualTo(new BigDecimal("530.00"));
        verify(pricingEngine, times(1)).calculateQuote(request);
    }

    @Test
    @DisplayName("bookParcel throws IllegalArgumentException when senderUserId is null")
    void bookParcel_nullSenderUserId_throwsException() {
        assertThatThrownBy(() -> customerBookingService.bookParcel(validRequest, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Authenticated sender user ID must be provided.");
    }

    @Test
    @DisplayName("bookParcel throws IllegalArgumentException when origin zone does not exist")
    void bookParcel_originZoneNotFound_throwsException() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerBookingService.bookParcel(validRequest, senderUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin delivery zone with ID 1 does not exist.");
    }

    @Test
    @DisplayName("bookParcel throws IllegalArgumentException when origin zone is inactive")
    void bookParcel_originZoneInactive_throwsException() {
        originZone.setIsActive(false);
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));

        assertThatThrownBy(() -> customerBookingService.bookParcel(validRequest, senderUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Origin delivery zone WP-COL-01 (Colombo Central) is currently inactive.");
    }

    @Test
    @DisplayName("bookParcel throws IllegalArgumentException when destination zone does not exist")
    void bookParcel_destZoneNotFound_throwsException() {
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerBookingService.bookParcel(validRequest, senderUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination delivery zone with ID 2 does not exist.");
    }

    @Test
    @DisplayName("bookParcel throws IllegalArgumentException when destination zone is inactive")
    void bookParcel_destZoneInactive_throwsException() {
        destZone.setIsActive(false);
        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));

        assertThatThrownBy(() -> customerBookingService.bookParcel(validRequest, senderUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination delivery zone CP-KAN-01 (Kandy Metro) is currently inactive.");
    }

    @Test
    @DisplayName("bookParcel succeeds: saves Parcel, creates history, publishes RabbitMQ event, and returns response")
    void bookParcel_success() {
        UUID parcelId = UUID.randomUUID();
        String trackingNumber = "SD-20261107-7K9P2X";

        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(pricingEngine.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(mockQuote);
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn(trackingNumber);

        Parcel savedParcel = Parcel.builder()
                .id(parcelId)
                .trackingNumber(trackingNumber)
                .senderUserId(senderUserId)
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress(validRequest.getPickupAddress())
                .dropoffAddress(validRequest.getDropoffAddress())
                .recipientName(validRequest.getRecipientName())
                .recipientPhone(validRequest.getRecipientPhone())
                .weightKg(validRequest.getWeightKg())
                .parcelSize(validRequest.getParcelSize())
                .estimatedFee(mockQuote.getTotalFee())
                .currentStatus(ParcelStatus.CREATED)
                .build();
        savedParcel.setCreatedAt(Instant.now());

        when(parcelRepository.save(any(Parcel.class))).thenReturn(savedParcel);

        BookingResponse response = customerBookingService.bookParcel(validRequest, senderUserId);

        assertThat(response).isNotNull();
        assertThat(response.getParcelId()).isEqualTo(parcelId);
        assertThat(response.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(response.getCurrentStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.getEstimatedFee()).isEqualTo(new BigDecimal("530.00"));
        assertThat(response.getOriginZoneCode()).isEqualTo("WP-COL-01");
        assertThat(response.getDestinationZoneCode()).isEqualTo("CP-KAN-01");
        assertThat(response.getCurrency()).isEqualTo("LKR");

        verify(parcelRepository, times(1)).save(any(Parcel.class));
        verify(parcelStatusHistoryRepository, times(1)).save(any(ParcelStatusHistory.class));
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMqConfig.PARCEL_EVENTS_EXCHANGE),
                eq(RabbitMqConfig.ROUTING_KEY_PARCEL_CREATED),
                any(ParcelCreatedEvent.class)
        );
    }

    @Test
    @DisplayName("bookParcel handles RabbitMQ publish failure gracefully without aborting booking")
    void bookParcel_rabbitMqFailure_isNonFatal() {
        UUID parcelId = UUID.randomUUID();
        String trackingNumber = "SD-20261107-ABC123";

        when(deliveryZoneRepository.findById(1)).thenReturn(Optional.of(originZone));
        when(deliveryZoneRepository.findById(2)).thenReturn(Optional.of(destZone));
        when(pricingEngine.calculateQuote(any(ParcelQuoteRequest.class))).thenReturn(mockQuote);
        when(trackingNumberGenerator.generateUniqueTrackingNumber()).thenReturn(trackingNumber);

        Parcel savedParcel = Parcel.builder()
                .id(parcelId)
                .trackingNumber(trackingNumber)
                .senderUserId(senderUserId)
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress(validRequest.getPickupAddress())
                .dropoffAddress(validRequest.getDropoffAddress())
                .recipientName(validRequest.getRecipientName())
                .recipientPhone(validRequest.getRecipientPhone())
                .weightKg(validRequest.getWeightKg())
                .parcelSize(validRequest.getParcelSize())
                .estimatedFee(mockQuote.getTotalFee())
                .currentStatus(ParcelStatus.CREATED)
                .build();

        when(parcelRepository.save(any(Parcel.class))).thenReturn(savedParcel);
        doThrow(new AmqpException("Connection refused")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        // Must succeed without throwing
        BookingResponse response = customerBookingService.bookParcel(validRequest, senderUserId);

        assertThat(response).isNotNull();
        assertThat(response.getTrackingNumber()).isEqualTo(trackingNumber);
        verify(parcelRepository, times(1)).save(any(Parcel.class));
        verify(parcelStatusHistoryRepository, times(1)).save(any(ParcelStatusHistory.class));
    }
}

