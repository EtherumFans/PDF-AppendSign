package com.demo.pdf;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.TextField;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Nursing record template built with iText 5 primitives.
 */
public final class NursingRecordTemplate {

    private static final float MARGIN = 36f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float ROW_HEIGHT = 30f;
    private static final float TITLE_MARGIN = 24f;
    private static final String[] HEADER_LABELS = {"记录时间", "护理内容", "护士", "签名"};
    private static final String TITLE = "护理记录单";

    private NursingRecordTemplate() {
    }

    public static final String FIELD_TIME_PREFIX = "recordTime_";
    public static final String FIELD_CONTENT_PREFIX = "recordContent_";
    public static final String FIELD_NURSE_PREFIX = "nurseName_";
    public static final String FIELD_SIGN_PREFIX = "nurseSign_";

    public static void createTemplate(String dest, int rowCount) throws IOException, DocumentException {
        if (rowCount <= 0) {
            rowCount = 10;
        }
        ensureParent(dest);

        Document document = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        try (FileOutputStream os = new FileOutputStream(dest)) {
            PdfWriter writer = PdfWriter.getInstance(document, os);
            writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
            writer.setFullCompression();
            document.open();

            BaseFont base = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(base, 16, Font.BOLD);
            Paragraph title = new Paragraph(TITLE, titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(TITLE_MARGIN);
            document.add(title);

            float[] proportions = {80f, 320f, 80f, 120f};
            float total = 0f;
            for (float p : proportions) {
                total += p;
            }
            Rectangle page = document.getPageSize();
            float availableWidth = page.getWidth() - (MARGIN * 2);
            float scale = availableWidth / total;
            float[] colWidths = new float[proportions.length];
            for (int i = 0; i < proportions.length; i++) {
                colWidths[i] = proportions[i] * scale;
            }

            float tableTop = page.getHeight() - MARGIN - TITLE_MARGIN - 24f;
            float tableHeight = HEADER_HEIGHT + ROW_HEIGHT * rowCount;
            float left = MARGIN;
            PdfContentByte canvas = writer.getDirectContent();
            canvas.saveState();
            canvas.rectangle(left, tableTop - tableHeight, availableWidth, tableHeight);
            canvas.stroke();

            float currentY = tableTop;
            // Horizontal lines
            canvas.moveTo(left, currentY - HEADER_HEIGHT);
            canvas.lineTo(left + availableWidth, currentY - HEADER_HEIGHT);
            for (int i = 0; i <= rowCount; i++) {
                float y = currentY - HEADER_HEIGHT - (i * ROW_HEIGHT);
                canvas.moveTo(left, y);
                canvas.lineTo(left + availableWidth, y);
            }
            canvas.stroke();

            // Vertical lines
            float x = left;
            for (float width : colWidths) {
                x += width;
                canvas.moveTo(x, tableTop);
                canvas.lineTo(x, tableTop - tableHeight);
            }
            canvas.stroke();
            canvas.restoreState();

            Font headerFont = new Font(base, 12, Font.BOLD);
            for (int i = 0; i < HEADER_LABELS.length; i++) {
                float centerX = left + columnOffset(colWidths, i) + colWidths[i] / 2f;
                float centerY = tableTop - HEADER_HEIGHT / 2f + 4f;
                ColumnText.showTextAligned(canvas, Element.ALIGN_CENTER,
                        new Paragraph(HEADER_LABELS[i], headerFont), centerX, centerY, 0f);
            }

            addFields(writer, base, colWidths, left, tableTop, rowCount);
            document.close();
        }
    }

    private static void addFields(PdfWriter writer,
                                  BaseFont baseFont,
                                  float[] colWidths,
                                  float left,
                                  float tableTop,
                                  int rowCount) throws IOException, DocumentException {
        float padding = 4f;
        for (int i = 1; i <= rowCount; i++) {
            float rowTop = tableTop - HEADER_HEIGHT - (i - 1) * ROW_HEIGHT;
            float rowBottom = rowTop - ROW_HEIGHT;

            Rectangle timeRect = new Rectangle(
                    left + padding,
                    rowBottom + padding,
                    left + colWidths[0] - padding,
                    rowTop - padding);
            TextField timeField = new TextField(writer, timeRect, FIELD_TIME_PREFIX + i);
            timeField.setFont(baseFont);
            timeField.setFontSize(11f);
            writer.addAnnotation(timeField.getTextField());

            Rectangle contentRect = new Rectangle(
                    left + colWidths[0] + padding,
                    rowBottom + padding,
                    left + colWidths[0] + colWidths[1] - padding,
                    rowTop - padding);
            TextField contentField = new TextField(writer, contentRect, FIELD_CONTENT_PREFIX + i);
            contentField.setOptions(TextField.MULTILINE);
            contentField.setFont(baseFont);
            contentField.setFontSize(11f);
            writer.addAnnotation(contentField.getTextField());

            Rectangle nurseRect = new Rectangle(
                    left + colWidths[0] + colWidths[1] + padding,
                    rowBottom + padding,
                    left + colWidths[0] + colWidths[1] + colWidths[2] - padding,
                    rowTop - padding);
            TextField nurseField = new TextField(writer, nurseRect, FIELD_NURSE_PREFIX + i);
            nurseField.setFont(baseFont);
            nurseField.setFontSize(11f);
            writer.addAnnotation(nurseField.getTextField());

            Rectangle signRect = new Rectangle(
                    left + colWidths[0] + colWidths[1] + colWidths[2] + padding,
                    rowBottom + padding,
                    left + colWidths[0] + colWidths[1] + colWidths[2] + colWidths[3] - padding,
                    rowTop - padding);
            PdfFormField sigField = PdfFormField.createSignature(writer, signRect, FIELD_SIGN_PREFIX + i);
            sigField.setFlags(PdfAnnotation.FLAGS_PRINT);
            writer.addAnnotation(sigField);
        }
    }

    private static float columnOffset(float[] widths, int index) {
        float offset = 0f;
        for (int i = 0; i < index; i++) {
            offset += widths[i];
        }
        return offset;
    }

    private static void ensureParent(String dest) {
        File file = new File(dest);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}
