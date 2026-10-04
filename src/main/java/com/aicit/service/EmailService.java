package com.aicit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Email notification service.
 * Controlled by app.mail.enabled — set to false in dev to avoid
 * sending real emails during testing.
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

    @Value("${app.frontend.url:http://localhost:5174}")
    private String frontendUrl;

    // ── Institute approval ────────────────────────────────────

    @Async
    public void sendInstituteApprovalEmail(String toEmail, String instituteName,
                                            String contactName, String tempPassword) {
        String subject = "AICIT – Your Institute Has Been Approved!";
        String body = String.format("""
                Dear %s,
                
                Congratulations! Your institute "%s" has been approved on the AICIT platform.
                
                You can now log in to the Institute Portal using the following credentials:
                
                  Portal URL : %s/institute/login
                  Email      : %s
                  Password   : %s
                
                Please change your password after your first login.
                
                If you have any questions, please contact us at info@aicit.org.
                
                Best regards,
                AICIT Team
                All India Council for Information Technology
                """,
                contactName, instituteName, frontendUrl, toEmail, tempPassword);

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
                Dear %s,
                
                Your AICIT institute portal password for "%s" has been reset by the administrator.
                
                  Portal URL : %s/institute/login
                  Email      : %s
                  New Password: %s
                
                Please log in and change your password immediately.
                
                Best regards,
                AICIT Team
                """,
                contactName, instituteName, frontendUrl, toEmail, newPassword);

        send(toEmail, subject, body);
    }

    @Async
    public void sendCertificateReadyEmail(String toEmail, String contactName,
                                           String instituteName, String studentName,
                                           String certNumber) {
        String subject = "AICIT – Certificate Ready for Download";
        String body = String.format("""
                Dear %s,
                
                The certificate for student "%s" has been approved and is ready to download.
                
                  Certificate Number : %s
                  Verify Online      : %s/verify/%s
                
                Please log in to your institute portal to download the certificate PDF.
                
                Best regards,
                AICIT Team
                """,
                contactName, studentName, certNumber, frontendUrl, certNumber);

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
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromAddress);
            msg.setTo(to);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);
            log.info("Email sent to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
