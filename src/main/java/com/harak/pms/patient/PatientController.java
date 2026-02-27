package com.harak.pms.patient;

import com.harak.pms.audit.Auditable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for patient management endpoints.
 *
 * <p>All endpoints require authentication and role-based authorization.
 * Every action that creates, reads, updates, or deletes PHI is audit-logged
 * automatically via the {@link Auditable} AOP aspect per HIPAA §164.312(b).
 */
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    /**
     * Creates a new patient record with encrypted PHI fields.
     *
     * <p>The medical record number is checked for uniqueness. The creation event
     * is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param request the validated patient creation request containing PHI fields.
     * @return the created patient with masked SSN, wrapped in a {@code 201 Created} response.
     * @throws IllegalArgumentException if a patient with the given MRN already exists.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "PATIENT_CREATED", entityType = "PATIENT",
            detailExpression = "'Created patient with MRN: ' + #request.medicalRecordNumber()")
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request) {

        Patient patient = patientService.createPatient(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(patient));
    }

    /**
     * Retrieves a patient record by its unique identifier.
     *
     * <p>The SSN is masked in the response (only last 4 digits visible).
     * Access is restricted to ADMIN, DOCTOR, and NURSE roles.
     * The access event is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param id the UUID of the patient to retrieve.
     * @return the patient data with masked SSN, wrapped in a {@code 200 OK} response.
     * @throws PatientNotFoundException if no active patient exists with the given ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "PATIENT_VIEWED", entityType = "PATIENT", detail = "Viewed patient record")
    public ResponseEntity<PatientResponse> getPatientById(@PathVariable UUID id) {

        Patient patient = patientService.getPatientById(id);
        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    /**
     * Retrieves a paginated list of all active patients.
     *
     * <p>Supports configurable pagination and sorting. Page size is clamped
     * to a maximum of 100 to prevent excessive data retrieval.
     *
     * @param page      the zero-based page index (default 0).
     * @param size      the page size (default 20, max 100).
     * @param sortBy    the field to sort by (default {@code createdAt}).
     * @param direction the sort direction: {@code asc} or {@code desc} (default {@code desc}).
     * @return a page of patients with masked SSNs, wrapped in a {@code 200 OK} response.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "PATIENT_LIST_VIEWED", entityType = "PATIENT",
            detailExpression = "'Listed patients — page: ' + #page + ', size: ' + T(Math).min(#size, 100)")
    public ResponseEntity<Page<PatientResponse>> getAllPatients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        int clampedSize = Math.min(size, 100);
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        Page<Patient> patients = patientService.getAllPatients(pageable);
        Page<PatientResponse> responsePage = patients.map(PatientResponse::from);

        return ResponseEntity.ok(responsePage);
    }

    /**
     * Retrieves a patient record by medical record number.
     *
     * @param mrn the medical record number to look up.
     * @return the patient data with masked SSN, wrapped in a {@code 200 OK} response.
     * @throws PatientNotFoundException if no active patient exists with the given MRN.
     */
    @GetMapping("/mrn/{mrn}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "PATIENT_VIEWED", entityType = "PATIENT", detail = "Viewed patient by MRN")
    public ResponseEntity<PatientResponse> getPatientByMrn(@PathVariable String mrn) {

        Patient patient = patientService.getPatientByMedicalRecordNumber(mrn);
        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    /**
     * Updates an existing patient record with the provided non-null fields.
     *
     * <p>Only fields present in the request body are applied. The list of updated
     * field names (not values) is recorded in the audit trail to avoid logging PHI.
     *
     * @param id      the UUID of the patient to update.
     * @param request the validated update request containing fields to change.
     * @return the updated patient with masked SSN, wrapped in a {@code 200 OK} response.
     * @throws PatientNotFoundException if no active patient exists with the given ID.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "PATIENT_UPDATED", entityType = "PATIENT",
            detailExpression = "'Updated fields: ' + @patientService.getUpdatedFieldNames(#request)")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request) {

        Patient patient = patientService.updatePatient(id, request);
        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    /**
     * Soft-deletes a patient record. Restricted to ADMIN role only.
     *
     * <p>Per HIPAA §164.530(j), patient records are never physically deleted.
     * The soft-delete event is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param id the UUID of the patient to soft-delete.
     * @return a {@code 204 No Content} response.
     * @throws PatientNotFoundException if no active patient exists with the given ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Auditable(action = "PATIENT_DELETED", entityType = "PATIENT", detail = "Soft-deleted patient record")
    public ResponseEntity<Void> deletePatient(@PathVariable UUID id) {

        patientService.deletePatient(id);
        return ResponseEntity.noContent().build();
    }
}
