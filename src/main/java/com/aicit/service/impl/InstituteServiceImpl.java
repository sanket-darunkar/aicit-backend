package com.aicit.service.impl;

import com.aicit.dto.request.InstituteActionRequest;
import com.aicit.dto.request.InstituteRegistrationRequest;
import com.aicit.dto.response.InstituteResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.AdminUser;
import com.aicit.entity.Institute;
import com.aicit.entity.InstituteUser;
import com.aicit.exception.DataConflictException;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.AdminUserRepository;
import com.aicit.repository.InstituteRepository;
import com.aicit.repository.InstituteUserRepository;
import com.aicit.repository.StudentRepository;
import com.aicit.repository.CertificateRepository;
import com.aicit.service.InstituteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import com.aicit.service.AuditLogService;
import com.aicit.service.EmailService;

@Service
@RequiredArgsConstructor
@Slf4j
public class InstituteServiceImpl implements InstituteService {

    private final InstituteRepository     instituteRepository;
    private final InstituteUserRepository instituteUserRepository;
    private final AdminUserRepository     adminUserRepository;
    private final StudentRepository       studentRepository;
    private final CertificateRepository   certificateRepository;
    private final PasswordEncoder         passwordEncoder;
    private final EmailService            emailService;
    private final AuditLogService         auditLogService;

    // ── Institute Code Generator ─────────────────────────────
    private String generateInstituteCode() {
        long count = instituteRepository.count() + 1;
        return String.format("AICIT-INST-%04d", count);
    }

    @Override
    @Transactional
    public InstituteResponse register(InstituteRegistrationRequest req) {
        if (instituteRepository.existsByContactEmail(req.getContactEmail())) {
            throw new DataConflictException(
                    "An application already exists for email: " + req.getContactEmail());
        }

        Institute institute = Institute.builder()
                .instituteCode(generateInstituteCode())
                .name(req.getInstituteName())
                .type(req.getInstituteType())
                .addressLine1(req.getAddressLine1())
                .addressLine2(req.getAddressLine2())
                .city(req.getCity())
                .district(req.getDistrict())
                .state(req.getState())
                .pinCode(req.getPinCode())
                .websiteUrl(req.getWebsiteUrl())
                .contactPersonName(req.getContactPersonName())
                .contactEmail(req.getContactEmail())
                .contactMobile(req.getContactMobile())
                .status(Institute.Status.PENDING_REVIEW)
                .build();

        return InstituteResponse.from(instituteRepository.save(institute));
    }

    @Override
    public PagedResponse<InstituteResponse> listAll(Institute.Status status, String search,
                                                     int page, int size) {
        String statusStr = (status != null) ? status.name() : null;
        String searchParam = (search != null && search.isBlank()) ? null : search;
        Page<Institute> pageResult = instituteRepository.findByFilters(
                statusStr, searchParam, PageRequest.of(page, size));
        return PagedResponse.from(pageResult.map(InstituteResponse::from));
    }

    @Override
    public InstituteResponse getById(Long id) {
        return InstituteResponse.from(findOrThrow(id));
    }

