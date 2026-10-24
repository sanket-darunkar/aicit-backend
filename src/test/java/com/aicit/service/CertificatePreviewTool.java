package com.aicit.service;

import com.aicit.entity.*;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ────────────────────────────────────────────────────────────────────────
 *  CERTIFICATE PREVIEW TOOL  —  fast "hit and run" layout loop
 * ────────────────────────────────────────────────────────────────────────
 *
 *  Generates sample PDFs for BOTH certificate templates and writes them to
 *      target/cert-preview/achievement.pdf
 *      target/cert-preview/typing.pdf
 *  so you can open them and eyeball your CertificateLayout coordinate tweaks
 *  WITHOUT deploying, logging in, or hitting the database.
 *
 *  ── How to use ──────────────────────────────────────────────────────────
 *   1. Edit CertificateLayout.java (positions / sizes / colors) or the artwork.
 *   2. Run:  ./preview-cert.sh         (recommended — builds + opens the PDFs)
 *      or:   mvn -q -Dtest=CertificatePreviewTool test
 *   3. Open target/cert-preview/*.pdf (the script opens them for you on macOS).
 *   4. Repeat. Each run overwrites the files.
 *
 *  This is a developer tool, not a real assertion test — it just renders.
 */
class CertificatePreviewTool {

    private static final Path OUT = Path.of("target", "cert-preview");

    @Test
    void renderSampleCertificates() throws Exception {
        PdfGeneratorService svc = new PdfGeneratorService();
        // @Value field isn't injected outside Spring — set it via reflection.
        var f = PdfGeneratorService.class.getDeclaredField("frontendUrl");
        f.setAccessible(true);
        f.set(svc, "https://aicit.org.in");
        // @PostConstruct isn't called outside Spring — load the fonts manually.
        var load = PdfGeneratorService.class.getDeclaredMethod("loadFontBytes");
        load.setAccessible(true);
        load.invoke(svc);

        Files.createDirectories(OUT);

        byte[] achievement = svc.generateCertificatePdf(sampleAchievement());
        Files.write(OUT.resolve("achievement.pdf"), achievement);
        renderPng(achievement, OUT.resolve("achievement.png"));

        byte[] typing = svc.generateCertificatePdf(sampleTyping());
        Files.write(OUT.resolve("typing.pdf"), typing);
        renderPng(typing, OUT.resolve("typing.png"));

        System.out.println("\n✅ Certificate previews written to:");
        System.out.println("   " + OUT.resolve("achievement.pdf").toAbsolutePath());
        System.out.println("   " + OUT.resolve("typing.pdf").toAbsolutePath() + "\n");
    }

    /** Render page 1 of a PDF to a PNG (for quick visual verification). */
    private static void renderPng(byte[] pdf, java.nio.file.Path out) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc =
                 org.apache.pdfbox.Loader.loadPDF(pdf)) {
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(doc);
            java.awt.image.BufferedImage img = renderer.renderImageWithDPI(0, 140);
            javax.imageio.ImageIO.write(img, "PNG", out.toFile());
        }
    }

    // ── Sample data (edit these to preview different content) ───────────────

    private static Certificate sampleAchievement() {
        Institute inst = Institute.builder()
                .name("Fusion Software Pvt Ltd").instituteCode("AICIT-INST-0001")
                .city("Pune").district("Pune").state("Maharashtra")
                .contactEmail("admin@fusion.local").contactPersonName("Admin").contactMobile("9000000001")
                .status(Institute.Status.APPROVED).build();
        Student stu = Student.builder()
                .studentId("FS-2026-001").firstName("Aryan").middleName("A.").surname("Meshram")
                .ownMobile("9000000002").institute(inst).status(Student.Status.ACTIVE).build();
        Course course = Course.builder()
                .name("Advanced Tally Prime with GST").code("TALLY-ADV")
                .durationMonths(3).isActive(true).build();
        return Certificate.builder()
                .certificateNumber("AICIT-2026-000001")
                .institute(inst).student(stu).course(course)
                .marks("92/100").grade("A+").issueDate(LocalDate.of(2026, 7, 23))
                .status(Certificate.Status.ISSUED).pdfGeneratedAt(LocalDateTime.now()).build();
    }

    private static Certificate sampleTyping() {
        Institute inst = Institute.builder()
                .name("Master Computer Academy").instituteCode("M26584")
                .city("Nagpur").district("Nagpur").state("Maharashtra")
                .contactEmail("admin@mca.local").contactPersonName("Admin").contactMobile("9000000003")
                .status(Institute.Status.APPROVED).build();
        Student stu = Student.builder()
                .studentId("M2026-28").firstName("Navya").middleName("D.").surname("Agare")
                .ownMobile("9000000004").institute(inst).status(Student.Status.ACTIVE).build();
        Course course = Course.builder()
                .name("Computer Based Typing Examination 40 W.P.M.").code("TYPING-ENG")
                .durationMonths(1).isActive(true).build();
        return Certificate.builder()
                .certificateNumber("AICIT-2026-063501")
                .institute(inst).student(stu).course(course)
                .marks("90/100").grade("A").issueDate(LocalDate.of(2026, 7, 1))
                .status(Certificate.Status.ISSUED).pdfGeneratedAt(LocalDateTime.now()).build();
    }
}
