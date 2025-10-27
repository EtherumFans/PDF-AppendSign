package com.demo;

import com.demo.crypto.DemoKeystoreUtil;
import com.demo.pdf.ElectronicSignatureSigner;
import com.demo.pdf.NursingRecordSigner;
import com.demo.pdf.NursingRecordTemplate;
import com.demo.pdf.SignatureVerifier;
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
                    VerifyPdf.class,
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

    @CommandLine.Command(name = "create-template", description = "Create a nursing record template PDF")
    static class CreateTemplate implements Callable<Integer> {
        @CommandLine.Option(names = "--out", required = true, description = "Destination PDF file")
        private Path output;

        @CommandLine.Option(names = "--rows", defaultValue = "6", description = "Number of template rows")
        private int rows;

        @CommandLine.Option(names = "--certP12", description = "Optional PKCS#12 used to DocMDP certify the template")
        private Path certPath;

        @CommandLine.Option(names = "--password", description = "Password for the PKCS#12 (default 123456)")
        private String password;

        @Override
        public Integer call() throws Exception {
            Path target = output.toAbsolutePath();
            if (certPath == null) {
                NursingRecordTemplate.createTemplate(target.toString(), rows);
                System.out.println("[create-template] Template written to " + target);
            } else {
                Path temp = Files.createTempFile("nursing-template", ".pdf");
                try {
                    NursingRecordTemplate.createTemplate(temp.toString(), rows);
                    NursingRecordSigner.certifyDocument(temp.toString(), target.toString(),
                            certPath.toAbsolutePath().toString(), password);
                    System.out.println("[create-template] Certified template written to " + target);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            return 0;
        }
    }

    @CommandLine.Command(name = "sign-row", description = "Fill a row and append a signature")
    static class SignRow implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF to sign")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF output")
        private Path destination;

        @CommandLine.Option(names = "--row", required = true, description = "Row number (1-based)")
        private int row;

        @CommandLine.Option(names = "--time", required = true, description = "Time value")
        private String time;

        @CommandLine.Option(names = "--text", required = true, description = "Nursing note text")
        private String text;

        @CommandLine.Option(names = "--nurse", required = true, description = "Nurse name")
        private String nurse;

        @CommandLine.Option(names = "--pkcs12", description = "Signer PKCS#12 path")
        private Path pkcs12;

        @CommandLine.Option(names = "--password", description = "PKCS#12 password (default 123456)")
        private String password;

        @CommandLine.Option(names = "--tsa", description = "Optional TSA URL")
        private String tsaUrl;

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
            params.setTsaUrl(tsaUrl);
            NursingRecordSigner.signRow(params);
            System.out.println("[sign-row] Signed row " + row + " -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "sign-electronic", description = "Append a visible electronic signature")
    static class SignElectronic implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF")
        private Path destination;

        @CommandLine.Option(names = "--pkcs12", description = "Signer PKCS#12 file")
        private Path pkcs12;

        @CommandLine.Option(names = "--password", description = "Password for PKCS#12 (default 123456)")
        private String password;

        @CommandLine.Option(names = "--page", defaultValue = "1", description = "Page number")
        private int page;

        @CommandLine.Option(names = "--x", defaultValue = "72", description = "Lower-left X coordinate")
        private float x;

        @CommandLine.Option(names = "--y", defaultValue = "72", description = "Lower-left Y coordinate")
        private float y;

        @CommandLine.Option(names = "--width", defaultValue = "180", description = "Signature width")
        private float width;

        @CommandLine.Option(names = "--height", defaultValue = "72", description = "Signature height")
        private float height;

        @CommandLine.Option(names = "--field", defaultValue = "sig_electronic", description = "Signature field name")
        private String fieldName;

        @CommandLine.Option(names = "--signer", description = "Signer display name")
        private String signer;

        @CommandLine.Option(names = "--reason", defaultValue = "电子签名", description = "Reason string")
        private String reason;

        @CommandLine.Option(names = "--location", defaultValue = "Ward A", description = "Location string")
        private String location;

        @CommandLine.Option(names = "--contact", defaultValue = "nurse-signer@example.com", description = "Contact info")
        private String contact;

        @CommandLine.Option(names = "--cjk-font", description = "Optional CJK font for appearance")
        private java.io.File cjkFont;

        @CommandLine.Option(names = "--tsa", description = "Optional TSA URL")
        private String tsaUrl;

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
            if (cjkFont != null) {
                Path resolved = cjkFont.toPath().toAbsolutePath();
                if (Files.isRegularFile(resolved)) {
                    params.setCjkFontPath(resolved);
                } else {
                    System.err.println("[sign-electronic] Ignoring missing font: " + resolved);
                }
            }
            params.setTsaUrl(tsaUrl);
            ElectronicSignatureSigner.sign(params);
            System.out.println("[sign-electronic] Signature appended -> " + destination.toAbsolutePath());
            return 0;
        }
    }

    @CommandLine.Command(name = "verify", description = "Verify signatures in a PDF")
    static class VerifyPdf implements Callable<Integer> {
        @CommandLine.Option(names = "--pdf", required = true, description = "PDF file to verify")
        private Path pdf;

        @Override
        public Integer call() throws Exception {
            return SignatureVerifier.verify(pdf.toAbsolutePath().toString());
        }
    }

    @CommandLine.Command(name = "certify", description = "Apply DocMDP certification in append mode")
    static class Certify implements Callable<Integer> {
        @CommandLine.Option(names = "--src", required = true, description = "Source PDF")
        private Path source;

        @CommandLine.Option(names = "--dest", required = true, description = "Destination PDF")
        private Path destination;

        @CommandLine.Option(names = "--certP12", description = "PKCS#12 file")
        private Path certPath;

        @CommandLine.Option(names = "--password", description = "Password for PKCS#12 (default 123456)")
        private String password;

        @Override
        public Integer call() throws Exception {
            NursingRecordSigner.certifyDocument(source.toAbsolutePath().toString(),
                    destination.toAbsolutePath().toString(),
                    certPath != null ? certPath.toAbsolutePath().toString() : null,
                    password);
            System.out.println("[certify] Certification applied -> " + destination.toAbsolutePath());
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
}
