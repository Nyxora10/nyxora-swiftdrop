package com.nyxora.swiftdrop.parcel.courier.storage;

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