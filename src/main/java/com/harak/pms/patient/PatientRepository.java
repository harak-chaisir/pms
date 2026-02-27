package com.harak.pms.patient;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientRepository extends JpaRepository<Patient, UUID> {

    Optional<Patient> findByIdAndDeletedFalse(UUID id);

    Page<Patient> findAllByDeletedFalse(Pageable pageable);

    List<Patient> findAllByDeletedFalse();
}

