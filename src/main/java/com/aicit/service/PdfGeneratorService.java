package com.aicit.service;

import com.aicit.entity.Certificate;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Certificate PDF generation service.
 *
 * <p>Uses Apache PDFBox to draw the official AICIT certificate artwork (a blank
 * PNG template designed in a graphics tool) as a full-page background, then
 * overlays the student / certificate specific data (name, photo, marks, grade,
 * certificate number, issue date, QR code, ATC, etc.) at positions expressed as
 * fractions of the page — so the layout tracks the artwork regardless of its
 * exact pixel size.
 *
 * <p><b>Templates (classpath:templates/certificates/):</b>
 * <ul>
 *   <li>{@code GeneralCertificateTemplate.png} — Certificate of Achievement (all normal courses)</li>
 *   <li>{@code TypingTemplate.png}             — Computer Based Typing Examination (typing courses)</li>
 * </ul>
 *
 * <p>Routing is driven by the course name: any name containing "typing"
 * (case-insensitive) uses the typing template; all others use the general one.
 *
 * <p>Signatures, badges, ISO seal, labels and grade-system footer are already
 * baked into the PNG artwork — this service only overlays dynamic values and
 * never fabricates data (blank/absent fields are simply not drawn).
 */
@Service
@Slf4j
public class PdfGeneratorService {

    // ── Template classpath paths (blank artwork PNGs) ─────────
    static final String TEMPLATE_GENERAL = "templates/certificates/GeneralCertificateTemplate.png";
    static final String TEMPLATE_TYPING  = "templates/certificates/TypingTemplate.png";

    @Value("${app.frontend.url:https://aicit.org.in}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Brand colours sampled from the artwork
    private static final Color NAVY = new Color(11, 27, 64);
    private static final Color GOLD = new Color(176, 132, 46);

    // =========================================================
    //  Public enums / helpers (unchanged API)
    // =========================================================

    public enum CertificateType { AICIT, TYPING }

    public static CertificateType detectType(String courseName) {
        return isTypingCourse(courseName) ? CertificateType.TYPING : CertificateType.AICIT;
    }

    public static boolean isTypingCourse(String courseName) {
        if (courseName == null) return false;
        return courseName.toLowerCase(Locale.ROOT).contains("typing");
    }

    // =========================================================
    //  Main entry point (unchanged signature)
    // =========================================================

    public byte[] generateCertificatePdf(Certificate cert) {
        validate(cert);

        CertificateType type = detectType(cert.getCourse().getName());
        String templatePath  = (type == CertificateType.TYPING) ? TEMPLATE_TYPING : TEMPLATE_GENERAL;

        if (!new ClassPathResource(templatePath).exists()) {
            throw new RuntimeException(
                "Certificate template not found on classpath: " + templatePath +
                ". Place the PNG template at src/main/resources/" + templatePath);
        }

        log.info("Generating {} certificate for {} — template={}",
                type, cert.getCertificateNumber(), templatePath);

        try {
            return (type == CertificateType.TYPING)
                    ? generateTyping(cert)
                    : generateGeneral(cert);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "PDF generation failed for " + cert.getCertificateNumber() + ": " + e.getMessage(), e);
        }
    }

    // =========================================================
    //  GENERAL — "Certificate of Achievement"
    //  Positions are fractions of page W/H, origin TOP-LEFT
    //  (converted to PDFBox bottom-left via ty()).
    // =========================================================

    private byte[] generateGeneral(Certificate cert) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            Page p = newPageFromTemplate(doc, TEMPLATE_GENERAL);
            float W = p.w, H = p.h;

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont reg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont ital = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            // ── Dynamic values (all from the real record; blanks stay blank) ──
            String name     = buildFullName(cert).toUpperCase();
            String course   = safe(cert.getCourse().getName()).toUpperCase();
            String certNo   = cert.getCertificateNumber();
            String grade    = safe(cert.getGrade());
            String marks    = safe(cert.getMarks());
            String studentId= safe(cert.getStudent().getStudentId());
            String issue    = cert.getIssueDate() != null ? cert.getIssueDate().format(DATE_FMT) : "";
            Integer months  = cert.getCourse().getDurationMonths();
            String duration = months != null ? months + " Months" : "";
            String atcName  = safe(cert.getInstitute().getName()).toUpperCase();
            String atcCity  = safe(cert.getInstitute().getCity()).toUpperCase();

