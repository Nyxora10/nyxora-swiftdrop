package com.nyxora.swiftdrop.parcel.courier.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response payload carrying an AWS S3 Pre-signed PUT URL for direct
 * frontend-to-cloud delivery proof uploads (Task 1.8 Enterprise Architecture Pattern).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedProofResponseDto {

    private UUID parcelId;
    private String filename;
    private String uploadUrl;
    private String publicFileUrl;
    private Instant expiresAt;
}
