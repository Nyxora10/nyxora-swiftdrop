package com.nyxora.swiftdrop.notification.dispatcher.email;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailNotificationDispatcherTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    private EmailNotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new EmailNotificationDispatcher(mailSender, templateEngine);
        dispatcher.setFromEmail("no-reply@swiftdrop.lk");
        dispatcher.setTrackingBaseUrl("http://localhost:3000/track/");
    }

    @Test
    @DisplayName("Should successfully dispatch HTML email when all parameters are valid")
    void shouldSendHtmlEmailSuccessfully() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(eq("email/parcel-booked"), any(Context.class)))
                .thenReturn("<html><body>Your booking is confirmed</body></html>");

        Map<String, Object> model = new HashMap<>();
        model.put("trackingNumber", "SWIFT-999000-LK");
        model.put("currentStatus", "CREATED");

        boolean result = dispatcher.sendHtmlEmail(
                "customer@example.com",
                "Booking Confirmed",
                "email/parcel-booked",
                model
        );

        assertTrue(result);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should automatically construct trackingUrl if trackingNumber is provided without trackingUrl")
    void shouldAutoConstructTrackingUrl() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        ArgumentCaptor<Context> contextCaptor = ArgumentCaptor.forClass(Context.class);
        when(templateEngine.process(eq("email/out-for-delivery"), contextCaptor.capture()))
                .thenReturn("<html>Out for delivery</html>");

        Map<String, Object> model = new HashMap<>();
        model.put("trackingNumber", "SWIFT-888-LK");

        boolean result = dispatcher.sendHtmlEmail(
                "customer@example.com",
                "Out for Delivery",
                "email/out-for-delivery",
                model
        );

        assertTrue(result);
        Context captured = contextCaptor.getValue();
        assertEquals("http://localhost:3000/track/SWIFT-888-LK", captured.getVariable("trackingUrl"));
    }

    @Test
    @DisplayName("Should return false when recipient email is null or blank")
    void shouldReturnFalseWhenToEmailIsInvalid() {
        assertFalse(dispatcher.sendHtmlEmail(null, "Subject", "email/test", Map.of()));
        assertFalse(dispatcher.sendHtmlEmail("   ", "Subject", "email/test", Map.of()));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should return false when subject line is null or blank")
    void shouldReturnFalseWhenSubjectIsInvalid() {
        assertFalse(dispatcher.sendHtmlEmail("customer@example.com", null, "email/test", Map.of()));
        assertFalse(dispatcher.sendHtmlEmail("customer@example.com", "   ", "email/test", Map.of()));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should return false when templateName is null or blank")
    void shouldReturnFalseWhenTemplateNameIsInvalid() {
        assertFalse(dispatcher.sendHtmlEmail("customer@example.com", "Subject", null, Map.of()));
        assertFalse(dispatcher.sendHtmlEmail("customer@example.com", "Subject", "   ", Map.of()));

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("Should handle MailException gracefully and return false without throwing")
    void shouldHandleMailExceptionGracefully() {
        MimeMessage mimeMessage = new MimeMessage((Session) null);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html>Body</html>");
        doThrow(new MailSendException("SMTP relay unreachable")).when(mailSender).send(any(MimeMessage.class));

        boolean result = dispatcher.sendHtmlEmail(
                "customer@example.com",
                "Important Alert",
                "email/out-for-delivery",
                Map.of("trackingNumber", "SWIFT-123")
        );

        assertFalse(result);
        verify(mailSender).send(mimeMessage);
    }

    @Test
    @DisplayName("Should support getters and setters for configuration properties")
    void shouldSupportConfigProperties() {
        dispatcher.setFromEmail("custom-from@swiftdrop.lk");
        dispatcher.setTrackingBaseUrl("https://swiftdrop.lk/track/");

        assertEquals("custom-from@swiftdrop.lk", dispatcher.getFromEmail());
        assertEquals("https://swiftdrop.lk/track/", dispatcher.getTrackingBaseUrl());
    }
}

