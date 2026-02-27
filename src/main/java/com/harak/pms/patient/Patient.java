package com.harak.pms.patient;

import com.harak.pms.encryption.PhiEncryptionConverter;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "patients")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Patient {

    @Id
    @Column(columnDefinition = "UUID")
    private UUID id;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(nullable = false)
    private String ssn;

    @Convert(converter = PhiEncryptionConverter.class)
    private String email;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "date_of_birth")
    private String dateOfBirth;

    @Convert(converter = PhiEncryptionConverter.class)
    @Column(name = "medical_record_number", unique = true, nullable = false)
    private String medicalRecordNumber;

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

