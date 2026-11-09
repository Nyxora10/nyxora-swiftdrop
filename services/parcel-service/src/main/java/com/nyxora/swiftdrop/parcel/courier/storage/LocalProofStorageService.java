package com.nyxora.swiftdrop.parcel.courier.storage;

import com.nyxora.swiftdrop.parcel.courier.dto.ProofUploadResponseDto;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Local filesystem implementation of {@link ProofStorageService}.
 * Stores delivery proof photos and recipient signatures locally on disk.
 * Serves as both local storage and reliable fallback for cloud providers.
 */
@Slf4j
@Service("localProofStorageService")
public class LocalProofStorageService implements ProofStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpeg", "jpg", "webp");

    private final Path storageLocation;

    public LocalProofStorageService(@Value("${app.storage.proofs-dir:uploads/proofs}") String proofsDir) {
        this.storageLocation = Paths.get(proofsDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(this.storageLocation);
            log.info("Local proof storage directory initialized at: {}", this.storageLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize proof storage directory: " + this.storageLocation, e);
        }
    }

    @Override
    public ProofUploadResponseDto storeProof(UUID parcelId, MultipartFile file) {
        if (parcelId == null) {
            throw new IllegalArgumentException("Parcel ID cannot be null.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file cannot be empty.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("File size exceeds maximum allowed limit of 5MB.");
        }

        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() != null ? file.getOriginalFilename() : "");
        String extension = extractExtension(originalFilename, file.getContentType());

        if (!ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
            throw new IllegalArgumentException("Invalid file format. Only PNG, JPEG, and WEBP images are permitted.");
        }

        // Generate safe unique filename: {parcelId}_{UUID-snippet}.{ext}
        String safeFilename = String.format("%s_%s.%s", parcelId, UUID.randomUUID().toString().substring(0, 8), extension);
        Path targetPath = this.storageLocation.resolve(safeFilename).normalize();

        // Path traversal security check
        if (!targetPath.startsWith(this.storageLocation)) {
            throw new IllegalArgumentException("Invalid file path: " + safeFilename);
        }

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            log.info("Delivery proof stored locally: {}", targetPath);
        } catch (IOException e) {
            log.error("Failed to store proof file {} for parcel {}", safeFilename, parcelId, e);
            throw new IllegalStateException("Failed to store proof file on local disk.", e);
        }

        String proofPhotoUrl = "/api/v1/parcels/proofs/" + safeFilename;

        return ProofUploadResponseDto.builder()
                .parcelId(parcelId)
                .filename(safeFilename)
                .proofPhotoUrl(proofPhotoUrl)
                .uploadedAt(Instant.now())
                .build();
    }

    @Override
    public Resource loadProofAsResource(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("Filename cannot be empty.");
        }

        try {
            Path filePath = this.storageLocation.resolve(filename).normalize();
            if (!filePath.startsWith(this.storageLocation)) {
                throw new IllegalArgumentException("Invalid file path request: " + filename);
            }

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new IllegalArgumentException("Proof file not found: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Error resolving proof file: " + filename, e);
        }
    }

    @Override
    public String determineContentType(String filename) {
        if (filename == null) {
            return "application/octet-stream";
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        } else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private String extractExtension(String originalFilename, String contentType) {
        if (originalFilename != null && originalFilename.contains(".")) {
            return originalFilename.substring(originalFilename.lastIndexOf(".") + 1);
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        } else if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return "jpg";
        } else if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        return "png";
    }
}