package com.aicit.controller;

import com.aicit.dto.request.InstituteActionRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.InstituteResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.dto.response.StudentResponse;
import com.aicit.repository.CertificateRepository;
import com.aicit.repository.InstituteRepository;
import com.aicit.repository.StudentRepository;
import com.aicit.entity.Certificate;
import com.aicit.entity.Institute;
import com.aicit.repository.AdminUserRepository;
import com.aicit.service.InstituteService;
import com.aicit.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MCA_ADMIN')")
@Tag(name = "Admin – Institutes", description = "Institute management (JWT required – admin roles)")
@SecurityRequirement(name = "bearerAuth")
public class AdminInstituteController {

    private final InstituteService     instituteService;
    private final StudentService       studentService;
    private final AdminUserRepository  adminUserRepository;
    private final InstituteRepository  instituteRepository;
    private final StudentRepository    studentRepository;
    private final CertificateRepository certificateRepository;

    // ── Dashboard Stats ───────────────────────────────────────
    @GetMapping("/dashboard/stats")
    @Operation(summary = "Admin dashboard statistics")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getDashboardStats() {
        java.util.Map<String, Long> stats = new java.util.LinkedHashMap<>();
        stats.put("pendingApplications",  instituteRepository.countByStatus(Institute.Status.PENDING_REVIEW));
        stats.put("approvedInstitutes",   instituteRepository.countByStatus(Institute.Status.APPROVED));
        stats.put("suspendedInstitutes",  instituteRepository.countByStatus(Institute.Status.SUSPENDED));
        stats.put("totalStudents",        studentRepository.count());
        stats.put("pendingCertificates",  certificateRepository.countByStatus(Certificate.Status.REQUESTED));
        stats.put("issuedCertificates",   certificateRepository.countByStatus(Certificate.Status.ISSUED));
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", stats));
    }

    // ── Institutes ────────────────────────────────────────────
    @GetMapping("/institutes")
    @Operation(summary = "List all institutes", description = "Filterable by status and search term.")
    public ResponseEntity<ApiResponse<PagedResponse<InstituteResponse>>> listInstitutes(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "15") int size) {

        Institute.Status statusEnum = null;
        if (status != null && !status.isBlank()) {
            try { statusEnum = Institute.Status.valueOf(status.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }
        return ResponseEntity.ok(ApiResponse.success("Institutes retrieved",
                instituteService.listAll(statusEnum, search, page, Math.min(size, 100))));
    }

    @GetMapping("/institutes/{id}")
    @Operation(summary = "Get institute by ID")
    public ResponseEntity<ApiResponse<InstituteResponse>> getInstitute(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Institute retrieved",
                instituteService.getById(id)));
    }

    @PostMapping("/institutes/{id}/approve")
    @Operation(summary = "Approve institute application")
    public ResponseEntity<ApiResponse<InstituteResponse>> approve(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Institute approved",
                instituteService.approve(id, getAdminId(auth))));
    }

    @PostMapping("/institutes/{id}/reject")
    @Operation(summary = "Reject institute application")
    public ResponseEntity<ApiResponse<InstituteResponse>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) InstituteActionRequest req,
            Authentication auth) {
        if (req == null) req = new InstituteActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Institute rejected",
                instituteService.reject(id, getAdminId(auth), req)));
    }

    @PostMapping("/institutes/{id}/suspend")
    @Operation(summary = "Suspend an approved institute")
    public ResponseEntity<ApiResponse<InstituteResponse>> suspend(
            @PathVariable Long id,
            @RequestBody(required = false) InstituteActionRequest req,
            Authentication auth) {
        if (req == null) req = new InstituteActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Institute suspended",
                instituteService.suspend(id, getAdminId(auth), req)));
    }

    @DeleteMapping("/institutes/{id}")
    @Operation(summary = "Delete an institute",
               description = "Only PENDING_REVIEW or REJECTED institutes with no students or certificates can be deleted.")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long id, Authentication auth) {
        instituteService.delete(id, getAdminId(auth));
        return ResponseEntity.ok(ApiResponse.success("Institute deleted"));
    }

    @PostMapping("/institutes/{id}/activate")
    @Operation(summary = "Re-activate a suspended institute")
    public ResponseEntity<ApiResponse<InstituteResponse>> activate(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Institute activated",
                instituteService.activate(id, getAdminId(auth))));
    }

    @PostMapping("/institutes/{id}/deactivate")
    @Operation(summary = "Deactivate an institute permanently")
    public ResponseEntity<ApiResponse<InstituteResponse>> deactivate(
            @PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success("Institute deactivated",
                instituteService.deactivate(id, getAdminId(auth))));
    }

    // ── Admin Student Browser ─────────────────────────────────
    @GetMapping("/students")
    @Operation(summary = "Platform-wide student list")
    public ResponseEntity<ApiResponse<PagedResponse<StudentResponse>>> listStudents(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long   instituteId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success("Students retrieved",
                studentService.listAll(search, instituteId, page, Math.min(size, 100))));
    }

    @GetMapping("/students/{id}")
    @Operation(summary = "Get any student by ID (admin)")
    public ResponseEntity<ApiResponse<StudentResponse>> getStudent(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Student retrieved",
                studentService.adminGetById(id)));
    }

    // ── Helpers ───────────────────────────────────────────────
    private Long getAdminId(Authentication auth) {
        return adminUserRepository.findByEmail(auth.getName())
                .map(a -> a.getId())
                .orElse(0L);
    }
}
