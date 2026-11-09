package com.nyxora.swiftdrop.notification.dispatcher.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EmailTemplateRenderingTest {

    @Autowired
    private TemplateEngine templateEngine;

    @Test
    @DisplayName("Should render parcel-booked.html template with variables correctly")
    void shouldRenderParcelBookedTemplate() {
        Context context = new Context();
        context.setVariable("recipientName", "Sunil Perera");
        context.setVariable("trackingNumber", "SWIFT-BOOKED-001");
        context.setVariable("currentStatus", "CREATED");
        context.setVariable("deliveryNote", "Fragile electronics inside");
        context.setVariable("timestamp", "2026-11-08 00:30:00 UTC");
        context.setVariable("trackingUrl", "http://localhost:3000/track/SWIFT-BOOKED-001");

        String html = templateEngine.process("email/parcel-booked", context);

        assertNotNull(html);
        assertTrue(html.contains("Sunil Perera"));
        assertTrue(html.contains("SWIFT-BOOKED-001"));
        assertTrue(html.contains("CREATED"));
        assertTrue(html.contains("Fragile electronics inside"));
        assertTrue(html.contains("http://localhost:3000/track/SWIFT-BOOKED-001"));
        assertTrue(html.contains("SwiftDrop"));
    }

    @Test
    @DisplayName("Should render out-for-delivery.html template with courier details")
    void shouldRenderOutForDeliveryTemplate() {
        Context context = new Context();
        context.setVariable("recipientName", "Alice Fernando");
        context.setVariable("trackingNumber", "SWIFT-OUT-002");
        context.setVariable("currentStatus", "OUT_FOR_DELIVERY");
        context.setVariable("courierName", "Kamal Silva");
        context.setVariable("courierPhone", "+94 77 123 4567");
        context.setVariable("deliveryNote", "Leave at reception");
        context.setVariable("timestamp", "2026-11-08 08:15:00 UTC");
        context.setVariable("trackingUrl", "http://localhost:3000/track/SWIFT-OUT-002");

        String html = templateEngine.process("email/out-for-delivery", context);

        assertNotNull(html);
        assertTrue(html.contains("Alice Fernando"));
        assertTrue(html.contains("SWIFT-OUT-002"));
        assertTrue(html.contains("OUT_FOR_DELIVERY"));
        assertTrue(html.contains("Kamal Silva"));
        assertTrue(html.contains("+94 77 123 4567"));
        assertTrue(html.contains("Leave at reception"));
        assertTrue(html.contains("http://localhost:3000/track/SWIFT-OUT-002"));
    }

    @Test
    @DisplayName("Should render delivery-success.html template with delivery confirmation")
    void shouldRenderDeliverySuccessTemplate() {
        Context context = new Context();
        context.setVariable("recipientName", "David Wickramasinghe");
        context.setVariable("trackingNumber", "SWIFT-DELIVERED-003");
        context.setVariable("currentStatus", "DELIVERED");
        context.setVariable("courierName", "Kamal Silva");
        context.setVariable("deliveryNote", "Signed by recipient");
        context.setVariable("timestamp", "2026-11-08 11:45:00 UTC");
        context.setVariable("trackingUrl", "http://localhost:3000/track/SWIFT-DELIVERED-003");

        String html = templateEngine.process("email/delivery-success", context);

        assertNotNull(html);
        assertTrue(html.contains("David Wickramasinghe"));
        assertTrue(html.contains("SWIFT-DELIVERED-003"));
        assertTrue(html.contains("DELIVERED"));
        assertTrue(html.contains("Kamal Silva"));
        assertTrue(html.contains("Signed by recipient"));
        assertTrue(html.contains("http://localhost:3000/track/SWIFT-DELIVERED-003"));
        assertTrue(html.contains("delivered successfully"));
        assertTrue(html.contains("View Delivery Confirmation"));
    }

    @Test
    @DisplayName("Should gracefully handle null optional attributes with template fallbacks")
    void shouldHandleNullOptionalAttributesGracefully() {
        Context context = new Context();
        context.setVariable("recipientName", null);
        context.setVariable("trackingNumber", "SWIFT-FALLBACK-004");
        context.setVariable("currentStatus", "OUT_FOR_DELIVERY");
        context.setVariable("courierName", null);
        context.setVariable("courierPhone", null);
        context.setVariable("deliveryNote", null);
        context.setVariable("timestamp", "2026-11-08 12:00:00 UTC");
        context.setVariable("trackingUrl", "http://localhost:3000/track/SWIFT-FALLBACK-004");

        String html = templateEngine.process("email/out-for-delivery", context);

        assertNotNull(html);
        assertTrue(html.contains("Valued Customer"));
        assertTrue(html.contains("SWIFT-FALLBACK-004"));
        assertFalse(html.contains("Assigned Courier"));
    }
}

