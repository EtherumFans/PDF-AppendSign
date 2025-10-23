package com.demo.pdf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class PdfSanityUtil {

    public static final String HEADER_ERROR = "PDF header not at byte 0 (BOM or stray bytes), Adobe may ignore signatures.";

    private PdfSanityUtil() {
    }

    public static String readHeader(Path path) throws IOException {
        byte[] buffer = new byte[8];
        try (InputStream in = Files.newInputStream(path)) {
            int read = in.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, StandardCharsets.US_ASCII);
        }
    }

    public static void requireHeader(Path path) throws IOException {
        String header = readHeader(path);
        if (!header.startsWith("%PDF-")) {
            throw new IllegalStateException(HEADER_ERROR);
        }
    }
}
