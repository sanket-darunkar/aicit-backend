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
     *  x1/x2       horizontal band as page-width fractions (CENTER uses the band;
     *              LEFT uses x1 as the start, x2 as the max-right for auto-shrink)
     *  y           baseline as a page-height fraction from the TOP
     *  size        base font size in points (auto-shrinks to fit the band)
     *  boldStroke  synthetic extra-bold: stroke width in points added around each
     *              glyph (0 = normal). Try 0.4–0.8 for a heavier look. Works on any
     *              font without needing a separate heavy font file.
     */
    public record FieldSpec(
            float x1, float x2, float y,
            FontRole font, float size, Color color, Align align, float boldStroke) {

        /** Backward-compatible constructor — no extra bold (boldStroke = 0). */
        public FieldSpec(float x1, float x2, float y,
                         FontRole font, float size, Color color, Align align) {
            this(x1, x2, y, font, size, color, align, 0f);
        }

        public FieldSpec withY(float ny) {
            return new FieldSpec(x1, x2, ny, font, size, color, align, boldStroke);
        }
    }

    // =========================================================
    //  ACHIEVEMENT ("General") — ARYAN A. MESHRAM reference
    // =========================================================
   public static final Map<String, FieldSpec> ACHIEVEMENT = Map.ofEntries(

    // Student name — shift slightly right, retain current vertical position.
    // boldStroke 0.6 = synthetic extra-bold (heavier than Playfair Bold alone).
    Map.entry("name",
        new FieldSpec(0.085f, 0.950f, 0.425f,
            FontRole.SERIF_BOLD, 29f, NAVY, Align.CENTER, 0.6f)),

    // Course title — retain current banner alignment.
    Map.entry("course",
        new FieldSpec(0.145f, 0.855f, 0.535f,
            FontRole.SERIF_BOLD, 18f, GOLD_LT, Align.CENTER)),

    // Statistics — move slightly upward.
    Map.entry("duration",
        new FieldSpec(0.145f, 0.375f, 0.685f,
            FontRole.SANS_BOLD, 11f, NAVY, Align.CENTER)),

    Map.entry("grade",
    new FieldSpec(0.425f, 0.655f, 0.685f,
        FontRole.SANS_BOLD, 12f, NAVY, Align.CENTER)),

    Map.entry("marks",
        new FieldSpec(0.735f, 0.945f, 0.685f,
            FontRole.SANS_BOLD, 12f, NAVY, Align.CENTER)),

    // Student ID — move upward.
    Map.entry("studentId",
        new FieldSpec(0.235f, 0.445f, 0.733f,
            FontRole.SANS_BOLD, 10f, NAVY, Align.CENTER)),

    // Certificate number — move upward.
    Map.entry("certNo",
        new FieldSpec(0.235f, 0.445f, 0.770f,
            FontRole.SANS_BOLD, 9f, NAVY, Align.CENTER)),

    // Date of issue — move upward.
    Map.entry("issue",
        new FieldSpec(0.235f, 0.445f, 0.808f,
            FontRole.SANS_BOLD, 10f, NAVY, Align.CENTER)),

    // ATC name — move upward and retain centered alignment.
    Map.entry("atc",
    new FieldSpec(0.490f, 0.960f, 0.765f,
        FontRole.SANS_BOLD, 10f, NAVY, Align.CENTER))
);

    // Photo & QR boxes (fractional rectangles: x1,y1 top-left → x2,y2 bottom-right)
    public static final float[] ACH_PHOTO_BOX = {0.775f, 0.345f, 0.955f, 0.475f};
    // QR sits inside the dashed "Verification QR" frame. The renderer keeps the
    // QR square (uses the smaller of width/height), so give it a near-square box.
    public static final float[] ACH_QR_BOX = {0.440f, 0.832f, 0.660f, 0.915f};

    // ── Cover bars: paint the cream background over the 7 field-value underlines
    //    that are baked into the artwork, so the values sit on a clean surface.
    //    Each entry is {x1, y1, x2, y2} as TOP-LEFT→BOTTOM-RIGHT page fractions,
    //    aligned to the underline under each field (NOT the signature lines).
    public static final Color ACH_PAPER = new Color(252, 251, 243); // sampled cream

    // Coordinates pixel-measured from the artwork, with a small margin so each
    // baked-in underline is fully covered (and nothing adjacent is touched).
    public static final float[][] ACH_UNDERLINE_COVERS = {
        // Stats strip underlines (y ≈ 0.6895–0.6908)
        {0.178f, 0.686f, 0.356f, 0.694f},   // Course Duration  (0.185–0.348)
        {0.511f, 0.686f, 0.628f, 0.694f},   // Grade            (0.519–0.620)
        {0.771f, 0.686f, 0.927f, 0.694f},   // Marks Obtained   (0.779–0.919)
        // Left info block
        {0.235f, 0.736f, 0.426f, 0.743f},   // Student ID       (0.242–0.419 @ 0.739)
        {0.270f, 0.773f, 0.426f, 0.780f},   // Certificate No.  (0.277–0.419 @ 0.776)
        {0.258f, 0.811f, 0.426f, 0.818f},   // Date of Issue    (0.265–0.419 @ 0.814)
        // Right: ATC underline
        {0.569f, 0.766f, 0.926f, 0.773f},   // Authorised Training Centre (0.576–0.919 @ 0.769)
    };

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
