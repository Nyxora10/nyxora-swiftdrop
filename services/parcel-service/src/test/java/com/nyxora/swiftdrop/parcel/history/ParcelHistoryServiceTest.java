package com.nyxora.swiftdrop.parcel.history;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.history.dto.MerchantOrderHistoryResponse;
import com.nyxora.swiftdrop.parcel.history.dto.OrderSummaryDto;
import com.nyxora.swiftdrop.parcel.history.dto.PagedOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParcelHistoryService Unit Tests")
class ParcelHistoryServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @InjectMocks
    private ParcelHistoryServiceImpl historyService;

    private UUID customerUserId;
    private UUID merchantId;
    private Parcel mockParcel;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        customerUserId = UUID.randomUUID();
        merchantId = UUID.randomUUID();
        pageable = PageRequest.of(0, 10);

        DeliveryZone origin = DeliveryZone.builder().id(1).zoneCode("WP-COL-01").zoneName("Colombo Central").build();
        DeliveryZone dest = DeliveryZone.builder().id(2).zoneCode("CP-KAN-01").zoneName("Kandy Metro").build();

        mockParcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingNumber("SD-20261107-7K9P2X")
                .senderUserId(customerUserId)
                .merchantId(merchantId)
                .originZone(origin)
                .destinationZone(dest)
                .recipientName("Kamal Perera")
                .recipientPhone("0771234567")
                .dropoffAddress("Kandy Road, Peradeniya")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("530.00"))
                .currentStatus(ParcelStatus.IN_TRANSIT)
                .build();
        mockParcel.setCreatedAt(Instant.now());
        mockParcel.setUpdatedAt(Instant.now());
    }

    @Test
    @DisplayName("getCustomerOrders throws IllegalArgumentException when customerUserId is null")
    void getCustomerOrders_nullUserId_throwsException() {
        assertThatThrownBy(() -> historyService.getCustomerOrders(null, null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer user ID cannot be null.");
    }

    @Test
    @DisplayName("getCustomerOrders retrieves all orders when status is not specified")
    void getCustomerOrders_withoutStatus_returnsAllCustomerOrders() {
        Page<Parcel> page = new PageImpl<>(List.of(mockParcel), pageable, 1);
        when(parcelRepository.findBySenderUserId(customerUserId, pageable)).thenReturn(page);

        PagedOrderResponse<OrderSummaryDto> response =
                historyService.getCustomerOrders(customerUserId, null, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getTrackingNumber()).isEqualTo("SD-20261107-7K9P2X");
        assertThat(response.getContent().get(0).getOriginZoneName()).isEqualTo("Colombo Central");
        assertThat(response.getContent().get(0).getDestinationZoneName()).isEqualTo("Kandy Metro");

        verify(parcelRepository, times(1)).findBySenderUserId(customerUserId, pageable);
        verify(parcelRepository, never()).findBySenderUserIdAndCurrentStatus(any(), any(), any());
    }

    @Test
    @DisplayName("getCustomerOrders filters by status when status parameter is supplied")
    void getCustomerOrders_withStatus_returnsFilteredCustomerOrders() {
        Page<Parcel> page = new PageImpl<>(List.of(mockParcel), pageable, 1);
        when(parcelRepository.findBySenderUserIdAndCurrentStatus(customerUserId, ParcelStatus.IN_TRANSIT, pageable))
                .thenReturn(page);

        PagedOrderResponse<OrderSummaryDto> response =
                historyService.getCustomerOrders(customerUserId, ParcelStatus.IN_TRANSIT, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).getCurrentStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);

        verify(parcelRepository, times(1))
                .findBySenderUserIdAndCurrentStatus(customerUserId, ParcelStatus.IN_TRANSIT, pageable);
    }

    @Test
    @DisplayName("getMerchantOrders throws IllegalArgumentException when merchantId is null")
    void getMerchantOrders_nullMerchantId_throwsException() {
        assertThatThrownBy(() -> historyService.getMerchantOrders(null, null, pageable))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Merchant ID cannot be null.");
    }

    @Test
    @DisplayName("getMerchantOrders returns KPI status counts along with paginated orders")
    void getMerchantOrders_success_returnsCountersAndOrders() {
        Page<Parcel> page = new PageImpl<>(List.of(mockParcel), pageable, 1);
        when(parcelRepository.findByMerchantId(merchantId, pageable)).thenReturn(page);
        when(parcelRepository.countByMerchantId(merchantId)).thenReturn(25L);
        when(parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.IN_TRANSIT)).thenReturn(4L);
        when(parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.OUT_FOR_DELIVERY)).thenReturn(2L);
        when(parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.DELIVERED)).thenReturn(18L);
        when(parcelRepository.countByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.FAILED)).thenReturn(1L);

        MerchantOrderHistoryResponse response = historyService.getMerchantOrders(merchantId, null, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getTotalOrders()).isEqualTo(25L);
        assertThat(response.getInTransitCount()).isEqualTo(6L); // 4 IN_TRANSIT + 2 OUT_FOR_DELIVERY
        assertThat(response.getDeliveredCount()).isEqualTo(18L);
        assertThat(response.getFailedCount()).isEqualTo(1L);
        assertThat(response.getOrders().getContent()).hasSize(1);
        assertThat(response.getOrders().getContent().get(0).getTrackingNumber()).isEqualTo("SD-20261107-7K9P2X");

        verify(parcelRepository, times(1)).findByMerchantId(merchantId, pageable);
        verify(parcelRepository, times(1)).countByMerchantId(merchantId);
    }

    @Test
    @DisplayName("getMerchantOrders with status filter and null zones maps cleanly")
    void getMerchantOrders_withStatusAndNullZones_mapsCleanly() {
        mockParcel.setOriginZone(null);
        mockParcel.setDestinationZone(null);

        Page<Parcel> page = new PageImpl<>(List.of(mockParcel), pageable, 1);
        when(parcelRepository.findByMerchantIdAndCurrentStatus(merchantId, ParcelStatus.IN_TRANSIT, pageable)).thenReturn(page);
        when(parcelRepository.countByMerchantId(merchantId)).thenReturn(10L);

        MerchantOrderHistoryResponse response = historyService.getMerchantOrders(merchantId, ParcelStatus.IN_TRANSIT, pageable);

        assertThat(response).isNotNull();
        assertThat(response.getOrders().getContent().get(0).getOriginZoneName()).isNull();
        assertThat(response.getOrders().getContent().get(0).getDestinationZoneName()).isNull();
    }
}

