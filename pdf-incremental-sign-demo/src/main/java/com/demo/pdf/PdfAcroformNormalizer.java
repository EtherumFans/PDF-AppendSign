package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfBoolean;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfString;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PdfAcroformNormalizer {

    private PdfAcroformNormalizer() {
    }

    public static final class Fonts {
        public PdfFont helv;
        public PdfFont zapf;
        public PdfFont cjk;
        public String daAlias;
    }

    public static void dumpAcroFormFonts(PdfDocument doc, String tag) {
        if (doc == null) {
            System.out.println("=== [dumpAcroFormFonts] " + tag + " ===");
            System.out.println("PdfDocument is null");
            return;
        }
        PdfDictionary acro = doc.getCatalog().getPdfObject().getAsDictionary(PdfName.AcroForm);
        System.out.println("=== [dumpAcroFormFonts] " + tag + " ===");
        if (acro == null) {
            System.out.println("No AcroForm.");
            return;
        }
        System.out.println("DA: " + acro.get(PdfName.DA));
        PdfBoolean na = acro.getAsBoolean(PdfName.NeedAppearances);
        System.out.println("NeedAppearances: " + (na != null && na.getValue()));

        PdfDictionary dr = acro.getAsDictionary(PdfName.DR);
        System.out.println("DR present: " + (dr != null));
        PdfDictionary fonts = dr != null ? dr.getAsDictionary(PdfName.Font) : null;
        if (fonts == null) {
            System.out.println("DR.Font: null");
            return;
        }

        System.out.println("DR.Font keys: " + fonts.keySet());
        for (PdfName key : fonts.keySet()) {
            PdfObject fo = fonts.get(key);
            PdfDictionary fd = fo instanceof PdfDictionary ? (PdfDictionary) fo : null;
            PdfName type = fd != null ? fd.getAsName(PdfName.Type) : null;
            PdfName subtype = fd != null ? fd.getAsName(PdfName.Subtype) : null;
            System.out.println("  - " + key + " -> isDict=" + (fd != null)
                    + ", Type=" + type + ", Subtype=" + subtype);
        }
    }

    public static Fonts normalize(PdfDocument doc, Path cjkFontPath) throws IOException {
        if (doc == null) {
            throw new IllegalArgumentException("PdfDocument must not be null");
        }
        PdfAcroForm af = PdfAcroForm.getAcroForm(doc, true);

        af.getPdfObject().remove(PdfName.NeedAppearances);

        PdfDictionary dr = af.getPdfObject().getAsDictionary(PdfName.DR);
        if (dr == null) {
            dr = new PdfDictionary();
            af.getPdfObject().put(PdfName.DR, dr);
        }
        PdfDictionary fonts = dr.getAsDictionary(PdfName.Font);
        if (fonts == null) {
            fonts = new PdfDictionary();
            dr.put(PdfName.Font, fonts);
        }

        List<PdfName> toRemove = new ArrayList<>();
        for (PdfName key : fonts.keySet()) {
            PdfObject fo = fonts.get(key);
            PdfDictionary fd = fo instanceof PdfDictionary ? (PdfDictionary) fo : null;
            PdfName type = fd != null ? fd.getAsName(PdfName.Type) : null;
            if (type == null || !PdfName.Font.equals(type)) {
                toRemove.add(key);
            }
        }
        for (PdfName key : toRemove) {
            fonts.remove(key);
        }

        Fonts f = new Fonts();
        f.helv = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        f.helv.makeIndirect(doc);
        f.zapf = PdfFontFactory.createFont(StandardFonts.ZAPFDINGBATS);
        f.zapf.makeIndirect(doc);

        fonts.put(new PdfName("Helv"), f.helv.getPdfObject());
        fonts.put(new PdfName("ZaDb"), f.zapf.getPdfObject());
        fonts.put(new PdfName("F1"), f.helv.getPdfObject());

        Path normalizedCjkPath = null;
        if (cjkFontPath != null) {
            if (Files.isRegularFile(cjkFontPath)) {
                normalizedCjkPath = cjkFontPath;
            } else {
                System.err.println("[PdfAcroformNormalizer] CJK font not found or not a file: " + cjkFontPath);
            }
        }

        if (normalizedCjkPath != null) {
            byte[] fontBytes;
            try (InputStream is = Files.newInputStream(normalizedCjkPath)) {
                fontBytes = is.readAllBytes();
            } catch (IOException ex) {
                System.err.println("[PdfAcroformNormalizer] Failed to read CJK font '" + normalizedCjkPath
                        + "'. Falling back to Helvetica. Reason: " + ex.getMessage());
                fontBytes = null;
            }
            if (fontBytes != null) {
                try {
                    f.cjk = PdfFontFactory.createFont(fontBytes, PdfEncodings.IDENTITY_H,
                            PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
                    f.cjk.makeIndirect(doc);
                    fonts.put(new PdfName("CJK"), f.cjk.getPdfObject());
                } catch (IOException ex) {
                    System.err.println("[PdfAcroformNormalizer] Failed to create CJK font. Falling back to Helvetica. Reason: "
                            + ex.getMessage());
                    f.cjk = null;
                }
            }
        }

        if (f.cjk != null) {
            af.setDefaultAppearance("/CJK 12 Tf 0 g");
            f.daAlias = "/CJK";
        } else {
            af.setDefaultAppearance("/Helv 12 Tf 0 g");
            f.daAlias = "/Helv";
        }

        af.setSignatureFlags(PdfAcroForm.SIGNATURE_EXIST | PdfAcroForm.APPEND_ONLY);
        af.setModified();
        return f;
    }
}
