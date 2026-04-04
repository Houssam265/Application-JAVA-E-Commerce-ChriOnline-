package com.chrionline.ui.invoice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;

import java.awt.Color;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class InvoicePdfGenerator {

    private InvoicePdfGenerator() {}

    public static final class InvoiceLine {
        public final String label;
        public final int quantity;
        public final double unitPrice;

        public InvoiceLine(String label, int quantity, double unitPrice) {
            this.label = label == null ? "" : label;
            this.quantity = quantity;
            this.unitPrice = unitPrice;
        }

        public double subtotal() {
            return unitPrice * quantity;
        }
    }

    public static void generate(File outFile,
                                String orderId,
                                String customerName,
                                String customerEmail,
                                LocalDateTime issuedAt,
                                List<InvoiceLine> lines,
                                double tax,
                                double total) throws Exception {

        if (outFile == null) throw new IllegalArgumentException("outFile is null");

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float margin = 52;
            float y = page.getMediaBox().getHeight() - margin;
            float x = margin;

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            String issued = fmt.format(issuedAt != null ? issuedAt : LocalDateTime.now());

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float pageW = page.getMediaBox().getWidth();
                float contentW = pageW - (margin * 2);

                // Top accent bar
                cs.setNonStrokingColor(new Color(244, 106, 61));
                cs.addRect(0, page.getMediaBox().getHeight() - 32, pageW, 32);
                cs.fill();

                // Header
                cs.setNonStrokingColor(new Color(51, 65, 85));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 18);
                cs.newLineAtOffset(x, y);
                cs.showText("ChriOnline — Facture");
                cs.endText();

                y -= 26;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(x, y);
                cs.showText("Date: " + issued);
                cs.endText();

                y -= 14;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(x, y);
                cs.showText("Order ID: " + (orderId == null ? "—" : orderId));
                cs.endText();

                // Client card
                y -= 22;
                cs.setNonStrokingColor(new Color(248, 250, 252));
                cs.addRect(x, y - 34, contentW, 46);
                cs.fill();
                cs.setStrokingColor(new Color(226, 232, 240));
                cs.addRect(x, y - 34, contentW, 46);
                cs.stroke();

                cs.setNonStrokingColor(new Color(15, 23, 42));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                cs.newLineAtOffset(x + 10, y - 4);
                cs.showText("Client");
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(x + 10, y - 4);
                cs.showText((customerName == null || customerName.isBlank()) ? "—" : customerName);
                cs.endText();

                y -= 12;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(x + 10, y - 4);
                cs.showText((customerEmail == null || customerEmail.isBlank()) ? "—" : customerEmail);
                cs.endText();

                // Table header
                y -= 26;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 11);
                cs.newLineAtOffset(x, y);
                cs.showText("Articles");
                cs.endText();

                y -= 18;
                cs.setNonStrokingColor(new Color(241, 245, 249));
                cs.addRect(x, y - 12, contentW, 20);
                cs.fill();

                y -= 6;
                float colItem = x;
                float colQty = x + 320;
                float colUnit = x + 380;
                float colSub = x + 460;

                cs.setNonStrokingColor(new Color(51, 65, 85));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(colItem, y);
                cs.showText("Item");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(colQty, y);
                cs.showText("Qty");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(colUnit, y);
                cs.showText("Unit");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 10);
                cs.newLineAtOffset(colSub, y);
                cs.showText("Subtotal");
                cs.endText();

                y -= 14;
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.setNonStrokingColor(new Color(71, 85, 105));
                if (lines != null) {
                    for (InvoiceLine ln : lines) {
                        if (y < 120) break; // simple single-page guard
                        cs.beginText();
                        cs.newLineAtOffset(colItem, y);
                        cs.showText(truncate(ln.label, 44));
                        cs.endText();

                        cs.beginText();
                        cs.newLineAtOffset(colQty, y);
                        cs.showText(String.valueOf(ln.quantity));
                        cs.endText();

                        cs.beginText();
                        cs.newLineAtOffset(colUnit, y);
                        cs.showText(money(ln.unitPrice));
                        cs.endText();

                        cs.beginText();
                        cs.newLineAtOffset(colSub, y);
                        cs.showText(money(ln.subtotal()));
                        cs.endText();

                        cs.setStrokingColor(new Color(241, 245, 249));
                        cs.moveTo(x, y - 4);
                        cs.lineTo(page.getMediaBox().getWidth() - margin, y - 4);
                        cs.stroke();

                        y -= 14;
                    }
                }

                // Totals
                y -= 12;
                cs.setStrokingColor(new Color(200, 200, 200));
                cs.moveTo(x, y);
                cs.lineTo(page.getMediaBox().getWidth() - margin, y);
                cs.stroke();

                y -= 18;
                float right = page.getMediaBox().getWidth() - margin;

                cs.setNonStrokingColor(new Color(51, 65, 85));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(right - 170, y);
                cs.showText("Tax:");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(right - 70, y);
                cs.showText(money(tax));
                cs.endText();

                y -= 14;
                cs.setNonStrokingColor(new Color(15, 23, 42));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(right - 170, y);
                cs.showText("Total:");
                cs.endText();
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(right - 70, y);
                cs.showText(money(total));
                cs.endText();

                // Footer
                y = 70;
                PDExtendedGraphicsState gs = new PDExtendedGraphicsState();
                gs.setNonStrokingAlphaConstant(0.75f);
                cs.setGraphicsStateParameters(gs);
                cs.setNonStrokingColor(new Color(100, 116, 139));
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 9);
                cs.newLineAtOffset(x, y);
                cs.showText("Merci pour votre achat.");
                cs.endText();
            }

            doc.save(outFile);
        }
    }

    private static String money(double v) {
        return String.format(java.util.Locale.US, "%.2f Dhs", v);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}

