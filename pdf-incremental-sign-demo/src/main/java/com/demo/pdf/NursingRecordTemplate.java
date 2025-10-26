package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

/**
 * 生成“护理记录单”PDF 模板，并提供填充行文本的方法。
 * 注意：签名域仅为占位，实际电子签名请使用 PdfSigner 在后续流程中增量追加。
 */
public class NursingRecordTemplate {

    private static final float MARGIN = 36f;
    private static final float HEADER_FONT_SIZE = 14f;
    private static final float CELL_FONT_SIZE = 11f;
    private static final float ROW_HEIGHT = 28f;

    private static final String COL_TIME = "记录时间";
    private static final String COL_CONTENT = "护理内容";
    private static final String COL_NURSE = "护士";
    private static final String COL_SIGN = "签名";

    private static final String FIELD_TIME_PREFIX = "recordTime_";
    private static final String FIELD_CONTENT_PREFIX = "recordContent_";
    private static final String FIELD_NURSE_PREFIX = "nurseName_";
    private static final String FIELD_SIGN_PREFIX = "nurseSign_";

    /** 生成护理记录单模板 */
    public static void createTemplate(String dest, int rowCount) throws IOException {
        Objects.requireNonNull(dest, "dest must not be null");
        if (rowCount <= 0) rowCount = 10;

        ensureParentDir(dest);

        try (PdfWriter writer = new PdfWriter(dest, new WriterProperties().setPdfVersion(PdfVersion.PDF_1_7));
             PdfDocument pdf = new PdfDocument(writer);
             Document doc = new Document(pdf)) {

            doc.setMargins(MARGIN, MARGIN, MARGIN, MARGIN);

            PdfFont headerFont = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
            PdfFont cellFont = PdfFontFactory.createFont(StandardFonts.HELVETICA);

            // 标题
            Paragraph title = new Paragraph("护理记录单")
                    .setFont(headerFont)
                    .setFontSize(HEADER_FONT_SIZE)
                    .setFontColor(ColorConstants.BLACK)
                    .setMarginBottom(12f);
            doc.add(title);

            // 表格列宽（绝对宽度，避免 UnitValue 依赖）
            float[] colWidths = new float[]{80f, 320f, 80f, 120f};
            Table table = new Table(colWidths);
            table.setWidth(pdf.getFirstPage().getPageSize().getWidth() - 2 * MARGIN);
            table.setBorder(new SolidBorder(0.8f));

            table.addHeaderCell(new Paragraph(COL_TIME).setFont(headerFont).setFontSize(CELL_FONT_SIZE));
            table.addHeaderCell(new Paragraph(COL_CONTENT).setFont(headerFont).setFontSize(CELL_FONT_SIZE));
            table.addHeaderCell(new Paragraph(COL_NURSE).setFont(headerFont).setFontSize(CELL_FONT_SIZE));
            table.addHeaderCell(new Paragraph(COL_SIGN).setFont(headerFont).setFontSize(CELL_FONT_SIZE));

            for (int i = 1; i <= rowCount; i++) {
                table.addCell(new Paragraph("").setFont(cellFont).setFontSize(CELL_FONT_SIZE).setMinHeight(ROW_HEIGHT));
                table.addCell(new Paragraph("").setFont(cellFont).setFontSize(CELL_FONT_SIZE).setMinHeight(ROW_HEIGHT));
                table.addCell(new Paragraph("").setFont(cellFont).setFontSize(CELL_FONT_SIZE).setMinHeight(ROW_HEIGHT));
                table.addCell(new Paragraph("").setFont(cellFont).setFontSize(CELL_FONT_SIZE).setMinHeight(ROW_HEIGHT));
            }
            doc.add(table);
            doc.flush();

            PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, true);
            float pageHeight = pdf.getFirstPage().getPageSize().getHeight();

            float tableLeft = MARGIN;
            float headerHeight = ROW_HEIGHT + 6f;
            float firstRowTopY = pageHeight - MARGIN - HEADER_FONT_SIZE * 1.6f - headerHeight;

            float xTime = tableLeft;
            float xContent = xTime + colWidths[0];
            float xNurse = xContent + colWidths[1];
            float xSign = xNurse + colWidths[2];

            for (int i = 1; i <= rowCount; i++) {
                float rowTop = firstRowTopY - (i - 1) * ROW_HEIGHT;
                float rowBottom = rowTop - ROW_HEIGHT + 2f;
                float height = rowTop - rowBottom;

                Rectangle rTime = new Rectangle(xTime + 3f, rowBottom + 3f, colWidths[0] - 6f, height - 6f);
                PdfTextFormField tfTime = PdfTextFormField.createText(pdf, rTime, FIELD_TIME_PREFIX + i, "");
                tfTime.setDefaultAppearance(new PdfString(FormUtil.DEFAULT_APPEARANCE));
                tfTime.setFont(cellFont);
                tfTime.setFontSize(CELL_FONT_SIZE);
                form.addField(tfTime, pdf.getFirstPage());

                Rectangle rContent = new Rectangle(xContent + 3f, rowBottom + 3f, colWidths[1] - 6f, height - 6f);
                PdfTextFormField tfContent = PdfTextFormField.createMultilineText(pdf, rContent, FIELD_CONTENT_PREFIX + i, "");
                tfContent.setDefaultAppearance(new PdfString(FormUtil.DEFAULT_APPEARANCE));
                tfContent.setFont(cellFont);
                tfContent.setFontSize(CELL_FONT_SIZE);
                form.addField(tfContent, pdf.getFirstPage());

                Rectangle rNurse = new Rectangle(xNurse + 3f, rowBottom + 3f, colWidths[2] - 6f, height - 6f);
                PdfTextFormField tfNurse = PdfTextFormField.createText(pdf, rNurse, FIELD_NURSE_PREFIX + i, "");
                tfNurse.setDefaultAppearance(new PdfString(FormUtil.DEFAULT_APPEARANCE));
                tfNurse.setFont(cellFont);
                tfNurse.setFontSize(CELL_FONT_SIZE);
                form.addField(tfNurse, pdf.getFirstPage());

                Rectangle rSign = new Rectangle(xSign + 3f, rowBottom + 3f, colWidths[3] - 6f, height - 6f);
                PdfSignatureFormField sig = PdfSignatureFormField.createSignature(pdf, rSign);
                sig.setFieldName(FIELD_SIGN_PREFIX + i);
                // 绑定到第一页：addField 的第二个参数就是页面对象；不再调用 setPage(...)
                sig.getWidgets().get(0).setHighlightMode(PdfName.N);
                sig.getWidgets().get(0).setFlags(PdfAnnotation.PRINT);
                form.addField(sig, pdf.getFirstPage());
            }

            doc.flush();
        }
    }

    /** 填充某一行文本（不签名，签名请另行使用 PdfSigner 增量追加） */
    public static void fillRecord(String src, String dest, int rowIndex,
                                  String time, String content, String nurseName) throws IOException {
        Objects.requireNonNull(src, "src must not be null");
        Objects.requireNonNull(dest, "dest must not be null");
        if (rowIndex <= 0) throw new IllegalArgumentException("rowIndex must start from 1");

        ensureParentDir(dest);

        try (PdfDocument pdf = new PdfDocument(new PdfReader(src), new PdfWriter(dest))) {
            PdfAcroForm form = PdfAcroForm.getAcroForm(pdf, true);

            setIfPresent(form, FIELD_TIME_PREFIX + rowIndex, safe(time));
            setIfPresent(form, FIELD_CONTENT_PREFIX + rowIndex, safe(content));
            setIfPresent(form, FIELD_NURSE_PREFIX + rowIndex, safe(nurseName));

            // 如需锁定文本字段可扁平化；若后续还要编辑，注释掉下一行
            form.flattenFields();
        }
    }

    /* utils */

    private static void setIfPresent(PdfAcroForm form, String fieldName, String value) {
        PdfFormField f = form.getField(fieldName);
        if (f != null && value != null) {
            f.setValue(value);
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static void ensureParentDir(String path) {
        File f = new File(path);
        File parent = f.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    public static void main(String[] args) throws Exception {
        // 1) 生成 10 行模板
        String template = "target/nursing-template.pdf";
        createTemplate(template, 10);

        // 2) 按时段填充文本（不签名）
        String filled10 = "target/nursing-10h.pdf";
        fillRecord(template, filled10, 1, "10:00", "晨间巡视：生命体征平稳", "张三");

        String filled13 = "target/nursing-13h.pdf";
        fillRecord(filled10, filled13, 2, "13:00", "进餐后复测血糖，完成宣教", "李四");

        String filled15 = "target/nursing-15h.pdf";
        fillRecord(filled13, filled15, 3, "15:00", "更换静脉通道，病情观察", "王五");

        System.out.println("已生成：");
        System.out.println(" - " + template);
        System.out.println(" - " + filled10);
        System.out.println(" - " + filled13);
        System.out.println(" - " + filled15);
    }
}
