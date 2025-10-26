package com.demo.pdf;

import com.demo.crypto.DemoKeystoreUtil;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDate;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.StampingProperties;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import com.itextpdf.kernel.pdf.canvas.PdfCanvasConstants;
import com.itextpdf.kernel.pdf.xobject.PdfFormXObject;
import com.itextpdf.layout.Canvas;
import com.itextpdf.layout.property.TextAlignment;
import com.itextpdf.signatures.BouncyCastleDigest;
import com.itextpdf.signatures.DigestAlgorithms;
import com.itextpdf.signatures.IExternalDigest;
import com.itextpdf.signatures.IExternalSignature;
import com.itextpdf.signatures.PdfSignature;
import com.itextpdf.signatures.PdfSignatureAppearance;
import com.itextpdf.signatures.PdfSigner;
import com.itextpdf.signatures.PrivateKeySignature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Signs a PDF by appending a single signature field whose appearance mimics a handwritten e-signature.
 */
public final class ElectronicSignatureSigner {

    private static final String DEFAULT_CONTACT = "nurse-signer@example.com";

    private final Params params;
    private final Path cjkFontPath;
    private final boolean debugFonts;

    private ElectronicSignatureSigner(Params params) {
        this.params = params;
        this.cjkFontPath = params.getCjkFontPath();
        this.debugFonts = params.isDebugFonts();
    }

    public static final class Params {
        private String source;
        private String destination;
        private String pkcs12Path;
        private String password;
        private String fieldName = "sig_electronic";
        private int page = 1;
        private float x = 72f;
        private float y = 72f;
        private float width = 180f;
        private float height = 72f;
        private String signerName;
        private String reason = "电子签名";
        private String location = "Ward A";
        private String contact = DEFAULT_CONTACT;
        private Path cjkFontPath;
        private boolean debugFonts;

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDestination() {
            return destination;
        }

        public void setDestination(String destination) {
            this.destination = destination;
        }

        public String getPkcs12Path() {
            return pkcs12Path;
        }

        public void setPkcs12Path(String pkcs12Path) {
            this.pkcs12Path = pkcs12Path;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getFieldName() {
            return fieldName;
        }

        public void setFieldName(String fieldName) {
            if (fieldName != null && !fieldName.isBlank()) {
                this.fieldName = fieldName;
            }
        }

        public int getPage() {
            return page;
        }

        public void setPage(int page) {
            this.page = page;
        }

        public float getX() {
            return x;
        }

        public void setX(float x) {
            this.x = x;
        }

        public float getY() {
            return y;
        }

        public void setY(float y) {
            this.y = y;
        }

        public float getWidth() {
            return width;
        }

        public void setWidth(float width) {
            this.width = width;
        }

        public float getHeight() {
            return height;
        }

        public void setHeight(float height) {
            this.height = height;
        }

        public String getSignerName() {
            return signerName;
        }

        public void setSignerName(String signerName) {
            this.signerName = signerName;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            if (reason != null && !reason.isBlank()) {
                this.reason = reason;
            }
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            if (location != null && !location.isBlank()) {
                this.location = location;
            }
        }

        public String getContact() {
            return contact;
        }

        public void setContact(String contact) {
            if (contact != null && !contact.isBlank()) {
                this.contact = contact;
            }
        }

        public Path getCjkFontPath() {
            return cjkFontPath;
        }

        public void setCjkFontPath(Path cjkFontPath) {
            this.cjkFontPath = cjkFontPath;
        }

        public boolean isDebugFonts() {
            return debugFonts;
        }

        public void setDebugFonts(boolean debugFonts) {
            this.debugFonts = debugFonts;
        }
    }

    public static void sign(Params params) throws Exception {
        if (params == null) {
            throw new IllegalArgumentException("Params must not be null");
        }
        new ElectronicSignatureSigner(params).signInternal();
    }

