package com.demo.pdf;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.FontFactory;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfAnnotation;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfFormField;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.TextField;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;

/**
 * Minimal template generator implemented with iText 5 APIs.
 */
public final class NursingRecordTemplate {

    private static final float MARGIN = 36f;
    private static final float HEADER_HEIGHT = 24f;
    private static final float ROW_HEIGHT = 32f;
    private static final float TITLE_GAP = 30f;
    private static final float PADDING = 4f;

    private static final String FIELD_TIME_PREFIX = "recordTime_";
    private static final String FIELD_CONTENT_PREFIX = "recordContent_";
    private static final String FIELD_NURSE_PREFIX = "nurseName_";
    private static final String FIELD_SIGN_PREFIX = "nurseSign_";

    private static final String[] HEADER_LABELS = {
            "记录时间",
            "护理内容",
            "护士",
            "签名"
    };

    private NursingRecordTemplate() {
    }

    public static void createTemplate(String dest, int rowCount) throws IOException, DocumentException {
        Objects.requireNonNull(dest, "dest must not be null");
        if (rowCount <= 0) {
            rowCount = 10;
        }
        ensureParentDir(dest);

        Document doc = new Document(PageSize.A4, MARGIN, MARGIN, MARGIN, MARGIN);
        PdfWriter writer = PdfWriter.getInstance(doc, new FileOutputStream(dest));
        writer.setPdfVersion(PdfWriter.PDF_VERSION_1_7);
        doc.open();

        addTitle(doc);

        PdfContentByte canvas = writer.getDirectContent();
        Rectangle pageSize = doc.getPageSize();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();
        float tableLeft = MARGIN;
        float tableRight = pageWidth - MARGIN;
        float tableTop = pageHeight - MARGIN - TITLE_GAP;
        float tableWidth = tableRight - tableLeft;

        float[] unitWidths = new float[]{80f, 320f, 80f, 120f};
        float unitTotal = 0f;
        for (float w : unitWidths) {
            unitTotal += w;
        }
        float[] colWidths = new float[unitWidths.length];
        for (int i = 0; i < unitWidths.length; i++) {
            colWidths[i] = tableWidth * (unitWidths[i] / unitTotal);
        }

        float headerBottom = tableTop - HEADER_HEIGHT;
        float tableBottom = headerBottom - ROW_HEIGHT * rowCount;

        drawTableBorder(canvas, tableLeft, tableRight, tableTop, headerBottom, tableBottom, colWidths, rowCount);
        drawHeaderText(canvas, tableLeft, tableTop, colWidths, HEADER_LABELS);
        addFormFields(writer, colWidths, rowCount, tableLeft, headerBottom, tableBottom);

        doc.close();
    }

