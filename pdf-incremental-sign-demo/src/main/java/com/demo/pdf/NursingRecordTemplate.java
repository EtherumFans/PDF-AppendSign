package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.forms.fields.PdfTextFormField;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.signatures.PdfSigner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;

public final class NursingRecordTemplate {

    private NursingRecordTemplate() {
    }

    public static void create(String outPdf, int rows, String certP12, String p12Pass) throws Exception {
        DemoKeystoreUtil.ensureProvider();
        String certPath = certP12;
        char[] password = p12Pass != null ? p12Pass.toCharArray() : "123456".toCharArray();
        if (certPath == null) {
            Path temp = DemoKeystoreUtil.createDemoP12();
            certPath = temp.toAbsolutePath().toString();
            System.out.println("[create-template] Generated demo cert at " + certPath);
        }
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(certPath, password);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, password);
        PrivateKey privateKey = entry.getPrivateKey();
        X509Certificate[] chain = Arrays.stream(entry.getCertificateChain())
                .map(cert -> (X509Certificate) cert)
                .toArray(X509Certificate[]::new);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer);
             Document document = new Document(pdfDoc, PageSize.A4)) {
            document.setMargins(36, 36, 36, 36);
            document.add(new Paragraph("护理记录单")
                    .setBold()
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(""));

            PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
            form.setNeedAppearances(true);
            Rectangle pageSize = pdfDoc.getFirstPage().getPageSize();
            PdfCanvas canvas = new PdfCanvas(pdfDoc.getFirstPage());

            for (int row = 1; row <= rows; row++) {
                Rectangle rowBox = LayoutUtil.getRowBox(pageSize, row);
                canvas.saveState();
                canvas.rectangle(rowBox.getLeft(), rowBox.getBottom(), rowBox.getWidth(), rowBox.getHeight());
                canvas.stroke();
                canvas.restoreState();

                Rectangle timeRect = LayoutUtil.getFieldRect(pageSize, row, LayoutUtil.FieldSlot.TIME);
                Rectangle textRect = LayoutUtil.getFieldRect(pageSize, row, LayoutUtil.FieldSlot.TEXT);
                Rectangle nurseRect = LayoutUtil.getFieldRect(pageSize, row, LayoutUtil.FieldSlot.NURSE);
                Rectangle sigRect = LayoutUtil.getFieldRect(pageSize, row, LayoutUtil.FieldSlot.SIGNATURE);

                PdfTextFormField timeField = PdfTextFormField.createText(pdfDoc, timeRect, String.format("row%d.time", row), "")
                        .setJustification(PdfFormField.ALIGN_CENTER);
                PdfTextFormField textField = PdfTextFormField.createMultilineText(pdfDoc, textRect, String.format("row%d.text", row), "");
                PdfTextFormField nurseField = PdfTextFormField.createText(pdfDoc, nurseRect, String.format("row%d.nurse", row), "")
                        .setJustification(PdfFormField.ALIGN_CENTER);
                timeField.setFontSize(12);
                textField.setFontSize(12);
                nurseField.setFontSize(12);
                form.addField(timeField, pdfDoc.getFirstPage());
                form.addField(textField, pdfDoc.getFirstPage());
                form.addField(nurseField, pdfDoc.getFirstPage());

                PdfSignatureFormField sigField = PdfSignatureFormField.createSignature(pdfDoc, sigRect);
                sigField.setFieldName(String.format("sig_row_%d", row));
                form.addField(sigField, pdfDoc.getFirstPage());
            }
        }

        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(baos.toByteArray()));
             FileOutputStream fos = new FileOutputStream(outPdf)) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties());
            DocMDPUtil.applyCertification(signer, privateKey, chain, DocMDPUtil.Permission.FORM_FILL_AND_SIGNATURES);
        }
        System.out.println("[create-template] DocMDP certification applied");
    }
}
