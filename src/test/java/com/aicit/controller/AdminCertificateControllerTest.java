package com.aicit.controller;

import com.aicit.entity.*;
import com.aicit.repository.*;
import com.aicit.service.PdfGeneratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@code AdminCertificateController}.
 *
 * Runs against an H2 in-memory database (test profile).
 * Verifies:
 * <ul>
 *   <li>Approve endpoint generates a BLUE AICIT PDF for a normal course.</li>
 *   <li>Approve endpoint generates an ORANGE Typing PDF for a typing course.</li>
 *   <li>Both generated PDFs are valid, single-page, and downloadable.</li>
 *   <li>Public verification endpoint still works after approval.</li>
 *   <li>certificateType field in the response reflects the correct type.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminCertificateControllerTest {

    @Autowired MockMvc          mvc;
    @Autowired ObjectMapper     mapper;
    @Autowired PasswordEncoder  passwordEncoder;

    @Autowired AdminUserRepository      adminUserRepository;
    @Autowired InstituteRepository      instituteRepository;
    @Autowired InstituteUserRepository  instituteUserRepository;
    @Autowired StudentRepository        studentRepository;
    @Autowired CourseRepository         courseRepository;
    @Autowired CertificateRepository    certificateRepository;

    private String adminToken;
    private Institute institute;
    private Student   student;
    private Course    aicitCourse;
    private Course    typingCourse;

    // ── Fixture setup ─────────────────────────────────────────

    @BeforeEach
    void setUp() throws Exception {
        // Admin user
        AdminUser admin = AdminUser.builder()
                .email("testadmin@aicit.test")
                .password(passwordEncoder.encode("Admin123!"))
                .fullName("Test Admin")
                .role(AdminUser.Role.SUPER_ADMIN)
                .isActive(true)
                .build();
        adminUserRepository.save(admin);

        // Institute
        institute = Institute.builder()
                .instituteCode("AICIT-INST-TEST")
                .name("Test Institute")
                .contactPersonName("Test Contact")
                .contactEmail("inst@test.local")
                .contactMobile("9000000001")
                .city("Pune")
                .district("Pune")
                .state("Maharashtra")
                .status(Institute.Status.APPROVED)
                .build();
        instituteRepository.save(institute);

        // Student
        student = Student.builder()
                .studentId("TEST-001")
                .firstName("Ravi")
                .middleName("K.")
                .surname("Lande")
                .ownMobile("9000000002")
                .institute(institute)
                .status(Student.Status.ACTIVE)
                .build();
        studentRepository.save(student);

        // Courses
        aicitCourse = Course.builder()
                .name("Advanced Tally Prime with GST")
                .code("TALLY-ADV-TEST")
                .durationMonths(3)
                .isActive(true)
                .build();
        courseRepository.save(aicitCourse);

        typingCourse = Course.builder()
                .name("Computer Based Typing Examination")
                .code("TYPING-TEST")
                .durationMonths(1)
                .isActive(true)
                .build();
        courseRepository.save(typingCourse);

        // Get admin JWT
        adminToken = obtainToken("testadmin@aicit.test", "Admin123!");
    }

    // ── Tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("Approve normal course → certificateType = AICIT in response")
    void approve_normalCourse_returnsCertTypeAicit() throws Exception {
        Long certId = createCertificateRequest(aicitCourse);

        String responseBody = mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.certificateType").value("AICIT"))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseBody).contains("AICIT");
    }

    @Test
    @DisplayName("Approve typing course → certificateType = TYPING in response")
    void approve_typingCourse_returnsCertTypeTyping() throws Exception {
        Long certId = createCertificateRequest(typingCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("ISSUED"))
                .andExpect(jsonPath("$.data.certificateType").value("TYPING"));
    }

    @Test
    @DisplayName("Approve normal course → hasPdf = true")
    void approve_normalCourse_hasPdfTrue() throws Exception {
        Long certId = createCertificateRequest(aicitCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPdf").value(true));
    }

    @Test
    @DisplayName("Approve typing course → hasPdf = true")
    void approve_typingCourse_hasPdfTrue() throws Exception {
        Long certId = createCertificateRequest(typingCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasPdf").value(true));
    }

    @Test
    @DisplayName("Download AICIT cert → valid single-page PDF bytes")
    void download_aicitCert_isValidSinglePagePdf() throws Exception {
        Long certId = createAndApproveCertificate(aicitCourse);

        MvcResult result = mvc.perform(get("/api/admin/certificates/{id}/download", certId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages())
                    .as("Downloaded AICIT certificate must be exactly one page")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Download Typing cert → valid single-page PDF bytes")
    void download_typingCert_isValidSinglePagePdf() throws Exception {
        Long certId = createAndApproveCertificate(typingCourse);

        MvcResult result = mvc.perform(get("/api/admin/certificates/{id}/download", certId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn();

        byte[] pdf = result.getResponse().getContentAsByteArray();
        assertThat(pdf).isNotEmpty();

        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages())
                    .as("Downloaded Typing certificate must be exactly one page")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("AICIT and Typing cert downloads produce different PDFs")
    void download_twoCertTypes_produceDifferentPdfs() throws Exception {
        Long aicitId  = createAndApproveCertificate(aicitCourse);
        Long typingId = createAndApproveCertificate(typingCourse);

        byte[] aicitPdf = mvc.perform(get("/api/admin/certificates/{id}/download", aicitId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();

        byte[] typingPdf = mvc.perform(get("/api/admin/certificates/{id}/download", typingId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();

        assertThat(aicitPdf).isNotEqualTo(typingPdf);
    }

    @Test
    @DisplayName("Approve with custom cert number → custom number stored and returned")
    void approve_customCertNumber_stored() throws Exception {
        Long certId = createCertificateRequest(aicitCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customCertNumber\":\"AICIT-2026-099999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.certificateNumber").value("AICIT-2026-099999"));
    }

    @Test
    @DisplayName("Approve with invalid cert number format → 400")
    void approve_invalidCertNumberFormat_returns400() throws Exception {
        Long certId = createCertificateRequest(aicitCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customCertNumber\":\"WRONG-FORMAT\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Public verify works after approval — returns certificate data")
    void publicVerify_afterApproval_returnsCertData() throws Exception {
        Long certId = createAndApproveCertificate(aicitCourse);

        // Get the cert number
        String certNumber = mapper.readTree(
                mvc.perform(get("/api/admin/certificates/{id}", certId)
                                .header("Authorization", "Bearer " + adminToken))
                        .andReturn().getResponse().getContentAsString())
                .at("/data/certificateNumber").asText();

        // Verify publicly
        mvc.perform(get("/api/public/certificates/verify/{number}", certNumber))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.certificateNumber").value(certNumber))
                .andExpect(jsonPath("$.data.status").value("ISSUED"));
    }

    @Test
    @DisplayName("Approve already-issued cert → 400 invalid state")
    void approve_alreadyIssued_returns400() throws Exception {
        Long certId = createAndApproveCertificate(aicitCourse);

        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Unauthenticated download → 401")
    void download_unauthenticated_returns401() throws Exception {
        Long certId = createAndApproveCertificate(aicitCourse);
        mvc.perform(get("/api/admin/certificates/{id}/download", certId))
                .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────

    private String obtainToken(String email, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("email", email, "password", password));
        String response = mvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).at("/data/token").asText();
    }

    /** Creates a REQUESTED certificate for the given course and returns its id. */
    private Long createCertificateRequest(Course course) {
        Certificate cert = Certificate.builder()
                .institute(institute)
                .student(student)
                .course(course)
                .marks("90/100")
                .grade("A")
                .status(Certificate.Status.REQUESTED)
                .paymentStatus(com.aicit.entity.PaymentStatus.PAID)
                .build();
        return certificateRepository.save(cert).getId();
    }

    /** Creates and approves a certificate; returns the cert id. */
    private Long createAndApproveCertificate(Course course) throws Exception {
        Long certId = createCertificateRequest(course);
        mvc.perform(post("/api/admin/certificates/{id}/approve", certId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
        return certId;
    }
}
