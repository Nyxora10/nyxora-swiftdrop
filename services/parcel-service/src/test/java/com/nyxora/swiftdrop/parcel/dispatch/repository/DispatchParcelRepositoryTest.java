package com.nyxora.swiftdrop.parcel.dispatch.repository;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.entity.ParcelStatusHistory;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import com.nyxora.swiftdrop.parcel.domain.repository.DeliveryZoneRepository;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("DispatchParcelRepository JPQL Query Tests (Member 5: Tasks 1.10 & 6.4)")
class DispatchParcelRepositoryTest {

    @Autowired
    private DispatchParcelRepository dispatchParcelRepository;

    @Autowired
    private DeliveryZoneRepository deliveryZoneRepository;

    @Autowired
    private ParcelStatusHistoryRepository parcelStatusHistoryRepository;

    private DeliveryZone colomboZone;
    private DeliveryZone kandyZone;

    @BeforeEach
    void setUp() {
        colomboZone = deliveryZoneRepository.findByZoneCode("WP-COL-01")
                .orElseGet(() -> deliveryZoneRepository.save(DeliveryZone.builder()
                        .zoneCode("WP-COL-01")
                        .zoneName("Colombo Central")
                        .district("Colombo")
                        .baseSurcharge(BigDecimal.valueOf(100.00))
                        .isActive(true)
                        .build()));

        kandyZone = deliveryZoneRepository.findByZoneCode("CP-KAN-01")
                .orElseGet(() -> deliveryZoneRepository.save(DeliveryZone.builder()
                        .zoneCode("CP-KAN-01")
                        .zoneName("Kandy Central")
                        .district("Kandy")
                        .baseSurcharge(BigDecimal.valueOf(150.00))
                        .isActive(true)
                        .build()));
    }

    private Parcel createParcel(String tracking, ParcelStatus status, DeliveryZone destZone) {
        return dispatchParcelRepository.save(Parcel.builder()
                .trackingNumber(tracking)
                .senderUserId(UUID.randomUUID())
                .originZone(colomboZone)
                .destinationZone(destZone)
                .pickupAddress("123 Galle Road, Colombo")
                .dropoffAddress("456 Peradeniya Road, Kandy")
                .recipientName("Nimal Perera")
                .recipientPhone("+94771234567")
                .weightKg(BigDecimal.valueOf(2.50))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(BigDecimal.valueOf(750.00))
                .currentStatus(status)
                .build());
    }

    @Test
    @DisplayName("countParcelsByStatusBetween aggregates status counts within the date window")
    void countParcelsByStatusBetween_aggregatesCorrectly() {
        createParcel("SD-REPO-01", ParcelStatus.DELIVERED, colomboZone);
        createParcel("SD-REPO-02", ParcelStatus.FAILED, colomboZone);
        createParcel("SD-REPO-03", ParcelStatus.IN_TRANSIT, kandyZone);

        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(1));

        List<Object[]> results = dispatchParcelRepository.countParcelsByStatusBetween(start, end);
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("getDeliveredParcelTimestampsBetween returns delivered parcel createdAt and transition timestamp")
    void getDeliveredParcelTimestampsBetween_returnsTimestamps() {
        Parcel p = createParcel("SD-REPO-DELIV", ParcelStatus.DELIVERED, colomboZone);

        ParcelStatusHistory history = ParcelStatusHistory.builder()
                .parcel(p)
                .status(ParcelStatus.DELIVERED)
                .updatedByUserId(UUID.randomUUID())
                .deliveryNote("Successfully handed over to recipient")
                .recipientSignatureName("Nimal")
                .build();
        parcelStatusHistoryRepository.save(history);

        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(1));

        List<Object[]> rows = dispatchParcelRepository.getDeliveredParcelTimestampsBetween(start, end);
        assertThat(rows).isNotEmpty();

        Object[] firstRow = rows.get(0);
        assertThat(firstRow[0]).isNotNull(); // parcel ID
        assertThat(firstRow[1]).isNotNull(); // parcel createdAt
        assertThat(firstRow[2]).isNotNull(); // history min createdAt
    }

    @Test
    @DisplayName("getFailedParcelHistoryBetween returns failed parcels with failure reasons")
    void getFailedParcelHistoryBetween_returnsFailureRecords() {
        Parcel p = createParcel("SD-REPO-FAIL", ParcelStatus.FAILED, colomboZone);

        ParcelStatusHistory history = ParcelStatusHistory.builder()
                .parcel(p)
                .status(ParcelStatus.FAILED)
                .updatedByUserId(UUID.randomUUID())
                .failureReason("RECIPIENT_UNAVAILABLE")
                .deliveryNote("No one at address after 3 attempts")
                .build();
        parcelStatusHistoryRepository.save(history);

        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(1));

        List<Object[]> rows = dispatchParcelRepository.getFailedParcelHistoryBetween(start, end);
        assertThat(rows).isNotEmpty();

        Object[] firstRow = rows.get(0);
        assertThat(firstRow[0]).isEqualTo(p.getId());
        assertThat(firstRow[1]).isEqualTo("RECIPIENT_UNAVAILABLE");
    }

    @Test
    @DisplayName("getDailyParcelStatusBetween returns date and currentStatus")
    void getDailyParcelStatusBetween_returnsDailyData() {
        createParcel("SD-REPO-DAILY", ParcelStatus.CREATED, colomboZone);

        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(1));

        List<Object[]> rows = dispatchParcelRepository.getDailyParcelStatusBetween(start, end);
        assertThat(rows).isNotEmpty();

        Object[] firstRow = rows.get(0);
        assertThat(firstRow[0]).isInstanceOf(Instant.class);
        assertThat(firstRow[1]).isInstanceOf(ParcelStatus.class);
    }

    @Test
    @DisplayName("getZonePerformanceBetween returns zone aggregate counts")
    void getZonePerformanceBetween_returnsZoneAggregates() {
        createParcel("SD-REPO-ZONE-1", ParcelStatus.DELIVERED, colomboZone);
        createParcel("SD-REPO-ZONE-2", ParcelStatus.IN_TRANSIT, colomboZone);

        Instant start = Instant.now().minus(Duration.ofHours(1));
        Instant end = Instant.now().plus(Duration.ofHours(1));

        List<Object[]> rows = dispatchParcelRepository.getZonePerformanceBetween(start, end);
        assertThat(rows).isNotEmpty();

        Object[] firstRow = rows.get(0);
        assertThat(firstRow[0]).isNotNull(); // zoneCode
        assertThat(firstRow[1]).isNotNull(); // zoneName
        assertThat(firstRow[2]).isNotNull(); // district
        assertThat(((Number) firstRow[3]).longValue()).isGreaterThanOrEqualTo(2L); // totalParcels
        assertThat(((Number) firstRow[4]).longValue()).isGreaterThanOrEqualTo(1L); // deliveredParcels
    }
}
