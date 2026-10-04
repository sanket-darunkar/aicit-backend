package com.aicit.service;

import com.aicit.entity.AuditLog;
import com.aicit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Fire-and-forget: log an admin action asynchronously so it never blocks the main flow.
     */
    @Async
    public void logAdminAction(Long adminId, String adminEmail,
                               String action, String entityType, Long entityId,
                               String description, String ipAddress) {
        try {
            AuditLog log = AuditLog.builder()
                    .actorType("ADMIN")
                    .actorId(adminId)
                    .actorEmail(adminEmail)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .ipAddress(ipAddress)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception ex) {
            // Audit log failure must never break the business flow
        }
    }

    @Async
    public void logSystemAction(String action, String entityType, Long entityId, String description) {
        try {
            AuditLog log = AuditLog.builder()
                    .actorType("SYSTEM")
                    .actorId(0L)
                    .actorEmail("system")
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .build();
            auditLogRepository.save(log);
        } catch (Exception ignored) {}
    }

    public Page<AuditLog> listAll(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, Math.min(size, 100)));
    }

    public Page<AuditLog> listByEntity(String entityType, Long entityId, int page, int size) {
        return auditLogRepository.findByEntityTypeAndEntityId(
                entityType, entityId, PageRequest.of(page, Math.min(size, 100)));
    }
}
