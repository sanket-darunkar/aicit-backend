package com.aicit.controller;

import com.aicit.dto.response.ApiResponse;
import com.aicit.entity.AuditLog;
import com.aicit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MCA_ADMIN')")
@Tag(name = "Admin – Audit Logs", description = "View audit logs")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    @Operation(summary = "List all audit logs (newest first, paginated)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> result = auditLogService.listAll(page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content",       result.getContent());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages",    result.getTotalPages());
        body.put("page",          result.getNumber());
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved", body));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    @Operation(summary = "List audit logs for a specific entity (e.g. Institute/42)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listByEntity(
            @PathVariable String entityType,
            @PathVariable Long   entityId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<AuditLog> result = auditLogService.listByEntity(entityType, entityId, page, size);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content",       result.getContent());
        body.put("totalElements", result.getTotalElements());
        body.put("totalPages",    result.getTotalPages());
        body.put("page",          result.getNumber());
        return ResponseEntity.ok(ApiResponse.success("Audit logs retrieved", body));
    }
}