    private void signInternal() throws Exception {
        Params params = this.params;
        DemoKeystoreUtil.ensureProvider();
        Path srcPath = Path.of(params.getSource());
        Path destPath = Path.of(params.getDestination());
        if (!Files.exists(srcPath)) {
            throw new IllegalStateException("Source PDF does not exist: " + srcPath);
        }
        if (params.getWidth() <= 0 || params.getHeight() <= 0) {
            throw new IllegalArgumentException("Signature rectangle must have positive width and height");
        }
        PdfSanityUtil.requireHeader(srcPath);
        PdfSanityUtil.requireVersionAtLeast(srcPath, "1.6");

        char[] password = params.getPassword() != null ? params.getPassword().toCharArray() : "123456".toCharArray();
        String pkcs12Path = params.getPkcs12Path();
        if (pkcs12Path == null || pkcs12Path.isBlank()) {
            pkcs12Path = DemoKeystoreUtil.createDemoP12().toAbsolutePath().toString();
            System.out.println("[sign-electronic] Using generated demo PKCS#12: " + pkcs12Path);
        }
        KeyMaterial keyMaterial = loadKeyMaterial(pkcs12Path, password);

        long originalSize = Files.size(srcPath);
        Path parent = destPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        PdfDocument document = null;
        Rectangle rect = new Rectangle(params.getX(), params.getY(), params.getWidth(), params.getHeight());
        try (PdfReader reader = new PdfReader(params.getSource());
             OutputStream os = new BufferedOutputStream(Files.newOutputStream(destPath,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING,
                     StandardOpenOption.WRITE))) {
            PdfSigner signer = new PdfSigner(reader, os, new StampingProperties().useAppendMode());
            document = signer.getDocument();
            if (params.getPage() < 1 || params.getPage() > document.getNumberOfPages()) {
                throw new IllegalArgumentException("Page " + params.getPage() + " is out of bounds");
            }

            PdfSignatureFormField signatureField;
            try {
                signatureField = ensureSignatureField(document, params.getPage(), params.getFieldName(), rect);
            } catch (Exception ex) {
                System.err.println("[sign-electronic] Failed to ensure signature field '" + params.getFieldName()
                        + "' on page " + params.getPage() + " rect=" + rect + ": " + ex.getMessage());
                if (debugFonts) {
                    dumpDA_DR(document, "ON_ERROR_AFTER_NORMALIZE");
                }
                throw ex;
            }
            ensureFieldNotSigned(document, params.getFieldName());

            signer.setFieldName(params.getFieldName());
            Calendar signDate = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            PdfSignatureAppearance appearance = signer.getSignatureAppearance();
            appearance.setReuseAppearance(false);
            appearance.setPageNumber(params.getPage());
            appearance.setPageRect(rect);
            appearance.setReason(params.getReason());
            appearance.setLocation(params.getLocation());
            appearance.setContact(params.getContact());
            if (keyMaterial.certificate != null) {
                appearance.setCertificate(keyMaterial.certificate);
            }
            appearance.setLayer2Text("");
            drawSignatureGraphic(appearance, document);

            String signerName = params.getSignerName();
            if (signerName == null || signerName.isBlank()) {
                signerName = keyMaterial.certificate != null
                        ? keyMaterial.certificate.getSubjectX500Principal().getName()
                        : "Signer";
            }
            signer.setSignDate(signDate);
            String reason = params.getReason();
            String location = params.getLocation();
            String finalSignerName = signerName;
            Calendar finalSignDate = (Calendar) signDate.clone();
            signer.setSignatureEvent(signature -> configureSignatureDictionary(signature, finalSignerName, reason, location, finalSignDate));

            IExternalSignature pks = new PrivateKeySignature(keyMaterial.key, DigestAlgorithms.SHA256, BouncyCastleProvider.PROVIDER_NAME);
            IExternalDigest digest = new BouncyCastleDigest();
            assertWidgetPrintable(signatureField);
            signer.signDetached(digest, pks, keyMaterial.chain, null, null, null, 0, PdfSigner.CryptoStandard.CMS);
        } finally {
            if (document != null && !document.isClosed()) {
                document.close();
            }
        }

        long newSize = Files.size(destPath);
        if (newSize <= originalSize) {
            throw new IllegalStateException("Destination PDF did not grow after signing; aborting to avoid overwriting");
        }
        PostSignValidator.validate(destPath.toString(), params.getFieldName());
        PostSignValidator.strictTailCheck(destPath);
        System.out.println("[sign-electronic] Signature applied in append mode -> " + destPath.toAbsolutePath());
    }

    private PdfSignatureFormField ensureSignatureField(PdfDocument pdfDoc,
                                                       int pageIndex,
                                                       String fieldName,
                                                       Rectangle rect) throws Exception {
        if (debugFonts) {
            dumpDA_DR(pdfDoc, "BEFORE_NORMALIZE");
        }
        PdfAcroformNormalizer.normalizeToHelvOnly(pdfDoc);
        if (debugFonts) {
            dumpDA_DR(pdfDoc, "AFTER_NORMALIZE");
        }

        PdfAcroForm af = PdfAcroForm.getAcroForm(pdfDoc, true);
        PdfSignatureFormField sigField;
        PdfFormField existing = af.getField(fieldName);
        if (existing != null) {
            if (!(existing instanceof PdfSignatureFormField)) {
                throw new IllegalStateException("Field '" + fieldName + "' is not a signature field");
            }
            sigField = (PdfSignatureFormField) existing;
        } else {
            sigField = PdfSignatureFormField.createSignature(pdfDoc, rect);
            sigField.setFieldName(fieldName);
            sigField.setDefaultAppearance(new PdfString("/Helv 12 Tf 0 g"));
            af.addField(sigField, pdfDoc.getPage(pageIndex));
        }

        sigField.setDefaultAppearance(new PdfString("/Helv 12 Tf 0 g"));

        PdfWidgetAnnotation widget = sigField.getFirstFormAnnotation();
        if (widget != null) {
            widget.setFlag(PdfAnnotation.PRINT, true);
            widget.setFlag(PdfAnnotation.INVISIBLE, false);
            widget.setFlag(PdfAnnotation.HIDDEN, false);
            widget.setFlag(PdfAnnotation.NO_VIEW, false);
            widget.setFlag(PdfAnnotation.TOGGLE_NO_VIEW, false);
            widget.setRectangle(rect);

            PdfPage page = pdfDoc.getPage(pageIndex);
            boolean present = false;
            for (PdfAnnotation annotation : page.getAnnotations()) {
                if (annotation.getPdfObject() == widget.getPdfObject()) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                page.addAnnotation(widget);
            }

            ensureNormalAppearance(pdfDoc, widget, rect);
        }
        return sigField;
    }

