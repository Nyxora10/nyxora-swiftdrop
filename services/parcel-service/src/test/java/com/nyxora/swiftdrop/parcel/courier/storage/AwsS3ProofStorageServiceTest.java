package com.nyxora.swiftdrop.parcel.courier.storage;

import com.nyxora.swiftdrop.parcel.courier.dto.ProofUploadResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AwsS3ProofStorageService Unit Tests (Member 6)")
class AwsS3ProofStorageServiceTest {

    @Mock
    private ProofStorageService localFallback;

    private AwsS3ProofStorageService s3ServiceLocalMode;
    private AwsS3ProofStorageService s3ServiceNoCreds;
    private AwsS3ProofStorageService s3ServiceWithCreds;

    @BeforeEach
    void setUp() {
        s3ServiceLocalMode = new AwsS3ProofStorageService(
                "swiftdrop-delivery-proofs", "ap-southeast-2", "access", "secret", "local", localFallback);

        s3ServiceNoCreds = new AwsS3ProofStorageService(
                "swiftdrop-delivery-proofs", "ap-southeast-2", "", "", "s3", localFallback);

        s3ServiceWithCreds = new AwsS3ProofStorageService(
                "swiftdrop-delivery-proofs", "ap-southeast-2", "dummy-key", "dummy-secret", "s3", localFallback);
    }

