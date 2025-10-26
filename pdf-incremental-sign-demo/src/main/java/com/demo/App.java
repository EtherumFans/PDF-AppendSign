package com.demo;

import com.demo.crypto.DemoKeystoreUtil;
import com.demo.pdf.ElectronicSignatureSigner;
import com.demo.pdf.NursingRecordSigner;
import com.demo.pdf.NursingRecordTemplate;
import com.demo.pdf.PdfStructureDebugger;
import com.demo.pdf.PdfStructureDiff;
import com.demo.pdf.PdfStructureDump;
import com.demo.pdf.PostSignSanitizer;
import com.demo.pdf.SignatureVerifier;
import com.demo.pdf.SignatureWidgetRepairer;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

public class App {

    public static void main(String[] args) {
        int exit = new CommandLine(new Root()).execute(args);
        System.exit(exit);
    }

    @CommandLine.Command(name = "sign-electronic", description = "Append a single electronic signature with a handwritten-style appearance")
    static class SignElectronic implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF to sign")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF with appended signature")
        private Path destination;

        @CommandLine.Option(names = "--pkcs12", required = false, description = "Signer PKCS#12 file")
        private Path pkcs12;

        @CommandLine.Option(names = "--password", required = false, description = "Password for PKCS#12")
        private String password;

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

        @CommandLine.Option(names = "--field", required = false, defaultValue = "sig_electronic", description = "Signature field name to use or create")
        private String fieldName;

        @CommandLine.Option(names = "--signer", required = false, description = "Signer display name for the signature dictionary")
        private String signer;

        @CommandLine.Option(names = "--reason", required = false, defaultValue = "电子签名", description = "Reason string stored in the signature dictionary")
        private String reason;

        @CommandLine.Option(names = "--location", required = false, defaultValue = "Ward A", description = "Location stored in the signature dictionary")
        private String location;

        @CommandLine.Option(names = "--contact", required = false, defaultValue = "nurse-signer@example.com", description = "Contact info stored in the signature dictionary")
        private String contact;

        @CommandLine.Option(names = "--cjk-font", required = false, description = "Path to a CJK font (e.g., NotoSansCJKsc-Regular.otf)")
        private java.io.File cjkFont;

        @CommandLine.Option(names = "--debug-fonts", description = "Print AcroForm DA/DR/Font diagnostics")
        private boolean debugFonts;

        @Override
        public Integer call() throws Exception {
            ElectronicSignatureSigner.Params params = new ElectronicSignatureSigner.Params();
            params.setSource(source.toAbsolutePath().toString());
            params.setDestination(destination.toAbsolutePath().toString());
            params.setPkcs12Path(pkcs12 != null ? pkcs12.toAbsolutePath().toString() : null);
            params.setPassword(password);
            params.setPage(page);
            params.setX(x);
            params.setY(y);
            params.setWidth(width);
            params.setHeight(height);
            params.setFieldName(fieldName);
            params.setSignerName(signer);
            params.setReason(reason);
            params.setLocation(location);
            params.setContact(contact);
            params.setCjkFontPath(cjkFont != null ? cjkFont.toPath() : null);
            params.setDebugFonts(debugFonts);
            ElectronicSignatureSigner.sign(params);
            return 0;
        }
    }

    @CommandLine.Command(name = "app", mixinStandardHelpOptions = true,
            subcommands = {
                    CreateTemplate.class,
                    SignRow.class,
                    VerifyPdf.class,
                    CompareStructure.class,
                    DebugStructure.class,
                    FixWidgets.class,
                    SanitizeAcroform.class,
                    SignElectronic.class,
                    Certify.class,
                    GenDemoP12.class
            })
    static class Root implements Runnable {
        @Override
        public void run() {
            CommandLine.usage(this, System.out);
        }
    }

    @CommandLine.Command(name = "create-template", description = "Create a nursing record template PDF with DocMDP certification")
    static class CreateTemplate implements Callable<Integer> {
        @CommandLine.Option(names = "--out", required = true, description = "Destination PDF file")
        private Path output;

        @CommandLine.Option(names = "--rows", defaultValue = "3", description = "Number of rows to create")
        private int rows;

        @CommandLine.Option(names = "--certP12", required = false, description = "Path to PKCS#12 file for certification")
        private Path certPath;

        @CommandLine.Option(names = "--password", required = false, description = "Password for PKCS#12")
        private String password;

        @Override
        public Integer call() throws Exception {
            System.out.println("[create-template] Starting");
            String dest = output.toAbsolutePath().toString();
            if (certPath != null) {
                String temp = dest + ".tmp";
                NursingRecordTemplate.createTemplate(temp, rows);
                NursingRecordSigner.certifyDocument(temp, dest, certPath.toAbsolutePath().toString(), password);
                Files.deleteIfExists(Path.of(temp));
                System.out.println("[create-template] Template certified and written to " + output.toAbsolutePath());
            } else {
                NursingRecordTemplate.createTemplate(dest, rows);
                System.out.println("[create-template] Template written to " + output.toAbsolutePath());
            }
            return 0;
        }
    }

    @CommandLine.Command(name = "sign-row", description = "Fill a row and append a FieldMDP-locked signature")
    static class SignRow implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF to sign")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF with appended signature")
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

        @CommandLine.Option(names = "--tsaUrl", required = false, description = "Optional TSA URL")
        private String tsaUrl;

        @CommandLine.Option(names = "--mode", required = false, description = "Signing mode: template, inject, or auto", defaultValue = "auto")
        private String mode;

        @CommandLine.Option(names = "--page", required = false, description = "Page number where the row lives", defaultValue = "1")
        private int page;

        @CommandLine.Option(names = "--certify-on-first-inject", description = "Apply DocMDP certification when injecting for the first time")
        private boolean certifyOnFirstInject;

        @Override
        public Integer call() throws Exception {
            if (row < 1) {
                throw new IllegalArgumentException("Row must be at least 1");
            }
            NursingRecordSigner.SignParams params = new NursingRecordSigner.SignParams();
            params.setSource(source.toAbsolutePath().toString());
            params.setDestination(destination.toAbsolutePath().toString());
            params.setRow(row);
            params.setTimeValue(time);
            params.setTextValue(text);
            params.setNurse(nurse);
            params.setPkcs12Path(pkcs12 != null ? pkcs12.toAbsolutePath().toString() : null);
            params.setPassword(password);
            params.setTsaUrl(tsaUrl);
            params.setMode(mode);
            params.setPage(page);
            params.setCertifyOnFirstInject(certifyOnFirstInject);
            NursingRecordSigner.signRow(params);
            System.out.println("[sign-row] Signed row " + row + " -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "verify", description = "Verify signatures and list FieldMDP locks")
    static class VerifyPdf implements Callable<Integer> {
        @CommandLine.Option(names = "--pdf", required = false, description = "PDF to verify")
        private Path pdf;

        @CommandLine.Parameters(index = "0", arity = "0..1", description = "PDF to verify")
        private Path positional;

        @Override
        public Integer call() throws Exception {
            Path target = pdf != null ? pdf : positional;
            if (pdf != null && positional != null && !pdf.equals(positional)) {
                throw new CommandLine.ParameterException(new CommandLine(this),
                        "Specify either --pdf or positional argument (not both) or ensure they match.");
            }
            if (target == null) {
                throw new CommandLine.ParameterException(new CommandLine(this),
                        "No PDF specified. Use --pdf <file> or provide a positional argument.");
            }
            int rc = SignatureVerifier.verify(target.toAbsolutePath().toString());
            if (rc != 0) {
                return rc;
            }
            return 0;
        }
    }

    @CommandLine.Command(name = "debug-structure", description = "Dump signature field structure for Adobe troubleshooting")
    static class DebugStructure implements Callable<Integer> {
        @CommandLine.Option(names = "--pdf", required = true, description = "PDF to inspect")
        private Path pdf;

        @Override
        public Integer call() throws Exception {
            int rc = PdfStructureDebugger.inspect(pdf.toAbsolutePath());
            return rc;
        }
    }

    @CommandLine.Command(name = "certify", description = "Apply DocMDP certification to an existing PDF in append mode")
    static class Certify implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF")
        private Path destination;

        @CommandLine.Option(names = "--certP12", required = false, description = "PKCS#12 file for certification")
        private Path cert;

        @CommandLine.Option(names = "--password", required = false, description = "Password for the PKCS#12")
        private String password;

        @Override
        public Integer call() throws Exception {
            DemoKeystoreUtil.ensureProvider();
            String certPath = cert != null ? cert.toAbsolutePath().toString() : null;
            NursingRecordSigner.certifyDocument(source.toAbsolutePath().toString(), destination.toAbsolutePath().toString(), certPath, password);
            System.out.println("[certify] Certification applied -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "fix-widgets", description = "Repair signature widget flags and appearance in append mode")
    static class FixWidgets implements Callable<Integer> {

        @CommandLine.Option(names = "--pdf", required = true, description = "Source PDF with hidden signatures")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF with repaired widgets")
        private Path destination;

        @Override
        public Integer call() throws Exception {
            if (source.equals(destination)) {
                throw new CommandLine.ParameterException(new CommandLine(this),
                        "Source and destination must differ to preserve the original revision");
            }
            SignatureWidgetRepairer.repair(source.toAbsolutePath(), destination.toAbsolutePath());
            System.out.println("[fix-widgets] Repaired widgets -> " + destination.toAbsolutePath());
            return 0;
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
            DemoKeystoreUtil.createDemoP12(output.toAbsolutePath(), password.toCharArray(), commonName);
            System.out.println("[gen-demo-p12] Demo keystore written to " + output.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "compare-structure", description = "Diff test PDF against golden PDF (Acrobat-friendly)")
    static class CompareStructure implements Callable<Integer> {
        @CommandLine.Option(names = "--gold", required = true, description = "Reference PDF with Acrobat-friendly structure")
        private Path gold;

        @CommandLine.Option(names = "--test", required = true, description = "Test PDF to compare against the golden reference")
        private Path test;

        @Override
        public Integer call() throws Exception {
            PdfStructureDump golden = PdfStructureDump.load(gold);
            PdfStructureDump candidate = PdfStructureDump.load(test);
            PdfStructureDiff.DiffResult diff = PdfStructureDiff.diff(golden, candidate);
            diff.printTo(System.out);
            return PdfStructureDiff.hasAcrobatBlockers(candidate) ? 2 : 0;
        }
    }

    @CommandLine.Command(name = "sanitize-acroform", description = "Normalize AcroForm/Widgets for Acrobat visibility (append-only)")
    static class SanitizeAcroform implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF to sanitize")
        private Path src;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF written with append-only fixes")
        private Path dest;

        @CommandLine.Option(names = "--field", required = false, description = "Signature field name to ensure exists", defaultValue = "sig_row_1")
        private String fieldName;

        @Override
        public Integer call() throws Exception {
            new PostSignSanitizer().sanitize(src, dest, fieldName);
            return 0;
        }
    }

}

