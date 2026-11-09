package com.nyxora.swiftdrop.parcel.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload returned after a delivery proof file (photo or signature)
 * has been successfully uploaded to the storage service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProofUploadResponseDto {

    private UUID parcelId;
    private String filename;
    private String proofPhotoUrl;
    private Instant uploadedAt;
}