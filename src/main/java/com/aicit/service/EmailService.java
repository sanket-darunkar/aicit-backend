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

    @Value("${app.mail.from:noreply@aicit.org}")
    private String fromAddress;

    // Display name for the From sender
    @Value("${app.mail.from-name:AICIT}")
    private String fromName;

    // Used in email links — points to the public website (not the CORS origin)
    @Value("${app.api.base-url:https://aicit.org}")
    private String frontendUrl;

    // ── Brevo HTTP API (works on Render — SMTP ports are blocked there) ──
    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    private static final String BREVO_URL = "https://api.brevo.com/v3/smtp/email";
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
                  Need help? Contact us at info@aicit.org
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org | +91 8888723485
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
                For further assistance, please contact info@aicit.org.
                
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
                  Need help? Contact us at info@aicit.org
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org | +91 8888723485
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
                  Need help? Contact us at info@aicit.org
                  Phone: +91 8888723485
                ──────────────────────────────────────────────────

                Best regards,
                AICIT Team
                All India Council for Information Technology
                %s | info@aicit.org | +91 8888723485
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

    private void send(String to, String subject, String body) {
        if (!mailEnabled) {
            log.info("[EMAIL DISABLED] To: {} | Subject: {} | Body preview: {}...",
                    to, subject, body.substring(0, Math.min(80, body.length())));
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
            } else {
                log.error("Brevo API failed for {} — HTTP {} : {}",
                        to, response.statusCode(), response.body());
            }
        } catch (Exception e) {
            log.error("Failed to send email via Brevo to {}: {}", to, e.getMessage());
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
