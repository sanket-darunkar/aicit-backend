package com.aicit.controller;

import com.aicit.dto.request.CertificateActionRequest;
import com.aicit.dto.request.ResetPasswordRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.AdminUser;
import com.aicit.entity.InstituteUser;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.AdminUserRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.service.CertificateService;
import com.aicit.service.EmailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','MCA_ADMIN')")
@Tag(name = "Admin – Certificates & Password Reset", description = "Admin certificate management + password reset")
@SecurityRequirement(name = "bearerAuth")
public class AdminCertificateController {

    private final CertificateService      certService;
    private final AdminUserRepository     adminUserRepository;
    private final InstituteUserRepository instituteUserRepository;
    private final PasswordEncoder         passwordEncoder;
    private final EmailService            emailService;

    // ── Certificates ──────────────────────────────────────────

    @GetMapping("/certificates")
    @Operation(summary = "List all certificates (filterable by status/search)")
    public ResponseEntity<ApiResponse<PagedResponse<CertificateResponse>>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "20")  int size) {
        return ResponseEntity.ok(ApiResponse.success("Certificates retrieved",
                certService.listAll(status, search, page, Math.min(size, 100))));
    }

    @GetMapping("/certificates/{id}")
    @Operation(summary = "Get certificate by ID")
    public ResponseEntity<ApiResponse<CertificateResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Certificate retrieved", certService.adminGet(id)));
    }

    @PostMapping("/certificates/{id}/approve")
    @Operation(summary = "Approve certificate — generates cert number and PDF")
    public ResponseEntity<ApiResponse<CertificateResponse>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) CertificateActionRequest req,
            Authentication auth) {
        if (req == null) req = new CertificateActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Certificate approved",
                certService.approve(id, getAdminId(auth), req)));
    }

    @PostMapping("/certificates/{id}/reject")
    @Operation(summary = "Reject certificate request")
    public ResponseEntity<ApiResponse<CertificateResponse>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) CertificateActionRequest req,
            Authentication auth) {
        if (req == null) req = new CertificateActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Certificate rejected",
                certService.reject(id, getAdminId(auth), req)));
    }

    @PostMapping("/certificates/{id}/revoke")
    @Operation(summary = "Revoke an issued certificate")
    public ResponseEntity<ApiResponse<CertificateResponse>> revoke(
            @PathVariable Long id,
            @RequestBody(required = false) CertificateActionRequest req,
            Authentication auth) {
        if (req == null) req = new CertificateActionRequest();
        return ResponseEntity.ok(ApiResponse.success("Certificate revoked",
                certService.revoke(id, getAdminId(auth), req)));
    }

    @GetMapping("/certificates/{id}/download")
    @Operation(summary = "Download certificate PDF (admin)")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        byte[] pdf = certService.adminDownloadPdf(id);
        if (pdf == null) return ResponseEntity.noContent().build();
        CertificateResponse cert = certService.adminGet(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + cert.getCertificateNumber() + ".pdf\"")
                .body(pdf);
    }

    // ── Password Reset ────────────────────────────────────────

    @PostMapping("/institutes/{id}/reset-password")
    @org.springframework.transaction.annotation.Transactional
    @Operation(summary = "Reset institute login password",
               description = "Generates a new password for the institute's INSTITUTE_ADMIN user and sends it by email.")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @PathVariable Long id,
            @RequestBody(required = false) ResetPasswordRequest req) {

        List<InstituteUser> users = instituteUserRepository
                .findByInstituteIdAndRole(id, InstituteUser.Role.INSTITUTE_ADMIN);

        if (users.isEmpty()) {
            throw new ResourceNotFoundException("No INSTITUTE_ADMIN user found for institute " + id);
        }

        InstituteUser user = users.get(0);

        // Read institute name while still in transaction context
        String instituteName = user.getInstitute().getName();
        String userEmail     = user.getEmail();
        String userFullName  = user.getFullName();

        String newPassword = (req != null && req.getNewPassword() != null && !req.getNewPassword().isBlank())
                ? req.getNewPassword()
                : UUID.randomUUID().toString().substring(0, 10);

        user.setPassword(passwordEncoder.encode(newPassword));
        instituteUserRepository.save(user);

        // Send email with new password (async — lazy loading safe because we pre-fetched data above)
        emailService.sendPasswordResetEmail(userEmail, userFullName, instituteName, newPassword);

        log.info("Password reset for institute {} user {} — new password sent by email", id, userEmail);

        return ResponseEntity.ok(ApiResponse.success(
                "Password reset successfully. New password sent to " + userEmail,
                newPassword));
    }

    // ── Helpers ───────────────────────────────────────────────

    private Long getAdminId(Authentication auth) {
        return adminUserRepository.findByEmail(auth.getName())
                .map(AdminUser::getId).orElse(0L);
    }

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AdminCertificateController.class);
}