    private void ensureNormalAppearance(PdfDocument pdfDoc,
                                        PdfWidgetAnnotation widget,
                                        Rectangle rect) throws Exception {
        PdfFormXObject xobj = new PdfFormXObject(rect);
        PdfCanvas pdfCanvas = new PdfCanvas(xobj, pdfDoc);

        pdfCanvas.saveState();
        pdfCanvas.setLineWidth(1f);
        pdfCanvas.rectangle(0.5f, 0.5f, rect.getWidth() - 1f, rect.getHeight() - 1f);
        pdfCanvas.stroke();
        pdfCanvas.restoreState();

        PdfFont font;
        String text;
        if (this.cjkFontPath != null) {
            try {
                byte[] fontBytes = Files.readAllBytes(this.cjkFontPath);
                font = PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H, true);
                text = "已签名";
            } catch (IOException ex) {
                System.err.println("[sign-electronic] Failed to load CJK font '" + this.cjkFontPath
                        + "'. Falling back to Helvetica. Reason: " + ex.getMessage());
                font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
                text = "Signed";
            }
        } else {
            font = PdfFontFactory.createFont(StandardFonts.HELVETICA);
            text = "Signed";
        }
        xobj.getResources(true).addFont(pdfDoc, font);

        try (Canvas layout = new Canvas(pdfCanvas, pdfDoc, rect)) {
            layout.setFont(font).setFontSize(10);
            layout.showTextAligned(text, rect.getWidth() / 2f, rect.getHeight() / 2f, TextAlignment.CENTER);
        }
        pdfCanvas.release();

        widget.setAppearance(PdfName.N, xobj.getPdfObject());
        widget.getPdfObject().setModified();
    }

    private static void dumpDA_DR(PdfDocument doc, String tag) {
        PdfDictionary acro = doc.getCatalog().getPdfObject().getAsDictionary(PdfName.AcroForm);
        System.out.println("=== [dumpAcroFormFonts] " + tag + " ===");
        if (acro == null) {
            System.out.println("No AcroForm.");
            return;
        }
        System.out.println("DA: " + acro.get(PdfName.DA));
        PdfDictionary dr = acro.getAsDictionary(PdfName.DR);
        System.out.println("DR present: " + (dr != null));
        PdfDictionary fonts = dr != null ? dr.getAsDictionary(PdfName.Font) : null;
        if (fonts == null) {
            System.out.println("DR.Font: null");
            return;
        }
        System.out.println("DR.Font keys: " + fonts.keySet());
        for (PdfName key : fonts.keySet()) {
            PdfDictionary fd = fonts.getAsDictionary(key);
            PdfName type = fd != null ? fd.getAsName(PdfName.Type) : null;
            PdfName subtype = fd != null ? fd.getAsName(PdfName.Subtype) : null;
            System.out.println("  - " + key + " -> isDict=" + (fd != null)
                    + ", Type=" + type + ", Subtype=" + subtype);
        }
    }

