package com.harak.pms.patient;

import com.harak.pms.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;
    private final AuditService auditService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    public ResponseEntity<PatientResponse> createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            HttpServletRequest httpRequest) {

        Patient patient = patientService.createPatient(request);

        auditService.logEvent(
                "PATIENT_CREATED",
                "PATIENT",
                patient.getId(),
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Created patient with MRN: " + patient.getMedicalRecordNumber()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(PatientResponse.from(patient));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    public ResponseEntity<PatientResponse> getPatientById(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        Patient patient = patientService.getPatientById(id);

        auditService.logEvent(
                "PATIENT_VIEWED",
                "PATIENT",
                patient.getId(),
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Viewed patient record"
        );

        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    public ResponseEntity<Page<PatientResponse>> getAllPatients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            HttpServletRequest httpRequest) {

        int clampedSize = Math.min(size, 100);
        Sort sort = direction.equalsIgnoreCase("asc")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, clampedSize, sort);

        Page<Patient> patients = patientService.getAllPatients(pageable);
        Page<PatientResponse> responsePage = patients.map(PatientResponse::from);

        auditService.logEvent(
                "PATIENT_LIST_VIEWED",
                "PATIENT",
                null,
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Listed patients — page: " + page + ", size: " + clampedSize
        );

        return ResponseEntity.ok(responsePage);
    }

    @GetMapping("/mrn/{mrn}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    public ResponseEntity<PatientResponse> getPatientByMrn(
            @PathVariable String mrn,
            HttpServletRequest httpRequest) {

        Patient patient = patientService.getPatientByMedicalRecordNumber(mrn);

        auditService.logEvent(
                "PATIENT_VIEWED",
                "PATIENT",
                patient.getId(),
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Viewed patient by MRN"
        );

        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_DOCTOR', 'ROLE_NURSE')")
    public ResponseEntity<PatientResponse> updatePatient(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePatientRequest request,
            HttpServletRequest httpRequest) {

        List<String> updatedFields = patientService.getUpdatedFieldNames(request);
        Patient patient = patientService.updatePatient(id, request);

        auditService.logEvent(
                "PATIENT_UPDATED",
                "PATIENT",
                patient.getId(),
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Updated fields: " + updatedFields
        );

        return ResponseEntity.ok(PatientResponse.from(patient));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<Void> deletePatient(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        patientService.deletePatient(id);

        auditService.logEvent(
                "PATIENT_DELETED",
                "PATIENT",
                id,
                getCurrentUsername(),
                getClientIp(httpRequest),
                "Soft-deleted patient record"
        );

        return ResponseEntity.noContent().build();
    }

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

