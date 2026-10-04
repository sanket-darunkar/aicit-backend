package com.aicit.service;

import com.aicit.entity.Certificate;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Certificate PDF generation service.
 *
 * <p>Uses Apache PDFBox to overlay student/certificate data on top of the
 * original AICIT certificate PDF templates, preserving all original design,
 * logos, borders, watermarks, colours and signatures exactly.
 *
 * <p><b>Templates (classpath:templates/certificates/):</b>
 * <ul>
 *   <li>{@code aicit_certificate.pdf}        — BLUE A4 portrait, all normal courses</li>
 *   <li>{@code aicit_typing_certificate.pdf} — ORANGE US-Letter portrait, typing courses</li>
 * </ul>
 *
 * <p>Routing is driven by the course name: any course name that contains
 * "typing" (case-insensitive) uses the typing template; all others use the
 * AICIT template.
 */
@Service
@Slf4j
public class PdfGeneratorService {

    // ── Template classpath paths ──────────────────────────────
    static final String TEMPLATE_AICIT  = "templates/certificates/aicit_certificate.pdf";
    static final String TEMPLATE_TYPING = "templates/certificates/aicit_typing_certificate.pdf";

    @Value("${app.frontend.url:http://localhost:5174}")
    private String frontendUrl;

    // ── Standard PDFBox fonts ─────────────────────────────────
    // Helvetica = clean sans-serif matching the certificate's printed style
    // Helvetica-Bold for names/headings
    // Times-Roman italic for script-style lines

    private static final DateTimeFormatter DATE_FMT  = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // =========================================================
    //  Public enums and helpers
    // =========================================================

    /** The two certificate design variants. */
    public enum CertificateType { AICIT, TYPING }

    /**
     * Determines the certificate type from the course name.
     * Case-insensitive: "typing" anywhere in the name → {@code TYPING}.
     */
    public static CertificateType detectType(String courseName) {
        return isTypingCourse(courseName) ? CertificateType.TYPING : CertificateType.AICIT;
    }

    /** Returns {@code true} if the course name contains "typing" (case-insensitive). */
    public static boolean isTypingCourse(String courseName) {
        if (courseName == null) return false;
        return courseName.toLowerCase(Locale.ROOT).contains("typing");
    }

    // =========================================================
    //  Main entry point
    // =========================================================

