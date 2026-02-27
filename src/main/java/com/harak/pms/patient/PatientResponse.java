package com.harak.pms.patient;

import java.time.Instant;
import java.util.UUID;

public record PatientResponse(
        UUID id,
        String firstName,
        String lastName,
        String maskedSsn,
        String email,
        String dateOfBirth,
        String medicalRecordNumber,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Creates a PatientResponse from a Patient entity.
     * SSN is masked to protect PHI — only the last 4 digits are visible.
     */
    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getFirstName(),
                patient.getLastName(),
                maskSsn(patient.getSsn()),
                patient.getEmail(),
                patient.getDateOfBirth(),
                patient.getMedicalRecordNumber(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    private static String maskSsn(String ssn) {
        if (ssn == null || ssn.length() < 4) {
            return "***-**-****";
        }
        return "***-**-" + ssn.substring(ssn.length() - 4);
    }
}

