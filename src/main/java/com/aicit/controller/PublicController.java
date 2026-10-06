package com.aicit.controller;

import com.aicit.dto.request.ContactRequest;
import com.aicit.dto.request.InstituteRegistrationRequest;
import com.aicit.dto.response.ApiResponse;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.CourseResponse;
import com.aicit.dto.response.InstituteResponse;
import com.aicit.entity.Certificate;
import com.aicit.entity.InstituteUser;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.CertificateRepository;
import com.aicit.repository.CertificateVerificationLogRepository;
import com.aicit.repository.CourseRepository;
import com.aicit.repository.InstituteRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.repository.StudentRepository;
import com.aicit.service.EmailService;
import com.aicit.service.InstituteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import com.aicit.entity.CertificateVerificationLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public", description = "Publicly accessible endpoints — no authentication required")
public class PublicController {

    private final CertificateRepository            certificateRepository;
    private final CertificateVerificationLogRepository vlogRepository;
    private final CourseRepository                 courseRepository;
    private final InstituteRepository              instituteRepository;
    private final InstituteUserRepository          instituteUserRepository;
    private final StudentRepository                studentRepository;
    private final InstituteService                 instituteService;
    private final EmailService                     emailService;
    private final PasswordEncoder                  passwordEncoder;

    // Where public contact-form submissions are delivered.
    @org.springframework.beans.factory.annotation.Value("${app.contact.recipient:info@aicit.org.in}")
    private String contactRecipient;

    // ── Health ────────────────────────────────────────────────
    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status",  "UP",
                "service", "AICIT Platform",
                "time",    LocalDateTime.now().toString()));
    }

    // ── Certificate Verification ──────────────────────────────
    @GetMapping("/certificates/verify/{certificateNumber}")
    @Operation(summary = "Verify certificate",
               description = "Public certificate verification. Logs every attempt. Returns safe public fields only.")
    public ResponseEntity<ApiResponse<CertificateResponse>> verifyCertificate(
            @PathVariable String certificateNumber,
            HttpServletRequest request) {

        Certificate cert = certificateRepository.findByCertificateNumber(certificateNumber)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Certificate not found: " + certificateNumber));

        // Log verification attempt
        var log = CertificateVerificationLog.builder()
                .certificate(cert)
                .certificateNumber(certificateNumber)
                .verifiedAt(LocalDateTime.now())
                .ipAddress(getClientIp(request))
                .userAgent(request.getHeader("User-Agent"))
                .result(cert.getStatus() == Certificate.Status.REVOKED
                        ? CertificateVerificationLog.Result.REVOKED
                        : CertificateVerificationLog.Result.FOUND)
                .build();
        vlogRepository.save(log);

        return ResponseEntity.ok(
                ApiResponse.success("Certificate found", CertificateResponse.publicView(cert)));
    }

    // ── Courses ───────────────────────────────────────────────
    @GetMapping("/courses")
    @Operation(summary = "List all active courses")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourses() {
        List<CourseResponse> courses = courseRepository.findByIsActiveTrueOrderByNameAsc()
                .stream().map(CourseResponse::from).toList();
        return ResponseEntity.ok(ApiResponse.success("Courses retrieved", courses));
    }

    // ── Institute Registration ────────────────────────────────
    @PostMapping("/institutes/register")
    @Operation(summary = "Submit institute registration application")
    public ResponseEntity<ApiResponse<InstituteResponse>> registerInstitute(
            @Valid @RequestBody InstituteRegistrationRequest request) {
        InstituteResponse response = instituteService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Application submitted successfully. Our team will review and contact you within 3-5 working days.",
                        response));
    }

    // ── Platform Stats ────────────────────────────────────────
    @GetMapping("/stats")
    @Operation(summary = "Public platform statistics")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStats() {
        Map<String, Long> stats = Map.of(
            "totalInstitutes",   instituteRepository.countByStatus(com.aicit.entity.Institute.Status.APPROVED),
            "totalStudents",     studentRepository.count(),
            "totalCertificates", certificateRepository.countByStatus(Certificate.Status.ISSUED)
        );
        return ResponseEntity.ok(ApiResponse.success("Stats retrieved", stats));
    }

    // ── Contact Form ──────────────────────────────────────────
    @PostMapping("/contact")
    @Operation(summary = "Submit contact form message")
    public ResponseEntity<ApiResponse<Void>> contact(
            @Valid @RequestBody ContactRequest req) {
        // Log the contact message — email admin notification
        String body = String.format("Contact Form Submission\n\nName: %s\nEmail: %s\nMobile: %s\nSubject: %s\n\nMessage:\n%s",
                req.getName(), req.getEmail(), req.getMobile(), req.getSubject(), req.getMessage());
        emailService.sendRawEmail(contactRecipient, "AICIT Contact Form: " + req.getSubject(), body);
        return ResponseEntity.ok(ApiResponse.success("Message received. We will get back to you shortly."));
    }

    // ── Forgot Password ───────────────────────────────────────
    @PostMapping("/forgot-password")
    @Transactional
    @Operation(summary = "Request password reset for institute portal",
               description = "Generates a new temporary password and emails it to the registered address. " +
                             "Always returns 200 to prevent email enumeration.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @RequestBody Map<String, String> body) {

        String email = body.getOrDefault("email", "").trim().toLowerCase();

        // Always return success — never reveal whether email exists (security)
        if (email.isBlank()) {
            return ResponseEntity.ok(ApiResponse.success(
                    "If that email is registered, a new password has been sent."));
        }

        instituteUserRepository.findByEmail(email).ifPresent(user -> {
            // Only reset for active users of approved institutes
            if (!user.isActive()) return;
            if (user.getInstitute().getStatus() != com.aicit.entity.Institute.Status.APPROVED) return;

            String newPassword = UUID.randomUUID().toString().substring(0, 10);
            user.setPassword(passwordEncoder.encode(newPassword));
            instituteUserRepository.save(user);

            log.info("Password reset requested for institute user: {}", email);

            emailService.sendPasswordResetEmail(
                    user.getEmail(),
                    user.getFullName(),
                    user.getInstitute().getName(),
                    newPassword);
        });

        return ResponseEntity.ok(ApiResponse.success(
                "If that email is registered, a new password has been sent."));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