    /**
     * Generates a certificate PDF by overlaying data on the official template.
     *
     * @param cert fully-populated Certificate entity (course, student, institute,
     *             marks, grade, issueDate, certificateNumber must not be null)
     * @return raw single-page PDF bytes
     * @throws IllegalArgumentException for missing required fields
     * @throws RuntimeException         if PDF generation fails
     */
    public byte[] generateCertificatePdf(Certificate cert) {
        validate(cert);

        CertificateType type = detectType(cert.getCourse().getName());
        String templatePath  = (type == CertificateType.TYPING) ? TEMPLATE_TYPING : TEMPLATE_AICIT;
        boolean templateExists = new ClassPathResource(templatePath).exists();

        log.info("Generating {} certificate for {} — template={} ({})",
                type, cert.getCertificateNumber(), templatePath,
                templateExists ? "overlay mode" : "programmatic fallback");

        if (!templateExists) {
            throw new RuntimeException(
                "Certificate template not found on classpath: " + templatePath +
                ". Place the PDF template at src/main/resources/" + templatePath);
        }

        try {
            return type == CertificateType.TYPING
                    ? generateTypingOverlay(cert)
                    : generateAicitOverlay(cert);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "PDF generation failed for " + cert.getCertificateNumber() + ": " + e.getMessage(), e);
        }
    }

    // =========================================================
    //  BLUE — AICIT Certificate overlay
    //  Template: A4 portrait  595.28 × 841.89 pts
    //  PDFBox origin: BOTTOM-LEFT
    // =========================================================

    private byte[] generateAicitOverlay(Certificate cert) throws Exception {
        ClassPathResource res = new ClassPathResource(TEMPLATE_AICIT);
        try (InputStream in = res.getInputStream();
             PDDocument doc  = Loader.loadPDF(in.readAllBytes())) {

            PDPage page = doc.getPage(0);
            float W = page.getMediaBox().getWidth();   // 595.28
            float H = page.getMediaBox().getHeight();  // 841.89

            PDFont bold   = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont reg    = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont italic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            String studentName   = buildFullName(cert);
            String instituteName = cert.getInstitute().getName().trim();
            String city          = cert.getInstitute().getCity() != null
                    ? cert.getInstitute().getCity().trim() : "";
            String atcLine = "ATC : " + instituteName.toUpperCase()
                    + (city.isEmpty() ? "" : " | " + city.toUpperCase());
            String grade     = cert.getGrade()  != null ? cert.getGrade()  : "";
            String marks     = cert.getMarks()  != null ? cert.getMarks()  : "";
            String course    = cert.getCourse().getName();
            String certNo    = cert.getCertificateNumber();
            String issueDate = cert.getIssueDate() != null
                    ? cert.getIssueDate().format(DATE_FMT) : "";
            Integer duration = cert.getCourse().getDurationMonths();

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, AppendMode.APPEND, true, true)) {

                // ── Student Photo (top-right box on the template) ─────
                // Measured from scan: photo box is at approx x=432, y=563 pts from bottom,
                // size 98 × 118 pts
                if (cert.getStudent().getPhotoData() != null) {
                    try {
                        PDImageXObject photo = PDImageXObject.createFromByteArray(
                                doc, cert.getStudent().getPhotoData(), "photo");
                        cs.drawImage(photo, 432f, 563f, 98f, 118f);
                    } catch (Exception e) {
                        log.warn("Could not embed student photo for {}: {}", certNo, e.getMessage());
                    }
                }

                // ── Student name ──────────────────────────────────────
                // Measured: "ARYAN A. MESHRAM" sits at y ≈ 488 pts from bottom (57.9% of 841.89)
                float nameY = 488f;
                drawCenteredText(cs, bold, 18f,
                        new Color(0, 51, 153),          // dark navy matching template
                        studentName.toUpperCase(), W, nameY);

                // ── ATC line ─────────────────────────────────────────
                // Measured: sits 24 pts below name → y ≈ 462
                float atcY = 462f;
                drawCenteredText(cs, bold, 9f,
                        new Color(0, 51, 153),
                        atcLine, W, atcY);

                // ── Grade / marks line ────────────────────────────────
                // "has passed the prescribed examination with ... Grade A+ (92%)"
                // Measured: y ≈ 415
                float gradeLineY = 415f;
                String gradeMarks = "has passed the prescribed examination with ..........  Grade  "
                        + grade
                        + (marks.isEmpty() ? "" : "    (" + marks + ")");
                drawCenteredText(cs, italic, 11f,
                        new Color(185, 28, 28),
                        gradeMarks, W, gradeLineY);

                // ── "has been awarded the" ────────────────────────────
                float awardY = 396f;
                drawCenteredText(cs, italic, 11f,
                        new Color(185, 28, 28),
                        "has been awarded the", W, awardY);

                // ── Course name ───────────────────────────────────────
                float courseY = 374f;
                drawCenteredText(cs, bold, 10f,
                        new Color(0, 51, 153),
                        course.toUpperCase(), W, courseY);

                // ── Duration ─────────────────────────────────────────
                if (duration != null) {
                    drawCenteredText(cs, reg, 7.5f,
                            Color.DARK_GRAY,
                            "(Course Duration : " + duration + " Months)",
                            W, 358f);
                }

                // ── Certificate number & date (bottom-left) ───────────
                // Measured from scan: cert no at y=75, date at y=60
                cs.beginText();
                cs.setFont(bold, 8f);
                cs.setNonStrokingColor(new Color(30, 58, 138));
                cs.newLineAtOffset(48f, 75f);
                cs.showText("Certificate No. : " + certNo);
                cs.endText();

                if (!issueDate.isEmpty()) {
                    cs.beginText();
                    cs.setFont(bold, 8f);
                    cs.setNonStrokingColor(new Color(30, 58, 138));
                    cs.newLineAtOffset(48f, 62f);
                    cs.showText("Date of Issue     : " + issueDate);
                    cs.endText();
                }

                // ── QR code (bottom-right, near cert no) ─────────────
                String verifyUrl = frontendUrl + "/verify/" + certNo;
                try {
                    byte[] qrBytes = generateQrCode(verifyUrl, 80);
                    PDImageXObject qrImg = PDImageXObject.createFromByteArray(doc, qrBytes, "qr");
                    cs.drawImage(qrImg, W - 108f, 42f, 72f, 72f);
                } catch (Exception e) {
                    log.warn("QR code generation failed for {}: {}", certNo, e.getMessage());
                }
            }

            return toBytes(doc);
        }
    }

    // =========================================================
    //  ORANGE — Typing Certificate overlay
    //  Template: US Letter  612 × 792 pts
    //  PDFBox origin: BOTTOM-LEFT
    // =========================================================

    private byte[] generateTypingOverlay(Certificate cert) throws Exception {
        ClassPathResource res = new ClassPathResource(TEMPLATE_TYPING);
        try (InputStream in = res.getInputStream();
             PDDocument doc  = Loader.loadPDF(in.readAllBytes())) {

            PDPage page = doc.getPage(0);
            float W = page.getMediaBox().getWidth();   // 612.0
            float H = page.getMediaBox().getHeight();  // 792.0

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont reg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            String studentName   = buildFullName(cert);
            String studentId     = cert.getStudent().getStudentId();
            String centerCode    = cert.getInstitute().getInstituteCode();
            String atcName       = cert.getInstitute().getName().toUpperCase().trim();
            String grade         = cert.getGrade() != null ? cert.getGrade() : "";
            String city          = cert.getInstitute().getCity() != null
                    ? cert.getInstitute().getCity() : "";
            String district      = cert.getInstitute().getDistrict() != null
                    ? cert.getInstitute().getDistrict() : city;
            String venue         = city + (district.isEmpty() || district.equals(city)
                    ? "" : "/" + district);
            String month         = cert.getIssueDate() != null
                    ? cert.getIssueDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) : "";
            String subject       = cert.getCourse().getName();
            String certNo        = cert.getCertificateNumber();

            // Parse marks: "obtained/max" → split on "/"
            String marksObtained = "";
            String marksMax      = "100";
            String marksMin      = "40";
            if (cert.getMarks() != null && !cert.getMarks().isBlank()) {
                String[] parts = cert.getMarks().split("/");
                marksObtained = parts[0].trim();
                if (parts.length >= 2) marksMax = parts[1].trim();
            }

            Color navy = new Color(0, 51, 153);

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, AppendMode.APPEND, true, true)) {

                // ── Student ID / Center Code / ATC row ────────────────
                // Measured from scan: data row sits at y ≈ 582 pts from bottom (73.5% of 792)
                // Table spans x≈55 to x≈580 across 3 columns
                //   Col1 (Student ID):  x center ≈ 130  → box 55–225
                //   Col2 (Center Code): x center ≈ 295  → box 225–365
                //   Col3 (ATC Name):    x center ≈ 475  → box 365–580
                float idRowY = 582f;
                drawCenteredTextInBox(cs, bold, 9f, navy, studentId,   55f,  225f, idRowY);
                drawCenteredTextInBox(cs, bold, 9f, navy, centerCode, 225f,  365f, idRowY);
                drawCenteredTextInBox(cs, bold, 9f, navy, atcName,    365f,  580f, idRowY);

                // ── Student name ──────────────────────────────────────
                // Measured: "NAVYA D. AGARE" at y ≈ 510 pts (64.4% of 792)
                float nameY = 510f;
                drawCenteredText(cs, bold, 16f, navy,
                        studentName.toUpperCase(), W, nameY);

                // ── Location / month / grade row ──────────────────────
                // Measured from scan: "held at  Nagpur/Nagpur  in  A  Centre/Grade"
                // y ≈ 395 pts from bottom (49.9%)
                //   venue: after "in the month of" label, x ≈ 165
                //   month: x ≈ 190 (fills the blank after "in the month of")
                //   grade: x ≈ 468 (fills blank after "in")
                float locationY = 395f;
                // venue (city/district) — placed right of "held at" label which ends ~90 pts
                cs.beginText();
                cs.setFont(bold, 9f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(110f, locationY + 2f);   // top sub-line: "held at"
                cs.showText(venue);
                cs.endText();
                // month — right of "in the month of" label
                cs.beginText();
                cs.setFont(bold, 9f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(190f, locationY - 12f);  // bottom sub-line
                cs.showText(month);
                cs.endText();
                // grade — right of "in" on bottom sub-line
                cs.beginText();
                cs.setFont(bold, 10f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(468f, locationY - 12f);
                cs.showText(grade);
                cs.endText();

                // ── Marks table data row ──────────────────────────────
                // Measured: data row at y ≈ 333 pts (42.1% of 792)
                // 5 columns across x≈55 to x≈577:
                //   Subject (col1):       x 55–230
                //   Speed WPM (col2):     x 230–295  → leave blank (not stored)
                //   Max Marks (col3):     x 295–385
                //   Min Marks (col4):     x 385–470
                //   Marks Obtained (col5):x 470–577
                float marksY = 333f;
                drawCenteredTextInBox(cs, bold, 8f, navy, subject,        55f,  230f, marksY);
                // Speed WPM intentionally blank
                drawCenteredTextInBox(cs, bold, 8f, navy, marksMax,      295f,  385f, marksY);
                drawCenteredTextInBox(cs, bold, 8f, navy, marksMin,      385f,  470f, marksY);
                drawCenteredTextInBox(cs, bold, 8f, navy, marksObtained, 470f,  577f, marksY);

                // ── Student photo ─────────────────────────────────────
                // Measured from scan: photo box center ≈ x=306, bottom edge y≈148
                // Photo size in scan: ~80×95 pts
                if (cert.getStudent().getPhotoData() != null) {
                    try {
                        PDImageXObject photo = PDImageXObject.createFromByteArray(
                                doc, cert.getStudent().getPhotoData(), "photo");
                        float photoW = 80f;
                        float photoH = 95f;
                        float photoX = (W - photoW) / 2f;  // centered: (612-80)/2 = 266
                        float photoY = 148f;                // bottom of photo box
                        cs.drawImage(photo, photoX, photoY, photoW, photoH);
                    } catch (Exception e) {
                        log.warn("Could not embed student photo for {}: {}", certNo, e.getMessage());
                    }
                }

                // ── Certificate number (bottom-right) ─────────────────
                // Measured: "Certificate No. 26-63501" at bottom-right, y ≈ 28 pts
                cs.beginText();
                cs.setFont(bold, 8f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(W - 195f, 28f);
                cs.showText("Certificate No. " + certNo);
                cs.endText();
            }

            return toBytes(doc);
        }
    }

    // =========================================================
    //  Drawing helpers
    // =========================================================

    /** Draw text centered horizontally across the full page width. */
    private void drawCenteredText(PDPageContentStream cs, PDFont font, float size,
                                  Color color, String text, float pageWidth, float y)
            throws Exception {
        if (text == null || text.isBlank()) return;
        float tw = font.getStringWidth(text) / 1000f * size;
        float x  = (pageWidth - tw) / 2f;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    /** Draw text centered within a horizontal band [x1, x2] at height y. */
    private void drawCenteredTextInBox(PDPageContentStream cs, PDFont font, float size,
                                       Color color, String text,
                                       float x1, float x2, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        float tw = font.getStringWidth(text) / 1000f * size;
        float boxW = x2 - x1;
        float x = x1 + (boxW - tw) / 2f;
        if (x < x1) x = x1 + 2f; // clamp if text too wide
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    // =========================================================
    //  Utility helpers
    // =========================================================

    private String buildFullName(Certificate cert) {
        String fn = cert.getStudent().getFirstName();
        String mn = cert.getStudent().getMiddleName();
        String sn = cert.getStudent().getSurname();
        return (fn + (mn != null && !mn.isBlank() ? " " + mn : "") + " " + sn).trim();
    }

    private byte[] toBytes(PDDocument doc) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }

    private byte[] generateQrCode(String content, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter()
                .encode(content, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    // =========================================================
    //  Validation
    // =========================================================

    private void validate(Certificate cert) {
        if (cert == null)
            throw new IllegalArgumentException("Certificate must not be null");
        if (cert.getCourse() == null)
            throw new IllegalArgumentException("Certificate has no course");
        if (cert.getStudent() == null)
            throw new IllegalArgumentException("Certificate has no student");
        if (cert.getInstitute() == null)
            throw new IllegalArgumentException("Certificate has no institute");
        if (cert.getCertificateNumber() == null || cert.getCertificateNumber().isBlank())
            throw new IllegalArgumentException("Certificate number must be set before PDF generation");
    }
}
