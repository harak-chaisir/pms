package com.harak.pms.clinicalrecord;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for clinical record data returned to the client.
 *
 * <p>Exposes decrypted PHI fields (diagnosis, treatmentPlan, notes, medications, visitDate)
 * to authorized users only. Access is controlled via {@code @PreAuthorize} on controller endpoints.
 *
 * @param id                  the unique identifier of the clinical record.
 * @param patientId           the UUID of the patient this record belongs to.
 * @param recordType          the type of clinical record.
 * @param diagnosis           the clinical diagnosis.
 * @param treatmentPlan       the treatment plan (may be {@code null}).
 * @param notes               additional clinical notes (may be {@code null}).
 * @param medications         prescribed medications (may be {@code null}).
 * @param attendingPhysician  the name of the attending physician.
 * @param visitDate           the date of the clinical visit.
 * @param createdAt           the timestamp when the record was created.
 * @param updatedAt           the timestamp when the record was last updated (may be {@code null}).
 */
public record ClinicalRecordResponse(
        UUID id,
        UUID patientId,
        String recordType,
        String diagnosis,
        String treatmentPlan,
        String notes,
        String medications,
        String attendingPhysician,
        String visitDate,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Creates a {@link ClinicalRecordResponse} from a {@link ClinicalRecord} entity.
     *
     * @param record the clinical record entity with decrypted PHI fields.
     * @return a response DTO suitable for the API response body.
     */
    public static ClinicalRecordResponse from(ClinicalRecord record) {
        return new ClinicalRecordResponse(
                record.getId(),
                record.getPatientId(),
                record.getRecordType(),
                record.getDiagnosis(),
                record.getTreatmentPlan(),
                record.getNotes(),
                record.getMedications(),
                record.getAttendingPhysician(),
                record.getVisitDate(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }
}

