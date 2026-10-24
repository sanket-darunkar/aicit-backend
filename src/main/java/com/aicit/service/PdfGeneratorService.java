package com.aicit.service;

import com.aicit.entity.Certificate;
import com.aicit.service.CertificateLayout.Align;
import com.aicit.service.CertificateLayout.FieldSpec;
import com.aicit.service.CertificateLayout.FontRole;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Certificate PDF generation service.
 *
 * <p>Draws the official AICIT certificate artwork (a blank PNG template) as a
 * full-page background, then overlays the student/certificate specific data at
 * positions defined declaratively in {@link CertificateLayout}. Positions are
 * fractions of the page so the layout tracks the artwork regardless of the
 * exact pixel size; PDFBox's bottom-left origin conversion is handled centrally.
 *
 * <p>Typography uses embedded serif fonts (Playfair Display for display
 * names/titles, EB Garamond for body serif) to match the reference designs,
 * plus Standard-14 Helvetica for compact data fields. Font size, colour,
 * alignment and max width are per-field (see CertificateLayout), and long
 * names / course titles auto-shrink (and the course banner also wraps) to fit.
 *
 * <p>Routing: course names containing "typing" use the typing template; all
 * others use the general (achievement) template. Signatures, badges, seal,
 * labels and footers are baked into the artwork — this service only overlays
 * dynamic values and never fabricates data (blank fields are not drawn).
 */
@Service
@Slf4j
public class PdfGeneratorService {

    static final String TEMPLATE_GENERAL = "templates/certificates/GeneralCertificateTemplate.png";
    static final String TEMPLATE_TYPING  = "templates/certificates/TypingTemplate.png";

    static final String FONT_SERIF_BOLD = "fonts/PlayfairDisplay-Bold.ttf";
    static final String FONT_SERIF      = "fonts/EBGaramond-Regular.ttf";

    @Value("${app.frontend.url:https://aicit.org.in}")
    private String frontendUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Cached raw font bytes (loaded once; PDType0Font must be created per-document).
    private byte[] serifBoldBytes;
    private byte[] serifBytes;

    @PostConstruct
    void loadFontBytes() {
        serifBoldBytes = readClasspath(FONT_SERIF_BOLD);
        serifBytes     = readClasspath(FONT_SERIF);
        if (serifBoldBytes == null)
            log.warn("Serif-bold font {} not found — will fall back to Helvetica-Bold.", FONT_SERIF_BOLD);
    }

    // =========================================================
    //  Public API (unchanged)
    // =========================================================

    public enum CertificateType { AICIT, TYPING }

    public static CertificateType detectType(String courseName) {
        return isTypingCourse(courseName) ? CertificateType.TYPING : CertificateType.AICIT;
    }

    public static boolean isTypingCourse(String courseName) {
        if (courseName == null) return false;
        return courseName.toLowerCase(Locale.ROOT).contains("typing");
    }

