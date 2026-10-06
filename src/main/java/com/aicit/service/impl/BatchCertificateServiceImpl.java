package com.aicit.service.impl;

import com.aicit.dto.request.BulkCertificateRow;
import com.aicit.dto.request.CertificateRequestDto;
import com.aicit.dto.request.PaymentActionRequest;
import com.aicit.dto.request.UtrSubmissionRequest;
import com.aicit.dto.response.BatchResponse;
import com.aicit.dto.response.CertificateResponse;
import com.aicit.dto.response.CsvValidationResponse;
import com.aicit.dto.response.PagedResponse;
import com.aicit.entity.*;
import com.aicit.exception.DataConflictException;
import com.aicit.exception.ResourceNotFoundException;
import com.aicit.repository.*;
import com.aicit.service.AuditLogService;
import com.aicit.service.BatchCertificateService;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class BatchCertificateServiceImpl implements BatchCertificateService {

    private static final BigDecimal UNIT_AMOUNT = new BigDecimal("250");
    private static final List<Certificate.Status> DUPLICATE_EXCLUDED =
            List.of(Certificate.Status.REJECTED, Certificate.Status.REVOKED);

    private static final int MAX_CSV_ROWS = 1000;

    private final CertificateBatchRepository batchRepository;
    private final CertificateRepository      certificateRepository;
    private final StudentRepository          studentRepository;
    private final CourseRepository           courseRepository;
    private final InstituteRepository        instituteRepository;
    private final AdminUserRepository        adminUserRepository;
    private final InstituteUserRepository    instituteUserRepository;
    private final PdfGeneratorService        pdfGenerator;
    private final CertificateNumberGenerator certNumGenerator;
    private final EmailService               emailService;
    private final AuditLogService            auditLogService;
    private final SequenceRepository         sequenceRepository;

    // ══════════════════════════════════════════════════════════
    //  SINGLE
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public BatchResponse createSingle(Long instituteId, Long instituteUserId,
                                      CertificateRequestDto req) {
        Institute institute = findInstitute(instituteId);

        Student student = studentRepository.findByIdAndInstituteId(req.getStudentId(), instituteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Student not found or does not belong to your institute."));

        Course course = courseRepository.findById(req.getCourseId())
                .orElseThrow(() -> new ResourceNotFoundException("Course", req.getCourseId()));

        assertNoActiveDuplicate(student.getId(), course.getId());

        // Amount is always server-computed
        CertificateBatch batch = newBatch(institute, CertificateBatch.Type.SINGLE, 1);
        batch.setSubmittedBy(resolveInstituteUser(instituteUserId));
        batch = batchRepository.save(batch);

        Certificate cert = Certificate.builder()
                .institute(institute)
                .student(student)
                .course(course)
                .marks(req.getMarks())
                .grade(req.getGrade())
                .status(Certificate.Status.REQUESTED)
                .batch(batch)
                .amount(UNIT_AMOUNT)
                .paymentStatus(PaymentStatus.PENDING)
                .build();
        certificateRepository.save(cert);

        batch.setBatchStatus(CertificateBatch.BatchStatus.VALIDATED);
        batchRepository.save(batch);

        return loadDetail(batch.getId());
    }

    // ══════════════════════════════════════════════════════════
    //  BULK — CSV validation (creates nothing)
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public CsvValidationResponse validateCsv(Long instituteId, MultipartFile file) {
        findInstitute(instituteId);
        List<ParsedRow> rows = parseCsv(file);

        List<CsvValidationResponse.ValidRow>   valid   = new ArrayList<>();
        List<CsvValidationResponse.InvalidRow> invalid = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();   // studentCode|courseCode within this CSV

        for (ParsedRow row : rows) {
            List<String> errors = new ArrayList<>();

            String studentCode = safe(row.studentId);
            String courseCode  = safe(row.courseCode);

            if (!StringUtils.hasText(studentCode)) errors.add("studentId is required");
            if (!StringUtils.hasText(courseCode))  errors.add("courseCode is required");

            Student student = null;
            Course  course  = null;

            if (StringUtils.hasText(studentCode)) {
                student = studentRepository
                        .findByInstituteIdAndStudentId(instituteId, studentCode)
                        .orElse(null);
                if (student == null) {
                    errors.add("Student '" + studentCode
                            + "' does not exist in your institute");
                }
            }

            if (StringUtils.hasText(courseCode)) {
                course = courseRepository.findByCode(courseCode).orElse(null);
                if (course == null) {
                    errors.add("Course code '" + courseCode + "' not found");
                }
            }

            // Duplicate WITHIN the CSV
            if (StringUtils.hasText(studentCode) && StringUtils.hasText(courseCode)) {
                String key = studentCode + "|" + courseCode;
                if (!seenInFile.add(key)) {
                    errors.add("Duplicate row for the same student and course in this file");
                }
            }

            // Duplicate against EXISTING active certificates
            if (student != null && course != null && errors.isEmpty()) {
                boolean hasActive = !certificateRepository.findActiveByStudentAndCourse(
                        student.getId(), course.getId(), DUPLICATE_EXCLUDED).isEmpty();
                if (hasActive) {
                    errors.add("An active certificate request already exists for this student and course");
                }
            }

            if (errors.isEmpty()) {
                valid.add(CsvValidationResponse.ValidRow.builder()
                        .rowNumber(row.rowNumber)
                        .studentCode(studentCode)
                        .studentName(student.getFirstName() + " " + student.getSurname())
                        .courseCode(courseCode)
                        .courseName(course.getName())
                        .marks(safe(row.marks))
                        .grade(safe(row.grade))
                        .build());
            } else {
                invalid.add(CsvValidationResponse.InvalidRow.builder()
                        .rowNumber(row.rowNumber)
                        .studentCode(studentCode)
                        .courseCode(courseCode)
                        .errors(errors)
                        .build());
            }
        }

        BigDecimal total = UNIT_AMOUNT.multiply(BigDecimal.valueOf(valid.size()));
        return CsvValidationResponse.builder()
                .totalRows(rows.size())
                .validCount(valid.size())
                .invalidCount(invalid.size())
                .unitAmount(UNIT_AMOUNT)
                .totalAmount(total)
                .validRows(valid)
                .invalidRows(invalid)
                .build();
    }

    // ══════════════════════════════════════════════════════════
    //  BULK — create batch from CSV (re-validates, skips invalid)
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public BatchResponse createBulk(Long instituteId, Long instituteUserId, MultipartFile file) {
        Institute institute = findInstitute(instituteId);
        List<ParsedRow> rows = parseCsv(file);

        // Resolve valid rows (same rules as validateCsv), server-side only
        record Resolved(Student student, Course course, String marks, String grade) {}
        List<Resolved> resolved = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();

        for (ParsedRow row : rows) {
            String studentCode = safe(row.studentId);
            String courseCode  = safe(row.courseCode);
            if (!StringUtils.hasText(studentCode) || !StringUtils.hasText(courseCode)) continue;

            Student student = studentRepository
                    .findByInstituteIdAndStudentId(instituteId, studentCode).orElse(null);
            Course course = courseRepository.findByCode(courseCode).orElse(null);
            if (student == null || course == null) continue;

            if (!seenInFile.add(studentCode + "|" + courseCode)) continue;

            boolean hasActive = !certificateRepository.findActiveByStudentAndCourse(
                    student.getId(), course.getId(), DUPLICATE_EXCLUDED).isEmpty();
            if (hasActive) continue;

            resolved.add(new Resolved(student, course, safe(row.marks), safe(row.grade)));
        }

        if (resolved.isEmpty()) {
            throw new DataConflictException(
                "No valid rows to create a batch. Validate the CSV and fix the reported errors.");
        }

        CertificateBatch batch = newBatch(institute, CertificateBatch.Type.BULK, resolved.size());
        batch.setSubmittedBy(resolveInstituteUser(instituteUserId));
        batch = batchRepository.save(batch);

        final CertificateBatch savedBatch = batch;
        List<Certificate> certs = resolved.stream()
                .map(r -> Certificate.builder()
                        .institute(institute)
                        .student(r.student())
                        .course(r.course())
                        .marks(r.marks())
                        .grade(r.grade())
                        .status(Certificate.Status.REQUESTED)
                        .batch(savedBatch)
                        .amount(UNIT_AMOUNT)
                        .paymentStatus(PaymentStatus.PENDING)
                        .build())
                .toList();
        certificateRepository.saveAll(certs);

        batch.setBatchStatus(CertificateBatch.BatchStatus.VALIDATED);
        batchRepository.save(batch);

        log.info("Created BULK batch {} with {} certificates for institute {}",
                batch.getBatchCode(), resolved.size(), instituteId);
        return loadDetail(batch.getId());
    }

    // ══════════════════════════════════════════════════════════
    //  Institute — UTR submission
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public BatchResponse submitUtr(Long instituteId, Long batchId, UtrSubmissionRequest req) {
        CertificateBatch batch = findBatchForInstitute(instituteId, batchId);

        if (batch.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("This batch is already paid.");
        }
        if (batch.getPaymentStatus() == PaymentStatus.REJECTED) {
            // allow re-submission after a rejection
            batch.setPaymentRejectionReason(null);
        }

        batch.setUtrNumber(req.getUtrNumber().trim());
        batch.setPaymentStatus(PaymentStatus.VERIFICATION_PENDING);
        batch.setBatchStatus(CertificateBatch.BatchStatus.SUBMITTED);
        applyPaymentStatusToCerts(batch, PaymentStatus.VERIFICATION_PENDING);
        batchRepository.save(batch);

        return loadDetail(batch.getId());
    }

    // ══════════════════════════════════════════════════════════
    //  Institute — read
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BatchResponse> listByInstitute(Long instituteId, String paymentStatus,
                                                        int page, int size) {
        PaymentStatus ps = parsePaymentStatus(paymentStatus);
        Page<CertificateBatch> result = batchRepository.findByInstituteFiltered(
                instituteId, ps, PageRequest.of(page, Math.min(size, 100)));
        return PagedResponse.from(result.map(BatchResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResponse getByInstitute(Long instituteId, Long batchId) {
        findBatchForInstitute(instituteId, batchId);
        return loadDetail(batchId);
    }

    // ══════════════════════════════════════════════════════════
    //  Admin — payment verify / reject
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<BatchResponse> listAll(String paymentStatus, int page, int size) {
        PaymentStatus ps = parsePaymentStatus(paymentStatus);
        Page<CertificateBatch> result = batchRepository.findAllFiltered(
                ps, PageRequest.of(page, Math.min(size, 100)));
        return PagedResponse.from(result.map(BatchResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public BatchResponse adminGet(Long batchId) {
        findBatch(batchId);
        return loadDetail(batchId);
    }

    @Override
    @Transactional
    public BatchResponse verifyPayment(Long batchId, Long adminId, PaymentActionRequest req) {
        CertificateBatch batch = findBatch(batchId);
        AdminUser admin = findAdmin(adminId);

        if (batch.getPaymentStatus() != PaymentStatus.VERIFICATION_PENDING
                && batch.getPaymentStatus() != PaymentStatus.UTR_SUBMITTED) {
            throw new IllegalStateException(
                "Only batches awaiting verification can be marked PAID. Current: "
                        + batch.getPaymentStatus());
        }
        if (!StringUtils.hasText(batch.getUtrNumber())) {
            throw new IllegalStateException("Cannot verify a batch with no UTR submitted.");
        }

        batch.setPaymentStatus(PaymentStatus.PAID);
        batch.setPaidAt(LocalDateTime.now());
        batch.setVerifiedBy(admin);
        batch.setBatchStatus(CertificateBatch.BatchStatus.SUBMITTED);
        applyPaymentStatusToCerts(batch, PaymentStatus.PAID);
        batchRepository.save(batch);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "BATCH_PAYMENT_VERIFIED", "CertificateBatch", batch.getId(),
                "Payment verified for batch " + batch.getBatchCode()
                        + " (UTR " + batch.getUtrNumber() + ", amount " + batch.getTotalAmount() + ")", null);

        return loadDetail(batch.getId());
    }

    @Override
    @Transactional
    public BatchResponse rejectPayment(Long batchId, Long adminId, PaymentActionRequest req) {
        CertificateBatch batch = findBatch(batchId);
        AdminUser admin = findAdmin(adminId);

        if (batch.getPaymentStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Cannot reject a batch that is already PAID.");
        }

        batch.setPaymentStatus(PaymentStatus.REJECTED);
        batch.setPaymentRejectionReason(
                StringUtils.hasText(req.getReason()) ? req.getReason() : "Payment could not be verified");
        batch.setVerifiedBy(admin);
        batch.setBatchStatus(CertificateBatch.BatchStatus.REJECTED);
        applyPaymentStatusToCerts(batch, PaymentStatus.REJECTED);
        batchRepository.save(batch);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "BATCH_PAYMENT_REJECTED", "CertificateBatch", batch.getId(),
                "Payment rejected for batch " + batch.getBatchCode()
                        + ". Reason: " + batch.getPaymentRejectionReason(), null);

        return loadDetail(batch.getId());
    }

    // ══════════════════════════════════════════════════════════
    //  Admin — process (generate certificates) for a PAID batch
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional
    public BatchResponse processBatch(Long batchId, Long adminId) {
        CertificateBatch batch = findBatch(batchId);
        AdminUser admin = findAdmin(adminId);

        if (batch.getPaymentStatus() != PaymentStatus.PAID) {
            throw new IllegalStateException(
                "Batch payment must be PAID before processing. Current: " + batch.getPaymentStatus());
        }

        // Idempotency / double-processing guard: only start from a non-terminal,
        // not-already-processing state. The @Version column + this status flip make
        // concurrent process() calls fail fast (one wins, the other sees PROCESSING
        // or an OptimisticLockException).
        CertificateBatch.BatchStatus current = batch.getBatchStatus();
        if (current == CertificateBatch.BatchStatus.PROCESSING) {
            throw new IllegalStateException("Batch is already being processed.");
        }
        if (current == CertificateBatch.BatchStatus.COMPLETED) {
            throw new IllegalStateException("Batch has already been fully processed.");
        }
        batch.setBatchStatus(CertificateBatch.BatchStatus.PROCESSING);
        batchRepository.saveAndFlush(batch); // flush now so @Version conflict surfaces early

        List<Certificate> certs = certificateRepository.findByBatchId(batchId);
        int issued = 0;
        for (Certificate cert : certs) {
            if (cert.getStatus() == Certificate.Status.ISSUED) { issued++; continue; }
            if (cert.getStatus() == Certificate.Status.REJECTED
                    || cert.getStatus() == Certificate.Status.REVOKED) continue;

            String certNumber = generateUniqueCertNumber();
            cert.setCertificateNumber(certNumber);
            cert.setStatus(Certificate.Status.APPROVED);
            cert.setReviewedBy(admin);
            cert.setReviewedAt(LocalDateTime.now());
            cert.setIssueDate(LocalDate.now());

            try {
                byte[] pdf = pdfGenerator.generateCertificatePdf(cert);
                cert.setPdfData(pdf);
                cert.setPdfGeneratedAt(LocalDateTime.now());
            } catch (Exception e) {
                log.error("PDF generation failed for {} in batch {}: {}",
                        certNumber, batch.getBatchCode(), e.getMessage());
            }
            cert.setStatus(Certificate.Status.ISSUED);
            certificateRepository.save(cert);
            issued++;
        }

        batch.setBatchStatus(issued == certs.size()
                ? CertificateBatch.BatchStatus.COMPLETED
                : CertificateBatch.BatchStatus.GENERATED);
        batchRepository.save(batch);

        auditLogService.logAdminAction(adminId, admin.getEmail(),
                "BATCH_PROCESSED", "CertificateBatch", batch.getId(),
                "Processed batch " + batch.getBatchCode() + " — issued " + issued
                        + " of " + certs.size() + " certificates", null);

        // Notify institute once per batch
        try {
            Institute inst = batch.getInstitute();
            emailService.sendCertificateReadyEmail(
                    inst.getContactEmail(), inst.getContactPersonName(),
                    inst.getName(), batch.getType() + " batch " + batch.getBatchCode(),
                    batch.getBatchCode());
        } catch (Exception e) {
            log.warn("Batch-ready email failed for {}: {}", batch.getBatchCode(), e.getMessage());
        }

        return loadDetail(batch.getId());
    }

    // ══════════════════════════════════════════════════════════
    //  Downloads
    // ══════════════════════════════════════════════════════════

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadBatchZip(Long batchId) {
        findBatch(batchId);
        return zipCertificates(batchId);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadBatchZipForInstitute(Long instituteId, Long batchId) {
        findBatchForInstitute(instituteId, batchId);
        return zipCertificates(batchId);
    }

    @Override
    public byte[] csvTemplate() {
        String csv = "studentId,courseCode,marks,grade\n"
                + "INST001-2026-001,MSCIT,88,A\n"
                + "INST001-2026-002,DCA,75,B\n";
        return csv.getBytes(StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════

    private byte[] zipCertificates(Long batchId) {
        List<Certificate> certs = certificateRepository.findByBatchId(batchId);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(baos)) {
            int added = 0;
            for (Certificate cert : certs) {
                if (cert.getStatus() != Certificate.Status.ISSUED || cert.getPdfData() == null) continue;
                String name = (cert.getCertificateNumber() != null
                        ? cert.getCertificateNumber() : ("cert-" + cert.getId())) + ".pdf";
                zip.putNextEntry(new ZipEntry(name));
                zip.write(cert.getPdfData());
                zip.closeEntry();
                added++;
            }
            zip.finish();
            if (added == 0) return null;
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build certificate ZIP: " + e.getMessage(), e);
        }
    }

    private CertificateBatch newBatch(Institute institute, CertificateBatch.Type type, int count) {
        BigDecimal total = UNIT_AMOUNT.multiply(BigDecimal.valueOf(count));
        return CertificateBatch.builder()
                .batchCode(generateUniqueBatchCode())
                .institute(institute)
                .type(type)
                .certificateCount(count)
                .unitAmount(UNIT_AMOUNT)
                .totalAmount(total)
                .paymentStatus(PaymentStatus.PENDING)
                .batchStatus(CertificateBatch.BatchStatus.DRAFT)
                .build();
    }

    /** Single-statement payment-status propagation to all certs in a batch (no N+1). */
    private void applyPaymentStatusToCerts(CertificateBatch batch, PaymentStatus status) {
        certificateRepository.updatePaymentStatusByBatchId(batch.getId(), status);
    }

    private void assertNoActiveDuplicate(Long studentId, Long courseId) {
        boolean hasActive = !certificateRepository.findActiveByStudentAndCourse(
                studentId, courseId, DUPLICATE_EXCLUDED).isEmpty();
        if (hasActive) {
            throw new DataConflictException(
                "A certificate request already exists for this student and course.");
        }
    }

    private BatchResponse loadDetail(Long batchId) {
        CertificateBatch batch = findBatch(batchId);
        List<CertificateResponse> certs = certificateRepository.findByBatchId(batchId).stream()
                .map(CertificateResponse::from).toList();
        return BatchResponse.withCertificates(batch, certs);
    }

    /** Atomic, race-safe batch code via DB sequence. */
    private String generateUniqueBatchCode() {
        long seq = sequenceRepository.nextVal("batch_code_seq");
        return String.format("BATCH-%d-%06d", LocalDate.now().getYear(), seq);
    }

    /** Atomic, race-safe certificate number via DB sequence. */
    private String generateUniqueCertNumber() {
        long seq = sequenceRepository.nextVal("cert_number_seq");
        return certNumGenerator.generate(seq);
    }

    private List<ParsedRow> parseCsv(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DataConflictException("CSV file is empty.");
        }
        List<ParsedRow> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            int[] idx = {0, 1, 2, 3}; // default column order
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                String[] cols = splitCsv(line);

                // Header detection on the first non-blank line
                if (lineNo == 1 && looksLikeHeader(cols)) {
                    idx = mapHeader(cols);
                    continue;
                }
                if (out.size() >= MAX_CSV_ROWS) {
                    throw new DataConflictException(
                        "CSV exceeds the maximum of " + MAX_CSV_ROWS + " data rows. "
                        + "Split it into smaller files.");
                }
                ParsedRow row = new ParsedRow();
                row.rowNumber = lineNo;
                row.studentId  = col(cols, idx[0]);
                row.courseCode = col(cols, idx[1]);
                row.marks      = col(cols, idx[2]);
                row.grade      = col(cols, idx[3]);
                out.add(row);
            }
        } catch (DataConflictException e) {
            throw e; // propagate validation errors (empty, too many rows) unchanged
        } catch (Exception e) {
            throw new DataConflictException("Failed to read CSV: " + e.getMessage());
        }
        if (out.isEmpty()) {
            throw new DataConflictException("CSV contains no data rows.");
        }
        return out;
    }

    private boolean looksLikeHeader(String[] cols) {
        for (String c : cols) {
            String s = c == null ? "" : c.trim().toLowerCase();
            if (s.equals("studentid") || s.equals("student_id")
                    || s.equals("coursecode") || s.equals("course_code")) {
                return true;
            }
        }
        return false;
    }

    private int[] mapHeader(String[] cols) {
        int[] idx = {-1, -1, -1, -1};
        for (int i = 0; i < cols.length; i++) {
            String s = cols[i] == null ? "" : cols[i].trim().toLowerCase();
            switch (s) {
                case "studentid", "student_id" -> idx[0] = i;
                case "coursecode", "course_code" -> idx[1] = i;
                case "marks" -> idx[2] = i;
                case "grade" -> idx[3] = i;
                default -> { }
            }
        }
        // Fallback to positional for any unmapped column
        if (idx[0] < 0) idx[0] = 0;
        if (idx[1] < 0) idx[1] = 1;
        if (idx[2] < 0) idx[2] = 2;
        if (idx[3] < 0) idx[3] = 3;
        return idx;
    }

    private String[] splitCsv(String line) {
        // Simple CSV split (no embedded commas expected in these fields)
        return line.split(",", -1);
    }

    private String col(String[] cols, int i) {
        if (i < 0 || i >= cols.length) return null;
        String v = cols[i];
        return v == null ? null : v.trim();
    }

    private String safe(String s) {
        return s == null ? null : s.trim();
    }

    private Institute findInstitute(Long id) {
        return instituteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Institute", id));
    }

    private CertificateBatch findBatch(Long id) {
        return batchRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CertificateBatch", id));
    }

    private CertificateBatch findBatchForInstitute(Long instituteId, Long batchId) {
        return batchRepository.findByIdAndInstituteId(batchId, instituteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Batch not found or does not belong to your institute."));
    }

    private AdminUser findAdmin(Long adminId) {
        return adminUserRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminUser", adminId));
    }

    private InstituteUser resolveInstituteUser(Long instituteUserId) {
        if (instituteUserId == null) return null;
        return instituteUserRepository.findById(instituteUserId).orElse(null);
    }

    private PaymentStatus parsePaymentStatus(String s) {
        if (!StringUtils.hasText(s)) return null;
        try { return PaymentStatus.valueOf(s.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }

    /** Mutable intermediate holder for a parsed CSV row. */
    private static class ParsedRow {
        int rowNumber;
        String studentId;
        String courseCode;
        String marks;
        String grade;
    }
}
