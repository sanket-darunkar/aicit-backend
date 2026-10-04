package com.aicit.controller;

import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.InstituteResponse;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.CertificateRepository;
import com.aicit.repository.InstituteRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.repository.StudentRepository;
import com.aicit.entity.Certificate;
import com.aicit.entity.InstituteUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/institute")
@RequiredArgsConstructor
@Tag(name = "Institute – Profile", description = "Institute profile and dashboard")
@SecurityRequirement(name = "bearerAuth")
public class InstituteProfileController {

    private final InstituteRepository    instituteRepository;
    private final InstituteUserRepository instituteUserRepository;
    private final StudentRepository      studentRepository;
    private final CertificateRepository  certificateRepository;
    private final PasswordEncoder        passwordEncoder;

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Get own institute profile")
    public ResponseEntity<ApiResponse<InstituteResponse>> getProfile(HttpServletRequest request) {
        Long instituteId = (Long) request.getAttribute("authenticatedInstituteId");
        if (instituteId == null) throw new IllegalStateException("Institute context not found.");
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved",
                InstituteResponse.from(instituteRepository.findById(instituteId)
                        .orElseThrow(() -> new ResourceNotFoundException("Institute", instituteId)))));
    }

    @GetMapping("/dashboard/stats")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Operation(summary = "Institute dashboard statistics")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getDashboardStats(HttpServletRequest request) {
        Long instituteId = (Long) request.getAttribute("authenticatedInstituteId");
        if (instituteId == null) throw new IllegalStateException("Institute context not found.");

        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("totalStudents",       studentRepository.countByInstituteId(instituteId));
        stats.put("totalCertificates",   certificateRepository.countByInstituteId(instituteId));
        stats.put("pendingCertificates", certificateRepository.countByInstituteIdAndStatus(
                instituteId, Certificate.Status.REQUESTED));
        stats.put("issuedCertificates",  certificateRepository.countByInstituteIdAndStatus(
                instituteId, Certificate.Status.ISSUED));
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", stats));
    }

    // ── Change Password (logged-in user) ──────────────────────
    @PostMapping("/profile/change-password")
    @PreAuthorize("hasAnyRole('INSTITUTE_ADMIN','INSTITUTE_STAFF')")
    @Transactional
    @Operation(summary = "Change own portal password",
               description = "Requires current password for verification. " +
                             "New password must be at least 8 characters.")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication auth) {

        String currentPassword = body.getOrDefault("currentPassword", "").trim();
        String newPassword     = body.getOrDefault("newPassword",     "").trim();

        if (currentPassword.isBlank())
            throw new IllegalStateException("Current password is required.");
        if (newPassword.length() < 8)
            throw new IllegalStateException("New password must be at least 8 characters.");
        if (newPassword.equals(currentPassword))
            throw new IllegalStateException("New password must be different from current password.");

        InstituteUser user = instituteUserRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (!passwordEncoder.matches(currentPassword, user.getPassword()))
            throw new IllegalStateException("Current password is incorrect.");

        user.setPassword(passwordEncoder.encode(newPassword));
        instituteUserRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success("Password changed successfully."));
    }
}
