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

    // ── Signature image assets (optional — overlaid if present) ──
    static final String SIGN_EXAM_EXEC      = "templates/certificates/sign_exam_executive.png";
    static final String SIGN_HEAD_INSTITUTE = "templates/certificates/sign_head_institute.png";

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

            Color navy = new Color(0, 51, 153);

            // ── Layout reference (PDF coords, origin = bottom-left) ──
            // Measured from the actual template raster (see positioning notes):
            //   "Certificate" (red cursive)              → y ≈ 671
            //   "This is to certify that,"               → baseline y ≈ 525
            //   "has passed ... with ..... Grade ....."  → baseline y ≈ 352 (cursive)
            //   "has been awarded the"                   → baseline y ≈ 314 (cursive)
            //   "Design and developed as per..."         → baseline y ≈ 288
            //   "EXAM EXECUTIVE" block                   → top line y ≈ 124
            //
            // Blank areas to fill (confirmed against reference certificate):
            //   Student name   → centered, y ≈ 495  (large gap below "certify that,")
            //   ATC / institute→ centered, y ≈ 470
            //   Student photo  → top-right of the blank band, x≈448 y≈475 (90×105)
            //   Grade value    → dotted blank after "with",  centre x ≈ 418, y 352
            //   Marks / %      → dotted blank after "Grade", centre x ≈ 542, y 352
            //   Course name    → own line, centered, y ≈ 300 (above "Design and...")
            //   Signature      → centered above "EXAM EXECUTIVE", x≈445 y≈138

            try (PDPageContentStream cs = new PDPageContentStream(
                    doc, page, AppendMode.APPEND, true, true)) {

                // ── Student photo (top-right of the blank band) ───────
                if (cert.getStudent().getPhotoData() != null) {
                    try {
                        PDImageXObject photo = PDImageXObject.createFromByteArray(
                                doc, cert.getStudent().getPhotoData(), "photo");
                        cs.drawImage(photo, 448f, 472f, 90f, 105f);
                    } catch (Exception e) {
                        log.warn("Could not embed student photo for {}: {}", certNo, e.getMessage());
                    }
                }

                // ── Student name (centered in the gap below "certify that,") ──
                drawCenteredText(cs, bold, 20f, navy,
                        studentName.toUpperCase(), W, 495f);

                // ── ATC / institute line ──────────────────────────────
                drawCenteredText(cs, bold, 10f, navy,
                        atcLine, W, 470f);

                // ── Fill the two dotted blanks on the pre-printed line ──
                // Line reads: "has passed ... examination with [GRADE] Grade [MARKS/%]"
                //   BLANK1 (dots after "with")  centre x ≈ 418 → grade value
                //   BLANK2 (dots after "Grade") centre x ≈ 542 → marks / percentage
                // (Order matches the official reference certificate.)
                float lineY = 366f;
                if (!grade.isEmpty()) {
                    drawCenteredTextInBox(cs, bold, 13f, navy,
                            grade, 388f, 452f, lineY);
                }
                if (!marks.isEmpty()) {
                    drawCenteredTextInBox(cs, bold, 12f, navy,
                            marks, 508f, 582f, lineY);
                }

                // ── Course name — own line above "Design and developed..." ──
                drawCenteredText(cs, bold, 12f, navy,
                        course.toUpperCase(), W, 300f);

                // ── Duration (small, just below course name) ──────────
                if (duration != null) {
                    drawCenteredText(cs, reg, 7.5f,
                            Color.DARK_GRAY,
                            "(Course Duration : " + duration + " Months)",
                            W, 290f);
                }

                // ── Exam Executive signature (above "EXAM EXECUTIVE") ──
                drawSignature(doc, cs, SIGN_EXAM_EXEC, 392f, 132f, 106f, 42f, certNo);

                // ── Certificate number (bottom-left) ──────────────────
                cs.beginText();
                cs.setFont(bold, 8f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(48f, 68f);
                cs.showText("Certificate No. : " + certNo);
                cs.endText();

                // ── Date of issue (bottom-left, below cert no) ────────
                if (!issueDate.isEmpty()) {
                    cs.beginText();
                    cs.setFont(bold, 8f);
                    cs.setNonStrokingColor(navy);
                    cs.newLineAtOffset(48f, 55f);
                    cs.showText("Date of Issue     : " + issueDate);
                    cs.endText();
                }

                // ── QR code (bottom-centre, in the open band) ─────────
                String verifyUrl = frontendUrl + "/verify/" + certNo;
                try {
                    byte[] qrBytes = generateQrCode(verifyUrl, 100);
                    PDImageXObject qrImg = PDImageXObject.createFromByteArray(doc, qrBytes, "qr");
                    cs.drawImage(qrImg, 92f, 150f, 74f, 74f);
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
                // Measured: input boxes sit at pdf_y ≈ 505. Box left edges at
                // x = 62, 192, 328; right edge ≈ 550.
                //   Col1 (Student ID):  box 62–192  (center 127)
                //   Col2 (Center Code): box 192–328 (center 260)
                //   Col3 (ATC Name):    box 328–550 (center 439)
                float idRowY = 513f;
                drawCenteredTextInBox(cs, bold, 9f, navy, studentId,   62f,  192f, idRowY);
                drawCenteredTextInBox(cs, bold, 9f, navy, centerCode, 192f,  328f, idRowY);
                drawCenteredTextInBox(cs, bold, 7.5f, navy, atcName,  328f,  550f, idRowY);

                // ── Student name — in the "WITHIN SIGNED" box ─────────
                // Box spans x ≈ 342–552; vertical centre baseline pdf_y ≈ 480.
                drawCenteredTextInBox(cs, bold, 13f, navy,
                        studentName.toUpperCase(), 342f, 552f, 480f);

                // ── Location / month / grade rows ─────────────────────
                // Measured sub-lines (origin = bottom-left):
                //   top:    "held at ______            Centre"         baseline y ≈ 348
                //   bottom: "in the month of ______  in ___ Grade"     baseline y ≈ 330
                // Label right edges: "held at"≈x127, "in the month of"≈x143,
                // "in"(before Grade)≈x390, "Centre/Grade" column starts ≈x535.
                cs.beginText();
                cs.setFont(bold, 9f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(150f, 348f);
                cs.showText(venue);
                cs.endText();

                cs.beginText();
                cs.setFont(bold, 9f);
                cs.setNonStrokingColor(navy);
                cs.newLineAtOffset(160f, 330f);
                cs.showText(month);
                cs.endText();

                // Grade value sits between "in" (x≈390) and "Centre/Grade" (x≈535)
                drawCenteredTextInBox(cs, bold, 11f, navy, grade, 405f, 525f, 330f);

                // ── Marks table data row ──────────────────────────────
                // Measured: data box row at pdf_y ≈ 288. Column borders at
                // x = 192, 240, 335, 440, 545 (table edges ≈ 55 and 545):
                //   Subject (col1):        55–192
                //   Speed WPM (col2):     192–240  → blank (not stored)
                //   Max Marks (col3):     240–335
                //   Min Marks (col4):     335–440
                //   Marks Obtained (col5):440–545
                float marksY = 288f;
                drawCenteredTextInBox(cs, bold, 7f, navy, subject,        55f,  192f, marksY);
                // Speed WPM intentionally blank
                drawCenteredTextInBox(cs, bold, 8f, navy, marksMax,      240f,  335f, marksY);
                drawCenteredTextInBox(cs, bold, 8f, navy, marksMin,      335f,  440f, marksY);
                drawCenteredTextInBox(cs, bold, 8f, navy, marksObtained, 440f,  545f, marksY);

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

                // ── Head-of-Institute signature (bottom-left) ────────
                // Sits in the open space just above the pre-printed label
                // "Signature of the Head of the Institute with Institution Seal".
                // (The Exam-Executive signature is already baked into this template.)
                drawSignature(doc, cs, SIGN_HEAD_INSTITUTE, 145f, 158f, 120f, 46f, certNo);

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

    /**
     * Draw a signature image from the classpath at the given position/size.
     * Silently skips if the asset is not present so certificates still
     * generate when signature files have not been provided.
     *
     * @param x bottom-left X in PDF points
     * @param y bottom-left Y in PDF points
     * @param w target width in points
     * @param h target height in points
     */
    private void drawSignature(PDDocument doc, PDPageContentStream cs, String classpath,
                               float x, float y, float w, float h, String certNo) {
        ClassPathResource res = new ClassPathResource(classpath);
        if (!res.exists()) {
            log.debug("Signature asset not found (skipping): {}", classpath);
            return;
        }
        try (InputStream in = res.getInputStream()) {
            PDImageXObject img = PDImageXObject.createFromByteArray(doc, in.readAllBytes(), "sig");
            cs.drawImage(img, x, y, w, h);
        } catch (Exception e) {
            log.warn("Could not embed signature {} for {}: {}", classpath, certNo, e.getMessage());
        }
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
