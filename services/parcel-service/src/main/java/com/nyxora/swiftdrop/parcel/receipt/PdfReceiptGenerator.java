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
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.nyxora.swiftdrop.parcel.domain.entity.Parcel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * High-performance digital delivery receipt and invoice generator.
 * Uses OpenPDF to construct structured PDF documents and Google ZXing to generate
 * embedded linear Code-128 tracking barcodes in memory.
 */
@Slf4j
@Component
public class PdfReceiptGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private static final Color PRIMARY_COLOR = new Color(30, 41, 59);    // Slate 800 (#1E293B)
    private static final Color ACCENT_COLOR  = new Color(2, 132, 199);   // Sky 600 (#0284C7)
    private static final Color LIGHT_BG      = new Color(248, 250, 252); // Slate 50 (#F8FAFC)
    private static final Color BORDER_COLOR  = new Color(226, 232, 240); // Slate 200 (#E2E8F0)

    /**
     * Generates a complete digital PDF delivery receipt with an embedded Code-128 tracking barcode.
     *
     * @param parcel the persisted parcel entity with zone and pricing data
     * @return raw PDF document byte array
     */
    public byte[] generateReceipt(Parcel parcel) {
        log.debug("Generating digital PDF receipt for trackingNumber={}", parcel.getTrackingNumber());

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, baos);
            document.open();

            // 1. Header Banner
            addHeader(document, parcel);

            // 2. Linear Code-128 Barcode
            addBarcode(document, parcel.getTrackingNumber());

            // 3. Sender & Recipient Information Table
            addPartyDetails(document, parcel);

            // 4. Shipment & Delivery Specs
            addShipmentDetails(document, parcel);

            // 5. Itemized Financial Summary Table
            addFinancialTable(document, parcel);

            // 6. Corporate Verification Footer
            addFooter(document);

            document.close();
            return baos.toByteArray();

        } catch (DocumentException | IOException ex) {
            log.error("Failed to generate PDF delivery receipt for parcel {}: {}", parcel.getId(), ex.getMessage(), ex);
            throw new IllegalStateException("Failed to generate digital PDF delivery receipt.", ex);
        }
    }

    private void addHeader(Document document, Parcel parcel) throws DocumentException {
        PdfPTable headerTable = new PdfPTable(2);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{65, 35});
        headerTable.setSpacingAfter(15);

        // Left Column: Brand Name & Slogan
        Font brandFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, PRIMARY_COLOR);
        Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.GRAY);

        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(PdfPCell.NO_BORDER);
        leftCell.addElement(new Paragraph("SWIFTDROP LOGISTICS", brandFont));
        leftCell.addElement(new Paragraph("Smart Island-wide Express Delivery Platform", subtitleFont));
        leftCell.addElement(new Paragraph("Colombo, Sri Lanka | support@swiftdrop.lk", subtitleFont));
        headerTable.addCell(leftCell);

        // Right Column: Document Title & Metadata
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, ACCENT_COLOR);
        Font metaFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);

        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(PdfPCell.NO_BORDER);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph docTitle = new Paragraph("DELIVERY RECEIPT", titleFont);
        docTitle.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(docTitle);

        String issuedAt = parcel.getCreatedAt() != null ? DATE_TIME_FORMATTER.format(parcel.getCreatedAt()) : "N/A";
        Paragraph docDate = new Paragraph("Date: " + issuedAt, metaFont);
        docDate.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(docDate);

        Paragraph receiptNo = new Paragraph("Status: " + parcel.getCurrentStatus(), metaFont);
        receiptNo.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(receiptNo);

        headerTable.addCell(rightCell);
        document.add(headerTable);
    }

    private void addBarcode(Document document, String trackingNumber) throws IOException, DocumentException {
        // Generate linear Code-128 barcode image in memory using ZXing
        Code128Writer barcodeWriter = new Code128Writer();
        BitMatrix bitMatrix = barcodeWriter.encode(trackingNumber, BarcodeFormat.CODE_128, 320, 65);

        ByteArrayOutputStream barcodeBaos = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", barcodeBaos);
        Image barcodeImg = Image.getInstance(barcodeBaos.toByteArray());
        barcodeImg.setAlignment(Element.ALIGN_CENTER);
        barcodeImg.scalePercent(80);

        PdfPTable barcodeTable = new PdfPTable(1);
        barcodeTable.setWidthPercentage(100);
        barcodeTable.setSpacingAfter(15);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT_BG);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(10);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);

        cell.addElement(barcodeImg);

        Font trackingFont = FontFactory.getFont(FontFactory.COURIER_BOLD, 11, PRIMARY_COLOR);
        Paragraph trackingText = new Paragraph("TRACKING NO: " + trackingNumber, trackingFont);
        trackingText.setAlignment(Element.ALIGN_CENTER);
        trackingText.setSpacingBefore(4);
        cell.addElement(trackingText);

        barcodeTable.addCell(cell);
        document.add(barcodeTable);
    }

    private void addPartyDetails(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{50, 50});
        table.setSpacingAfter(15);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, ACCENT_COLOR);
        Font valueFont = FontFactory.getFont(FontFactory.HELVETICA, 9, PRIMARY_COLOR);

        // Sender Cell
        PdfPCell senderCell = new PdfPCell();
        senderCell.setBackgroundColor(LIGHT_BG);
        senderCell.setBorderColor(BORDER_COLOR);
        senderCell.setPadding(8);

        senderCell.addElement(new Paragraph("ORIGIN / PICKUP", labelFont));
        senderCell.addElement(new Paragraph("Zone: " + (parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneName() + " (" + parcel.getOriginZone().getZoneCode() + ")" : "N/A"), valueFont));
        senderCell.addElement(new Paragraph("Address: " + parcel.getPickupAddress(), valueFont));
        senderCell.addElement(new Paragraph("Sender ID: " + parcel.getSenderUserId(), valueFont));
        table.addCell(senderCell);

        // Recipient Cell
        PdfPCell recipientCell = new PdfPCell();
        recipientCell.setBackgroundColor(LIGHT_BG);
        recipientCell.setBorderColor(BORDER_COLOR);
        recipientCell.setPadding(8);

        recipientCell.addElement(new Paragraph("DESTINATION / DROPOFF", labelFont));
        recipientCell.addElement(new Paragraph("Recipient: " + parcel.getRecipientName(), valueFont));
        recipientCell.addElement(new Paragraph("Phone: " + parcel.getRecipientPhone(), valueFont));
        recipientCell.addElement(new Paragraph("Zone: " + (parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneName() + " (" + parcel.getDestinationZone().getZoneCode() + ")" : "N/A"), valueFont));
        recipientCell.addElement(new Paragraph("Address: " + parcel.getDropoffAddress(), valueFont));
        table.addCell(recipientCell);

        document.add(table);
    }

    private void addShipmentDetails(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25, 25, 25, 25});
        table.setSpacingAfter(15);

        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.GRAY);
        Font valFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, PRIMARY_COLOR);

        addSpecCell(table, "PARCEL WEIGHT", parcel.getWeightKg() + " kg", labelFont, valFont);
        addSpecCell(table, "SIZE CATEGORY", String.valueOf(parcel.getParcelSize()), labelFont, valFont);
        addSpecCell(table, "ORIGIN ZONE", parcel.getOriginZone() != null ? parcel.getOriginZone().getZoneCode() : "N/A", labelFont, valFont);
        addSpecCell(table, "DEST ZONE", parcel.getDestinationZone() != null ? parcel.getDestinationZone().getZoneCode() : "N/A", labelFont, valFont);

        document.add(table);
    }

    private void addSpecCell(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(Color.WHITE);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(6);
        cell.addElement(new Paragraph(label, labelFont));
        cell.addElement(new Paragraph(value, valFont));
        table.addCell(cell);
    }

    private void addFinancialTable(Document document, Parcel parcel) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{75, 25});
        table.setSpacingAfter(20);

        Font thFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font tdFont = FontFactory.getFont(FontFactory.HELVETICA, 9, PRIMARY_COLOR);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, PRIMARY_COLOR);

        // Header Row
        PdfPCell thDesc = new PdfPCell(new Phrase("DESCRIPTION", thFont));
        thDesc.setBackgroundColor(PRIMARY_COLOR);
        thDesc.setPadding(6);
        table.addCell(thDesc);

        PdfPCell thAmount = new PdfPCell(new Phrase("AMOUNT (LKR)", thFont));
        thAmount.setBackgroundColor(PRIMARY_COLOR);
        thAmount.setHorizontalAlignment(Element.ALIGN_RIGHT);
        thAmount.setPadding(6);
        table.addCell(thAmount);

        // Line Item
        String desc = "SwiftDrop Express Delivery Fee (" + parcel.getOriginZone().getZoneCode() + " \u2192 "
                + parcel.getDestinationZone().getZoneCode() + ", " + parcel.getWeightKg() + "kg, "
                + parcel.getParcelSize() + ")";
        addFeeRow(table, desc, "LKR " + parcel.getEstimatedFee(), tdFont);

        // Total Row
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL AMOUNT", totalFont));
        totalLabelCell.setBackgroundColor(LIGHT_BG);
        totalLabelCell.setBorderColor(BORDER_COLOR);
        totalLabelCell.setPadding(8);
        table.addCell(totalLabelCell);

        PdfPCell totalValCell = new PdfPCell(new Phrase("LKR " + parcel.getEstimatedFee(), totalFont));
        totalValCell.setBackgroundColor(LIGHT_BG);
        totalValCell.setBorderColor(BORDER_COLOR);
        totalValCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalValCell.setPadding(8);
        table.addCell(totalValCell);

        document.add(table);
    }

    private void addFeeRow(PdfPTable table, String desc, String amount, Font font) {
        PdfPCell c1 = new PdfPCell(new Phrase(desc, font));
        c1.setBorderColor(BORDER_COLOR);
        c1.setPadding(6);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase(amount, font));
        c2.setBorderColor(BORDER_COLOR);
        c2.setHorizontalAlignment(Element.ALIGN_RIGHT);
        c2.setPadding(6);
        table.addCell(c2);
    }

    private void addFooter(Document document) throws DocumentException {
        Font footerFont = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);
        Paragraph footer = new Paragraph(
                "This is a system-generated delivery receipt by SwiftDrop Logistics (Pvt) Ltd. "
                        + "Track online at https://swiftdrop.lk/track or contact +94 11 234 5678.",
                footerFont
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
    }
}

