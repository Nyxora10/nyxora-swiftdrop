package com.nyxora.swiftdrop.parcel.domain.entity;

import com.nyxora.swiftdrop.parcel.domain.enums.ParcelStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Immutable audit record of a parcel's status transition.
 * Maps to the {@code parcel_status_history} table in {@code parcel_db}.
 */
@Entity
@Table(
        name = "parcel_status_history",
        indexes = {
                @Index(name = "idx_status_history_parcel", columnList = "parcel_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParcelStatusHistory extends BaseCreatedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "parcel_id", nullable = false)
    private Parcel parcel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ParcelStatus status;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    @Column(name = "delivery_note", columnDefinition = "TEXT")
    private String deliveryNote;

    @Column(name = "recipient_signature_name", length = 150)
    private String recipientSignatureName;

    @Column(name = "proof_photo_url", length = 500)
    private String proofPhotoUrl;

    @Column(name = "failure_reason", length = 100)
    private String failureReason;
}

