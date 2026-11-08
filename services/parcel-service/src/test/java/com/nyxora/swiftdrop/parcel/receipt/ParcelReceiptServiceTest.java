package com.nyxora.swiftdrop.parcel.receipt;

import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import com.nyxora.swiftdrop.parcel.domain.repository.ParcelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ParcelReceiptService Unit Tests")
class ParcelReceiptServiceTest {

    @Mock
    private ParcelRepository parcelRepository;

    @Mock
    private PdfReceiptGenerator pdfReceiptGenerator;

    @InjectMocks
    private ParcelReceiptServiceImpl receiptService;

    private UUID parcelId;
    private UUID senderUserId;
    private UUID otherUserId;
    private Parcel parcel;
    private byte[] mockPdfBytes;

    @BeforeEach
    void setUp() {
        parcelId = UUID.randomUUID();
        senderUserId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        parcel = Parcel.builder()
                .id(parcelId)
                .trackingNumber("SD-20261107-7K9P2X")
                .senderUserId(senderUserId)
                .build();

        mockPdfBytes = "%PDF-1.4 Mock PDF Content".getBytes();
    }

    @Test
    @DisplayName("generateReceiptPdf throws IllegalArgumentException when parcelId is null")
    void generateReceiptPdf_nullParcelId_throwsException() {
        assertThatThrownBy(() -> receiptService.generateReceiptPdf(null, senderUserId, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcel ID cannot be null.");
    }

    @Test
    @DisplayName("generateReceiptPdf throws IllegalArgumentException when parcel does not exist")
    void generateReceiptPdf_parcelNotFound_throwsException() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> receiptService.generateReceiptPdf(parcelId, senderUserId, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcel with ID " + parcelId + " was not found.");
    }

    @Test
    @DisplayName("generateReceiptPdf throws AccessDeniedException when customer is neither sender nor merchant")
    void generateReceiptPdf_unauthorizedCustomer_throwsAccessDenied() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));

        assertThatThrownBy(() -> receiptService.generateReceiptPdf(
                parcelId,
                otherUserId,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        ))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You do not have permission to access the delivery receipt for this parcel.");
    }

    @Test
    @DisplayName("generateReceiptPdf succeeds when requester is the sender")
    void generateReceiptPdf_authorizedSender_success() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(pdfReceiptGenerator.generateReceipt(parcel)).thenReturn(mockPdfBytes);

        byte[] result = receiptService.generateReceiptPdf(
                parcelId,
                senderUserId,
                List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))
        );

        assertThat(result).isEqualTo(mockPdfBytes);
        verify(pdfReceiptGenerator, times(1)).generateReceipt(parcel);
    }

    @Test
    @DisplayName("generateReceiptPdf succeeds for ROLE_ADMIN even if not the sender")
    void generateReceiptPdf_adminUser_success() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(pdfReceiptGenerator.generateReceipt(parcel)).thenReturn(mockPdfBytes);

        byte[] result = receiptService.generateReceiptPdf(
                parcelId,
                otherUserId,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        assertThat(result).isEqualTo(mockPdfBytes);
        verify(pdfReceiptGenerator, times(1)).generateReceipt(parcel);
    }

    @Test
    @DisplayName("generateReceiptPdf throws IllegalArgumentException when requesterUserId is null")
    void generateReceiptPdf_nullRequesterId_throwsException() {
        assertThatThrownBy(() -> receiptService.generateReceiptPdf(parcelId, null, Collections.emptyList()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Requester user ID cannot be null.");
    }

    @Test
    @DisplayName("generateReceiptPdf succeeds when requester is the merchant")
    void generateReceiptPdf_authorizedMerchant_success() {
        UUID merchantId = UUID.randomUUID();
        parcel.setMerchantId(merchantId);

        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(pdfReceiptGenerator.generateReceipt(parcel)).thenReturn(mockPdfBytes);

        byte[] result = receiptService.generateReceiptPdf(
                parcelId,
                merchantId,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))
        );

        assertThat(result).isEqualTo(mockPdfBytes);
        verify(pdfReceiptGenerator, times(1)).generateReceipt(parcel);
    }

    @Test
    @DisplayName("generateReceiptPdf handles null authorities collection cleanly")
    void generateReceiptPdf_nullAuthorities_checksOwnership() {
        when(parcelRepository.findById(parcelId)).thenReturn(Optional.of(parcel));
        when(pdfReceiptGenerator.generateReceipt(parcel)).thenReturn(mockPdfBytes);

        byte[] result = receiptService.generateReceiptPdf(parcelId, senderUserId, null);

        assertThat(result).isEqualTo(mockPdfBytes);
        verify(pdfReceiptGenerator, times(1)).generateReceipt(parcel);
    }
}

