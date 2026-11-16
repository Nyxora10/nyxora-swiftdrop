package com.nyxora.swiftdrop.parcel.courier.storage;

import com.nyxora.swiftdrop.parcel.courier.dto.PresignedProofResponseDto;
import com.nyxora.swiftdrop.parcel.courier.dto.ProofUploadResponseDto;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Strategy interface defining storage operations for delivery proof artifacts
 * (recipient signatures and delivery proof photos).
 */
public interface ProofStorageService {

    /**
     * Validates and persists an uploaded proof file for a given parcel.
     *
     * @param parcelId Unique ID of the parcel
     * @param file     Multipart image file (PNG, JPEG, WEBP)
     * @return ProofUploadResponseDto containing the stored filename and public/accessible URL
     */
    ProofUploadResponseDto storeProof(UUID parcelId, MultipartFile file);

    /**
     * Generates a short-lived Pre-signed URL allowing clients (frontend/mobile)
     * to upload proof artifacts directly to AWS S3 without passing through backend servers
     * (Task 1.8 Enterprise Architecture Pattern).
     *
     * @param parcelId    Unique ID of the parcel
     * @param filename    Original filename / proposed artifact name
     * @param contentType MIME type of the upload (e.g. "image/png", "image/jpeg")
     * @return PresignedProofResponseDto with uploadUrl and publicFileUrl, or null if unsupported/local
     */
    default PresignedProofResponseDto generatePresignedUrl(UUID parcelId, String filename, String contentType) {
        return null;
    }

    /**
     * Loads a stored delivery proof file as a Spring Resource for HTTP streaming/viewing.
     *
     * @param filename Stored proof filename
     * @return Spring Resource pointing to the file
     */
    Resource loadProofAsResource(String filename);

    /**
     * Resolves the MIME content type of the stored proof file.
     *
     * @param filename Stored proof filename
     * @return Content type string (e.g. "image/png", "image/jpeg")
     */
    String determineContentType(String filename);
}