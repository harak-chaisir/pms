package com.harak.pms.patient;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePatientRequest(

        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
        String lastName,

        @NotBlank(message = "SSN is required")
        @Pattern(regexp = "^\\d{3}-\\d{2}-\\d{4}$", message = "SSN must be in format XXX-XX-XXXX")
        String ssn,

        @Email(message = "Email must be a valid email address")
        String email,

        @NotBlank(message = "Date of birth is required")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "Date of birth must be in format YYYY-MM-DD")
        String dateOfBirth,

        @NotBlank(message = "Medical record number is required")
        @Size(min = 1, max = 50, message = "Medical record number must be between 1 and 50 characters")
        String medicalRecordNumber
) {
}

