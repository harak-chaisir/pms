-- CLINICAL RECORDS TABLE (PHI encrypted at application layer)
-- HIPAA §164.312(a)(2)(iv): All PHI fields (diagnosis, treatment_plan, notes, medications, visit_date)
-- are encrypted via AES-256-GCM at the application layer using PhiEncryptionConverter.
-- No FK constraint on patient_id to preserve Spring Modulith module boundaries.
-- Referential integrity is enforced at the application layer via PatientService.

CREATE TABLE IF NOT EXISTS clinical_records
(
    id                   UUID PRIMARY KEY,
    patient_id           UUID         NOT NULL,
    record_type          VARCHAR(50)  NOT NULL,
    diagnosis            VARCHAR(255) NOT NULL,
    treatment_plan       VARCHAR(255),
    notes                VARCHAR(255),
    medications          VARCHAR(255),
    attending_physician  VARCHAR(255) NOT NULL,
    visit_date           VARCHAR(255) NOT NULL,
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP
);

-- Index on patient_id for efficient lookups by patient (no FK, application-layer integrity)
CREATE INDEX IF NOT EXISTS idx_clinical_records_patient_id ON clinical_records (patient_id);

-- Composite index for filtered queries by patient and record type
CREATE INDEX IF NOT EXISTS idx_clinical_records_patient_id_record_type ON clinical_records (patient_id, record_type);

