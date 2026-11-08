package com.nyxora.swiftdrop.notification.dispatcher.email;

import com.nyxora.swiftdrop.common.security.util.PiiMaskingUtil;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Service responsible for assembling and dispatching responsive, UTF-8 multipart HTML emails.
 *
 * Capabilities:
 * - Ingests Thymeleaf model attributes and dynamically renders HTML email bodies.
 * - Enforces data privacy by masking recipient email addresses via PiiMaskingUtil in all log records.
 * - Validates recipient addresses and required email fields defensibly.
 * - Catches and isolates transmission exceptions to prevent consumer pipeline failures.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationDispatcher {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notification.from-email:no-reply@swiftdrop.lk}")
    private String fromEmail = "no-reply@swiftdrop.lk";

    @Value("${app.notification.tracking-base-url:http://localhost:3000/track/}")
    private String trackingBaseUrl = "http://localhost:3000/track/";

    /**
     * Renders a Thymeleaf template and dispatches an HTML email to the specified recipient.
     *
     * @param toEmail Recipient email address
     * @param subject Subject line of the email
     * @param templateName Path to Thymeleaf template (e.g., "email/parcel-booked")
     * @param templateModel Map of model variables to bind into the template
     * @return true if the email was successfully generated and accepted by the mail server; false otherwise
     */
    public boolean sendHtmlEmail(String toEmail, String subject, String templateName, Map<String, Object> templateModel) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Cannot dispatch email: recipient 'toEmail' is null or blank.");
            return false;
        }

        if (subject == null || subject.isBlank()) {
            log.warn("Cannot dispatch email: 'subject' is null or blank for recipient: {}",
                    PiiMaskingUtil.maskEmail(toEmail));
            return false;
        }

        if (templateName == null || templateName.isBlank()) {
            log.warn("Cannot dispatch email: 'templateName' is null or blank for recipient: {}",
                    PiiMaskingUtil.maskEmail(toEmail));
            return false;
        }

        String maskedEmail = PiiMaskingUtil.maskEmail(toEmail);

        try {
            Context context = new Context();
            if (templateModel != null) {
                templateModel.forEach(context::setVariable);
                // Automatically construct trackingUrl if trackingNumber is present and trackingUrl was not provided
                if (templateModel.containsKey("trackingNumber") && !templateModel.containsKey("trackingUrl")) {
                    String trackingNum = String.valueOf(templateModel.get("trackingNumber"));
                    context.setVariable("trackingUrl", trackingBaseUrl + trackingNum);
                }
            }

            String htmlContent = templateEngine.process(templateName, context);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(toEmail.trim());
            helper.setSubject(subject);
            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);

            log.info("Successfully dispatched HTML email: recipient={}, subject='{}', template='{}'",
                    maskedEmail,
                    subject,
                    templateName);

            return true;
        } catch (Exception ex) {
            log.error("Failed to dispatch HTML email to recipient={}: {}",
                    maskedEmail,
                    ex.getMessage(),
                    ex);
            return false;
        }
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public String getTrackingBaseUrl() {
        return trackingBaseUrl;
    }

    public void setTrackingBaseUrl(String trackingBaseUrl) {
        this.trackingBaseUrl = trackingBaseUrl;
    }
}

