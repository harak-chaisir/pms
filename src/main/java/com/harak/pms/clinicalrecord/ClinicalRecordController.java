package com.harak.pms.clinicalrecord;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for clinical record management endpoints.
 *
 * <p>All endpoints require authentication and role-based authorization.
 * Every action that creates, reads, updates, or deletes PHI is audit-logged
 * automatically via the {@link Auditable} AOP aspect per HIPAA §164.312(b).
 */
@RestController
@RequestMapping("/api/clinical-records")
@RequiredArgsConstructor
public class ClinicalRecordController {

    private final ClinicalRecordService clinicalRecordService;

    /**
     * Creates a new clinical record for an existing patient.
     *
     * <p>Patient existence is validated at the service layer. The creation event
     * is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param request the validated clinical record creation request.
     * @return the created clinical record, wrapped in a {@code 201 Created} response.
     * @throws com.harak.pms.patient.PatientNotFoundException if no active patient exists with the given ID.
     */
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "CLINICAL_RECORD_CREATED", entityType = "CLINICAL_RECORD",
            detailExpression = "'Created clinical record for patient ID: ' + #request.patientId()")
    public ResponseEntity<ClinicalRecordResponse> createClinicalRecord(
            @Valid @RequestBody CreateClinicalRecordRequest request) {

        ClinicalRecord record = clinicalRecordService.createClinicalRecord(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ClinicalRecordResponse.from(record));
    }

    /**
     * Retrieves a clinical record by its unique identifier.
     *
     * <p>Access is restricted to ADMIN, DOCTOR, and NURSE roles.
     * The access event is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param id the UUID of the clinical record to retrieve.
     * @return the clinical record data, wrapped in a {@code 200 OK} response.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "CLINICAL_RECORD_VIEWED", entityType = "CLINICAL_RECORD",
            detail = "Viewed clinical record")
    public ResponseEntity<ClinicalRecordResponse> getClinicalRecordById(@PathVariable UUID id) {

        ClinicalRecord record = clinicalRecordService.getClinicalRecordById(id);
        return ResponseEntity.ok(ClinicalRecordResponse.from(record));
    }

    /**
     * Retrieves a paginated list of clinical records for a given patient.
     *
     * <p>Supports optional filtering by {@code recordType} and configurable
     * pagination/sorting. Page size is clamped to a maximum of 100.
     *
     * @param patientId  the UUID of the patient whose records to retrieve.
     * @param recordType optional filter by record type (e.g., CONSULTATION, LAB_RESULT).
     * @param page       the zero-based page index (default 0).
     * @param size       the page size (default 20, max 100).
     * @param sortBy     the field to sort by (default {@code createdAt}).
     * @param direction  the sort direction: {@code asc} or {@code desc} (default {@code desc}).
     * @return a page of clinical records, wrapped in a {@code 200 OK} response.
     * @throws com.harak.pms.patient.PatientNotFoundException if no active patient exists with the given ID.
     */
    @GetMapping("/patient/{patientId}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "CLINICAL_RECORD_LIST_VIEWED", entityType = "CLINICAL_RECORD",
            detailExpression = "'Listed clinical records for patient ID: ' + #patientId"
                    + " + ' — page: ' + #page + ', size: ' + T(Math).min(#size, 100)"
                    + " + (#recordType != null ? ', recordType: ' + #recordType : '')")
    public ResponseEntity<Page<ClinicalRecordResponse>> getClinicalRecordsByPatientId(
            @PathVariable UUID patientId,
            @RequestParam(required = false) String recordType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {

        int clampedSize = Math.min(size, 100);
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        Page<ClinicalRecord> records = clinicalRecordService.getClinicalRecordsByPatientId(
                patientId, recordType, pageable);
        Page<ClinicalRecordResponse> responsePage = records.map(ClinicalRecordResponse::from);

        return ResponseEntity.ok(responsePage);
    }

    /**
     * Updates an existing clinical record with the provided non-null fields.
     *
     * <p>Only fields present in the request body are applied. The list of updated
     * field names (not values) is recorded in the audit trail.
     *
     * @param id      the UUID of the clinical record to update.
     * @param request the validated update request containing fields to change.
     * @return the updated clinical record, wrapped in a {@code 200 OK} response.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    @Auditable(action = "CLINICAL_RECORD_UPDATED", entityType = "CLINICAL_RECORD",
            detailExpression = "'Updated fields: ' + @clinicalRecordService.getUpdatedFieldNames(#request)")
    public ResponseEntity<ClinicalRecordResponse> updateClinicalRecord(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateClinicalRecordRequest request) {

        ClinicalRecord record = clinicalRecordService.updateClinicalRecord(id, request);
        return ResponseEntity.ok(ClinicalRecordResponse.from(record));
    }

    /**
     * Soft-deletes a clinical record. Restricted to ADMIN role only.
     *
     * <p>Per HIPAA §164.530(j), clinical records are never physically deleted.
     * The soft-delete event is recorded in the audit trail automatically via {@link Auditable}.
     *
     * @param id the UUID of the clinical record to soft-delete.
     * @return a {@code 204 No Content} response.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Auditable(action = "CLINICAL_RECORD_DELETED", entityType = "CLINICAL_RECORD",
            detail = "Soft-deleted clinical record")
    public ResponseEntity<Void> deleteClinicalRecord(@PathVariable UUID id) {

        clinicalRecordService.deleteClinicalRecord(id);
        return ResponseEntity.noContent().build();
    }
}

