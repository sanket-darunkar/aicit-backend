package com.aicit.service;

import java.awt.Color;
import java.util.Map;

/**
 * Centralized, declarative layout configuration for the two certificate
 * templates. All coordinates are expressed as FRACTIONS of the page (origin
 * TOP-LEFT, matching how the artwork is measured); the renderer converts to
 * PDFBox's bottom-left origin. This keeps every magic number in one documented
 * place instead of scattered through the drawing code.
 *
 * Fractions were derived from the blank template artwork:
 *   General (Achievement): 1055 x 1491  (ratio 0.7075, ~A4 portrait)
 *   Typing:                1103 x 1426  (ratio 0.7735)
 */
public final class CertificateLayout {

    private CertificateLayout() {}

    // ── Palette sampled from the artwork ──────────────────────
    public static final Color NAVY      = new Color(11, 27, 64);     // deep navy headings
    public static final Color NAVY_SOFT = new Color(30, 58, 138);    // secondary navy
    public static final Color GOLD      = new Color(176, 132, 46);   // gold/dark-brown name (typing)
    public static final Color GOLD_LT   = new Color(231, 200, 119);  // light gold on navy banner
    public static final Color INK       = new Color(51, 51, 51);

    /** Which embedded font family a field uses. */
    public enum FontRole {
        SERIF_BOLD,   // Playfair Display Bold — display names & titles
        SERIF,        // EB Garamond — elegant body serif
        SANS_BOLD,    // Helvetica-Bold — compact data (IDs, numbers)
        SANS          // Helvetica — labels / small values
    }

    public enum Align { LEFT, CENTER }

    /**
     * One dynamic field placement.
     *  x1/x2  horizontal band as page-width fractions (CENTER uses the band;
     *         LEFT uses x1 as the start, x2 as the max-right for auto-shrink)
     *  y      baseline as a page-height fraction from the TOP
     *  size   base font size in points (auto-shrinks to fit the band)
     */
    public record FieldSpec(
            float x1, float x2, float y,
            FontRole font, float size, Color color, Align align) {

        public FieldSpec withY(float ny) { return new FieldSpec(x1, x2, ny, font, size, color, align); }
    }

    // =========================================================
    //  ACHIEVEMENT ("General") — ARYAN A. MESHRAM reference
    // =========================================================
    public static final Map<String, FieldSpec> ACHIEVEMENT = Map.ofEntries(
        // Student name — large navy serif, centered in the blank band under
        // "This is to certify that".
        Map.entry("name",      new FieldSpec(0.08f, 0.74f, 0.452f, FontRole.SERIF_BOLD, 32f, NAVY,    Align.CENTER)),
        // Course title — inside the navy/gold banner, light-gold serif.
        // y nudged down to vertically center within the banner bar.
        Map.entry("course",    new FieldSpec(0.155f, 0.725f, 0.543f, FontRole.SERIF_BOLD, 19f, GOLD_LT, Align.CENTER)),
        // Stats strip values on the three underlines.
        Map.entry("duration",  new FieldSpec(0.145f, 0.375f, 0.700f, FontRole.SANS_BOLD, 12f, NAVY, Align.CENTER)),
        Map.entry("grade",     new FieldSpec(0.470f, 0.635f, 0.700f, FontRole.SANS_BOLD, 13f, NAVY, Align.CENTER)),
        Map.entry("marks",     new FieldSpec(0.775f, 0.945f, 0.700f, FontRole.SANS_BOLD, 13f, NAVY, Align.CENTER)),
        // Left info block (left-aligned on the blanks).
        Map.entry("studentId", new FieldSpec(0.235f, 0.470f, 0.748f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        Map.entry("certNo",    new FieldSpec(0.235f, 0.470f, 0.787f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        Map.entry("issue",     new FieldSpec(0.235f, 0.470f, 0.824f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        // ATC name + city on the long right underline (auto-shrinks to the band).
        Map.entry("atc",       new FieldSpec(0.635f, 0.930f, 0.772f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT))
    );

    // Photo & QR boxes (fractional rectangles: x1,y1 top-left → x2,y2 bottom-right)
    public static final float[] ACH_PHOTO_BOX = {0.775f, 0.345f, 0.955f, 0.475f};
    public static final float[] ACH_QR_BOX    = {0.455f, 0.862f, 0.565f, 0.930f};

    // =========================================================
    //  TYPING — NAVYA D. AGARE reference
    // =========================================================
    public static final Map<String, FieldSpec> TYPING = Map.ofEntries(
        // Header cells
        Map.entry("studentId", new FieldSpec(0.055f, 0.300f, 0.425f, FontRole.SANS_BOLD, 11f, NAVY, Align.CENTER)),
        Map.entry("centreCode",new FieldSpec(0.305f, 0.520f, 0.425f, FontRole.SANS_BOLD, 11f, NAVY, Align.CENTER)),
        Map.entry("atc",       new FieldSpec(0.535f, 0.955f, 0.425f, FontRole.SANS_BOLD, 10f, NAVY, Align.CENTER)),
        // Student name — gold/dark-brown serif, centered in the left/main area
        // (clear of the photo frame on the right and the divider above).
        Map.entry("name",      new FieldSpec(0.030f, 0.730f, 0.505f, FontRole.SERIF_BOLD, 24f, GOLD, Align.CENTER)),
        // held-at row
        Map.entry("venue",     new FieldSpec(0.360f, 0.560f, 0.650f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        Map.entry("month",     new FieldSpec(0.360f, 0.560f, 0.668f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        Map.entry("grade",     new FieldSpec(0.880f, 0.955f, 0.660f, FontRole.SANS_BOLD, 14f, NAVY, Align.CENTER)),
        // Results table numeric cells (subject label is pre-printed in artwork)
        Map.entry("wpm",       new FieldSpec(0.300f, 0.420f, 0.735f, FontRole.SANS_BOLD, 10f, NAVY, Align.CENTER)),
        Map.entry("maxMarks",  new FieldSpec(0.420f, 0.640f, 0.735f, FontRole.SANS_BOLD, 11f, NAVY, Align.CENTER)),
        Map.entry("obtained",  new FieldSpec(0.790f, 0.955f, 0.735f, FontRole.SANS_BOLD, 11f, NAVY, Align.CENTER)),
        // Certificate no + issue date on the underlines
        Map.entry("certNo",    new FieldSpec(0.485f, 0.720f, 0.815f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT)),
        Map.entry("issue",     new FieldSpec(0.485f, 0.720f, 0.853f, FontRole.SANS_BOLD, 11f, NAVY, Align.LEFT))
    );

    public static final float[] TYP_PHOTO_BOX = {0.790f, 0.450f, 0.950f, 0.585f};
}