    public static void fillRecord(String src, String dest, int rowIndex,
                                  String time, String content, String nurseName) throws IOException, DocumentException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");
        if (rowIndex <= 0) {
            throw new IllegalArgumentException("rowIndex must be >= 1");
        }
        ensureParentDir(dest);
        PdfReader reader = new PdfReader(src);
        try (FileOutputStream fos = new FileOutputStream(dest)) {
            PdfStamper stamper = new PdfStamper(reader, fos);
            AcroFields fields = stamper.getAcroFields();
            setFieldIfPresent(fields, timeFieldName(rowIndex), safe(time));
            setFieldIfPresent(fields, contentFieldName(rowIndex), safe(content));
            setFieldIfPresent(fields, nurseFieldName(rowIndex), safe(nurseName));
            stamper.setFormFlattening(false);
            stamper.close();
        } finally {
            reader.close();
        }
    }

    private static void addTitle(Document doc) throws DocumentException {
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16f, BaseColor.BLACK);
        Paragraph title = new Paragraph("护理记录单", titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(12f);
        doc.add(title);
    }

    private static void drawTableBorder(PdfContentByte canvas,
                                        float left, float right,
                                        float top, float headerBottom,
                                        float bottom,
                                        float[] colWidths,
                                        int rowCount) {
        canvas.saveState();
        canvas.setLineWidth(0.8f);

        canvas.moveTo(left, top);
        canvas.lineTo(right, top);
        canvas.moveTo(left, headerBottom);
        canvas.lineTo(right, headerBottom);
        canvas.moveTo(left, bottom);
        canvas.lineTo(right, bottom);

        float currentY = headerBottom;
        for (int i = 0; i < rowCount; i++) {
            float nextY = currentY - ROW_HEIGHT;
            canvas.moveTo(left, nextY);
            canvas.lineTo(right, nextY);
            currentY = nextY;
        }

        float currentX = left;
        canvas.moveTo(currentX, top);
        canvas.lineTo(currentX, bottom);
        for (float w : colWidths) {
            currentX += w;
            canvas.moveTo(currentX, top);
            canvas.lineTo(currentX, bottom);
        }

        canvas.stroke();
        canvas.restoreState();
    }

    private static void drawHeaderText(PdfContentByte canvas,
                                       float left,
                                       float top,
                                       float[] colWidths,
                                       String[] labels) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12f, BaseColor.BLACK);
        float currentX = left;
        float baseline = top - HEADER_HEIGHT + HEADER_HEIGHT / 2f + 2f;
        for (int i = 0; i < labels.length; i++) {
            Phrase phrase = new Phrase(labels[i], headerFont);
            float textX = currentX + 4f;
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT, phrase, textX, baseline, 0f);
            currentX += colWidths[i];
        }
    }

    private static void addFormFields(PdfWriter writer,
                                      float[] colWidths,
                                      int rowCount,
                                      float left,
                                      float headerBottom,
                                      float tableBottom) throws DocumentException {
        float[] columnStarts = new float[colWidths.length + 1];
        columnStarts[0] = left;
        for (int i = 0; i < colWidths.length; i++) {
            columnStarts[i + 1] = columnStarts[i] + colWidths[i];
        }

        for (int i = 1; i <= rowCount; i++) {
            float rowTop = headerBottom - (i - 1) * ROW_HEIGHT;
            float rowBottom = Math.max(tableBottom, rowTop - ROW_HEIGHT);

            addTextField(writer, timeFieldName(i),
                    columnStarts[0] + PADDING,
                    rowBottom + PADDING,
                    columnStarts[1] - PADDING,
                    rowTop - PADDING);
            addTextField(writer, contentFieldName(i),
                    columnStarts[1] + PADDING,
                    rowBottom + PADDING,
                    columnStarts[2] - PADDING,
                    rowTop - PADDING,
                    true);
            addTextField(writer, nurseFieldName(i),
                    columnStarts[2] + PADDING,
                    rowBottom + PADDING,
                    columnStarts[3] - PADDING,
                    rowTop - PADDING);
            addSignatureField(writer, signatureFieldName(i),
                    columnStarts[3] + PADDING,
                    rowBottom + PADDING,
                    columnStarts[4] - PADDING,
                    rowTop - PADDING);
        }
    }

    private static void addTextField(PdfWriter writer,
                                     String name,
                                     float llx, float lly,
                                     float urx, float ury) throws DocumentException {
        addTextField(writer, name, llx, lly, urx, ury, false);
    }

    private static void addTextField(PdfWriter writer,
                                     String name,
                                     float llx, float lly,
                                     float urx, float ury,
                                     boolean multiline) throws DocumentException {
        Rectangle rect = new Rectangle(llx, lly, urx, ury);
        TextField field = new TextField(writer, rect, name);
        field.setFontSize(11f);
        field.setOptions(TextField.REMOVE_TRAILING_SPACES);
        if (multiline) {
            field.setOptions(field.getOptions() | TextField.MULTILINE);
        }
        PdfFormField formField = field.getTextField();
        writer.addAnnotation(formField);
    }

    private static void addSignatureField(PdfWriter writer,
                                          String name,
                                          float llx, float lly,
                                          float urx, float ury) {
        Rectangle rect = new Rectangle(llx, lly, urx, ury);
        PdfFormField sig = PdfFormField.createSignature(writer);
        sig.setWidget(rect, PdfAnnotation.HIGHLIGHT_NONE);
        sig.setFlags(PdfAnnotation.FLAGS_PRINT);
        sig.setFieldName(name);
        writer.addAnnotation(sig);
    }

    private static void setFieldIfPresent(AcroFields fields, String name, String value) throws IOException, DocumentException {
        if (fields.getFieldItem(name) != null) {
            fields.setField(name, value);
        }
    }

    private static String safe(String input) {
        return input == null ? "" : input;
    }

    static String timeFieldName(int row) {
        return FIELD_TIME_PREFIX + row;
    }

    static String contentFieldName(int row) {
        return FIELD_CONTENT_PREFIX + row;
    }

    static String nurseFieldName(int row) {
        return FIELD_NURSE_PREFIX + row;
    }

    public static String signatureFieldName(int row) {
        return FIELD_SIGN_PREFIX + row;
    }

    private static void ensureParentDir(String path) {
        File file = new File(path);
        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}