    private static void drawSignatureGraphic(PdfSignatureAppearance appearance, PdfDocument document) {
        PdfFormXObject layer2 = appearance.getLayer2();
        if (layer2 == null) {
            return;
        }
        Rectangle bbox = layer2.getBBox().toRectangle();
        PdfCanvas canvas = new PdfCanvas(layer2, document);
        canvas.saveState();
        canvas.setFillColor(ColorConstants.WHITE);
        canvas.rectangle(0, 0, bbox.getWidth(), bbox.getHeight());
        canvas.fill();
        canvas.restoreState();

        float designWidth = 300f;
        float designHeight = 110f;
        float inset = 12f;
        float usableWidth = bbox.getWidth() - inset * 2;
        float usableHeight = bbox.getHeight() - inset * 2;
        float scale = Math.min(usableWidth / designWidth, usableHeight / designHeight);
        if (scale <= 0) {
            scale = 1f;
        }
        float offsetX = (bbox.getWidth() - designWidth * scale) / 2f;
        float offsetY = (bbox.getHeight() - designHeight * scale) / 2f;

        float[][][] strokes = new float[][][]{
                {{10f, 10f}, {40f, 90f}, {70f, 20f}, {110f, 70f}},
                {{120f, 30f}, {140f, 80f}, {160f, 20f}, {190f, 70f}},
                {{200f, 25f}, {220f, 85f}, {250f, 15f}, {280f, 70f}},
                {{40f, 60f}, {70f, 105f}, {110f, 60f}},
                {{160f, 60f}, {190f, 100f}, {230f, 55f}, {270f, 95f}}
        };

        canvas.saveState();
        canvas.setLineWidth(3f);
        canvas.setStrokeColor(ColorConstants.BLACK);
        canvas.setLineCapStyle(PdfCanvasConstants.LineCapStyle.ROUND);
        for (float[][] stroke : strokes) {
            if (stroke.length < 2) {
                continue;
            }
            canvas.moveTo(offsetX + stroke[0][0] * scale, offsetY + stroke[0][1] * scale);
            for (int i = 1; i < stroke.length; i++) {
                canvas.lineTo(offsetX + stroke[i][0] * scale, offsetY + stroke[i][1] * scale);
            }
            canvas.stroke();
        }
        canvas.restoreState();
        canvas.release();
    }

    private static void ensureFieldNotSigned(PdfDocument document, String fieldName) throws IOException {
        com.itextpdf.signatures.SignatureUtil util = new com.itextpdf.signatures.SignatureUtil(document);
        if (util.getSignatureNames().contains(fieldName)) {
            throw new IllegalStateException("Signature field already signed: " + fieldName);
        }
    }

    private static void assertWidgetPrintable(com.itextpdf.forms.fields.PdfSignatureFormField field) {
        if (field == null) {
            throw new IllegalArgumentException("Signature field must not be null");
        }
        java.util.List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            throw new IllegalStateException("Signature field has no widget annotation: " + field.getFieldName());
        }
        PdfWidgetAnnotation widget = widgets.get(0);
        int flags = widget.getFlags();
        if ((flags & PdfAnnotation.PRINT) == 0) {
            throw new IllegalStateException("Signature widget is not printable: " + field.getFieldName());
        }
        if ((flags & (PdfAnnotation.INVISIBLE | PdfAnnotation.HIDDEN | PdfAnnotation.NO_VIEW | PdfAnnotation.TOGGLE_NO_VIEW)) != 0) {
            throw new IllegalStateException("Signature widget is hidden: " + field.getFieldName());
        }
    }

    private static void configureSignatureDictionary(PdfSignature signature,
                                                      String signerName,
                                                      String reason,
                                                      String location,
                                                      Calendar signDate) {
        if (signature == null) {
            return;
        }
        signature.put(PdfName.Type, PdfName.Sig);
        signature.put(PdfName.Filter, PdfName.Adobe_PPKLite);
        signature.put(PdfName.SubFilter, PdfName.Adbe_pkcs7_detached);
        if (signDate != null) {
            signature.put(PdfName.M, new PdfDate(signDate).getPdfObject());
        }
        if (signerName != null && !signerName.isBlank()) {
            signature.put(PdfName.Name, new PdfString(signerName));
        }
        if (reason != null && !reason.isBlank()) {
            signature.put(PdfName.Reason, new PdfString(reason));
        }
        if (location != null && !location.isBlank()) {
            signature.put(PdfName.Location, new PdfString(location));
        }
        signature.put(PdfName.ContactInfo, new PdfString(DEFAULT_CONTACT));
    }

    private static KeyMaterial loadKeyMaterial(String pkcs12Path, char[] password) throws Exception {
        Security.addProvider(new BouncyCastleProvider());
        KeyStore ks = DemoKeystoreUtil.loadKeyStore(pkcs12Path, password);
        KeyStore.PrivateKeyEntry entry = DemoKeystoreUtil.firstPrivateKey(ks, password);
        PrivateKey key = entry.getPrivateKey();
        if (key == null) {
            throw new IllegalStateException("Private key not found in keystore: " + pkcs12Path);
        }
        Certificate[] chain = entry.getCertificateChain();
        X509Certificate cert = null;
        if (chain != null && chain.length > 0) {
            cert = (X509Certificate) chain[0];
        }
        return new KeyMaterial(key, chain, cert);
    }

    private static final class KeyMaterial {
        private final PrivateKey key;
        private final Certificate[] chain;
        private final X509Certificate certificate;

        private KeyMaterial(PrivateKey key, Certificate[] chain, X509Certificate certificate) {
            this.key = key;
            this.chain = chain;
            this.certificate = certificate;
        }
    }
}
