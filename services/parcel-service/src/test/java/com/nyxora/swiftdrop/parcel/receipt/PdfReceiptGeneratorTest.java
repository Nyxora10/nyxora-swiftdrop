package com.nyxora.swiftdrop.parcel.receipt;

import com.nyxora.swiftdrop.parcel.domain.entity.DeliveryZone;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelSize;
import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PdfReceiptGenerator Unit Tests")
class PdfReceiptGeneratorTest {

    private PdfReceiptGenerator generator;
    private Parcel mockParcel;

    @BeforeEach
    void setUp() {
        generator = new PdfReceiptGenerator();

        DeliveryZone origin = DeliveryZone.builder()
                .id(1)
                .zoneCode("WP-COL-01")
                .zoneName("Colombo Central")
                .build();

        DeliveryZone dest = DeliveryZone.builder()
                .id(2)
                .zoneCode("CP-KAN-01")
                .zoneName("Kandy Metro")
                .build();

        mockParcel = Parcel.builder()
                .id(UUID.randomUUID())
                .trackingNumber("SD-20261107-7K9P2X")
                .senderUserId(UUID.randomUUID())
                .originZone(origin)
                .destinationZone(dest)
                .pickupAddress("No. 45, Galle Road, Colombo 03")
                .dropoffAddress("No. 128, Kandy Road, Peradeniya")
                .recipientName("Kamal Perera")
                .recipientPhone("0771234567")
                .weightKg(new BigDecimal("2.50"))
                .parcelSize(ParcelSize.MEDIUM)
                .estimatedFee(new BigDecimal("530.00"))
                .currentStatus(ParcelStatus.CREATED)
                .build();
        mockParcel.setCreatedAt(Instant.now());
    }

    @Test
    @DisplayName("Generates a non-empty PDF document starting with standard %PDF- magic bytes")
    void generateReceipt_success_returnsValidPdfBytes() {
        byte[] pdfBytes = generator.generateReceipt(mockParcel);

        assertThat(pdfBytes).isNotNull();
        assertThat(pdfBytes.length).isGreaterThan(1000);

        // Standard PDF magic bytes verification: "%PDF-"
        String header = new String(pdfBytes, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }
}

