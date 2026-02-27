package com.harak.pms.clinicalrecord;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for updating an existing clinical record.
 *
 * <p>All fields are optional. Only non-null fields will be applied during the update.
 *
 * @param recordType          the updated record type (optional, must match allowed values if provided).
 * @param diagnosis           the updated diagnosis (optional, up to 500 characters).
 * @param treatmentPlan       the updated treatment plan (optional, up to 1000 characters).
 * @param notes               the updated clinical notes (optional, up to 2000 characters).
 * @param medications         the updated medications (optional, up to 1000 characters).
 * @param attendingPhysician  the updated attending physician (optional, up to 200 characters).
 * @param visitDate           the updated visit date in {@code YYYY-MM-DD} format (optional).
 */
public record UpdateClinicalRecordRequest(

        @Pattern(regexp = "^(CONSULTATION|LAB_RESULT|IMAGING|PRESCRIPTION|SURGERY|FOLLOW_UP|EMERGENCY)$",
                message = "Record type must be one of: CONSULTATION, LAB_RESULT, IMAGING, PRESCRIPTION, "
                        + "SURGERY, FOLLOW_UP, EMERGENCY")
        String recordType,

        @Size(min = 1, max = 500, message = "Diagnosis must be between 1 and 500 characters")
        String diagnosis,

        @Size(max = 1000, message = "Treatment plan must not exceed 1000 characters")
        String treatmentPlan,

        @Size(max = 2000, message = "Notes must not exceed 2000 characters")
        String notes,

        @Size(max = 1000, message = "Medications must not exceed 1000 characters")
        String medications,

        @Size(min = 1, max = 200, message = "Attending physician must be between 1 and 200 characters")
        String attendingPhysician,

        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Visit date must be in format YYYY-MM-DD")
        String visitDate
) {
}

