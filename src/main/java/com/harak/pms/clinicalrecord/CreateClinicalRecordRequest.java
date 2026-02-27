package com.harak.pms.clinicalrecord;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Request DTO for creating a new clinical record.
 *
 * <p>All PHI fields are validated before reaching the service layer.
 * The {@code recordType} is restricted to a predefined set of clinical record categories.
 *
 * @param patientId           the UUID of the patient this record belongs to.
 * @param recordType          the type of clinical record (e.g., CONSULTATION, LAB_RESULT).
 * @param diagnosis           the clinical diagnosis (1–500 characters).
 * @param treatmentPlan       the treatment plan (optional, up to 1000 characters).
 * @param notes               additional clinical notes (optional, up to 2000 characters).
 * @param medications         prescribed medications (optional, up to 1000 characters).
 * @param attendingPhysician  the name of the attending physician (1–200 characters).
 * @param visitDate           the date of the clinical visit in {@code YYYY-MM-DD} format.
 */
public record CreateClinicalRecordRequest(

        @NotNull(message = "Patient ID is required")
        UUID patientId,

        @NotBlank(message = "Record type is required")
        @Pattern(regexp = "^(CONSULTATION|LAB_RESULT|IMAGING|PRESCRIPTION|SURGERY|FOLLOW_UP|EMERGENCY)$",
                message = "Record type must be one of: CONSULTATION, LAB_RESULT, IMAGING, PRESCRIPTION, "
                        + "SURGERY, FOLLOW_UP, EMERGENCY")
        String recordType,

        @NotBlank(message = "Diagnosis is required")
        @Size(min = 1, max = 500, message = "Diagnosis must be between 1 and 500 characters")
        String diagnosis,

        @Size(max = 1000, message = "Treatment plan must not exceed 1000 characters")
        String treatmentPlan,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        @Size(max = 1000, message = "Medications must not exceed 1000 characters")
        String medications,

        @NotBlank(message = "Attending physician is required")
        @Size(min = 1, max = 200, message = "Attending physician must be between 1 and 200 characters")
        String attendingPhysician,

        @NotBlank(message = "Visit date is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Visit date must be in format YYYY-MM-DD")
        String visitDate
) {
}

