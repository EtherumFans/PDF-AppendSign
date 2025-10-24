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

    public static void requireVersionAtLeast(Path path, String minimumVersion) throws IOException {
        if (minimumVersion == null || minimumVersion.isBlank()) {
            throw new IllegalArgumentException("minimumVersion must not be blank");
        }
        String header = readHeader(path);
        if (!header.startsWith("%PDF-")) {
            throw new IllegalStateException(HEADER_ERROR);
        }
        String version = extractVersion(header);
        if (version == null) {
            throw new IllegalStateException("Unable to parse PDF version from header: '" + header + "'");
        }
        if (comparePdfVersions(version, minimumVersion) < 0) {
            throw new IllegalStateException("PDF version " + version + " is below required " + minimumVersion);
        }
    }

    private static String extractVersion(String header) {
        if (header == null || header.length() <= 5) {
            return null;
        }
        StringBuilder version = new StringBuilder();
        for (int i = 5; i < header.length(); i++) {
            char c = header.charAt(i);
            if (Character.isDigit(c) || c == '.') {
                version.append(c);
            } else {
                break;
            }
        }
        return version.length() == 0 ? null : version.toString();
    }

    private static int comparePdfVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int l = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int r = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        if (part == null || part.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid PDF version component: '" + part + "'", e);
        }
    }
}
