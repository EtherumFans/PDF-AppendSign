package com.demo;

import com.demo.crypto.DemoKeystoreUtil;
import com.demo.pdf.NursingRecordSigner;
import com.demo.pdf.NursingRecordTemplate;
import com.demo.pdf.SignatureVerifier;
import picocli.CommandLine;

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
                NursingRecordTemplate.create(dest, rows, certPath.toAbsolutePath().toString(), password);
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
        @CommandLine.Option(names = "--pdf", required = true, description = "PDF to verify")
        private Path pdf;

        @Override
        public Integer call() throws Exception {
            return SignatureVerifier.verify(pdf.toAbsolutePath().toString());
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