    public byte[] generateCertificatePdf(Certificate cert) {
        validate(cert);
        CertificateType type = detectType(cert.getCourse().getName());
        String templatePath  = (type == CertificateType.TYPING) ? TEMPLATE_TYPING : TEMPLATE_GENERAL;

        if (!new ClassPathResource(templatePath).exists()) {
            throw new RuntimeException("Certificate template not found on classpath: " + templatePath);
        }
        log.info("Generating {} certificate for {} — template={}",
                type, cert.getCertificateNumber(), templatePath);
        try {
            return (type == CertificateType.TYPING) ? generateTyping(cert) : generateGeneral(cert);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                "PDF generation failed for " + cert.getCertificateNumber() + ": " + e.getMessage(), e);
        }
    }

    // =========================================================
    //  Per-document font set
    // =========================================================

    private static final class Fonts {
        PDFont serifBold, serif, sansBold, sans;
        PDFont of(FontRole role) {
            return switch (role) {
                case SERIF_BOLD -> serifBold;
                case SERIF      -> serif;
                case SANS_BOLD  -> sansBold;
                case SANS       -> sans;
            };
        }
    }

    private Fonts loadFonts(PDDocument doc) throws Exception {
        Fonts f = new Fonts();
        f.sansBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        f.sans     = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        // Embedded serifs (fall back to Helvetica-Bold if a font file is missing).
        f.serifBold = (serifBoldBytes != null)
                ? PDType0Font.load(doc, new ByteArrayInputStream(serifBoldBytes), true)
                : f.sansBold;
        f.serif = (serifBytes != null)
                ? PDType0Font.load(doc, new ByteArrayInputStream(serifBytes), true)
                : f.sans;
        return f;
    }

    // =========================================================
    //  ACHIEVEMENT — "Certificate of Achievement"
    // =========================================================

    private byte[] generateGeneral(Certificate cert) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            Page p = newPageFromTemplate(doc, TEMPLATE_GENERAL);
            Fonts f = loadFonts(doc);
            Map<String, FieldSpec> L = CertificateLayout.ACHIEVEMENT;

            String duration = safe(cert.getCourse().getDurationLabel());
            if (duration.isEmpty() && cert.getCourse().getDurationMonths() != null)
                duration = cert.getCourse().getDurationMonths() + " Months";
            String atc = safe(cert.getInstitute().getName()).toUpperCase()
                    + (safe(cert.getInstitute().getCity()).isEmpty() ? ""
                       : "  " + safe(cert.getInstitute().getCity()).toUpperCase());

            try (PDPageContentStream cs = new PDPageContentStream(doc, p.page)) {
                cs.drawImage(p.bg, 0, 0, p.w, p.h);

                // Paint over the baked-in field-value underlines (cream rectangles)
                // before drawing any values, so the values sit on a clean surface.
                drawCoverBars(cs, p, CertificateLayout.ACH_UNDERLINE_COVERS, CertificateLayout.ACH_PAPER);

                // Course title — wraps to 2 lines inside the banner if needed.
                drawWrapped(cs, f, p, L.get("course"),
                        safe(cert.getCourse().getName()).toUpperCase(), 2);

                drawField(cs, f, p, L.get("name"),      buildFullName(cert).toUpperCase());
                drawField(cs, f, p, L.get("duration"),  duration);
                drawField(cs, f, p, L.get("grade"),     safe(cert.getGrade()));
                drawField(cs, f, p, L.get("marks"),     safe(cert.getMarks()));
                drawField(cs, f, p, L.get("studentId"), safe(cert.getStudent().getStudentId()));
                drawField(cs, f, p, L.get("certNo"),    cert.getCertificateNumber());
                drawField(cs, f, p, L.get("issue"),
                        cert.getIssueDate() != null ? cert.getIssueDate().format(DATE_FMT) : "");
                drawField(cs, f, p, L.get("atc"),       atc);

                drawPhotoInBox(doc, cs, cert, p, CertificateLayout.ACH_PHOTO_BOX);
                drawQrInBox(doc, cs, cert.getCertificateNumber(), p, CertificateLayout.ACH_QR_BOX);
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
            Fonts f = loadFonts(doc);
            Map<String, FieldSpec> L = CertificateLayout.TYPING;

            String city     = safe(cert.getInstitute().getCity());
            String district = safe(cert.getInstitute().getDistrict());
            String venue    = city + (district.isEmpty() || district.equalsIgnoreCase(city) ? "" : "/" + district);
            String month    = cert.getIssueDate() != null
                    ? cert.getIssueDate().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) : "";
            String subject  = safe(cert.getCourse().getName());

            String obtained = "", max = "";
            if (!safe(cert.getMarks()).isBlank()) {
                String[] parts = cert.getMarks().split("/");
                obtained = parts[0].trim();
                if (parts.length >= 2) max = parts[1].trim();
            }

            try (PDPageContentStream cs = new PDPageContentStream(doc, p.page)) {
                cs.drawImage(p.bg, 0, 0, p.w, p.h);

                drawField(cs, f, p, L.get("studentId"),  safe(cert.getStudent().getStudentId()));
                drawField(cs, f, p, L.get("centreCode"), safe(cert.getInstitute().getInstituteCode()));
                drawField(cs, f, p, L.get("atc"),        safe(cert.getInstitute().getName()).toUpperCase());
                drawField(cs, f, p, L.get("name"),       buildFullName(cert).toUpperCase());
                drawField(cs, f, p, L.get("venue"),      venue);
                drawField(cs, f, p, L.get("month"),      month);
                drawField(cs, f, p, L.get("grade"),      safe(cert.getGrade()));
                drawField(cs, f, p, L.get("wpm"),        extractWpm(subject));
                drawField(cs, f, p, L.get("maxMarks"),   max);
                drawField(cs, f, p, L.get("obtained"),   obtained);
                drawField(cs, f, p, L.get("certNo"),     cert.getCertificateNumber());
                drawField(cs, f, p, L.get("issue"),
                        cert.getIssueDate() != null ? cert.getIssueDate().format(DATE_FMT) : "");

                drawPhotoInBox(doc, cs, cert, p, CertificateLayout.TYP_PHOTO_BOX);
            }
            return toBytes(doc);
        }
    }

    // =========================================================
    //  Core field renderer (align + auto-shrink, config-driven)
    // =========================================================

    /** Draw a single configured field. No-op for blank values. */
    private void drawField(PDPageContentStream cs, Fonts f, Page p, FieldSpec spec, String text)
            throws Exception {
        if (spec == null || text == null || text.isBlank()) return;
        String s = sanitize(text, f.of(spec.font()));
        if (s.isBlank()) return;

        float x1 = p.w * spec.x1();
        float x2 = p.w * spec.x2();
        float maxW = x2 - x1;
        float y  = p.h * (1f - spec.y());          // top-origin fraction → bottom-left pt

        PDFont font = f.of(spec.font());
        float size = fitSize(font, s, spec.size(), maxW - 2f);
        float tw   = textWidth(font, s, size);

        float x = (spec.align() == Align.CENTER) ? x1 + (maxW - tw) / 2f : x1;
        if (x < x1) x = x1;

        emit(cs, font, size, spec.color(), s, x, y, spec.boldStroke());
    }

    /** Draw text that may wrap to up to {@code maxLines} centered lines (banner titles). */
    private void drawWrapped(PDPageContentStream cs, Fonts f, Page p, FieldSpec spec,
                             String text, int maxLines) throws Exception {
        if (spec == null || text == null || text.isBlank()) return;
        PDFont font = f.of(spec.font());
        String s = sanitize(text, font);
        float x1 = p.w * spec.x1(), x2 = p.w * spec.x2();
        float maxW = x2 - x1;
        float baseY = p.h * (1f - spec.y());

        // Try single line at full size first (auto-shrink down to ~80% before wrapping).
        float size = spec.size();
        if (textWidth(font, s, size) <= maxW) {
            emitCentered(cs, font, size, spec.color(), s, x1, maxW, baseY);
            return;
        }
        // Wrap into <= maxLines by words, shrinking until it fits.
        List<String> lines = wrap(font, s, maxW, size);
        while (lines.size() > maxLines && size > 10f) {
            size -= 1f;
            lines = wrap(font, s, maxW, size);
        }
        // If still too many lines, hard-shrink the single longest arrangement.
        if (lines.size() > maxLines) {
            size = fitSize(font, s, spec.size(), maxW);
            lines = List.of(s);
        }
        float lineH = size * 1.05f;
        // Vertically center the block around the configured baseline.
        float startY = baseY + (lines.size() - 1) * lineH / 2f;
        for (int i = 0; i < lines.size(); i++) {
            emitCentered(cs, font, size, spec.color(), lines.get(i), x1, maxW, startY - i * lineH);
        }
    }

    private List<String> wrap(PDFont font, String s, float maxW, float size) throws Exception {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : s.split("\\s+")) {
            String trial = cur.length() == 0 ? word : cur + " " + word;
            if (textWidth(font, trial, size) <= maxW || cur.length() == 0) {
                cur = new StringBuilder(trial);
            } else {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ── Low-level emit ────────────────────────────────────────

    private void emit(PDPageContentStream cs, PDFont font, float size, Color color,
                      String s, float x, float y) throws Exception {
        emit(cs, font, size, color, s, x, y, 0f);
    }

    /**
     * Draw text. When {@code stroke > 0}, glyphs are both filled and stroked in
     * the same colour, producing a synthetic extra-bold (heavier than the font's
     * own bold) — useful when no heavier font file is available.
     */
    private void emit(PDPageContentStream cs, PDFont font, float size, Color color,
                      String s, float x, float y, float stroke) throws Exception {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        if (stroke > 0f) {
            cs.setStrokingColor(color);
            cs.setLineWidth(stroke);
            cs.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.FILL_STROKE);
        }
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
        if (stroke > 0f) {
            // Reset so subsequent text isn't accidentally bolded.
            cs.setRenderingMode(org.apache.pdfbox.pdmodel.graphics.state.RenderingMode.FILL);
            cs.setLineWidth(1f);
        }
    }

    private void emitCentered(PDPageContentStream cs, PDFont font, float size, Color color,
                              String s, float x1, float maxW, float y) throws Exception {
        float tw = textWidth(font, s, size);
        emit(cs, font, size, color, s, x1 + (maxW - tw) / 2f, y);
    }

    private float textWidth(PDFont font, String s, float size) throws Exception {
        return font.getStringWidth(s) / 1000f * size;
    }

    private float fitSize(PDFont font, String text, float size, float maxWidth) throws Exception {
        float s = size;
        while (s > 6f && textWidth(font, text, s) > maxWidth) s -= 0.5f;
        return s;
    }

    // =========================================================
    //  Page / image helpers
    // =========================================================

    private static final class Page {
        PDPage page; PDImageXObject bg; float w; float h;
    }

    private Page newPageFromTemplate(PDDocument doc, String classpath) throws Exception {
        byte[] bytes = readClasspath(classpath);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
        float ratio = (float) img.getHeight() / (float) img.getWidth();
        float w = PDRectangle.A4.getWidth();   // 595.28 pts
        float h = w * ratio;                    // preserve artwork proportions (no distortion)
        Page p = new Page();
        p.page = new PDPage(new PDRectangle(w, h));
        doc.addPage(p.page);
        p.bg = PDImageXObject.createFromByteArray(doc, bytes, "bg");
        p.w = w; p.h = h;
        return p;
    }

    /** Draw the student photo inside a fractional box, preserving aspect ratio (center-crop to fill). */
    private void drawPhotoInBox(PDDocument doc, PDPageContentStream cs, Certificate cert,
                                Page p, float[] box) {
        byte[] photoData = cert.getStudent().getPhotoData();
        if (photoData == null || photoData.length == 0) return;   // blank box stays blank
        try {
            PDImageXObject photo = PDImageXObject.createFromByteArray(doc, photoData, "photo");
            float boxX = p.w * box[0];
            float boxY = p.h * (1f - box[3]);
            float boxW = p.w * (box[2] - box[0]);
            float boxH = p.h * (box[3] - box[1]);
            // Scale to COVER the frame, then center (center-crop) so no empty gaps.
            float ir = (float) photo.getHeight() / (float) photo.getWidth();
            float drawW = boxW, drawH = boxW * ir;
            if (drawH < boxH) { drawH = boxH; drawW = boxH / ir; }
            float dx = boxX + (boxW - drawW) / 2f;
            float dy = boxY + (boxH - drawH) / 2f;
            // Clip to the box so the cover-scaled photo doesn't bleed past the frame.
            cs.saveGraphicsState();
            cs.addRect(boxX, boxY, boxW, boxH);
            cs.clip();
            cs.drawImage(photo, dx, dy, drawW, drawH);
            cs.restoreGraphicsState();
        } catch (Exception e) {
            log.warn("Could not embed student photo for {}: {}", cert.getCertificateNumber(), e.getMessage());
        }
    }

    /**
     * Paint filled rectangles (in the paper colour) over regions of the artwork —
     * used to hide the baked-in field-value underlines without editing the PNG.
     * Each box is {x1,y1,x2,y2} as TOP-LEFT→BOTTOM-RIGHT page fractions.
     */
    private void drawCoverBars(PDPageContentStream cs, Page p, float[][] boxes, Color paper)
            throws Exception {
        if (boxes == null) return;
        cs.setNonStrokingColor(paper);
        for (float[] b : boxes) {
            float x = p.w * b[0];
            float y = p.h * (1f - b[3]);              // bottom edge (PDFBox bottom-left origin)
            float w = p.w * (b[2] - b[0]);
            float h = p.h * (b[3] - b[1]);
            cs.addRect(x, y, w, h);
        }
        cs.fill();
    }

    private void drawQrInBox(PDDocument doc, PDPageContentStream cs, String certNo, Page p, float[] box) {
        try {
            String verifyUrl = frontendUrl.split(",")[0].trim().replaceAll("/+$", "") + "/verify/" + certNo;
            byte[] qr = generateQrCode(verifyUrl, 240);
            PDImageXObject qrImg = PDImageXObject.createFromByteArray(doc, qr, "qr");
            float boxX = p.w * box[0];
            float boxY = p.h * (1f - box[3]);
            float side = Math.min(p.w * (box[2] - box[0]), p.h * (box[3] - box[1]));
            cs.drawImage(qrImg, boxX, boxY, side, side);
        } catch (Exception e) {
            log.warn("QR code generation failed for {}: {}", certNo, e.getMessage());
        }
    }

    // =========================================================
    //  Utility
    // =========================================================

    private String extractWpm(String courseName) {
        if (courseName == null) return "";
        var m = java.util.regex.Pattern
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

    private static String safe(String s) { return s == null ? "" : s.trim(); }

    /**
     * Drop characters the chosen font cannot encode. Embedded TTFs cover full
     * Latin; Standard-14 Helvetica is WinAnsi only. Checking per-font prevents
     * "U+XXXX is not available in this font" crashes while keeping as much text
     * as possible.
     */
    private static String sanitize(String s, PDFont font) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            try {
                font.getStringWidth(ch);   // throws if unencodable
                out.append(ch);
            } catch (Exception ignore) {
                out.append(' ');
            }
            i += Character.charCount(cp);
        }
        return out.toString().replaceAll("\\s+", " ").trim();
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

    private static byte[] readClasspath(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (Exception e) {
            return null;
        }
    }

    // =========================================================
    //  Validation (unchanged)
    // =========================================================

    private void validate(Certificate cert) {
        if (cert == null) throw new IllegalArgumentException("Certificate must not be null");
        if (cert.getCourse() == null) throw new IllegalArgumentException("Certificate has no course");
        if (cert.getStudent() == null) throw new IllegalArgumentException("Certificate has no student");
        if (cert.getInstitute() == null) throw new IllegalArgumentException("Certificate has no institute");
        if (cert.getCertificateNumber() == null || cert.getCertificateNumber().isBlank())
            throw new IllegalArgumentException("Certificate number must be set before PDF generation");
    }
}
