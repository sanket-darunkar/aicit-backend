package com.aicit.controller;

import com.aicit.entity.*;
import com.aicit.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for the single + bulk certificate request
 * and manual-UPI payment flow. Runs on H2 (test profile).
 *
 * Covers: single creation + ₹250, bulk CSV validation (valid/invalid/duplicate/
 * wrong-institute/missing-student), ₹250 × count, UTR submission, payment
 * verify/reject, batch processing (certificate generation), payment gate,
 * authorization/data-isolation, and duplicate-submission handling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class BatchCertificateFlowTest {

    @Autowired MockMvc         mvc;
    @Autowired ObjectMapper    mapper;
    @Autowired PasswordEncoder passwordEncoder;

    @Autowired AdminUserRepository       adminUserRepository;
    @Autowired InstituteRepository       instituteRepository;
    @Autowired InstituteUserRepository   instituteUserRepository;
    @Autowired StudentRepository         studentRepository;
    @Autowired CourseRepository          courseRepository;
    @Autowired CertificateRepository     certificateRepository;
    @Autowired CertificateBatchRepository batchRepository;

    private String adminToken;
    private String instituteToken;

    private Institute institute;
    private Institute otherInstitute;
    private Student   student1;
    private Student   student2;
    private Student   otherStudent;
    private Course    course;

    @BeforeEach
    void setUp() throws Exception {
        AdminUser admin = AdminUser.builder()
                .email("admin2@aicit.test").password(passwordEncoder.encode("Admin123!"))
                .fullName("Admin").role(AdminUser.Role.SUPER_ADMIN).isActive(true).build();
        adminUserRepository.save(admin);

        institute = instituteRepository.save(Institute.builder()
                .instituteCode("INST-A").name("Institute A")
                .contactPersonName("A").contactEmail("a@test.local").contactMobile("9000000001")
                .city("Pune").district("Pune").state("MH")
                .status(Institute.Status.APPROVED).build());

        otherInstitute = instituteRepository.save(Institute.builder()
                .instituteCode("INST-B").name("Institute B")
                .contactPersonName("B").contactEmail("b@test.local").contactMobile("9000000009")
                .city("Mumbai").district("Mumbai").state("MH")
                .status(Institute.Status.APPROVED).build());

        InstituteUser iu = instituteUserRepository.save(InstituteUser.builder()
                .institute(institute).email("a@test.local")
                .password(passwordEncoder.encode("Inst123!"))
                .fullName("Inst A Admin").role(InstituteUser.Role.INSTITUTE_ADMIN)
                .isActive(true).build());

        student1 = studentRepository.save(Student.builder()
                .studentId("S-001").firstName("Ravi").surname("Kumar")
                .ownMobile("9111111111").institute(institute).status(Student.Status.ACTIVE).build());
        student2 = studentRepository.save(Student.builder()
                .studentId("S-002").firstName("Sita").surname("Devi")
                .ownMobile("9222222222").institute(institute).status(Student.Status.ACTIVE).build());
        otherStudent = studentRepository.save(Student.builder()
                .studentId("S-999").firstName("Other").surname("Student")
                .ownMobile("9333333333").institute(otherInstitute).status(Student.Status.ACTIVE).build());

        course = courseRepository.save(Course.builder()
                .name("Diploma in Computer Application").code("DCA-T")
                .durationMonths(6).isActive(true).build());

        adminToken     = adminLogin("admin2@aicit.test", "Admin123!");
        instituteToken = instituteLogin("a@test.local", "Inst123!");
    }

    // ── SINGLE ────────────────────────────────────────────────

    @Test @DisplayName("Single: creates a 1-cert batch at ₹250, payment PENDING")
    void single_creates_batch_250() throws Exception {
        mvc.perform(post("/api/institute/batches/single")
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "studentId", student1.getId(), "courseId", course.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("SINGLE"))
                .andExpect(jsonPath("$.data.certificateCount").value(1))
                .andExpect(jsonPath("$.data.totalAmount").value(250))
                .andExpect(jsonPath("$.data.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.data.certificates.length()").value(1));
    }

    @Test @DisplayName("Single: duplicate active request → 409")
    void single_duplicate_conflict() throws Exception {
        createSingle(student1.getId());
        mvc.perform(post("/api/institute/batches/single")
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "studentId", student1.getId(), "courseId", course.getId()))))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("Single: student from another institute → 404 (data isolation)")
    void single_otherInstituteStudent_notFound() throws Exception {
        mvc.perform(post("/api/institute/batches/single")
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "studentId", otherStudent.getId(), "courseId", course.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("Single: unauthenticated → 401")
    void single_unauthenticated() throws Exception {
        mvc.perform(post("/api/institute/batches/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "studentId", student1.getId(), "courseId", course.getId()))))
                .andExpect(status().isUnauthorized());
    }

    // ── BULK CSV VALIDATION ───────────────────────────────────

    @Test @DisplayName("Bulk validate: valid + invalid rows, ₹250 × valid count")
    void bulk_validate_mixed() throws Exception {
        String csv = "studentId,courseCode,marks,grade\n"
                + "S-001,DCA-T,80,A\n"          // valid
                + "S-002,DCA-T,70,B\n"          // valid
                + "S-404,DCA-T,60,C\n"          // missing student
                + "S-999,DCA-T,90,A\n"          // belongs to other institute
                + "S-001,BAD-CODE,50,D\n"       // bad course
                + "S-001,DCA-T,80,A\n";         // duplicate of row 1 within file
        MockMultipartFile file = new MockMultipartFile("file", "b.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/institute/batches/bulk/validate").file(file)
                        .header("Authorization", "Bearer " + instituteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.validCount").value(2))
                .andExpect(jsonPath("$.data.invalidCount").value(4))
                .andExpect(jsonPath("$.data.totalAmount").value(500))
                .andExpect(jsonPath("$.data.unitAmount").value(250));
    }

    @Test @DisplayName("Bulk validate: creates no records")
    void bulk_validate_creates_nothing() throws Exception {
        String csv = "studentId,courseCode\nS-001,DCA-T\n";
        MockMultipartFile file = new MockMultipartFile("file", "b.csv", "text/csv", csv.getBytes());
        mvc.perform(multipart("/api/institute/batches/bulk/validate").file(file)
                        .header("Authorization", "Bearer " + instituteToken))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(batchRepository.count()).isZero();
        org.assertj.core.api.Assertions.assertThat(certificateRepository.count()).isZero();
    }

    // ── BULK CREATE ───────────────────────────────────────────

    @Test @DisplayName("Bulk create: only valid rows become certs, amount = valid × ₹250")
    void bulk_create_valid_only() throws Exception {
        String csv = "studentId,courseCode,marks,grade\n"
                + "S-001,DCA-T,80,A\n"
                + "S-002,DCA-T,70,B\n"
                + "S-404,DCA-T,60,C\n";   // invalid, excluded
        MockMultipartFile file = new MockMultipartFile("file", "b.csv", "text/csv", csv.getBytes());

        mvc.perform(multipart("/api/institute/batches/bulk").file(file)
                        .header("Authorization", "Bearer " + instituteToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.type").value("BULK"))
                .andExpect(jsonPath("$.data.certificateCount").value(2))
                .andExpect(jsonPath("$.data.totalAmount").value(500))
                .andExpect(jsonPath("$.data.certificates.length()").value(2));
    }

    // ── PAYMENT FLOW + GATE ───────────────────────────────────

    @Test @DisplayName("Full flow: UTR → verify → process generates certificates")
    void full_payment_and_process_flow() throws Exception {
        Long batchId = createSingle(student1.getId());

        // Submit UTR
        mvc.perform(post("/api/institute/batches/{id}/utr", batchId)
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("utrNumber", "UTR1234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("VERIFICATION_PENDING"));

        // Admin verifies payment
        mvc.perform(post("/api/admin/batches/{id}/verify-payment", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));

        // Admin processes → certificates generated + ISSUED
        mvc.perform(post("/api/admin/batches/{id}/process", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batchStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.certificates[0].status").value("ISSUED"))
                .andExpect(jsonPath("$.data.certificates[0].certificateNumber").isNotEmpty());
    }

    @Test @DisplayName("Payment gate: cannot process before payment is verified")
    void process_before_payment_rejected() throws Exception {
        Long batchId = createSingle(student1.getId());
        mvc.perform(post("/api/admin/batches/{id}/process", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("Payment reject: sets REJECTED, re-submission allowed")
    void payment_reject_then_resubmit() throws Exception {
        Long batchId = createSingle(student1.getId());
        submitUtr(batchId, "UTRBAD123456");

        mvc.perform(post("/api/admin/batches/{id}/reject-payment", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("reason", "UTR not found"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("REJECTED"));

        // Re-submit after rejection
        mvc.perform(post("/api/institute/batches/{id}/utr", batchId)
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("utrNumber", "UTRGOOD98765"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("VERIFICATION_PENDING"));
    }

    @Test @DisplayName("Double-process: second process after COMPLETED → 400")
    void double_process_blocked() throws Exception {
        Long batchId = createSingle(student1.getId());
        submitUtr(batchId, "UTR555555");
        verifyPayment(batchId);
        mvc.perform(post("/api/admin/batches/{id}/process", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        // second attempt
        mvc.perform(post("/api/admin/batches/{id}/process", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── AUTHORIZATION / ISOLATION ─────────────────────────────

    @Test @DisplayName("Institute cannot hit admin verify-payment → 403")
    void institute_cannot_verify_payment() throws Exception {
        Long batchId = createSingle(student1.getId());
        mvc.perform(post("/api/admin/batches/{id}/verify-payment", batchId)
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    // ── Helpers ───────────────────────────────────────────────

    private Long createSingle(Long studentId) throws Exception {
        String resp = mvc.perform(post("/api/institute/batches/single")
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "studentId", studentId, "courseId", course.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).at("/data/id").asLong();
    }

    private void submitUtr(Long batchId, String utr) throws Exception {
        mvc.perform(post("/api/institute/batches/{id}/utr", batchId)
                        .header("Authorization", "Bearer " + instituteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("utrNumber", utr))))
                .andExpect(status().isOk());
    }

    private void verifyPayment(Long batchId) throws Exception {
        mvc.perform(post("/api/admin/batches/{id}/verify-payment", batchId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    private String adminLogin(String email, String password) throws Exception {
        return login("/api/auth/admin/login", email, password);
    }

    private String instituteLogin(String email, String password) throws Exception {
        return login("/api/auth/institute/login", email, password);
    }

    private String login(String url, String email, String password) throws Exception {
        String resp = mvc.perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(resp).at("/data/token").asText();
    }
}
