package com.harak.pms.clinicalrecord;

/**
 * Exception thrown when a clinical record cannot be found by its identifier.
 *
 * <p>Mapped to HTTP {@code 404 Not Found} by the
 * {@link com.harak.pms.common.GlobalExceptionHandler}.
 */
public class ClinicalRecordNotFoundException extends RuntimeException {

    /**
     * Constructs a new {@code ClinicalRecordNotFoundException} with the specified detail message.
     *
     * @param message a human-readable description of the error.
     */
    public ClinicalRecordNotFoundException(String message) {
        super(message);
    }
}

