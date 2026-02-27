package com.harak.pms.clinicalrecord;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link ClinicalRecord} entities.
 *
 * <p>All queries filter by {@code deleted = false} to enforce the soft-delete
 * pattern required by HIPAA §164.530(j).
 */
@Repository
public interface ClinicalRecordRepository extends JpaRepository<ClinicalRecord, UUID> {

    Optional<ClinicalRecord> findByIdAndDeletedFalse(UUID id);

    Page<ClinicalRecord> findAllByPatientIdAndDeletedFalse(UUID patientId, Pageable pageable);

    Page<ClinicalRecord> findAllByPatientIdAndRecordTypeAndDeletedFalse(UUID patientId, String recordType,
                                                                        Pageable pageable);
}

