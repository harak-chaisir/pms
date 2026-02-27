package com.harak.pms.clinicalrecord;

import com.harak.pms.encryption.PhiEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity representing a clinical record linked to a patient.
 *
 * <p>All PHI fields (diagnosis, treatmentPlan, notes, medications, visitDate)
 * are encrypted at rest using AES-256-GCM via {@link PhiEncryptionConverter}
 * to comply with HIPAA §164.312(a)(2)(iv).
 *
 * <p>The {@code patientId} is stored as a bare UUID reference — no JPA relationship
 * or database foreign key — to preserve Spring Modulith module boundaries.
 * Referential integrity is enforced at the application layer.
 *
 * <p>This entity uses soft deletes ({@code deleted} flag) per HIPAA §164.530(j)
 * which mandates 6-year data retention.
 */
@Entity
@Table(name = "clinical_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClinicalRecord {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "record_type", nullable = false)
    private String recordType;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(nullable = false)
    private String diagnosis;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "treatment_plan")
    private String treatmentPlan;

    @Convert(converter = PhiEncryptionConverter.class)
    private String notes;

    @Convert(converter = PhiEncryptionConverter.class)
    private String medications;

    @Column(name = "attending_physician", nullable = false)
    private String attendingPhysician;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "visit_date", nullable = false)
    private String visitDate;

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}

