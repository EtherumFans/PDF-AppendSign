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
import com.itextpdf.signatures.*;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;

public final class NursingRecordTemplate {

    private NursingRecordTemplate() {
    }

    public static void create(String outPdf, String certP12, String p12Pass) throws Exception {
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
        Certificate[] chain = entry.getCertificateChain();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PdfWriter writer = new PdfWriter(baos);
             PdfDocument pdfDoc = new PdfDocument(writer)) {
            pdfDoc.setDefaultPageSize(PageSize.A4);
            Document document = new Document(pdfDoc);
            document.add(new Paragraph("护理记录单")
                    .setBold()
                    .setFontSize(18)
                    .setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(""));

            PdfCanvas canvas = new PdfCanvas(pdfDoc.getFirstPage());
            float margin = 36;
            float pageWidth = pdfDoc.getDefaultPageSize().getWidth();
            float pageHeight = pdfDoc.getDefaultPageSize().getHeight();
            float tableWidth = pageWidth - 2 * margin;
            float rowHeight = 120;
            float startY = pageHeight - 120;

            PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);

            for (int i = 0; i < 3; i++) {
                int row = i + 1;
                float yBottom = startY - i * rowHeight - rowHeight + 20;
                canvas.saveState();
                canvas.setLineWidth(0.5f);
                canvas.rectangle(margin, yBottom, tableWidth, rowHeight - 20);
                canvas.stroke();
                canvas.restoreState();

                float timeWidth = 80;
                float textWidth = 280;
                float nurseWidth = 120;
                float signatureWidth = 140;
                float fieldHeight = 24;
                float fieldBottom = yBottom + (rowHeight / 2f) - (fieldHeight / 2f);

                Rectangle timeRect = new Rectangle(margin + 10, fieldBottom, timeWidth, fieldHeight);
                Rectangle textRect = new Rectangle(margin + 20 + timeWidth, fieldBottom, textWidth, fieldHeight);
                Rectangle nurseRect = new Rectangle(margin + 30 + timeWidth + textWidth, fieldBottom, nurseWidth, fieldHeight);
                Rectangle sigRect = new Rectangle(margin + 40 + timeWidth + textWidth + nurseWidth, fieldBottom - 10, signatureWidth, fieldHeight + 20);

                PdfTextFormField timeField = PdfTextFormField.createText(pdfDoc, timeRect, String.format("row%d.time", row), "");
                PdfTextFormField textField = PdfTextFormField.createText(pdfDoc, textRect, String.format("row%d.text", row), "");
                PdfTextFormField nurseField = PdfTextFormField.createText(pdfDoc, nurseRect, String.format("row%d.nurse", row), "");
                timeField.setJustification(PdfFormField.ALIGN_CENTER);
                nurseField.setJustification(PdfFormField.ALIGN_CENTER);
                form.addField(timeField, pdfDoc.getFirstPage());
                form.addField(textField, pdfDoc.getFirstPage());
                form.addField(nurseField, pdfDoc.getFirstPage());

                PdfSignatureFormField sigField = PdfSignatureFormField.createSignature(pdfDoc, sigRect);
                sigField.setFieldName(String.format("sig_row_%d", row));
                form.addField(sigField, pdfDoc.getFirstPage());
            }
            document.close();
        }

        try (PdfReader reader = new PdfReader(new ByteArrayInputStream(baos.toByteArray()));
             FileOutputStream fos = new FileOutputStream(outPdf)) {
            PdfSigner signer = new PdfSigner(reader, fos, new StampingProperties());
            signer.setCertificationLevel(PdfSigner.CERTIFIED_FORM_FILLING_AND_ANNOTATIONS);
            signer.getSignatureAppearance()
                    .setReason("DocMDP Certification")
                    .setLocation("Template Generation")
                    .setPageRect(new Rectangle(0, 0, 0, 0))
                    .setPageNumber(1)
                    .setReuseAppearance(false);
            signer.setFieldName("docmdp");

            IExternalSignature pks = new PrivateKeySignature(privateKey, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            IExternalDigest digest = new BouncyCastleDigest();
            signer.signDetached(digest, pks, chain, null, null, null, 0, PdfSigner.CryptoStandard.CADES);
        }
        System.out.println("[create-template] DocMDP certification applied");
    }
}