            try (PDPageContentStream cs = new PDPageContentStream(doc, p.page)) {
                // background artwork
                cs.drawImage(p.bg, 0, 0, W, H);

                // Student name — large, navy, centered, in the blank below "certify that"
                drawCentered(cs, bold, 26f, NAVY, name, W, ty(H, 0.445f));

                // Course name — inside the navy panel (gold text)
                drawCentered(cs, bold, 17f, new Color(231, 200, 119), course, W, ty(H, 0.533f));

                // Student photo — top-right box (fractional box, keep aspect ratio)
                drawPhotoInBox(doc, cs, cert, W, H, 0.775f, 0.345f, 0.955f, 0.475f, certNo);

                // Stats strip: Course Duration | Grade | Marks Obtained (values on the underlines)
                drawCentered(cs, bold, 12f, NAVY, duration, frac(W, 0.145f), frac(W, 0.375f), ty(H, 0.700f));
                drawCentered(cs, bold, 13f, NAVY, grade,    frac(W, 0.470f), frac(W, 0.635f), ty(H, 0.700f));
                drawCentered(cs, bold, 13f, NAVY, marks,    frac(W, 0.775f), frac(W, 0.945f), ty(H, 0.700f));

                // Left info block: Student ID / Certificate No. / Date of Issue (left-aligned on blanks)
                drawText(cs, bold, 11f, NAVY, studentId, frac(W, 0.235f), ty(H, 0.748f));
                drawText(cs, bold, 11f, NAVY, certNo,    frac(W, 0.235f), ty(H, 0.787f));
                drawText(cs, bold, 11f, NAVY, issue,     frac(W, 0.235f), ty(H, 0.824f));

                // Right block: ATC name + city on the long underline (auto-shrink to fit)
                String atcLine = atcName + (atcCity.isEmpty() ? "" : "  " + atcCity);
                drawFitted(cs, bold, 11.5f, NAVY, atcLine, frac(W, 0.635f), frac(W, 0.945f), ty(H, 0.772f));

                // Verification QR — center-bottom dotted box (real /verify link)
                drawQrInBox(doc, cs, certNo, W, H, 0.455f, 0.862f, 0.565f, 0.930f);
            }

