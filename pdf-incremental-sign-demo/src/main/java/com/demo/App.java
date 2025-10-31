package com.demo;

import com.demo.crypto.DemoKeystoreUtil;
import com.demo.pdf.ElectronicSignatureSigner;
import com.demo.pdf.NursingRecordSigner;
import com.demo.pdf.NursingRecordTemplate;
import com.demo.pdf.SignatureVerifier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class App {

    public static void main(String[] args) {
        int exit = new CommandLine(new Root()).execute(args);
        System.exit(exit);
    }

    @CommandLine.Command(name = "app", mixinStandardHelpOptions = true,
            subcommands = {
                    CreateTemplate.class,
                    SignRow.class,
                    SignElectronic.class,
                    VerifyPdf.class,
                    GenDemoP12.class,
                    ListFields.class
            })
    static class Root implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @CommandLine.Command(name = "create-template", description = "Create a nursing record template PDF with blank rows")
    static class CreateTemplate implements Callable<Integer> {
        @CommandLine.Option(names = "--out", required = true, description = "Destination PDF file")
        private Path output;

        @CommandLine.Option(names = "--rows", defaultValue = "3", description = "Number of rows to create")
        private int rows;

        @Override
        public Integer call() throws Exception {
            NursingRecordTemplate.createTemplate(output.toAbsolutePath().toString(), rows);
            System.out.println("Template written to " + output.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "sign-row", description = "Fill a row and sign the corresponding signature field")
    static class SignRow implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF to sign")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF")
        private Path destination;

        @CommandLine.Option(names = "--row", required = true, description = "Row number to sign (1-based)")
        private int row;

        @CommandLine.Option(names = "--time", required = true, description = "Row time text")
        private String time;

        @CommandLine.Option(names = "--text", required = true, description = "Nursing note text")
        private String text;

        @CommandLine.Option(names = "--nurse", required = true, description = "Nurse name")
        private String nurse;

        @CommandLine.Option(names = "--pkcs12", required = false, description = "Signer PKCS#12 file")
        private Path pkcs12;

        @CommandLine.Option(names = "--password", required = false, description = "Password for PKCS#12")
        private String password;

        @CommandLine.Option(names = "--reason", required = false, defaultValue = "Nursing record approval")
        private String reason;

        @CommandLine.Option(names = "--location", required = false, defaultValue = "Ward")
        private String location;

        @CommandLine.Option(names = "--contact", required = false, defaultValue = "nurse@example.com")
        private String contact;

        @CommandLine.Option(names = "--tsaUrl", required = false, description = "Optional TSA URL")
        private String tsaUrl;

        @CommandLine.Option(names = "--certify-p3", required = false,
                description = "Apply DocMDP certification level 3 (use for the first signing round)")
        private boolean certifyP3;

        @CommandLine.Option(names = "--cjk-font", required = false, description = "Optional path to a CJK font")
        private Path cjkFont;

        @CommandLine.Option(names = "--fallback-draw", required = false,
                description = "Draw text directly onto the PDF when form fields are missing")
        private boolean fallbackDraw;

        @CommandLine.Option(names = "--form-off", required = false,
                description = "Disable form creation and use FreeText annotations instead")
        private boolean formOff;

        @CommandLine.Option(names = "--page-index", required = false, defaultValue = "1",
                description = "1-based page index used for fallback drawing")
        private int pageIndex;

        @CommandLine.Option(names = "--table-top-y", required = false, defaultValue = "650",
                description = "Top Y coordinate of the first data row for fallback drawing")
        private float tableTopY;

        @CommandLine.Option(names = "--row-height", required = false, defaultValue = "22",
                description = "Row height for fallback drawing")
        private float rowHeight;

        @CommandLine.Option(names = "--time-x", required = false, defaultValue = "90",
                description = "X coordinate for the time column when fallback drawing")
        private float timeX;

        @CommandLine.Option(names = "--text-x", required = false, defaultValue = "150",
                description = "X coordinate for the record text column when fallback drawing")
        private float textX;

        @CommandLine.Option(names = "--nurse-x", required = false, defaultValue = "500",
                description = "X coordinate for the nurse column when fallback drawing")
        private float nurseX;

        @CommandLine.Option(names = "--font-path", required = false,
                description = "Optional font used for fallback drawing (overrides bundled font)")
        private Path fontPath;

        @CommandLine.Option(names = "--font-size", required = false, defaultValue = "10",
                description = "Font size used for fallback drawing")
        private float fontSize;

        @CommandLine.Option(names = "--text-max-width", required = false, defaultValue = "330",
                description = "Maximum width for wrapping the record text column when fallback drawing")
        private float textMaxWidth;

        @CommandLine.Option(names = "--sign-visible", defaultValue = "true",
                description = "Place a visible signature (true) or create an invisible signature (false).")
        private boolean signVisible;

        @CommandLine.Option(names = "--sign-field", defaultValue = "sig_row_{row}",
                description = "Signature field name template. If field doesn't exist, a new field will be created when rectangle is used.")
        private String signFieldTemplate;

        @CommandLine.Option(names = "--sign-x", defaultValue = "-1",
                description = "Left X of the visible signature rectangle. If <0, default to nurse column X.")
        private float signX;

        @CommandLine.Option(names = "--sign-width", defaultValue = "120",
                description = "Width of the visible signature rectangle.")
        private float signWidth;

        @CommandLine.Option(names = "--sign-height", defaultValue = "18",
                description = "Height of the visible signature rectangle.")
        private float signHeight;

        @CommandLine.Option(names = "--sign-y-offset", defaultValue = "-12",
                description = "YOffset from the computed row baseline Y to the bottom of the signature rectangle.")
        private float signYOffset;

        @Override
        public Integer call() throws Exception {
            NursingRecordSigner.SignParams params = new NursingRecordSigner.SignParams();
            params.setSource(source.toAbsolutePath().toString());
            params.setDestination(destination.toAbsolutePath().toString());
            params.setRow(row);
            params.setTimeValue(time);
            params.setTextValue(text);
            params.setNurse(nurse);
            params.setPkcs12Path(pkcs12 != null ? pkcs12.toAbsolutePath().toString() : null);
            params.setPassword(password);
            params.setReason(reason);
            params.setLocation(location);
            params.setContact(contact);
            params.setTsaUrl(tsaUrl);
            params.setCertifyP3(certifyP3);
            params.setCjkFontPath(cjkFont != null ? cjkFont.toAbsolutePath().toString() : null);
            params.setFallbackDraw(fallbackDraw);
            params.setFormOff(formOff);
            params.setPageIndex(pageIndex);
            params.setTableTopY(tableTopY);
            params.setRowHeight(rowHeight);
            params.setTimeX(timeX);
            params.setTextX(textX);
            params.setNurseX(nurseX);
            params.setFontPath(fontPath != null ? fontPath.toAbsolutePath().toString() : null);
            params.setFontSize(fontSize);
            params.setTextMaxWidth(textMaxWidth);
            params.setSignVisible(signVisible);
            params.setSignFieldTemplate(signFieldTemplate);
            params.setSignX(signX);
            params.setSignWidth(signWidth);
            params.setSignHeight(signHeight);
            params.setSignYOffset(signYOffset);
            new NursingRecordSigner().signRow(params);
            System.out.println("Signed row " + row + " -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "list-fields", description = "List all AcroForm fields in a PDF")
    static class ListFields implements Callable<Integer> {

        @CommandLine.Option(names = "--src", required = true, description = "Source PDF")
        private Path source;

        @Override
        public Integer call() throws Exception {
            Path srcFile = source.toAbsolutePath();
            if (!Files.exists(srcFile)) {
                System.err.println("Source PDF does not exist: " + srcFile);
                return 1;
            }

            try (PDDocument doc = Loader.loadPDF(srcFile.toFile())) {
                PDAcroForm form = doc.getDocumentCatalog().getAcroForm();
                if (form == null || form.getFields().isEmpty()) {
                    System.out.println("No AcroForm fields found.");
                    return 0;
                }

                for (PDField field : form.getFieldTree()) {
                    PDAnnotationWidget widget = field.getWidgets().isEmpty() ? null : field.getWidgets().get(0);
                    Integer pageIndex = null;
                    String rect = "n/a";
                    if (widget != null) {
                        if (widget.getPage() != null) {
                            pageIndex = doc.getPages().indexOf(widget.getPage()) + 1;
                        }
                        if (widget.getRectangle() != null) {
                            rect = widget.getRectangle().toString();
                        }
                    }
                    System.out.printf("%s | %s | page=%s | rect=%s%n",
                            field.getFullyQualifiedName(), field.getClass().getSimpleName(),
                            pageIndex, rect);
                }
            }

            return 0;
        }
    }

    @CommandLine.Command(name = "sign-electronic", description = "Create a visible signature field and sign it")
    static class SignElectronic implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF")
        private Path destination;

        @CommandLine.Option(names = "--pkcs12", required = false, description = "Signer PKCS#12 file")
        private Path pkcs12;

        @CommandLine.Option(names = "--password", required = false, description = "Password for PKCS#12")
        private String password;

        @CommandLine.Option(names = "--field", required = false, defaultValue = "sig_electronic", description = "Signature field name")
        private String fieldName;

        @CommandLine.Option(names = "--page", required = false, defaultValue = "1", description = "Page number for the signature")
        private int page;

        @CommandLine.Option(names = "--x", required = false, defaultValue = "72", description = "Lower-left X coordinate in points")
        private float x;

        @CommandLine.Option(names = "--y", required = false, defaultValue = "72", description = "Lower-left Y coordinate in points")
        private float y;

        @CommandLine.Option(names = "--width", required = false, defaultValue = "180", description = "Signature width in points")
        private float width;

        @CommandLine.Option(names = "--height", required = false, defaultValue = "72", description = "Signature height in points")
        private float height;

        @CommandLine.Option(names = "--signer", required = false, description = "Signer display name")
        private String signer;

        @CommandLine.Option(names = "--reason", required = false, defaultValue = "电子签名")
        private String reason;

        @CommandLine.Option(names = "--location", required = false, defaultValue = "Ward")
        private String location;

        @CommandLine.Option(names = "--contact", required = false, defaultValue = "signer@example.com")
        private String contact;

        @CommandLine.Option(names = "--tsaUrl", required = false, description = "Optional TSA URL")
        private String tsaUrl;

        @Override
        public Integer call() throws Exception {
            ElectronicSignatureSigner.Params params = new ElectronicSignatureSigner.Params();
            params.setSource(source.toAbsolutePath().toString());
            params.setDestination(destination.toAbsolutePath().toString());
            params.setPkcs12Path(pkcs12 != null ? pkcs12.toAbsolutePath().toString() : null);
            params.setPassword(password);
            params.setFieldName(fieldName);
            params.setPage(page);
            params.setX(x);
            params.setY(y);
            params.setWidth(width);
            params.setHeight(height);
            params.setSignerName(signer);
            params.setReason(reason);
            params.setLocation(location);
            params.setContact(contact);
            params.setTsaUrl(tsaUrl);
            ElectronicSignatureSigner.sign(params);
            System.out.println("Electronic signature applied -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "verify", description = "Verify signatures in a PDF")
    static class VerifyPdf implements Callable<Integer> {
        @CommandLine.Option(names = "--pdf", required = true, description = "PDF to verify")
        private Path pdf;

        @Override
        public Integer call() throws Exception {
            return SignatureVerifier.verify(pdf.toAbsolutePath().toString());
        }
    }

    @CommandLine.Command(name = "gen-demo-p12", description = "Generate a demo PKCS#12 file")
    static class GenDemoP12 implements Callable<Integer> {
        @CommandLine.Option(names = "--out", required = true)
        private Path output;

        @CommandLine.Option(names = "--password", required = true)
        private String password;

        @CommandLine.Option(names = "--cn", required = true)
        private String commonName;

        @Override
        public Integer call() throws Exception {
            if (Files.notExists(output.getParent())) {
                Files.createDirectories(output.getParent());
            }
            DemoKeystoreUtil.createDemoP12(output.toAbsolutePath(), password.toCharArray(), commonName);
            System.out.println("Demo keystore written to " + output.toAbsolutePath());
            return 0;
        }
    }
}
