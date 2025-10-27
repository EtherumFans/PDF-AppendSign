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
                    SignElectronic.class,
                    VerifyPdf.class,
                    GenDemoP12.class
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
            NursingRecordSigner.signRow(params);
            System.out.println("Signed row " + row + " -> " + destination.toAbsolutePath());
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
