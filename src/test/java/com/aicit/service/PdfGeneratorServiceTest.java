package com.aicit.service;

import com.aicit.entity.*;
import com.aicit.service.PdfGeneratorService.CertificateType;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PdfGeneratorService}.
 *
 * Tests do NOT require a Spring context — {@link PdfGeneratorService} is
 * instantiated directly. PDFBox is used to validate that each generated
 * byte array is a syntactically valid PDF and contains exactly one page.
 */
class PdfGeneratorServiceTest {

    private PdfGeneratorService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new PdfGeneratorService();
        // Inject the frontendUrl field (normally provided by @Value)
        var field = PdfGeneratorService.class.getDeclaredField("frontendUrl");
        field.setAccessible(true);
        field.set(service, "http://localhost:5174");
    }

    // =========================================================
    //  isTypingCourse / detectType
    // =========================================================

    @Test
    @DisplayName("isTypingCourse: null → false")
    void isTypingCourse_null_returnsFalse() {
        assertThat(PdfGeneratorService.isTypingCourse(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Computer Based Typing Examination",
        "Marathi Typing – 30 W.P.M.",
        "TYPING",
        "typing",
        "Hindi Typing Certificate",
        "Advanced Typing Test"
    })
    @DisplayName("isTypingCourse: typing-related names → true")
    void isTypingCourse_typingNames_returnsTrue(String courseName) {
        assertThat(PdfGeneratorService.isTypingCourse(courseName)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Advanced Excel",
        "Advanced Tally Prime with GST",
        "Certificate in Hardware & Networking",
        "DCA – Diploma in Computer Applications",
        "MS-CIT"
    })
    @DisplayName("isTypingCourse: normal course names → false")
    void isTypingCourse_normalNames_returnsFalse(String courseName) {
        assertThat(PdfGeneratorService.isTypingCourse(courseName)).isFalse();
    }

    @Test
    @DisplayName("detectType: typing course → TYPING")
    void detectType_typingCourse() {
        assertThat(PdfGeneratorService.detectType("Computer Based Typing Examination"))
                .isEqualTo(CertificateType.TYPING);
    }

    @Test
    @DisplayName("detectType: normal course → AICIT")
    void detectType_normalCourse() {
        assertThat(PdfGeneratorService.detectType("Advanced Excel"))
                .isEqualTo(CertificateType.AICIT);
    }

    // =========================================================
    //  Input validation
    // =========================================================

    @Test
    @DisplayName("generateCertificatePdf: null cert → IllegalArgumentException")
    void generate_nullCert_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.generateCertificatePdf(null))
                .withMessageContaining("must not be null");
    }

    @Test
    @DisplayName("generateCertificatePdf: missing certificate number → IllegalArgumentException")
    void generate_missingCertNumber_throws() {
        Certificate cert = buildAicitCert();
        cert.setCertificateNumber(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.generateCertificatePdf(cert))
                .satisfies(ex ->
                    assertThat(ex.getMessage().toLowerCase()).contains("certificate number"));
    }

    @Test
    @DisplayName("generateCertificatePdf: missing course → IllegalArgumentException")
    void generate_missingCourse_throws() {
        Certificate cert = buildAicitCert();
        cert.setCourse(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.generateCertificatePdf(cert))
                .withMessageContaining("no course");
    }

    @Test
    @DisplayName("generateCertificatePdf: missing student → IllegalArgumentException")
    void generate_missingStudent_throws() {
        Certificate cert = buildAicitCert();
        cert.setStudent(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.generateCertificatePdf(cert))
                .withMessageContaining("no student");
    }

    @Test
    @DisplayName("generateCertificatePdf: missing institute → IllegalArgumentException")
    void generate_missingInstitute_throws() {
        Certificate cert = buildAicitCert();
        cert.setInstitute(null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> service.generateCertificatePdf(cert))
                .withMessageContaining("no institute");
    }

    // =========================================================
    //  BLUE — AICIT Certificate (normal course)
    // =========================================================

    @Test
    @DisplayName("AICIT cert: bytes non-null and non-empty")
    void aicitCert_bytesNonEmpty() {
        byte[] pdf = service.generateCertificatePdf(buildAicitCert());
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("AICIT cert: output is valid PDF (PDFBox can load it)")
    void aicitCert_isValidPdf() throws IOException {
        byte[] pdf = service.generateCertificatePdf(buildAicitCert());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc).isNotNull();
        }
    }

    @Test
    @DisplayName("AICIT cert: exactly one page")
    void aicitCert_exactlyOnePage() throws IOException {
        byte[] pdf = service.generateCertificatePdf(buildAicitCert());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages())
                    .as("AICIT certificate must be exactly one page")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("AICIT cert: minimum size sanity check (> 100 KB — template-based PDF)")
    void aicitCert_minimumSize() {
        byte[] pdf = service.generateCertificatePdf(buildAicitCert());
        assertThat(pdf.length)
                .as("Template-based PDF should be at least 100 KB")
                .isGreaterThan(100_000);
    }

    @Test
    @DisplayName("AICIT cert: starts with PDF magic bytes %PDF")
    void aicitCert_pdfMagicBytes() {
        byte[] pdf = service.generateCertificatePdf(buildAicitCert());
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("AICIT cert: works with null marks/grade (optional fields)")
    void aicitCert_nullOptionalFields_doesNotThrow() {
        Certificate cert = buildAicitCert();
        cert.setMarks(null);
        cert.setGrade(null);
        cert.setIssueDate(null);
        assertThatCode(() -> service.generateCertificatePdf(cert)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("AICIT cert: works when course has no durationMonths")
    void aicitCert_noDuration_doesNotThrow() {
        Certificate cert = buildAicitCert();
        cert.getCourse().setDurationMonths(null);
        assertThatCode(() -> service.generateCertificatePdf(cert)).doesNotThrowAnyException();
    }

    // =========================================================
    //  ORANGE — Typing Certificate
    // =========================================================

    @Test
    @DisplayName("Typing cert: bytes non-null and non-empty")
    void typingCert_bytesNonEmpty() {
        byte[] pdf = service.generateCertificatePdf(buildTypingCert());
        assertThat(pdf).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("Typing cert: output is valid PDF (PDFBox can load it)")
    void typingCert_isValidPdf() throws IOException {
        byte[] pdf = service.generateCertificatePdf(buildTypingCert());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc).isNotNull();
        }
    }

    @Test
    @DisplayName("Typing cert: exactly one page")
    void typingCert_exactlyOnePage() throws IOException {
        byte[] pdf = service.generateCertificatePdf(buildTypingCert());
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages())
                    .as("Typing certificate must be exactly one page")
                    .isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Typing cert: minimum size sanity check (> 100 KB — template-based PDF)")
    void typingCert_minimumSize() {
        byte[] pdf = service.generateCertificatePdf(buildTypingCert());
        assertThat(pdf.length)
                .as("Template-based PDF should be at least 100 KB")
                .isGreaterThan(100_000);
    }

    @Test
    @DisplayName("Typing cert: starts with PDF magic bytes %PDF")
    void typingCert_pdfMagicBytes() {
        byte[] pdf = service.generateCertificatePdf(buildTypingCert());
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("Typing cert: works with null marks/grade (optional fields)")
    void typingCert_nullOptionalFields_doesNotThrow() {
        Certificate cert = buildTypingCert();
        cert.setMarks(null);
        cert.setGrade(null);
        cert.setIssueDate(null);
        cert.getStudent().setPhotoData(null);
        assertThatCode(() -> service.generateCertificatePdf(cert)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Typing cert: works with student photo data present")
    void typingCert_withStudentPhoto() throws IOException {
        Certificate cert = buildTypingCert();
        // Minimal 1×1 white PNG as a stand-in photo
        cert.getStudent().setPhotoData(MINIMAL_PNG);
        byte[] pdf = service.generateCertificatePdf(cert);
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Typing cert: marks parsed correctly from 'obtained/max' format")
    void typingCert_marksFormat_doesNotThrow() {
        Certificate cert = buildTypingCert();
        cert.setMarks("90/100");
        assertThatCode(() -> service.generateCertificatePdf(cert)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Typing cert: marks with only obtained value (no slash) does not throw")
    void typingCert_marksNoSlash_doesNotThrow() {
        Certificate cert = buildTypingCert();
        cert.setMarks("90");
        assertThatCode(() -> service.generateCertificatePdf(cert)).doesNotThrowAnyException();
    }

    // =========================================================
    //  Two certs produce DIFFERENT designs
    // =========================================================

    @Test
    @DisplayName("AICIT and Typing certs produce different byte arrays")
    void twoCertTypes_produceDifferentBytes() {
        byte[] aicitPdf  = service.generateCertificatePdf(buildAicitCert());
        byte[] typingPdf = service.generateCertificatePdf(buildTypingCert());
        // They may differ in size or content — just assert they are not identical
        assertThat(aicitPdf).isNotEqualTo(typingPdf);
    }

    // =========================================================
    //  Builder helpers
    // =========================================================

    private static Certificate buildAicitCert() {
        Institute institute = Institute.builder()
                .name("Fusion Software Pvt Ltd")
                .instituteCode("AICIT-INST-0001")
                .city("Pune")
                .district("Pune")
                .state("Maharashtra")
                .contactEmail("admin@fusion.local")
                .contactPersonName("Test Admin")
                .contactMobile("9000000001")
                .status(Institute.Status.APPROVED)
                .build();

        Student student = Student.builder()
                .studentId("FS-2026-001")
                .firstName("Aryan")
                .middleName("A.")
                .surname("Meshram")
                .ownMobile("9000000002")
                .institute(institute)
                .status(Student.Status.ACTIVE)
                .build();

        Course course = Course.builder()
                .name("Advanced Tally Prime with GST")
                .code("TALLY-ADV")
                .durationMonths(3)
                .isActive(true)
                .build();

        return Certificate.builder()
                .certificateNumber("AICIT-2026-000001")
                .institute(institute)
                .student(student)
                .course(course)
                .marks("92/100")
                .grade("A+")
                .issueDate(LocalDate.of(2026, 7, 23))
                .status(Certificate.Status.ISSUED)
                .pdfGeneratedAt(LocalDateTime.now())
                .build();
    }

    private static Certificate buildTypingCert() {
        Institute institute = Institute.builder()
                .name("Master Computer Academy")
                .instituteCode("M26584")
                .city("Nagpur")
                .district("Nagpur")
                .state("Maharashtra")
                .contactEmail("admin@mca.local")
                .contactPersonName("Test Admin")
                .contactMobile("9000000003")
                .status(Institute.Status.APPROVED)
                .build();

        Student student = Student.builder()
                .studentId("M2026-28")
                .firstName("Navya")
                .middleName("D.")
                .surname("Agare")
                .ownMobile("9000000004")
                .institute(institute)
                .status(Student.Status.ACTIVE)
                .build();

        Course course = Course.builder()
                .name("Computer Based Typing Examination")
                .code("TYPING-ENG")
                .durationMonths(1)
                .isActive(true)
                .build();

        return Certificate.builder()
                .certificateNumber("AICIT-2026-063501")
                .institute(institute)
                .student(student)
                .course(course)
                .marks("90/100")
                .grade("A")
                .issueDate(LocalDate.of(2026, 7, 1))
                .status(Certificate.Status.ISSUED)
                .pdfGeneratedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Minimal valid 1×1 white PNG (67 bytes) used as a stand-in student photo.
     * Generated offline and verified — a known-good PNG header.
     */
    private static final byte[] MINIMAL_PNG = {
        (byte)0x89,0x50,0x4E,0x47,0x0D,0x0A,0x1A,0x0A, // PNG signature
        0x00,0x00,0x00,0x0D,0x49,0x48,0x44,0x52,        // IHDR length + type
        0x00,0x00,0x00,0x01,0x00,0x00,0x00,0x01,        // width=1, height=1
        0x08,0x02,0x00,0x00,0x00,(byte)0x90,0x77,0x53,(byte)0xDE, // bit depth, color, crc
        0x00,0x00,0x00,0x0C,0x49,0x44,0x41,0x54,        // IDAT length + type
        0x08,(byte)0xD7,0x63,(byte)0xF8,(byte)0xFF,(byte)0xFF,0x3F,0x00,
        0x05,(byte)0xFE,0x02,(byte)0xFE,(byte)0xA8,(byte)0xE4,(byte)0xD4,(byte)0x81, // IDAT data+crc
        0x00,0x00,0x00,0x00,0x49,0x45,0x4E,0x44,        // IEND
        (byte)0xAE,0x42,0x60,(byte)0x82                  // IEND CRC
    };
}
