-- USER TABLE
CREATE TABLE users
(
    id         UUID PRIMARY KEY,
    username   VARCHAR(100) UNIQUE NOT NULL,
    password   VARCHAR(255)        NOT NULL,
    role       VARCHAR(50)         NOT NULL,
    enabled    BOOLEAN             NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- PATIENT TABLE (PHI encrypted at application layer)
CREATE TABLE patients
(
    id                    UUID PRIMARY KEY,
    first_name            VARCHAR(255)        NOT NULL,
    last_name             VARCHAR(255)        NOT NULL,
    ssn                   VARCHAR(255)        NOT NULL,
    email                 VARCHAR(255),
    date_of_birth         DATE,
    medical_record_number VARCHAR(100) UNIQUE NOT NULL,
    deleted               BOOLEAN             NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            TIMESTAMP
);

-- AUDIT TABLE
CREATE TABLE audit_logs
(
    id           UUID PRIMARY KEY,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(100) NOT NULL,
    entity_id    UUID,
    performed_by VARCHAR(100),
    timestamp    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);