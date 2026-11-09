package com.nyxora.swiftdrop.parcel.courier.storage;

import com.nyxora.swiftdrop.parcel.courier.dto.ProofUploadResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Cloud Object Storage implementation of {@link ProofStorageService} backed by AWS S3.
 * Features automated graceful fallback to local disk storage if AWS is unreachable,
 * guaranteeing high availability for mission-critical courier delivery operations.
 */
@Slf4j
@Primary
@Service("awsS3ProofStorageService")
public class AwsS3ProofStorageService implements ProofStorageService {

    private static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("png", "jpeg", "jpg", "webp");

    private final String bucketName;
    private final String region;
    private final String accessKey;
    private final String secretKey;
    private final String storageType;
    private final ProofStorageService localFallback;

    public AwsS3ProofStorageService(
            @Value("${app.storage.s3.bucket-name:swiftdrop-delivery-proofs}") String bucketName,
            @Value("${app.storage.s3.region:ap-southeast-2}") String region,
            @Value("${app.storage.s3.access-key:}") String accessKey,
            @Value("${app.storage.s3.secret-key:}") String secretKey,
            @Value("${app.storage.type:s3}") String storageType,
            @Qualifier("localProofStorageService") ProofStorageService localFallback) {
        this.bucketName = bucketName;
        this.region = region;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.storageType = storageType;
        this.localFallback = localFallback;
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

        // If local mode is explicitly chosen or S3 credentials are not configured, fallback to local disk
        if ("local".equalsIgnoreCase(storageType) || !hasValidAwsCredentials()) {
            log.info("S3 storage unconfigured or set to 'local'. Storing delivery proof locally via fallback.");
            return localFallback.storeProof(parcelId, file);
        }

        String safeFilename = String.format("%s_%s.%s", parcelId, UUID.randomUUID().toString().substring(0, 8), extension);
        String contentType = file.getContentType() != null ? file.getContentType() : "image/" + extension;

        try (S3Client s3Client = createS3Client()) {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(safeFilename)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

            // Public S3 URL accessible by any browser/client
            String s3Url = String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, safeFilename);
            log.info("Delivery proof uploaded successfully to AWS S3: {}", s3Url);

            return ProofUploadResponseDto.builder()
                    .parcelId(parcelId)
                    .filename(safeFilename)
                    .proofPhotoUrl(s3Url)
                    .uploadedAt(Instant.now())
                    .build();

        } catch (Exception ex) {
            log.warn("Failed to upload proof to AWS S3. Gracefully falling back to local disk storage: {}", ex.getMessage());
            return localFallback.storeProof(parcelId, file);
        }
    }

    @Override
    public Resource loadProofAsResource(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new IllegalArgumentException("Filename cannot be empty.");
        }

        if ("local".equalsIgnoreCase(storageType) || !hasValidAwsCredentials()) {
            return localFallback.loadProofAsResource(filename);
        }

        try {
            S3Client s3Client = createS3Client();
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(filename)
                    .build();

            ResponseInputStream<GetObjectResponse> s3Stream = s3Client.getObject(getObjectRequest);
            return new InputStreamResource(s3Stream);

        } catch (Exception ex) {
            log.warn("Failed to stream proof from AWS S3. Attempting to load from local storage fallback: {}", ex.getMessage());
            return localFallback.loadProofAsResource(filename);
        }
    }

    @Override
    public String determineContentType(String filename) {
        return localFallback.determineContentType(filename);
    }

    private boolean hasValidAwsCredentials() {
        return StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey);
    }

    private S3Client createS3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();
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