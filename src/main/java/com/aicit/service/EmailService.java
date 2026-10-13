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

    // Used in email links — points to the public website (not the CORS origin)
    @Value("${app.api.base-url:https://aicit.org.in}")
    private String frontendUrl;

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

        send(toEmail, subject, body);
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

        send(toEmail, subject, body);
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

        send(toEmail, subject, body);
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

        send(toEmail, subject, body);
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
        if (!mailEnabled) {
            // NOTE: never log the body — welcome/reset emails contain plaintext passwords.
            log.info("[EMAIL DISABLED] To: {} | Subject: {}", to, subject);
            return;
        }

        // Prefer Brevo HTTP API when a key is configured (required on Render).
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevo(to, subject, body);
        } else {
            sendViaSmtp(to, subject, body);
        }
    }

    /** Send email through the Brevo transactional email HTTPS API (port 443). */
    private void sendViaBrevo(String to, String subject, String body) {
        sendViaBrevoReporting(to, subject, body);
    }

    /**
     * Brevo send that returns a status string (used by both the async path and
     * the diagnostic test endpoint). Never includes the API key in the result.
     */
    private String sendViaBrevoReporting(String to, String subject, String body) {
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
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent via SMTP to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email via SMTP to {}: {}", to, e.getMessage());
        }
    }
}