    @Test
    @DisplayName("storeProof validates parameters and throws IllegalArgumentException")
    void storeProofValidation() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "proof.png", "image/png", "valid".getBytes());

        // Null parcelId
        assertThatThrownBy(() -> s3ServiceLocalMode.storeProof(null, validFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcel ID cannot be null");

        // Null file
        assertThatThrownBy(() -> s3ServiceLocalMode.storeProof(parcelId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Uploaded file cannot be empty");

        // Empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> s3ServiceLocalMode.storeProof(parcelId, emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Uploaded file cannot be empty");

        // Exceeds 5MB
        MockMultipartFile oversized = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[6 * 1024 * 1024]);
        assertThatThrownBy(() -> s3ServiceLocalMode.storeProof(parcelId, oversized))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum allowed limit");

        // Invalid format
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf".getBytes());
        assertThatThrownBy(() -> s3ServiceLocalMode.storeProof(parcelId, pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file format");
    }

    @Test
    @DisplayName("storeProof in local mode delegates directly to localFallback")
    void storeProofLocalModeDelegates() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sig.png", "image/png", "content".getBytes());

        ProofUploadResponseDto expected = ProofUploadResponseDto.builder()
                .parcelId(parcelId)
                .filename("sig.png")
                .proofPhotoUrl("/api/v1/parcels/proofs/sig.png")
                .uploadedAt(Instant.now())
                .build();

        when(localFallback.storeProof(parcelId, file)).thenReturn(expected);

        ProofUploadResponseDto actual = s3ServiceLocalMode.storeProof(parcelId, file);

        assertThat(actual).isEqualTo(expected);
        verify(localFallback).storeProof(parcelId, file);
    }

    @Test
    @DisplayName("storeProof without AWS credentials falls back to local storage")
    void storeProofUnconfiguredCredsFallsBack() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "content".getBytes());

        ProofUploadResponseDto expected = ProofUploadResponseDto.builder()
                .parcelId(parcelId)
                .filename("photo.jpg")
                .proofPhotoUrl("/api/v1/parcels/proofs/photo.jpg")
                .uploadedAt(Instant.now())
                .build();

        when(localFallback.storeProof(parcelId, file)).thenReturn(expected);

        ProofUploadResponseDto actual = s3ServiceNoCreds.storeProof(parcelId, file);

        assertThat(actual).isEqualTo(expected);
        verify(localFallback).storeProof(parcelId, file);
    }

    @Test
    @DisplayName("storeProof with S3 failure gracefully falls back to local storage")
    void storeProofS3ExceptionFallsBack() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "delivery.webp", "image/webp", "content".getBytes());

        ProofUploadResponseDto fallbackResponse = ProofUploadResponseDto.builder()
                .parcelId(parcelId)
                .filename("fallback.webp")
                .proofPhotoUrl("/api/v1/parcels/proofs/fallback.webp")
                .uploadedAt(Instant.now())
                .build();

        when(localFallback.storeProof(eq(parcelId), any(MockMultipartFile.class))).thenReturn(fallbackResponse);

        // Dummy credentials will fail to reach real S3, triggering the catch block and localFallback
        ProofUploadResponseDto result = s3ServiceWithCreds.storeProof(parcelId, file);

        assertThat(result).isEqualTo(fallbackResponse);
        verify(localFallback).storeProof(eq(parcelId), any(MockMultipartFile.class));
    }

    @Test
    @DisplayName("storeProof handles files without extension in originalFilename")
    void storeProofFallbackContentTypeExtensions() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile pngFile = new MockMultipartFile(
                "file", "blob", "image/png", "bytes".getBytes());
        MockMultipartFile jpgFile = new MockMultipartFile(
                "file", "blob", "image/jpeg", "bytes".getBytes());
        MockMultipartFile webpFile = new MockMultipartFile(
                "file", "blob", "image/webp", "bytes".getBytes());

        when(localFallback.storeProof(eq(parcelId), any())).thenReturn(
                ProofUploadResponseDto.builder().parcelId(parcelId).filename("f.png").build());

        s3ServiceLocalMode.storeProof(parcelId, pngFile);
        s3ServiceLocalMode.storeProof(parcelId, jpgFile);
        s3ServiceLocalMode.storeProof(parcelId, webpFile);
    }

    @Test
    @DisplayName("loadProofAsResource throws for invalid filename")
    void loadProofValidation() {
        assertThatThrownBy(() -> s3ServiceLocalMode.loadProofAsResource(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename cannot be empty");

        assertThatThrownBy(() -> s3ServiceLocalMode.loadProofAsResource("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename cannot be empty");
    }

    @Test
    @DisplayName("loadProofAsResource in local mode delegates to localFallback")
    void loadProofLocalModeDelegates() {
        Resource expectedResource = new ByteArrayResource("test".getBytes());
        when(localFallback.loadProofAsResource("photo.png")).thenReturn(expectedResource);

        Resource result = s3ServiceLocalMode.loadProofAsResource("photo.png");

        assertThat(result).isEqualTo(expectedResource);
        verify(localFallback).loadProofAsResource("photo.png");
    }

    @Test
    @DisplayName("loadProofAsResource without credentials delegates to localFallback")
    void loadProofNoCredsDelegates() {
        Resource expectedResource = new ByteArrayResource("test".getBytes());
        when(localFallback.loadProofAsResource("photo.png")).thenReturn(expectedResource);

        Resource result = s3ServiceNoCreds.loadProofAsResource("photo.png");

        assertThat(result).isEqualTo(expectedResource);
        verify(localFallback).loadProofAsResource("photo.png");
    }

    @Test
    @DisplayName("loadProofAsResource with S3 failure gracefully falls back to local storage")
    void loadProofS3FailureFallsBack() {
        Resource expectedResource = new ByteArrayResource("test".getBytes());
        when(localFallback.loadProofAsResource("photo.png")).thenReturn(expectedResource);

        Resource result = s3ServiceWithCreds.loadProofAsResource("photo.png");

        assertThat(result).isEqualTo(expectedResource);
        verify(localFallback).loadProofAsResource("photo.png");
    }

    @Test
    @DisplayName("determineContentType delegates to localFallback")
    void determineContentTypeDelegates() {
        when(localFallback.determineContentType("photo.png")).thenReturn("image/png");

        assertThat(s3ServiceWithCreds.determineContentType("photo.png")).isEqualTo("image/png");
        verify(localFallback).determineContentType("photo.png");
    }
}

