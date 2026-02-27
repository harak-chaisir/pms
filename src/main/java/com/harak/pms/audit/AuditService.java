package com.harak.pms.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service responsible for persisting audit log entries to the database.
 *
 * <p>Every audit event is written in a <strong>new transaction</strong>
 * ({@link Propagation#REQUIRES_NEW}) so that the audit record is persisted
 * even if the enclosing business transaction rolls back. This satisfies
 * HIPAA §164.312(b) — audit controls must record and examine activity in
 * information systems that contain or use electronic PHI.
 *
 * <p>This service is invoked automatically by {@link AuditAspect} for
 * controller methods annotated with {@link Auditable}, and directly by
 * non-AOP callers such as {@link AuthenticationAuditListener},
 * {@link com.harak.pms.security.CustomAccessDeniedHandler}, and
 * {@link com.harak.pms.user.LoginAttemptService}.
 *
 * @see Auditable
 * @see AuditAspect
 * @see AuditLog
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records an audit event without IP address or detail information.
     *
     * <p>Convenience overload that delegates to
     * {@link #logEvent(String, String, UUID, String, String, String)} with
     * {@code null} for {@code ipAddress} and {@code detail}.
     *
     * @param action      the action performed (e.g., {@code "USER_REGISTERED"}).
     * @param entityType  the type of entity acted upon (e.g., {@code "USER"}).
     * @param entityId    the UUID of the affected entity, or {@code null} if not applicable.
     * @param performedBy the username of the authenticated principal who performed the action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String entityType, UUID entityId, String performedBy) {
        logEvent(action, entityType, entityId, performedBy, null, null);
    }

    /**
     * Records a full audit event with IP address and detail information.
     *
     * <p>Persists an {@link AuditLog} entry in a new transaction. The caller is
     * responsible for ensuring that no PHI values are included in the
     * {@code detail} string — only field names, entity IDs, and action
     * descriptions are permitted per HIPAA §164.312(b).
     *
     * @param action      the action performed (e.g., {@code "PATIENT_CREATED"}).
     * @param entityType  the type of entity acted upon (e.g., {@code "PATIENT"}).
     * @param entityId    the UUID of the affected entity, or {@code null} if not applicable.
     * @param performedBy the username of the authenticated principal who performed the action.
     * @param ipAddress   the client IP address, or {@code null} if not available.
     * @param detail      a human-readable description of the event (must not contain PHI).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String entityType, UUID entityId, String performedBy,
                         String ipAddress, String detail) {
        AuditLog auditLog = AuditLog.builder()
                .id(UUID.randomUUID())
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(performedBy)
                .ipAddress(ipAddress)
                .detail(detail)
                .timestamp(Instant.now())
                .build();
        auditLogRepository.save(auditLog);
        log.debug("Audit event: action={}, entityType={}, performedBy={}, ip={}", action, entityType, performedBy, ipAddress);
    }
}
