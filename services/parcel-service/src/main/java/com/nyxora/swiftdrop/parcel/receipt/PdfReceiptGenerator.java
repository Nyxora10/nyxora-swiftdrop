package com.nyxora.swiftdrop.parcel.receipt;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Enterprise-grade digital delivery receipt and logistics waybill generator.
 * Uses OpenPDF to construct structured, beautifully balanced single-page A4 documents,
 * and Google ZXing to render embedded linear Code-128 tracking barcodes.
 */
@Slf4j
@Component
public class PdfReceiptGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    // Corporate Color Palette (DHL / FedEx modern logistics standard)
    private static final Color BRAND_NAVY      = new Color(15, 23, 42);     // Slate 900 (#0F172A)
    private static final Color BRAND_BLUE      = new Color(2, 132, 199);    // Sky 600 (#0284C7)
    private static final Color ACCENT_EMERALD  = new Color(16, 149, 119);   // Emerald 600 (#109577)
    private static final Color BG_LIGHT        = new Color(248, 250, 252);  // Slate 50 (#F8FAFC)
    private static final Color BG_CARD_ALT     = new Color(241, 245, 249);  // Slate 100 (#F1F5F9)
    private static final Color BORDER_SUBTLE   = new Color(226, 232, 240);  // Slate 200 (#E2E8F0)
    private static final Color TEXT_MUTED      = new Color(100, 116, 139);  // Slate 500 (#64748B)

    /**
     * Generates a complete digital PDF delivery receipt with an embedded Code-128 tracking barcode.
     * Engineered to balance content across a full single-page A4 canvas with zero awkward whitespace.
     *
     * @param parcel the persisted parcel entity with zone and pricing data
     * @return raw PDF document byte array
     */
    public byte[] generateReceipt(Parcel parcel) {
        log.debug("Generating digital PDF receipt for trackingNumber={}", parcel.getTrackingNumber());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // A4 page with balanced 32pt margins
            Document document = new Document(PageSize.A4, 32, 32, 32, 32);
            PdfWriter.getInstance(document, baos);
            document.open();

            // 1. Top Decorative Brand Bar & Corporate Header
            addHeader(document, parcel);

            // 2. Linear Code-128 Tracking Barcode & Waybill Reference
            addBarcode(document, parcel.getTrackingNumber());

            // 3. Sender (Origin) & Recipient (Destination) Dual Cards
            addPartyDetails(document, parcel);

            // 4. Logistics Specifications 4-Column Matrix
            addShipmentDetails(document, parcel);

            // 5. Total Settlement Amount Card (Clean, Prominent Summary)
            addTotalChargeCard(document, parcel);

            // 6. Chain of Custody & Delivery Handover Sign-off Block
            addHandoverSignoff(document);

            // 7. Security Guarantee, Terms of Carriage & Verification Footer
            addFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (DocumentException | IOException ex) {
            log.error("Failed to generate PDF delivery receipt for parcel {}: {}", parcel.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to generate digital PDF delivery receipt.", ex);
        }
    }

    private void addHeader(Document document, Parcel parcel) throws DocumentException {
        // Decorative top brand bar
        PdfPTable stripeTable = new PdfPTable(2);
        stripeTable.setWidthPercentage(100);
        stripeTable.setWidths(new float[] { 70, 30 });

        PdfPCell blueStripe = new PdfPCell();
        blueStripe.setBackgroundColor(BRAND_BLUE);
        blueStripe.setFixedHeight(4);
        blueStripe.setBorder(Rectangle.NO_BORDER);
        stripeTable.addCell(blueStripe);

        PdfPCell navyStripe = new PdfPCell();
        navyStripe.setBackgroundColor(BRAND_NAVY);
        navyStripe.setFixedHeight(4);
        navyStripe.setBorder(Rectangle.NO_BORDER);
        stripeTable.addCell(navyStripe);

        stripeTable.setSpacingAfter(10);
        document.add(stripeTable);

        // Header Content Table
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[] { 62, 38 });
        headerTable.setSpacingAfter(14);

        // Left Column: Brand Name & Contact Placeholder
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, BRAND_NAVY);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_BLUE);
        Font contactFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.addElement(new Paragraph("SWIFTDROP LOGISTICS", brandFont));
        leftCell.addElement(new Paragraph("Smart Island-Wide Express Logistics Platform", subtitleFont));
        leftCell.addElement(new Paragraph("Colombo, Sri Lanka  \u2022  support@swiftdrop.lk  \u2022  xxxx", contactFont));
        headerTable.addCell(leftCell);

        // Right Column: Document Classification & Timestamp
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, BRAND_BLUE);
        Font metaLabelFont = FontFactory.getFont(FontFactory.HELVETICA, 8, TEXT_MUTED);
        Font statusFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ACCENT_EMERALD);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph docTitle = new Paragraph("CONSIGNMENT DELIVERY RECEIPT", titleFont);
        docTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(docTitle);

        String issuedAt = parcel.getCreatedAt() != null ? DATE_TIME_FORMATTER.format(parcel.getCreatedAt()) : "N/A";
        Paragraph docDate = new Paragraph("Date / Time: " + issuedAt, metaLabelFont);
        docDate.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(docDate);

        Paragraph statusPara = new Paragraph("Lifecycle Status: " + parcel.getCurrentStatus(), statusFont);
        statusPara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(statusPara);

        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addBarcode(Document document, String trackingNumber) throws IOException, DocumentException {
        // High-resolution Code-128 Barcode Image
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(trackingNumber, BarcodeFormat.CODE_128, 420, 75);

        ByteArrayOutputStream barcodeBaos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", barcodeBaos);
        Image barcodeImg = Image.getInstance(barcodeBaos.toByteArray());
        barcodeImg.setAlignment(Element.ALIGN_CENTER);
        barcodeImg.scalePercent(65);

        PdfPTable barcodeTable = new PdfPTable(1);
        barcodeTable.setWidthPercentage(100);
        barcodeTable.setSpacingAfter(14);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(BG_LIGHT);
        cell.setBorderColor(BORDER_SUBTLE);
        cell.setBorderWidth(1f);
        cell.setPaddingTop(9);
        cell.setPaddingBottom(9);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        cell.addElement(barcodeImg);

        Font trackingFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 12, BRAND_NAVY);
        Paragraph trackingText = new Paragraph("TRACKING NO:  " + trackingNumber, trackingFont);
        trackingText.setAlignment(Element.ALIGN_CENTER);
        trackingText.setSpacingBefore(3);
        cell.addElement(trackingText);

        Font instructionFont = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
        Paragraph noteText = new Paragraph("Scan linear barcode at hub dispatch terminals for instant parcel registration.", instructionFont);
        noteText.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(noteText);

        barcodeTable.addCell(cell);
        document.add(barcodeTable);
    }

    private void addPartyDetails(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 50, 50 });
        table.setSpacingAfter(14);

        Font sectionTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_BLUE);
        Font fieldLabelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, TEXT_MUTED);
        Font fieldValueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, BRAND_NAVY);
        Font highlightValueFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, BRAND_NAVY);

        // Sender Cell (Origin Hub)
        PdfPCell senderCell = new PdfPCell();
        senderCell.setBackgroundColor(BG_LIGHT);
        senderCell.setBorderColor(BORDER_SUBTLE);
        senderCell.setPadding(10);

        senderCell.addElement(new Paragraph("ORIGIN / PICKUP HUB", sectionTitleFont));
        senderCell.addElement(new Paragraph("Logistics Zone:", fieldLabelFont));
        String originZoneText = parcel.getOriginZone() != null
                ? parcel.getOriginZone().getZoneName() + " (" + parcel.getOriginZone().getZoneCode() + ")"
                : "Standard Origin Zone";
        senderCell.addElement(new Paragraph(originZoneText, highlightValueFont));

        senderCell.addElement(new Paragraph("Pickup Street Address:", fieldLabelFont));
        senderCell.addElement(new Paragraph(parcel.getPickupAddress(), fieldValueFont));

        senderCell.addElement(new Paragraph("Sender Account ID:", fieldLabelFont));
        senderCell.addElement(new Paragraph(parcel.getSenderUserId() != null ? parcel.getSenderUserId().toString() : "Direct Customer Handover", fieldValueFont));
        table.addCell(senderCell);

        // Recipient Cell (Destination Dropoff)
        PdfPCell recipientCell = new PdfPCell();
        recipientCell.setBackgroundColor(BG_LIGHT);
        recipientCell.setBorderColor(BORDER_SUBTLE);
        recipientCell.setPadding(10);

        recipientCell.addElement(new Paragraph("DESTINATION / DROPOFF HUB", sectionTitleFont));
        recipientCell.addElement(new Paragraph("Recipient Name & Contact:", fieldLabelFont));
        recipientCell.addElement(new Paragraph(parcel.getRecipientName() + "  (" + parcel.getRecipientPhone() + ")", highlightValueFont));

        recipientCell.addElement(new Paragraph("Destination Logistics Zone:", fieldLabelFont));
        String destZoneText = parcel.getDestinationZone() != null
                ? parcel.getDestinationZone().getZoneName() + " (" + parcel.getDestinationZone().getZoneCode() + ")"
                : "Standard Destination Zone";
        recipientCell.addElement(new Paragraph(destZoneText, highlightValueFont));

        recipientCell.addElement(new Paragraph("Delivery Street Address:", fieldLabelFont));
        recipientCell.addElement(new Paragraph(parcel.getDropoffAddress(), fieldValueFont));
        table.addCell(recipientCell);

        document.add(table);
    }

    private void addShipmentDetails(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 25, 25, 25, 25 });
        table.setSpacingAfter(14);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, TEXT_MUTED);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, BRAND_NAVY);

        addSpecCell(table, "PARCEL WEIGHT", parcel.getWeightKg() + " kg", labelFont, valFont);
        addSpecCell(table, "SIZE CLASSIFICATION", String.valueOf(parcel.getParcelSize()), labelFont, valFont);
        addSpecCell(table, "ORIGIN ZONE CODE",
                parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneCode() : "N/A", labelFont, valFont);
        addSpecCell(table, "DEST ZONE CODE",
                parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneCode() : "N/A", labelFont,
                valFont);

        document.add(table);
    }

    private void addSpecCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorderColor(BORDER_SUBTLE);
        cell.setPadding(8);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valFont));
        table.addCell(cell);
    }

    /**
     * Replaces the redundant multi-row breakdown with a clean, prominent Total Settlement Card.
     */
    private void addTotalChargeCard(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 65, 35 });
        table.setSpacingAfter(14);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, BRAND_NAVY);
        Font subFont = FontFactory.getFont(FontFactory.HELVETICA, 7.5f, TEXT_MUTED);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, BRAND_BLUE);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBackgroundColor(BG_CARD_ALT);
        leftCell.setBorderColor(BORDER_SUBTLE);
        leftCell.setPadding(11);
        leftCell.addElement(new Paragraph("TOTAL SETTLEMENT AMOUNT", labelFont));
        leftCell.addElement(new Paragraph("Inclusive of all island-wide transport surcharges, fuel tariffs & handling taxes.", subFont));
        table.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBackgroundColor(BG_CARD_ALT);
        rightCell.setBorderColor(BORDER_SUBTLE);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        rightCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        rightCell.setPadding(11);

        Paragraph valPara = new Paragraph("LKR " + formatCurrency(parcel.getEstimatedFee()), valFont);
        valPara.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(valPara);
        table.addCell(rightCell);

        document.add(table);
    }

    private void addHandoverSignoff(Document document) throws DocumentException {
        // Dual sign-off block for chain of custody
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[] { 50, 50 });
        table.setSpacingAfter(14);

        Font boxTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, BRAND_BLUE);
        Font smallLabel = FontFactory.getFont(FontFactory.HELVETICA, 7, TEXT_MUTED);
        Font lineFont = FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(148, 163, 184));

        // Courier Dispatch Handover Box
        PdfPCell courierCell = new PdfPCell();
        courierCell.setBackgroundColor(Color.WHITE);
        courierCell.setBorderColor(BORDER_SUBTLE);
        courierCell.setPadding(10);

        courierCell.addElement(new Paragraph("1. COURIER PICKUP & DISPATCH HANDOVER", boxTitleFont));
        courierCell.addElement(new Paragraph("Received parcel in secure, sealed condition from sender.", smallLabel));
        courierCell.addElement(new Paragraph(" \nCourier Staff ID / Name: ________________________", lineFont));
        courierCell.addElement(new Paragraph("Pickup Timestamp:       ________________________", lineFont));
        courierCell.addElement(new Paragraph("Courier Signature:      ________________________", lineFont));
        table.addCell(courierCell);

        // Recipient Proof of Delivery Box
        PdfPCell recipientCell = new PdfPCell();
        recipientCell.setBackgroundColor(Color.WHITE);
        recipientCell.setBorderColor(BORDER_SUBTLE);
        recipientCell.setPadding(10);

        recipientCell.addElement(new Paragraph("2. RECIPIENT PROOF OF DOORSTEP DELIVERY", boxTitleFont));
        recipientCell.addElement(new Paragraph("Delivered in good condition with intact tamper-evident seal.", smallLabel));
        recipientCell.addElement(new Paragraph(" \nRecipient Signature:    ________________________", lineFont));
        recipientCell.addElement(new Paragraph("National ID / Passport: ________________________", lineFont));
        recipientCell.addElement(new Paragraph("Received Date & Time:   ________________________", lineFont));
        table.addCell(recipientCell);

        document.add(table);
    }

    private void addFooter(Document document) throws DocumentException {
        // Structured footer panel matching enterprise carrier waybills (DHL / FedEx)
        PdfPTable footerTable = new PdfPTable(2);
        footerTable.setWidthPercentage(100);
        footerTable.setWidths(new float[] { 64, 36 });
        footerTable.setSpacingBefore(4);

        Font headingFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f, BRAND_NAVY);
        Font bulletTitleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.5f, BRAND_NAVY);
        Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, TEXT_MUTED);
        Font linkFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 6.5f, BRAND_BLUE);

        // Left Card: Structured Conditions of Carriage
        PdfPCell termsCell = new PdfPCell();
        termsCell.setBackgroundColor(BG_LIGHT);
        termsCell.setBorderColor(BORDER_SUBTLE);
        termsCell.setPadding(9);

        Paragraph termsTitle = new Paragraph("TERMS & CONDITIONS OF CARRIAGE", headingFont);
        termsTitle.setSpacingAfter(4);
        termsCell.addElement(termsTitle);

        Paragraph p1 = new Paragraph();
        p1.add(new Phrase("\u2022 Standard Liability: ", bulletTitleFont));
        p1.add(new Phrase("Carried subject to SwiftDrop Logistics Standard Carriage Regulations.", bodyFont));
        termsCell.addElement(p1);

        Paragraph p2 = new Paragraph();
        p2.add(new Phrase("\u2022 Fragile Packaging: ", bulletTitleFont));
        p2.add(new Phrase("Sender certifies items are packaged to withstand standard multi-hub handling.", bodyFont));
        termsCell.addElement(p2);

        Paragraph p3 = new Paragraph();
        p3.add(new Phrase("\u2022 Damage Claims: ", bulletTitleFont));
        p3.add(new Phrase("Claims must be registered in writing within 48 hours of doorstep delivery.", bodyFont));
        termsCell.addElement(p3);

        footerTable.addCell(termsCell);

        // Right Card: Customer Support & Electronic Authenticity
        PdfPCell supportCell = new PdfPCell();
        supportCell.setBackgroundColor(BG_LIGHT);
        supportCell.setBorderColor(BORDER_SUBTLE);
        supportCell.setPadding(9);

        Paragraph supportTitle = new Paragraph("SUPPORT & VERIFICATION", headingFont);
        supportTitle.setSpacingAfter(4);
        supportCell.addElement(supportTitle);

        Paragraph s1 = new Paragraph();
        s1.add(new Phrase("24/7 Dispatch Desk: ", bulletTitleFont));
        s1.add(new Phrase("xxxx", linkFont));
        supportCell.addElement(s1);

        Paragraph s2 = new Paragraph();
        s2.add(new Phrase("Consignment Tracking: ", bulletTitleFont));
        s2.add(new Phrase("xxxx", linkFont));
        supportCell.addElement(s2);

        Paragraph s3 = new Paragraph();
        s3.add(new Phrase("Verification: ", bulletTitleFont));
        s3.add(new Phrase("Secure System-Generated Receipt", bodyFont));
        supportCell.addElement(s3);

        footerTable.addCell(supportCell);

        document.add(footerTable);

        // Subtle bottom legal copyright
        Font copyrightFont = FontFactory.getFont(FontFactory.HELVETICA, 6.5f, new Color(148, 163, 184));
        Paragraph copyright = new Paragraph(
                "\u00A9 2026 SwiftDrop Logistics (Pvt) Ltd. All rights reserved. Registered express courier service in Sri Lanka.",
                copyrightFont);
        copyright.setAlignment(Element.ALIGN_CENTER);
        copyright.setSpacingBefore(6);
        document.add(copyright);
    }

    private String formatCurrency(BigDecimal amount) {
        return amount != null ? String.format("%.2f", amount) : "0.00";
    }
}
