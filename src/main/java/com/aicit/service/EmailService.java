package com.aicit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Email notification service.
 *
 * <p>Transport selection:
 * <ul>
 *   <li>If {@code BREVO_API_KEY} is set → send via Brevo HTTPS API (port 443).
 *       This is required on hosts like Render that block outbound SMTP ports.</li>
 *   <li>Otherwise → fall back to SMTP via {@link JavaMailSender}
 *       (works locally where SMTP is not blocked).</li>
 * </ul>
 *
 * Controlled by {@code app.mail.enabled} — set to false to disable sending.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:false}")
    private boolean mailEnabled;

    @Value("${app.mail.from:info@aicit.org.in}")
    private String fromAddress;

    // Display name for the From sender
    @Value("${app.mail.from-name:AICIT}")
    private String fromName;

    // Used in email links (portal login, verify URL) — must point to the public
    // FRONTEND site, not the backend API. Bound to app.frontend.url (same value
    // PdfGeneratorService uses for the certificate verify link).
    @Value("${app.frontend.url:https://aicit.org.in}")
    private String frontendUrl;

    // Absolute, internet-reachable logo URL for the email header. Email clients
    // cannot load relative paths, so this must be a full https URL. Falls back
    // to a text wordmark if the image fails to load.
    @Value("${app.mail.logo-url:https://aicit.org.in/logos/AICIT-logo.png}")
    private String logoUrl;

    // ── Brevo HTTP API (works on Render — SMTP ports are blocked there) ──
    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    // SMTP credentials — only used to detect misconfiguration at startup
    // (never logged). When SMTP is the active transport but the password is
    // blank, delivery will silently fail, so we warn loudly instead.
    @Value("${spring.mail.password:}")
    private String smtpPassword;

    @Value("${spring.mail.host:}")
    private String smtpHost;

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Startup self-check. Logs the resolved mail configuration — WITHOUT ever
     * printing secrets — so delivery problems can be diagnosed from the boot
     * log alone. This is the first thing to check when "emails aren't arriving".
     */
    @jakarta.annotation.PostConstruct
    void logMailConfig() {
        if (!mailEnabled) {
            log.warn("MAIL DISABLED (app.mail.enabled=false). No emails will be sent. " +
                    "Set MAIL_ENABLED=true to enable delivery.");
            return;
        }
        boolean brevo = brevoApiKey != null && !brevoApiKey.isBlank();
        if (brevo) {
            log.info("MAIL ENABLED — transport=BREVO (HTTPS API). from='{}' <{}>. " +
                    "Ensure this sender/domain is VERIFIED in Brevo or messages will be rejected.",
                    fromName, fromAddress);
        } else {
            log.info("MAIL ENABLED — transport=SMTP host={}:{} user={}. ",
                    smtpHost, "(port from spring.mail.port)", fromAddress);
            if (smtpPassword == null || smtpPassword.isBlank()) {
                log.error("SMTP password is EMPTY — authentication will fail and no mail will be delivered. " +
                        "Set MAIL_PASSWORD, or configure BREVO_API_KEY to use the Brevo HTTPS API instead.");
            }
            log.warn("SMTP transport selected. On hosts that block outbound SMTP ports (e.g. Render), " +
                    "delivery will time out. Set BREVO_API_KEY to switch to the HTTPS API (port 443).");
        }
    }

    // ── Institute approval ────────────────────────────────────

    @Async
    public void sendInstituteApprovalEmail(String toEmail, String instituteName,
                                            String contactName, String tempPassword) {
        String subject = "AICIT – Your Institute Has Been Approved! 🎉";
        String body = String.format("""
                ╔══════════════════════════════════════════════════╗
                          ALL INDIA COUNCIL FOR INFORMATION TECHNOLOGY
                                     AICIT Platform
                ╚══════════════════════════════════════════════════╝

                Dear %s,

                Congratulations! Your institute "%s" has been approved
                and is now an authorised AICIT partner institute.

                ──────────────────────────────────────────────────
                  YOUR LOGIN CREDENTIALS
                ──────────────────────────────────────────────────
                  Portal URL : %s/institute/login
                  Email      : %s
                  Password   : %s
                ──────────────────────────────────────────────────

                ⚠️  IMPORTANT: Please change your password immediately
                    after your first login.

                NEXT STEPS:
                  1. Log in to the Institute Portal
                  2. Add your students
                  3. Submit certificate requests
                  4. Download approved certificates

                ──────────────────────────────────────────────────
                  Need help? Contact us at info@aicit.org.in
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org.in | +91 8888723485
                """,
                contactName, instituteName, frontendUrl, toEmail, tempPassword, frontendUrl);

        String loginUrl = frontendUrl + "/institute/login";
        String inner =
              "<p>Dear <strong>" + esc(contactName) + "</strong>,</p>"
            + "<p>Congratulations! Your institute <strong>" + esc(instituteName) + "</strong> "
            + "has been approved and is now an authorised AICIT partner institute.</p>"
            + infoPanel(new String[][]{
                  {"Portal URL", "<a href=\"" + esc(loginUrl) + "\" style=\"color:" + NAVY + ";\">" + esc(loginUrl) + "</a>"},
                  {"Email",      esc(toEmail)},
                  {"Password",   esc(tempPassword)},
              })
            + "<p style=\"color:#b45309;\">&#9888; Please change your password immediately after your first login.</p>"
            + primaryButton("Log in to Portal", loginUrl)
            + "<p style=\"margin:12px 0 4px;color:" + NAVY + ";font-weight:bold;\">Next steps</p>"
            + "<ol style=\"margin:0 0 0 18px;padding:0;color:" + INK + ";\">"
            + "<li>Log in to the Institute Portal</li><li>Add your students</li>"
            + "<li>Submit certificate requests</li><li>Download approved certificates</li></ol>";

        send(toEmail, subject, body, htmlShell("Your Institute Has Been Approved", inner));
    }

    @Async
    public void sendInstituteRejectionEmail(String toEmail, String instituteName,
                                             String contactName, String reason) {
        String subject = "AICIT – Institute Application Update";
        String body = String.format("""
                Dear %s,
                
                Thank you for applying to join the AICIT platform.
                
                After reviewing your application for "%s", we regret to inform you that
                your application could not be approved at this time.
                
                Reason: %s
                
                You may submit a new application after addressing the above.
                For further assistance, please contact info@aicit.org.in.
                
                Best regards,
                AICIT Team
                """,
                contactName, instituteName,
                (reason != null && !reason.isBlank()) ? reason : "Please contact us for details.");

        String reasonText = (reason != null && !reason.isBlank()) ? reason : "Please contact us for details.";
        String inner =
              "<p>Dear <strong>" + esc(contactName) + "</strong>,</p>"
            + "<p>Thank you for applying to join the AICIT platform.</p>"
            + "<p>After reviewing your application for <strong>" + esc(instituteName) + "</strong>, "
            + "we regret to inform you that it could not be approved at this time.</p>"
            + infoPanel(new String[][]{ {"Reason", esc(reasonText)} })
            + "<p>You may submit a new application after addressing the above. "
            + "For assistance, contact <a href=\"mailto:info@aicit.org.in\" style=\"color:" + NAVY + ";\">info@aicit.org.in</a>.</p>";

        send(toEmail, subject, body, htmlShell("Institute Application Update", inner));
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String contactName,
                                        String instituteName, String newPassword) {
        String subject = "AICIT – Your Password Has Been Reset";
        String body = String.format("""
                ╔══════════════════════════════════════════════════╗
                          ALL INDIA COUNCIL FOR INFORMATION TECHNOLOGY
                                     AICIT Platform
                ╚══════════════════════════════════════════════════╝

                Dear %s,

                Your AICIT Institute Portal password for "%s" has been reset.

                ──────────────────────────────────────────────────
                  Portal URL    : %s/institute/login
                  Email         : %s
                  New Password  : %s
                ──────────────────────────────────────────────────

                ⚠️  Please log in and change your password immediately.

                ──────────────────────────────────────────────────
                  Need help? Contact us at info@aicit.org.in
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org.in | +91 8888723485
                """,
                contactName, instituteName, frontendUrl, toEmail, newPassword, frontendUrl);

        String loginUrl = frontendUrl + "/institute/login";
        String inner =
              "<p>Dear <strong>" + esc(contactName) + "</strong>,</p>"
            + "<p>Your AICIT Institute Portal password for "
            + "<strong>" + esc(instituteName) + "</strong> has been reset.</p>"
            + infoPanel(new String[][]{
                  {"Portal URL",   "<a href=\"" + esc(loginUrl) + "\" style=\"color:" + NAVY + ";\">" + esc(loginUrl) + "</a>"},
                  {"Email",        esc(toEmail)},
                  {"New Password", esc(newPassword)},
              })
            + "<p style=\"color:#b45309;\">&#9888; Please log in and change your password immediately.</p>"
            + primaryButton("Log in to Portal", loginUrl)
            + "<p style=\"color:" + MUTED + ";font-size:12px;\">If you did not request this reset, "
            + "contact us at info@aicit.org.in right away.</p>";

        send(toEmail, subject, body, htmlShell("Your Password Has Been Reset", inner));
    }

    @Async
    public void sendCertificateReadyEmail(String toEmail, String contactName,
                                           String instituteName, String studentName,
                                           String certNumber) {
        String subject = "AICIT – Certificate Ready for Download | " + certNumber;
        String verifyUrl = frontendUrl + "/verify/" + certNumber;
        String body = String.format("""
                ╔══════════════════════════════════════════════════╗
                          ALL INDIA COUNCIL FOR INFORMATION TECHNOLOGY
                                     AICIT Platform
                ╚══════════════════════════════════════════════════╝

                Dear %s,

                Great news! The certificate for student "%s" from %s has been
                approved and is now ready to download.

                ──────────────────────────────────────────────────
                  Certificate Number : %s
                  Student Name       : %s
                  Institute          : %s
                  Verify Online      : %s
                ──────────────────────────────────────────────────

                ACTION REQUIRED:
                Please log in to your Institute Portal to download the
                certificate PDF:

                  Portal URL : %s/institute/login

                The certificate PDF can be downloaded from:
                  Institute Portal → Certificates → Download

                ──────────────────────────────────────────────────
                  Need help? Contact us at info@aicit.org.in
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org.in | +91 8888723485
                """,
                contactName, studentName, instituteName,
                certNumber, studentName, instituteName,
                verifyUrl, frontendUrl, frontendUrl);

        String loginUrl = frontendUrl + "/institute/login";
        String inner =
              "<p>Dear <strong>" + esc(contactName) + "</strong>,</p>"
            + "<p>Great news! The certificate for student <strong>" + esc(studentName) + "</strong> "
            + "from <strong>" + esc(instituteName) + "</strong> has been approved and is ready to download.</p>"
            + infoPanel(new String[][]{
                  {"Certificate No.", esc(certNumber)},
                  {"Student Name",    esc(studentName)},
                  {"Institute",       esc(instituteName)},
                  {"Verify Online",   "<a href=\"" + esc(verifyUrl) + "\" style=\"color:" + NAVY + ";\">" + esc(verifyUrl) + "</a>"},
              })
            + "<p>Log in to your Institute Portal and go to "
            + "<strong>Certificates &rarr; Download</strong> to get the PDF.</p>"
            + primaryButton("Download Certificate", loginUrl);

        send(toEmail, subject, body, htmlShell("Certificate Ready for Download", inner));
    }

    // ── HTML email building ───────────────────────────────────
    // Email clients strip <style>/classes and don't support flex/grid, so every
    // style here is INLINE and the layout is table-based. Palette: deep navy
    // (#0b1b40) + gold (#c9a24a) + ivory (#fbf7ec) to match AICIT branding.

    private static final String NAVY  = "#0b1b40";
    private static final String GOLD  = "#c9a24a";
    private static final String IVORY = "#fbf7ec";
    private static final String INK   = "#1f2937";
    private static final String MUTED = "#6b7280";

    /** HTML-escape a dynamic value so names/reasons can't break the markup. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Wrap inner content in the branded AICIT email shell (header with logo,
     * gold rule, ivory card, footer). Fully inline-styled + table-based so it
     * renders consistently in Gmail / Outlook / Apple Mail.
     */
    private String htmlShell(String title, String innerHtml) {
        return ""
            + "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"></head>"
            + "<body style=\"margin:0;padding:0;background:#eef1f6;"
            + "font-family:Arial,Helvetica,sans-serif;color:" + INK + ";\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"background:#eef1f6;padding:24px 12px;\"><tr><td align=\"center\">"
            + "<table role=\"presentation\" width=\"600\" cellpadding=\"0\" cellspacing=\"0\" "
            + "style=\"width:600px;max-width:100%;background:#ffffff;border-radius:10px;"
            + "overflow:hidden;border:1px solid #e5e7eb;\">"

            // Header band (navy + gold)
            + "<tr><td style=\"background:" + NAVY + ";padding:22px 28px;\">"
            + "<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\"><tr>"
            + "<td align=\"left\" style=\"vertical-align:middle;\">"
            + "<img src=\"" + esc(logoUrl) + "\" alt=\"AICIT\" height=\"46\" "
            + "style=\"display:block;border:0;outline:none;height:46px;\">"
            + "</td><td align=\"right\" style=\"vertical-align:middle;\">"
            + "<div style=\"color:#ffffff;font-size:16px;font-weight:bold;letter-spacing:1px;\">AICIT</div>"
            + "<div style=\"color:" + GOLD + ";font-size:10px;letter-spacing:1px;\">"
            + "INFORMATION TECHNOLOGY</div>"
            + "</td></tr></table></td></tr>"
            // gold rule
            + "<tr><td style=\"height:4px;background:" + GOLD + ";font-size:0;line-height:0;\">&nbsp;</td></tr>"
            // org name strip
            + "<tr><td style=\"padding:16px 28px 0;text-align:center;\">"
            + "<div style=\"color:" + NAVY + ";font-size:14px;font-weight:bold;\">"
            + "ALL INDIA COUNCIL FOR INFORMATION TECHNOLOGY</div>"
            + "<div style=\"color:" + MUTED + ";font-size:11px;\">AICIT Platform</div></td></tr>"

            // Title
            + "<tr><td style=\"padding:18px 28px 0;\">"
            + "<h1 style=\"margin:0;color:" + NAVY + ";font-size:20px;\">" + esc(title) + "</h1></td></tr>"

            // Body content
            + "<tr><td style=\"padding:12px 28px 24px;font-size:14px;line-height:1.6;color:" + INK + ";\">"
            + innerHtml
            + "</td></tr>"

            // Footer
            + "<tr><td style=\"background:" + IVORY + ";border-top:1px solid #eadfc0;"
            + "padding:16px 28px;font-size:12px;color:" + MUTED + ";text-align:center;\">"
            + "Need help? <a href=\"mailto:info@aicit.org.in\" style=\"color:" + NAVY + ";\">info@aicit.org.in</a>"
            + " &nbsp;|&nbsp; +91 8888723485<br>"
            + "<span style=\"color:#9ca3af;\">All India Council for Information Technology</span>"
            + "</td></tr>"

            + "</table></td></tr></table></body></html>";
    }

    /** A gold-bordered info panel (label/value rows) used for credentials etc. */
    private String infoPanel(String[][] rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" "
                + "style=\"background:" + IVORY + ";border:1px solid " + GOLD + ";"
                + "border-radius:8px;margin:14px 0;\"><tr><td style=\"padding:14px 16px;\">");
        for (String[] r : rows) {
            sb.append("<div style=\"padding:4px 0;\">")
              .append("<span style=\"display:inline-block;min-width:130px;color:" + MUTED
                      + ";font-size:12px;\">").append(esc(r[0])).append("</span>")
              .append("<span style=\"color:" + NAVY + ";font-weight:bold;font-size:14px;\">")
              .append(r[1]).append("</span></div>");   // r[1] may contain safe HTML (links)
        }
        sb.append("</td></tr></table>");
        return sb.toString();
    }

    private String primaryButton(String label, String href) {
        return "<table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" style=\"margin:16px 0;\">"
             + "<tr><td style=\"background:" + NAVY + ";border-radius:8px;\">"
             + "<a href=\"" + esc(href) + "\" style=\"display:inline-block;padding:12px 26px;"
             + "color:#ffffff;font-size:14px;font-weight:bold;text-decoration:none;\">"
             + esc(label) + "</a></td></tr></table>";
    }

    // ── Internal ──────────────────────────────────────────────

    @Async
    public void sendRawEmail(String to, String subject, String body) {
        send(to, subject, body);
    }

    /**
     * Synchronous diagnostic send used by the admin "test email" endpoint.
     * Returns a human-readable status string describing the transport and
     * outcome — WITHOUT exposing any credentials — so delivery problems can be
     * diagnosed from the admin UI instead of trawling server logs.
     */
    public String sendTest(String to) {
        String subject = "AICIT – Mail Delivery Test";
        String body = "This is a test message from the AICIT platform mail service. "
                + "If you received this, email delivery is working.";

        if (!mailEnabled) {
            return "DISABLED: app.mail.enabled is false. Set MAIL_ENABLED=true to send email.";
        }
        boolean brevo = brevoApiKey != null && !brevoApiKey.isBlank();
        if (brevo) {
            return "BREVO: " + sendViaBrevoReporting(to, subject, body);
        }
        if (smtpPassword == null || smtpPassword.isBlank()) {
            return "SMTP: password not configured (MAIL_PASSWORD empty) — set MAIL_PASSWORD "
                    + "or configure BREVO_API_KEY. No email sent.";
        }
        try {
            sendViaSmtp(to, subject, body);
            return "SMTP: handed to " + smtpHost + ". If not received, the host may block SMTP ports "
                    + "(use BREVO_API_KEY) or credentials/sender may be invalid.";
        } catch (Exception e) {
            return "SMTP: failed — " + e.getMessage();
        }
    }

    private void send(String to, String subject, String body) {
        send(to, subject, body, null);
    }

    /**
     * Core send. {@code html} is optional — when present it is sent as the rich
     * body with {@code body} kept as the plain-text fallback (good for
     * deliverability and text-only clients).
     */
    private void send(String to, String subject, String body, String html) {
        if (!mailEnabled) {
            // NOTE: never log the body — welcome/reset emails contain plaintext passwords.
            log.info("[EMAIL DISABLED] To: {} | Subject: {}", to, subject);
            return;
        }

        // Prefer Brevo HTTP API when a key is configured (required on Render).
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevoReporting(to, subject, body, html);
        } else {
            sendViaSmtp(to, subject, body, html);
        }
    }

    /**
     * Brevo send that returns a status string (used by both the async path and
     * the diagnostic test endpoint). Never includes the API key in the result.
     */
    private String sendViaBrevoReporting(String to, String subject, String body) {
        return sendViaBrevoReporting(to, subject, body, null);
    }

    private String sendViaBrevoReporting(String to, String subject, String body, String html) {
        try {
            Map<String, Object> sender = new LinkedHashMap<>();
            sender.put("name",  fromName);
            sender.put("email", fromAddress);

            Map<String, String> recipient = new LinkedHashMap<>();
            recipient.put("email", to);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", sender);
            payload.put("to", List.of(recipient));
            payload.put("subject", subject);
            payload.put("textContent", body);
            if (html != null && !html.isBlank()) {
                payload.put("htmlContent", html);
            }

            String json = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BREVO_URL))
                    .timeout(Duration.ofSeconds(15))
                    .header("api-key", brevoApiKey)
                    .header("Content-Type", "application/json")
                    .header("accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Email sent via Brevo to {} — {}", to, subject);
                return "sent (HTTP " + response.statusCode() + ")";
            }
            // Brevo returns a JSON error body (e.g. unverified sender, bad key).
            log.error("Brevo API failed for {} — HTTP {} : {}",
                    to, response.statusCode(), response.body());
            return "FAILED HTTP " + response.statusCode() + " — " + response.body();
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}: {}", to, e.getMessage());
            return "ERROR — " + e.getMessage();
        }
    }

    /** Send email through SMTP (used in dev / where SMTP ports are open). */
    private void sendViaSmtp(String to, String subject, String body) {
        sendViaSmtp(to, subject, body, null);
    }

    private void sendViaSmtp(String to, String subject, String body, String html) {
        try {
            if (html != null && !html.isBlank()) {
                jakarta.mail.internet.MimeMessage mime = mailSender.createMimeMessage();
                org.springframework.mail.javamail.MimeMessageHelper helper =
                        new org.springframework.mail.javamail.MimeMessageHelper(
                                mime, true, "UTF-8");
                helper.setFrom(fromAddress, fromName);
                helper.setTo(to);
                helper.setSubject(subject);
                helper.setText(body, html);   // text fallback + html
                mailSender.send(mime);
            } else {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(fromAddress);
                msg.setTo(to);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
            }
            log.info("Email sent via SMTP to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", to, e.getMessage());
        }
    }
}
