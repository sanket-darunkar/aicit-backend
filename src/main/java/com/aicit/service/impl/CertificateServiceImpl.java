package com.aicit.service.impl;

import com.aicit.dto.request.CertificateActionRequest;
import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.*;
import com.aicit.exception.DataConflictException;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.*;
import com.aicit.service.AuditLogService;
import com.aicit.service.CertificateService;
import com.aicit.service.EmailService;
import com.aicit.service.PdfGeneratorService;
import com.aicit.util.CertificateNumberGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class CertificateServiceImpl implements CertificateService {

    private final CertificateRepository          certificateRepository;
    private final StudentRepository              studentRepository;
    private final CourseRepository               courseRepository;
    private final InstituteRepository            instituteRepository;
    private final AdminUserRepository            adminUserRepository;
    private final InstituteUserRepository        instituteUserRepository;
    private final PdfGeneratorService            pdfGenerator;
    private final CertificateNumberGenerator     certNumGenerator;
    private final EmailService                   emailService;
    private final AuditLogService                auditLogService;

    // ── Institute: request certificate ───────────────────────

    @Override
    @Transactional
    public CertificateResponse requestCertificate(Long instituteId, CertificateRequestDto req) {
        Institute institute = instituteRepository.findById(instituteId)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", instituteId));

        Student student = studentRepository.findByIdAndInstituteId(req.getStudentId(), instituteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found or does not belong to your institute."));

        Course course = courseRepository.findById(req.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.getCourseId()));

        // Check no duplicate pending/issued cert for this student+course
        if (!certificateRepository.findActiveByStudentAndCourse(
                req.getStudentId(), req.getCourseId(),
                java.util.List.of(Certificate.Status.REJECTED, Certificate.Status.REVOKED)
        ).isEmpty()) {
            throw new DataConflictException(
                "A certificate request already exists for this student and course.");
        }

        Certificate cert = Certificate.builder()
                .institute(institute)
                .student(student)
                .course(course)
                .marks(req.getMarks())
                .grade(req.getGrade())
                .status(Certificate.Status.REQUESTED)
                .build();

        return CertificateResponse.from(certificateRepository.save(cert));
    }

    @Override
    public PagedResponse<CertificateResponse> listByInstitute(Long instituteId, String status,
                                                                int page, int size) {
        Certificate.Status statusEnum = parseStatus(status);
        Page<Certificate> result = certificateRepository.findByInstituteIdFiltered(
                instituteId, statusEnum, PageRequest.of(page, size));
        return PagedResponse.from(result.map(CertificateResponse::from));
    }

    @Override
    public CertificateResponse getByInstitute(Long instituteId, Long certId) {
        return CertificateResponse.from(findByInstituteOrThrow(instituteId, certId));
    }

    @Override
    public byte[] downloadPdf(Long instituteId, Long certId) {
        Certificate cert = findByInstituteOrThrow(instituteId, certId);
        if (cert.getStatus() != Certificate.Status.ISSUED) {
            throw new IllegalStateException("Certificate PDF is only available for ISSUED certificates.");
        }
        return ensurePdf(cert);
    }

    // ── Admin ─────────────────────────────────────────────────

    @Override
    public PagedResponse<CertificateResponse> listAll(String status, String search,
                                                       int page, int size) {
        String statusStr = StringUtils.hasText(status) ? status.toUpperCase() : null;
        String searchStr = StringUtils.hasText(search) ? search : null;
        Page<Certificate> result = certificateRepository.findAllFiltered(
                statusStr, searchStr, PageRequest.of(page, size));
        return PagedResponse.from(result.map(CertificateResponse::from));
    }

    @Override
    public CertificateResponse adminGet(Long certId) {
        return CertificateResponse.from(findOrThrow(certId));
    }

    @Override
    @Transactional
    public CertificateResponse approve(Long certId, Long adminId, CertificateActionRequest req) {
        Certificate cert = findOrThrow(certId);

        if (cert.getStatus() != Certificate.Status.REQUESTED
                && cert.getStatus() != Certificate.Status.UNDER_REVIEW) {
            throw new IllegalStateException("Only REQUESTED or UNDER_REVIEW certificates can be approved.");
        }

        // Payment gate — a certificate can only be approved after its payment is verified.
        if (cert.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException(
                "Payment must be verified (PAID) before this certificate can be approved. Current payment status: "
                        + (cert.getPaymentStatus() == null ? "NONE" : cert.getPaymentStatus()));
        }

        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));

        // Determine certificate number: use admin-supplied value or auto-generate
        String certNumber;
        if (StringUtils.hasText(req.getCustomCertNumber())) {
            String custom = req.getCustomCertNumber().trim().toUpperCase();
            // Format validation: AICIT-YYYY-XXXXXX
            if (!custom.matches("^AICIT-\\d{4}-\\d{6}$")) {
                throw new IllegalStateException(
                    "Invalid certificate number format. Expected: AICIT-YYYY-XXXXXX (e.g. AICIT-2026-000042)");
            }
            // Uniqueness check
            if (certificateRepository.findByCertificateNumber(custom).isPresent()) {
                throw new DataConflictException(
                    "Certificate number " + custom + " is already in use.");
            }
            certNumber = custom;
            log.info("Admin supplied custom certificate number: {}", certNumber);
        } else {
            certNumber = generateUniqueCertNumber();
            log.info("Auto-generated certificate number: {}", certNumber);
        }
        cert.setCertificateNumber(certNumber);
        cert.setStatus(Certificate.Status.APPROVED);
        cert.setReviewedBy(admin);
        cert.setReviewedAt(LocalDateTime.now());
        cert.setIssueDate(LocalDate.now());

        if (StringUtils.hasText(req.getMarks())) cert.setMarks(req.getMarks());
        if (StringUtils.hasText(req.getGrade())) cert.setGrade(req.getGrade());

        // Generate PDF
        try {
            byte[] pdf = pdfGenerator.generateCertificatePdf(cert);
            cert.setPdfData(pdf);
            cert.setPdfGeneratedAt(LocalDateTime.now());
            cert.setStatus(Certificate.Status.ISSUED);
            log.info("Certificate PDF generated: {}", certNumber);
        } catch (Exception e) {
            log.error("PDF generation failed for {}: {}", certNumber, e.getMessage());
            cert.setStatus(Certificate.Status.ISSUED); // still issue even without PDF
        }

        Certificate saved = certificateRepository.save(cert);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "CERT_ISSUED", "Certificate", saved.getId(),
                "Certificate " + certNumber + " issued for student '"
                        + cert.getStudent().getFirstName() + " " + cert.getStudent().getSurname() + "'", null);

        // Notify institute
        String contactEmail = cert.getInstitute().getContactEmail();
        String contactName  = cert.getInstitute().getContactPersonName();
        String studentName  = cert.getStudent().getFirstName() + " " + cert.getStudent().getSurname();
        emailService.sendCertificateReadyEmail(contactEmail, contactName,
                cert.getInstitute().getName(), studentName, certNumber);

        return CertificateResponse.from(saved);
    }

    @Override
    @Transactional
    public CertificateResponse reject(Long certId, Long adminId, CertificateActionRequest req) {
        Certificate cert = findOrThrow(certId);
        if (cert.getStatus() != Certificate.Status.REQUESTED
                && cert.getStatus() != Certificate.Status.UNDER_REVIEW) {
            throw new IllegalStateException("Only REQUESTED or UNDER_REVIEW certificates can be rejected.");
        }
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        cert.setStatus(Certificate.Status.REJECTED);
        cert.setRejectionReason(req.getReason());
        cert.setReviewedBy(admin);
        cert.setReviewedAt(LocalDateTime.now());
        Certificate saved = certificateRepository.save(cert);
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "CERT_REJECTED", "Certificate", certId,
                "Certificate request rejected. Reason: " + req.getReason(), null);
        return CertificateResponse.from(saved);
    }

    @Override
    @Transactional
    public CertificateResponse revoke(Long certId, Long adminId, CertificateActionRequest req) {
        Certificate cert = findOrThrow(certId);
        if (cert.getStatus() != Certificate.Status.ISSUED) {
            throw new IllegalStateException("Only ISSUED certificates can be revoked.");
        }
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        cert.setStatus(Certificate.Status.REVOKED);
        cert.setRevokedAt(LocalDateTime.now());
        cert.setRevokeReason(req.getReason());
        Certificate saved = certificateRepository.save(cert);
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "CERT_REVOKED", "Certificate", certId,
                "Certificate " + cert.getCertificateNumber() + " revoked. Reason: " + req.getReason(), null);
        return CertificateResponse.from(saved);
    }

    @Override
    public byte[] adminDownloadPdf(Long certId) {
        Certificate cert = findOrThrow(certId);
        if (cert.getStatus() != Certificate.Status.ISSUED) {
            return cert.getPdfData(); // null is acceptable for non-issued certs (controller returns 204)
        }
        return ensurePdf(cert);
    }

    // ── Helpers ───────────────────────────────────────────────

    /**
     * Returns the stored PDF bytes.  If the stored PDF is null (generation
     * failed at approval time), regenerates it on-demand, persists the result,
     * and returns the fresh bytes.
     *
     * <p>This ensures institutes and admins can always download a certificate
     * even if the initial generation encountered a transient error.
     */
    @Transactional
    protected byte[] ensurePdf(Certificate cert) {
        if (cert.getPdfData() != null) {
            return cert.getPdfData();
        }
        log.warn("PDF missing for ISSUED certificate {} — regenerating on-demand",
                cert.getCertificateNumber());
        try {
            byte[] pdf = pdfGenerator.generateCertificatePdf(cert);
            cert.setPdfData(pdf);
            cert.setPdfGeneratedAt(LocalDateTime.now());
            certificateRepository.save(cert);
            log.info("On-demand PDF regeneration succeeded for {}", cert.getCertificateNumber());
            return pdf;
        } catch (Exception e) {
            log.error("On-demand PDF regeneration failed for {}: {}",
                    cert.getCertificateNumber(), e.getMessage(), e);
            throw new RuntimeException(
                    "PDF generation failed for certificate " + cert.getCertificateNumber()
                            + ": " + e.getMessage(), e);
        }
    }

    private Certificate findOrThrow(Long id) {
        return certificateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate", id));
    }

    private Certificate findByInstituteOrThrow(Long instituteId, Long certId) {
        return certificateRepository.findByIdAndInstituteId(certId, instituteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Certificate not found or does not belong to your institute."));
    }

    private Certificate.Status parseStatus(String s) {
        if (!StringUtils.hasText(s)) return null;
        try { return Certificate.Status.valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    private String generateUniqueCertNumber() {
        int year = LocalDate.now().getYear();
        String prefix = "AICIT-" + year + "-";
        long maxSeq = certificateRepository.findCertNumbersByPrefix(prefix).stream()
                .mapToLong(n -> {
                    try { return Long.parseLong(n.substring(prefix.length())); }
                    catch (Exception e) { return 0L; }
                })
                .max().orElse(0L);
        return certNumGenerator.generate(maxSeq + 1);
    }
}