            return toBytes(doc);
        }
    }

    // =========================================================
    //  TYPING — "Computer Based Typing Examination"
    // =========================================================

    private byte[] generateTyping(Certificate cert) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            Page p = newPageFromTemplate(doc, TEMPLATE_TYPING);
            float W = p.w, H = p.h;

            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

            String name       = buildFullName(cert).toUpperCase();
            String studentId  = safe(cert.getStudent().getStudentId());
            String centreCode = safe(cert.getInstitute().getInstituteCode());
            String atcName    = safe(cert.getInstitute().getName()).toUpperCase();
            String grade      = safe(cert.getGrade());
            String city       = safe(cert.getInstitute().getCity());
            String district   = safe(cert.getInstitute().getDistrict());
            String venue      = city + (district.isEmpty() || district.equalsIgnoreCase(city) ? "" : "/" + district);
            String month      = cert.getIssueDate() != null
                    ? cert.getIssueDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) : "";
            String subject    = safe(cert.getCourse().getName());
            String certNo     = cert.getCertificateNumber();
            String issue      = cert.getIssueDate() != null ? cert.getIssueDate().format(DATE_FMT) : "";

            // Marks "obtained/max" → split; min/WPM only shown if present in source
            String obtained = "", max = "";
            if (!safe(cert.getMarks()).isBlank()) {
                String[] parts = cert.getMarks().split("/");
                obtained = parts[0].trim();
                if (parts.length >= 2) max = parts[1].trim();
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, p.page)) {
                cs.drawImage(p.bg, 0, 0, W, H);

                // Header row: Student ID | Centre Code | ATC name (three boxes)
                drawCentered(cs, bold, 11f, NAVY, studentId,  frac(W, 0.055f), frac(W, 0.300f), ty(H, 0.425f));
                drawCentered(cs, bold, 11f, NAVY, centreCode, frac(W, 0.305f), frac(W, 0.520f), ty(H, 0.425f));
                drawCentered(cs, bold, 9.5f, NAVY, atcName,   frac(W, 0.535f), frac(W, 0.955f), ty(H, 0.425f));

                // Student name — in the blank band BELOW "THIS IS TO CERTIFY THAT",
                // centered across the left/main area (not the photo box on the right).
                drawCentered(cs, bold, 17f, NAVY, name, frac(W, 0.030f), frac(W, 0.730f), ty(H, 0.520f));

                // Student photo — right box
                drawPhotoInBox(doc, cs, cert, W, H, 0.790f, 0.450f, 0.950f, 0.585f, certNo);

                // held-at row: venue (top line) + month (bottom line) in the wide middle cell
                drawText(cs, bold, 11f, NAVY, venue, frac(W, 0.400f), ty(H, 0.650f));
                drawText(cs, bold, 11f, NAVY, month, frac(W, 0.400f), ty(H, 0.668f));
                // Centre Grade value (right-hand "Centre Grade" cell)
                drawCentered(cs, bold, 14f, NAVY, grade, frac(W, 0.880f), frac(W, 0.955f), ty(H, 0.660f));

                // Results table data row. The subject label ("Computer Based Typing
                // English") is ALREADY printed in the template, so we do NOT overlay it.
                // We only fill the numeric cells that come from the record.
                float rowY = ty(H, 0.735f);
                drawCentered(cs, bold, 10f, NAVY, extractWpm(subject), frac(W, 0.300f), frac(W, 0.420f), rowY);
                drawCentered(cs, bold, 11f, NAVY, max,      frac(W, 0.420f), frac(W, 0.640f), rowY);
                // Minimum marks column intentionally blank unless a source field exists
                drawCentered(cs, bold, 11f, NAVY, obtained, frac(W, 0.790f), frac(W, 0.955f), rowY);

                // Certificate No. + Date of Issue (on the underlines, below the labels)
                drawText(cs, bold, 11f, NAVY, certNo, frac(W, 0.485f), ty(H, 0.815f));
                drawText(cs, bold, 11f, NAVY, issue,  frac(W, 0.485f), ty(H, 0.853f));
            }

            return toBytes(doc);
        }
    }

    // =========================================================
    //  Page / template helpers
    // =========================================================

    /** A page sized to the template image's aspect ratio, with the image loaded. */
    private static final class Page {
        PDPage page; PDImageXObject bg; float w; float h;
    }

    /**
     * Create a new page whose size matches the template PNG's aspect ratio
     * (fixed to A4 width, height derived from the image ratio so the artwork
     * is never stretched), and load the PNG as a drawable image.
     */
    private Page newPageFromTemplate(PDDocument doc, String classpath) throws Exception {
        byte[] bytes;
        try (InputStream in = new ClassPathResource(classpath).getInputStream()) {
            bytes = in.readAllBytes();
        }
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        float ratio = (float) img.getHeight() / (float) img.getWidth();

        float w = PDRectangle.A4.getWidth();   // 595.28 pts
        float h = w * ratio;                    // keep the artwork's proportions

        Page p = new Page();
        p.page = new PDPage(new PDRectangle(w, h));
        doc.addPage(p.page);
        p.bg = PDImageXObject.createFromByteArray(doc, bytes, "bg");
        p.w = w;
        p.h = h;
        return p;
    }

    // =========================================================
    //  Drawing primitives (fraction-based)
    // =========================================================

    /** Convert a top-origin fraction to a PDFBox bottom-origin Y coordinate. */
    private static float ty(float pageHeight, float fracFromTop) {
        return pageHeight * (1f - fracFromTop);
    }

    /** Absolute coordinate for a fraction of a dimension. */
    private static float frac(float dim, float f) {
        return dim * f;
    }

    /** Left-aligned text at (x, y). No-op for blank values. */
    private void drawText(PDPageContentStream cs, PDFont font, float size,
                          Color color, String text, float x, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
    }

    /** Left-aligned text at x, auto-shrunk so it never exceeds (xMax - x). */
    private void drawFitted(PDPageContentStream cs, PDFont font, float size,
                            Color color, String text, float x, float xMax, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        String s = sanitize(text);
        float size2 = fitSize(font, s, size, xMax - x);
        drawText(cs, font, size2, color, s, x, y);
    }

    /** Text centered across the full page width. */
    private void drawCentered(PDPageContentStream cs, PDFont font, float size,
                              Color color, String text, float pageWidth, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        String s = sanitize(text);
        float size2 = fitSize(font, s, size, pageWidth * 0.9f);
        float tw = font.getStringWidth(s) / 1000f * size2;
        drawText(cs, font, size2, color, s, (pageWidth - tw) / 2f, y);
    }

    /** Text centered within a horizontal band [x1, x2], auto-shrunk to fit. */
    private void drawCentered(PDPageContentStream cs, PDFont font, float size,
                              Color color, String text, float x1, float x2, float y) throws Exception {
        if (text == null || text.isBlank()) return;
        String s = sanitize(text);
        float boxW = x2 - x1;
        float size2 = fitSize(font, s, size, boxW - 4f);
        float tw = font.getStringWidth(s) / 1000f * size2;
        float x = x1 + (boxW - tw) / 2f;
        if (x < x1) x = x1 + 2f;
        drawText(cs, font, size2, color, s, x, y);
    }

    /** Shrink the font size until the text fits the available width (min 6pt). */
    private float fitSize(PDFont font, String text, float size, float maxWidth) throws Exception {
        float s = size;
        while (s > 6f && font.getStringWidth(text) / 1000f * s > maxWidth) {
            s -= 0.5f;
        }
        return s;
    }

    /** Draw the student photo inside a fractional box, preserving aspect ratio. */
    private void drawPhotoInBox(PDDocument doc, PDPageContentStream cs, Certificate cert,
                                float W, float H, float fx1, float fy1, float fx2, float fy2,
                                String certNo) {
        byte[] photoData = cert.getStudent().getPhotoData();
        if (photoData == null || photoData.length == 0) return;   // blank box stays blank
        try {
            PDImageXObject photo = PDImageXObject.createFromByteArray(doc, photoData, "photo");
            float boxX = frac(W, fx1);
            float boxY = ty(H, fy2);                 // bottom of box
            float boxW = frac(W, fx2 - fx1);
            float boxH = H * (fy2 - fy1);
            // Fit within the box, preserving aspect ratio
            float ir = (float) photo.getHeight() / (float) photo.getWidth();
            float drawW = boxW, drawH = boxW * ir;
            if (drawH > boxH) { drawH = boxH; drawW = boxH / ir; }
            float dx = boxX + (boxW - drawW) / 2f;
            float dy = boxY + (boxH - drawH) / 2f;
            cs.drawImage(photo, dx, dy, drawW, drawH);
        } catch (Exception e) {
            log.warn("Could not embed student photo for {}: {}", certNo, e.getMessage());
        }
    }

    /** Draw the verification QR inside a fractional box (genuine /verify link). */
    private void drawQrInBox(PDDocument doc, PDPageContentStream cs, String certNo,
                             float W, float H, float fx1, float fy1, float fx2, float fy2) {
        try {
            String verifyUrl = frontendUrl.split(",")[0].trim().replaceAll("/+$", "")
                    + "/verify/" + certNo;
            byte[] qr = generateQrCode(verifyUrl, 240);
            PDImageXObject qrImg = PDImageXObject.createFromByteArray(doc, qr, "qr");
            float boxX = frac(W, fx1);
            float boxY = ty(H, fy2);
            float side = Math.min(frac(W, fx2 - fx1), H * (fy2 - fy1));
            cs.drawImage(qrImg, boxX, boxY, side, side);
        } catch (Exception e) {
            log.warn("QR code generation failed for {}: {}", certNo, e.getMessage());
        }
    }

    // =========================================================
    //  Utility
    // =========================================================

    /** Pull the WPM value out of a typing course name (e.g. "40 W.P.M." → "40"). */
    private String extractWpm(String courseName) {
        if (courseName == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\d{2,3})\\s*W\\.?P\\.?M\\.?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(courseName);
        return m.find() ? m.group(1) : "";
    }

    private String buildFullName(Certificate cert) {
        String fn = safe(cert.getStudent().getFirstName());
        String mn = safe(cert.getStudent().getMiddleName());
        String sn = safe(cert.getStudent().getSurname());
        return (fn + (mn.isBlank() ? "" : " " + mn) + " " + sn).trim();
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }

    /** Standard-14 fonts only support WinAnsi; drop unsupported chars defensively. */
    private static String sanitize(String s) {
        return s.replaceAll("[^\\x20-\\x7E]", "").trim();
    }

    private byte[] toBytes(PDDocument doc) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }

    private byte[] generateQrCode(String content, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, size, size);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", out);
        return out.toByteArray();
    }

    // =========================================================
    //  Validation (unchanged)
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
