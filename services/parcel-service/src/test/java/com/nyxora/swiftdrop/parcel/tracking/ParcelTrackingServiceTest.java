package com.nyxora.swiftdrop.parcel.tracking;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import com.nyxora.swiftdrop.parcel.tracking.dto.ParcelTrackingResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParcelTrackingService Unit Tests")
class ParcelTrackingServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    @InjectMocks
    private ParcelTrackingServiceImpl trackingService;

    private Parcel mockParcel;
    private UUID parcelId;
    private String trackingNumber;

    @BeforeEach
    void setUp() {
        parcelId = UUID.randomUUID();
        trackingNumber = "SD-20261107-7K9P2X";

        DeliveryZone originZone = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .district("Colombo")
                .build();

        DeliveryZone destZone = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy Metro")
                .district("Kandy")
                .build();

        mockParcel = Parcel.builder()
                .id(parcelId)
                .trackingNumber(trackingNumber)
                .senderUserId(UUID.randomUUID())
                .originZone(originZone)
                .destinationZone(destZone)
                .pickupAddress("No. 12, Main Street, Colombo")
                .dropoffAddress("No. 45, Galle Road, Colombo 03")
                .recipientName("Kamal Perera")
                .recipientPhone("0771234567")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("450.00"))
                .currentStatus(ParcelStatus.IN_TRANSIT)
                .build();
        mockParcel.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("trackParcel throws IllegalArgumentException when trackingNumber is blank")
    void trackParcel_blankCode_throwsException() {
        assertThatThrownBy(() -> trackingService.trackParcel("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Tracking number cannot be null or blank.");
    }

    @Test
    @DisplayName("trackParcel throws IllegalArgumentException when trackingNumber is not found")
    void trackParcel_notFound_throwsException() {
        when(parcelRepository.findByTrackingNumber("SD-UNKNOWN-000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> trackingService.trackParcel("SD-UNKNOWN-000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcel with tracking number 'SD-UNKNOWN-000000' was not found.");
    }

    @Test
    @DisplayName("trackParcel successfully retrieves timeline and masks PII")
    void trackParcel_success_masksPii() {
        ParcelStatusHistory milestone1 = ParcelStatusHistory.builder()
                .id(UUID.randomUUID())
                .parcel(mockParcel)
                .status(ParcelStatus.CREATED)
                .deliveryNote("Booking confirmed.")
                .build();
        milestone1.setCreatedAt(Instant.now().minusSeconds(3600));

        ParcelStatusHistory milestone2 = ParcelStatusHistory.builder()
                .id(UUID.randomUUID())
                .parcel(mockParcel)
                .status(ParcelStatus.IN_TRANSIT)
                .deliveryNote("Package in transit.")
                .build();
        milestone2.setCreatedAt(Instant.now());

        when(parcelRepository.findByTrackingNumber(trackingNumber)).thenReturn(Optional.of(mockParcel));
        when(parcelStatusHistoryRepository.findByParcel_IdOrderByCreatedAtAsc(parcelId))
                .thenReturn(List.of(milestone1, milestone2));

        ParcelTrackingResponse response = trackingService.trackParcel(trackingNumber);

        assertThat(response).isNotNull();
        assertThat(response.getTrackingNumber()).isEqualTo(trackingNumber);
        assertThat(response.getCurrentStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);
        assertThat(response.getOriginZoneName()).isEqualTo("Colombo Central");
        assertThat(response.getDestinationZoneName()).isEqualTo("Kandy Metro");

        // Verify PII masking
        assertThat(response.getMaskedRecipientName()).isEqualTo("K***l P***a");
        assertThat(response.getMaskedRecipientPhone()).isEqualTo("07****4567");
        assertThat(response.getMaskedDropoffAddress()).contains("No. ***, Galle Road, Colombo 03");

        // Verify Timeline
        assertThat(response.getTimeline()).hasSize(2);
        assertThat(response.getTimeline().get(0).getStatus()).isEqualTo(ParcelStatus.CREATED);
        assertThat(response.getTimeline().get(1).getStatus()).isEqualTo(ParcelStatus.IN_TRANSIT);

        verify(parcelRepository, times(1)).findByTrackingNumber(trackingNumber);
        verify(parcelStatusHistoryRepository, times(1)).findByParcel_IdOrderByCreatedAtAsc(parcelId);
    }

    @Test
    @DisplayName("trackParcel handles short names, single-letter initials, and null zones")
    void trackParcel_shortNamesAndNullZones_handlesEdgeCases() {
        mockParcel.setRecipientName("A B CD EFG");
        mockParcel.setOriginZone(null);
        mockParcel.setDestinationZone(null);

        when(parcelRepository.findByTrackingNumber(trackingNumber)).thenReturn(Optional.of(mockParcel));
        when(parcelStatusHistoryRepository.findByParcel_IdOrderByCreatedAtAsc(parcelId)).thenReturn(List.of());

        ParcelTrackingResponse response = trackingService.trackParcel(trackingNumber);

        assertThat(response).isNotNull();
        assertThat(response.getMaskedRecipientName()).isEqualTo("* * C* E***G");
        assertThat(response.getOriginZoneCode()).isNull();
        assertThat(response.getDestinationZoneCode()).isNull();
    }

    @Test
    @DisplayName("trackParcel handles null or empty recipient names gracefully")
    void trackParcel_nullOrEmptyName_returnsAsIs() {
        mockParcel.setRecipientName(null);

        when(parcelRepository.findByTrackingNumber(trackingNumber)).thenReturn(Optional.of(mockParcel));
        when(parcelStatusHistoryRepository.findByParcel_IdOrderByCreatedAtAsc(parcelId)).thenReturn(List.of());

        ParcelTrackingResponse response = trackingService.trackParcel(trackingNumber);

        assertThat(response).isNotNull();
        assertThat(response.getMaskedRecipientName()).isNull();

        mockParcel.setRecipientName("   ");
        response = trackingService.trackParcel(trackingNumber);
        assertThat(response.getMaskedRecipientName()).isEmpty();
    }
}