    @Override
    @Transactional
    public InstituteResponse approve(Long id, Long adminId) {
        Institute institute = findOrThrow(id);

        if (institute.getStatus() != Institute.Status.PENDING_REVIEW
                && institute.getStatus() != Institute.Status.REJECTED) {
            throw new IllegalStateException("Only PENDING_REVIEW or REJECTED institutes can be approved.");
        }

        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));

        institute.setStatus(Institute.Status.APPROVED);
        institute.setApprovedAt(LocalDateTime.now());
        institute.setApprovedBy(admin);
        institute.setRejectionReason(null);
        instituteRepository.save(institute);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_APPROVED", "Institute", id,
                "Institute '" + institute.getName() + "' approved", null);

        // Create INSTITUTE_ADMIN user if none exists
        if (!instituteUserRepository.existsByEmail(institute.getContactEmail())) {
            String tempPassword = UUID.randomUUID().toString().substring(0, 10);
            InstituteUser user = InstituteUser.builder()
                    .institute(institute)
                    .email(institute.getContactEmail())
                    .password(passwordEncoder.encode(tempPassword))
                    .fullName(institute.getContactPersonName())
                    .mobile(institute.getContactMobile())
                    .role(InstituteUser.Role.INSTITUTE_ADMIN)
                    .isActive(true)
                    .build();
            instituteUserRepository.save(user);

            // Pre-fetch values before async email (avoids lazy load outside session)
            String emailAddr = institute.getContactEmail();
            String instName  = institute.getName();
            String contactNm = institute.getContactPersonName();

            // Send welcome email with credentials
            emailService.sendInstituteApprovalEmail(emailAddr, instName, contactNm, tempPassword);
            log.info("Created institute user for {} — temporary password sent by email", emailAddr);
        }

        return InstituteResponse.from(institute);
    }

    @Override
    @Transactional
    public InstituteResponse reject(Long id, Long adminId, InstituteActionRequest req) {
        Institute institute = findOrThrow(id);
        if (institute.getStatus() != Institute.Status.PENDING_REVIEW) {
            throw new IllegalStateException("Only PENDING_REVIEW institutes can be rejected.");
        }
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        institute.setStatus(Institute.Status.REJECTED);
        institute.setRejectionReason(req.getReason());
        Institute saved = instituteRepository.save(institute);
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_REJECTED", "Institute", id,
                "Institute '" + institute.getName() + "' rejected. Reason: " + req.getReason(), null);
        emailService.sendInstituteRejectionEmail(
                institute.getContactEmail(),
                institute.getName(),
                institute.getContactPersonName(),
                req.getReason());
        return InstituteResponse.from(saved);
    }

    @Override
    @Transactional
    public InstituteResponse suspend(Long id, Long adminId, InstituteActionRequest req) {
        Institute institute = findOrThrow(id);
        if (institute.getStatus() != Institute.Status.APPROVED) {
            throw new IllegalStateException("Only APPROVED institutes can be suspended.");
        }
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        institute.setStatus(Institute.Status.SUSPENDED);
        institute.setSuspendedAt(LocalDateTime.now());
        institute.setSuspensionReason(req.getReason());
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_SUSPENDED", "Institute", id,
                "Institute '" + institute.getName() + "' suspended. Reason: " + req.getReason(), null);
        return InstituteResponse.from(instituteRepository.save(institute));
    }

    @Override
    @Transactional
    public InstituteResponse activate(Long id, Long adminId) {
        Institute institute = findOrThrow(id);
        if (institute.getStatus() != Institute.Status.SUSPENDED
                && institute.getStatus() != Institute.Status.DEACTIVATED) {
            throw new IllegalStateException("Only SUSPENDED or DEACTIVATED institutes can be re-activated.");
        }
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        institute.setStatus(Institute.Status.APPROVED);
        institute.setSuspendedAt(null);
        institute.setSuspensionReason(null);
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_ACTIVATED", "Institute", id,
                "Institute '" + institute.getName() + "' re-activated", null);
        return InstituteResponse.from(instituteRepository.save(institute));
    }

    @Override
    @Transactional
    public InstituteResponse deactivate(Long id, Long adminId) {
        Institute institute = findOrThrow(id);
        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
        institute.setStatus(Institute.Status.DEACTIVATED);
        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_DEACTIVATED", "Institute", id,
                "Institute '" + institute.getName() + "' deactivated", null);
        return InstituteResponse.from(instituteRepository.save(institute));
    }

    @Override
    @Transactional
    public void delete(Long id, Long adminId) {
        Institute institute = findOrThrow(id);

        // Safety: only allow deletion of PENDING_REVIEW or REJECTED institutes
        if (institute.getStatus() != Institute.Status.PENDING_REVIEW
                && institute.getStatus() != Institute.Status.REJECTED) {
            throw new IllegalStateException(
                "Only PENDING_REVIEW or REJECTED institutes can be deleted. " +
                "Deactivate the institute first before deleting.");
        }

        // Safety: block if any students exist under this institute
        long studentCount = studentRepository.countByInstituteId(id);
        if (studentCount > 0) {
            throw new IllegalStateException(
                "Cannot delete institute with " + studentCount + " student(s). " +
                "Remove all students first.");
        }

        // Safety: block if any certificates exist
        long certCount = certificateRepository.countByInstituteId(id);
        if (certCount > 0) {
            throw new IllegalStateException(
                "Cannot delete institute with " + certCount + " certificate record(s).");
        }

        AdminUser admin = adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));

        // Remove institute users first (FK constraint)
        instituteUserRepository.deleteByInstituteId(id);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "INSTITUTE_DELETED", "Institute", id,
                "Institute '" + institute.getName() + "' permanently deleted", null);

        instituteRepository.delete(institute);
    }

    private Institute findOrThrow(Long id) {
        return instituteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", id));
    }
}
