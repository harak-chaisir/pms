package com.harak.pms.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logEvent(String action, String entityType, UUID entityId, String performedBy) {
        logEvent(action, entityType, entityId, performedBy, null, null);
    }

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

