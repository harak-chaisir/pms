package com.harak.pms.patient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;

    @Transactional
    public Patient createPatient(CreatePatientRequest request) {
        if (existsByMedicalRecordNumber(request.medicalRecordNumber())) {
            throw new IllegalArgumentException("A patient with this medical record number already exists");
        }

        Patient patient = Patient.builder()
                .id(UUID.randomUUID())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .ssn(request.ssn())
                .email(request.email())
                .dateOfBirth(request.dateOfBirth())
                .medicalRecordNumber(request.medicalRecordNumber())
                .build();

        Patient saved = patientRepository.save(patient);
        log.info("Patient created with ID: {}", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Patient getPatientById(UUID id) {
        return patientRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + id));
    }

    /**
     * Looks up a patient by MRN. Because MRN is encrypted with AES-GCM (non-deterministic),
     * we must load all active patients and compare the decrypted values in memory.
     */
    @Transactional(readOnly = true)
    public Patient getPatientByMedicalRecordNumber(String mrn) {
        return patientRepository.findAllByDeletedFalse().stream()
                .filter(p -> mrn.equals(p.getMedicalRecordNumber()))
                .findFirst()
                .orElseThrow(() -> new PatientNotFoundException("Patient not found with MRN: " + mrn));
    }

    @Transactional(readOnly = true)
    public Page<Patient> getAllPatients(Pageable pageable) {
        return patientRepository.findAllByDeletedFalse(pageable);
    }

    @Transactional
    public Patient updatePatient(UUID id, UpdatePatientRequest request) {
        Patient patient = getPatientById(id);

        List<String> updatedFields = new ArrayList<>();

        if (request.firstName() != null) {
            patient.setFirstName(request.firstName());
            updatedFields.add("firstName");
        }
        if (request.lastName() != null) {
            patient.setLastName(request.lastName());
            updatedFields.add("lastName");
        }
        if (request.ssn() != null) {
            patient.setSsn(request.ssn());
            updatedFields.add("ssn");
        }
        if (request.email() != null) {
            patient.setEmail(request.email());
            updatedFields.add("email");
        }
        if (request.dateOfBirth() != null) {
            patient.setDateOfBirth(request.dateOfBirth());
            updatedFields.add("dateOfBirth");
        }

        Patient saved = patientRepository.save(patient);
        log.info("Patient updated with ID: {}. Fields changed: {}", id, updatedFields);
        return saved;
    }

    /**
     * Returns a list of field names that were updated, useful for audit logging.
     */
    public List<String> getUpdatedFieldNames(UpdatePatientRequest request) {
        List<String> fields = new ArrayList<>();
        if (request.firstName() != null) fields.add("firstName");
        if (request.lastName() != null) fields.add("lastName");
        if (request.ssn() != null) fields.add("ssn");
        if (request.email() != null) fields.add("email");
        if (request.dateOfBirth() != null) fields.add("dateOfBirth");
        return fields;
    }

    @Transactional
    public void deletePatient(UUID id) {
        Patient patient = getPatientById(id);
        patient.setDeleted(true);
        patientRepository.save(patient);
        log.info("Patient soft-deleted with ID: {}", id);
    }

    /**
     * Checks if a patient with the given MRN already exists.
     * Since MRN is encrypted with AES-GCM (random IV), we cannot query by encrypted value.
     * Instead, we load all active patients and compare decrypted MRNs in memory.
     */
    private boolean existsByMedicalRecordNumber(String mrn) {
        return patientRepository.findAllByDeletedFalse().stream()
                .anyMatch(p -> mrn.equals(p.getMedicalRecordNumber()));
    }
}

