package com.nyxora.swiftdrop.parcel.courier.storage;

import com.nyxora.swiftdrop.parcel.courier.dto.ProofUploadResponseDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("LocalProofStorageService Unit Tests (Member 6)")
class LocalProofStorageServiceTest {

    @TempDir
    Path tempDir;

    private LocalProofStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalProofStorageService(tempDir.toString());
        storageService.init();
    }

    @Test
    @DisplayName("storeProof succeeds for valid PNG image")
    void storeProofPngSuccess() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "signature.png", "image/png", "fake-png-bytes".getBytes());

        ProofUploadResponseDto response = storageService.storeProof(parcelId, file);

        assertThat(response).isNotNull();
        assertThat(response.getParcelId()).isEqualTo(parcelId);
        assertThat(response.getFilename()).startsWith(parcelId.toString()).endsWith(".png");
        assertThat(response.getProofPhotoUrl()).isEqualTo("/api/v1/parcels/proofs/" + response.getFilename());
        assertThat(response.getUploadedAt()).isNotNull();

        // Verify resource can be loaded back
        Resource resource = storageService.loadProofAsResource(response.getFilename());
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    @DisplayName("storeProof succeeds for valid JPEG and WEBP images")
    void storeProofJpegAndWebpSuccess() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile jpegFile = new MockMultipartFile(
                "file", "doorstep.jpg", "image/jpeg", "fake-jpeg-bytes".getBytes());

        ProofUploadResponseDto response = storageService.storeProof(parcelId, jpegFile);
        assertThat(response.getFilename()).endsWith(".jpg");

        MockMultipartFile webpFile = new MockMultipartFile(
                "file", "delivery.webp", "image/webp", "fake-webp-bytes".getBytes());

        ProofUploadResponseDto webpResponse = storageService.storeProof(parcelId, webpFile);
        assertThat(webpResponse.getFilename()).endsWith(".webp");
    }

    @Test
    @DisplayName("storeProof falls back to contentType when originalFilename has no extension")
    void storeProofFallbackExtensionFromContentType() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile fileWithoutExtPng = new MockMultipartFile(
                "file", "blob", "image/png", "png-content".getBytes());

        ProofUploadResponseDto responsePng = storageService.storeProof(parcelId, fileWithoutExtPng);
        assertThat(responsePng.getFilename()).endsWith(".png");

        MockMultipartFile fileWithoutExtJpg = new MockMultipartFile(
                "file", "blob", "image/jpeg", "jpg-content".getBytes());

        ProofUploadResponseDto responseJpg = storageService.storeProof(parcelId, fileWithoutExtJpg);
        assertThat(responseJpg.getFilename()).endsWith(".jpg");

        MockMultipartFile fileWithoutExtWebp = new MockMultipartFile(
                "file", "blob", "image/webp", "webp-content".getBytes());

        ProofUploadResponseDto responseWebp = storageService.storeProof(parcelId, fileWithoutExtWebp);
        assertThat(responseWebp.getFilename()).endsWith(".webp");
    }

    @Test
    @DisplayName("storeProof throws IllegalArgumentException for invalid inputs")
    void storeProofValidationErrors() {
        UUID parcelId = UUID.randomUUID();
        MockMultipartFile validFile = new MockMultipartFile(
                "file", "proof.png", "image/png", "bytes".getBytes());

        // Null parcelId
        assertThatThrownBy(() -> storageService.storeProof(null, validFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Parcel ID cannot be null");

        // Null file
        assertThatThrownBy(() -> storageService.storeProof(parcelId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Uploaded file cannot be empty");

        // Empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> storageService.storeProof(parcelId, emptyFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Uploaded file cannot be empty");

        // File exceeding 5MB limit
        byte[] oversizedBytes = new byte[6 * 1024 * 1024];
        MockMultipartFile oversizedFile = new MockMultipartFile(
                "file", "large.png", "image/png", oversizedBytes);
        assertThatThrownBy(() -> storageService.storeProof(parcelId, oversizedFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("File size exceeds maximum allowed limit");

        // Invalid file format
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "pdf-data".getBytes());
        assertThatThrownBy(() -> storageService.storeProof(parcelId, pdfFile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid file format");
    }

    @Test
    @DisplayName("loadProofAsResource throws for invalid or non-existent files")
    void loadProofValidationErrors() {
        // Null or blank filename
        assertThatThrownBy(() -> storageService.loadProofAsResource(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename cannot be empty");

        assertThatThrownBy(() -> storageService.loadProofAsResource("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Filename cannot be empty");

        // Non-existent file
        assertThatThrownBy(() -> storageService.loadProofAsResource("non-existent-proof.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Proof file not found");
    }

    @Test
    @DisplayName("determineContentType returns correct MIME type")
    void determineContentTypeTests() {
        assertThat(storageService.determineContentType("photo.png")).isEqualTo("image/png");
        assertThat(storageService.determineContentType("photo.PNG")).isEqualTo("image/png");
        assertThat(storageService.determineContentType("photo.jpg")).isEqualTo("image/jpeg");
        assertThat(storageService.determineContentType("photo.jpeg")).isEqualTo("image/jpeg");
        assertThat(storageService.determineContentType("photo.webp")).isEqualTo("image/webp");
        assertThat(storageService.determineContentType("photo.pdf")).isEqualTo("application/octet-stream");
        assertThat(storageService.determineContentType(null)).isEqualTo("application/octet-stream");
    }
}

