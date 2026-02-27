package com.harak.pms.clinicalrecord;

import com.harak.pms.patient.PatientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for managing clinical records.
 *
 * <p>Enforces application-layer referential integrity by validating patient existence
 * via {@link PatientService} before creating records. This replaces a database-level
 * foreign key constraint to preserve Spring Modulith module boundaries.
 *
 * <p>All PHI fields are transparently encrypted/decrypted by the JPA
 * {@link com.harak.pms.encryption.PhiEncryptionConverter} — business logic
 * in this service operates on plaintext values.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClinicalRecordService {

    private final ClinicalRecordRepository clinicalRecordRepository;
    private final PatientService patientService;

    /**
     * Creates a new clinical record for an existing patient.
     *
     * <p>Patient existence is validated at the application layer by delegating to
     * {@link PatientService#getPatientById(UUID)}, which throws
     * {@link com.harak.pms.patient.PatientNotFoundException} if the patient
     * does not exist or is soft-deleted.
     *
     * @param request the validated clinical record creation request.
     * @return the persisted {@link ClinicalRecord} entity with encrypted PHI fields.
     * @throws com.harak.pms.patient.PatientNotFoundException if no active patient exists with the given ID.
     */
    @Transactional
    public ClinicalRecord createClinicalRecord(CreateClinicalRecordRequest request) {
        // Application-layer referential integrity — validates patient exists and is not soft-deleted
        patientService.getPatientById(request.patientId());

        ClinicalRecord record = ClinicalRecord.builder()
                .id(UUID.randomUUID())
                .patientId(request.patientId())
                .recordType(request.recordType())
                .diagnosis(request.diagnosis())
                .treatmentPlan(request.treatmentPlan())
                .notes(request.notes())
                .medications(request.medications())
                .attendingPhysician(request.attendingPhysician())
                .visitDate(request.visitDate())
                .build();

        ClinicalRecord saved = clinicalRecordRepository.save(record);
        log.info("Clinical record created with ID: {} for patient ID: {}", saved.getId(), saved.getPatientId());
        return saved;
    }

    /**
     * Retrieves a clinical record by its unique identifier.
     *
     * @param id the UUID of the clinical record to retrieve.
     * @return the {@link ClinicalRecord} entity with decrypted PHI fields.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @Transactional(readOnly = true)
    public ClinicalRecord getClinicalRecordById(UUID id) {
        return clinicalRecordRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ClinicalRecordNotFoundException("Clinical record not found with ID: " + id));
    }

    /**
     * Retrieves a paginated list of clinical records for a given patient.
     *
     * <p>Optionally filters by {@code recordType} if provided. Patient existence is
     * validated before querying to return a clear 404 if the patient does not exist.
     *
     * @param patientId  the UUID of the patient whose records to retrieve.
     * @param recordType the optional record type filter (e.g., CONSULTATION, LAB_RESULT).
     * @param pageable   pagination and sorting parameters.
     * @return a page of {@link ClinicalRecord} entities for the specified patient.
     * @throws com.harak.pms.patient.PatientNotFoundException if no active patient exists with the given ID.
     */
    @Transactional(readOnly = true)
    public Page<ClinicalRecord> getClinicalRecordsByPatientId(UUID patientId, String recordType, Pageable pageable) {
        // Validate patient exists before querying records
        patientService.getPatientById(patientId);

        if (recordType != null && !recordType.isBlank()) {
            return clinicalRecordRepository.findAllByPatientIdAndRecordTypeAndDeletedFalse(
                    patientId, recordType, pageable);
        }
        return clinicalRecordRepository.findAllByPatientIdAndDeletedFalse(patientId, pageable);
    }

    /**
     * Updates an existing clinical record with the provided non-null fields.
     *
     * <p>Only fields present (non-null) in the request are applied. Unchanged fields
     * retain their current values. The list of updated field names is logged for auditing.
     *
     * @param id      the UUID of the clinical record to update.
     * @param request the validated update request containing fields to change.
     * @return the updated {@link ClinicalRecord} entity.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @Transactional
    public ClinicalRecord updateClinicalRecord(UUID id, UpdateClinicalRecordRequest request) {
        ClinicalRecord record = getClinicalRecordById(id);

        List<String> updatedFields = new ArrayList<>();

        if (request.recordType() != null) {
            record.setRecordType(request.recordType());
            updatedFields.add("recordType");
        }
        if (request.diagnosis() != null) {
            record.setDiagnosis(request.diagnosis());
            updatedFields.add("diagnosis");
        }
        if (request.treatmentPlan() != null) {
            record.setTreatmentPlan(request.treatmentPlan());
            updatedFields.add("treatmentPlan");
        }
        if (request.notes() != null) {
            record.setNotes(request.notes());
            updatedFields.add("notes");
        }
        if (request.medications() != null) {
            record.setMedications(request.medications());
            updatedFields.add("medications");
        }
        if (request.attendingPhysician() != null) {
            record.setAttendingPhysician(request.attendingPhysician());
            updatedFields.add("attendingPhysician");
        }
        if (request.visitDate() != null) {
            record.setVisitDate(request.visitDate());
            updatedFields.add("visitDate");
        }

        ClinicalRecord saved = clinicalRecordRepository.save(record);
        log.info("Clinical record updated with ID: {}. Fields changed: {}", id, updatedFields);
        return saved;
    }

    /**
     * Returns the list of field names that would be updated by the given request.
     *
     * <p>Used by the controller to build audit log detail strings without
     * including actual PHI values.
     *
     * @param request the update request to inspect.
     * @return a list of field names with non-null values in the request.
     */
    public List<String> getUpdatedFieldNames(UpdateClinicalRecordRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.recordType() != null) fields.add("recordType");
        if (request.diagnosis() != null) fields.add("diagnosis");
        if (request.treatmentPlan() != null) fields.add("treatmentPlan");
        if (request.notes() != null) fields.add("notes");
        if (request.medications() != null) fields.add("medications");
        if (request.attendingPhysician() != null) fields.add("attendingPhysician");
        if (request.visitDate() != null) fields.add("visitDate");
        return fields;
    }

    /**
     * Soft-deletes a clinical record by setting its {@code deleted} flag to {@code true}.
     *
     * <p>Per HIPAA §164.530(j), clinical records are never physically deleted.
     *
     * @param id the UUID of the clinical record to soft-delete.
     * @throws ClinicalRecordNotFoundException if no active clinical record exists with the given ID.
     */
    @Transactional
    public void deleteClinicalRecord(UUID id) {
        ClinicalRecord record = getClinicalRecordById(id);
        record.setDeleted(true);
        clinicalRecordRepository.save(record);
        log.info("Clinical record soft-deleted with ID: {}", id);
    }
}

