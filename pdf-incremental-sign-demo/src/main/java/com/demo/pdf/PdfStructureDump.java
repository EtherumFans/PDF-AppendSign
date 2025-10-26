package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.PdfFormField;
import com.itextpdf.forms.fields.PdfSignatureFormField;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfArray;
import com.itextpdf.kernel.pdf.PdfDictionary;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfIndirectReference;
import com.itextpdf.kernel.pdf.PdfName;
import com.itextpdf.kernel.pdf.PdfNumber;
import com.itextpdf.kernel.pdf.PdfObject;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfString;
import com.itextpdf.kernel.pdf.annot.PdfAnnotation;
import com.itextpdf.kernel.pdf.annot.PdfWidgetAnnotation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Collects structural facts about a PDF so we can diff Acrobat-visible properties.
 */
public final class PdfStructureDump {

    private final Path source;
    private final SortedSet<String> facts = new TreeSet<>();
    private final List<String> blockers = new ArrayList<>();

    private PdfStructureDump(Path source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public static PdfStructureDump load(Path path) throws Exception {
        if (path == null) {
            throw new IllegalArgumentException("Path must not be null");
        }
        PdfStructureDump dump = new PdfStructureDump(path.toAbsolutePath());
        dump.collect();
        return dump;
    }

    public Path getSource() {
        return source;
    }

    public SortedSet<String> getFacts() {
        return facts;
    }

    public List<String> getAcrobatBlockers() {
        return blockers;
    }

    private void collect() throws Exception {
        byte[] bytes = Files.readAllBytes(source);
        facts.add(format("File.size=%d", bytes.length));
        try (PdfDocument pdf = new PdfDocument(new PdfReader(source.toString()))) {
            analyzeAcroForm(pdf, bytes);
        }
        analyzeTail();
    }

    private void analyzeAcroForm(PdfDocument pdf, byte[] bytes) {
        PdfAcroForm acro = PdfAcroForm.getAcroForm(pdf, false);
        if (acro == null) {
            facts.add("Catalog.AcroForm.present=false");
            blockers.add("AcroForm missing");
            return;
        }
        facts.add("Catalog.AcroForm.present=true");
        PdfDictionary acroDict = acro.getPdfObject();

        PdfObject needAppearancesObj = acroDict.get(PdfName.NeedAppearances);
        if (needAppearancesObj != null) {
            String value = acroDict.getAsBoolean(PdfName.NeedAppearances) != null
                    && acroDict.getAsBoolean(PdfName.NeedAppearances).getValue() ? "true" : "false";
            facts.add(format("AcroForm.NeedAppearances=%s", value));
            if ("true".equals(value)) {
                blockers.add("AcroForm.NeedAppearances should be removed");
            }
        } else {
            facts.add("AcroForm.NeedAppearances=absent");
        }

        PdfNumber sigFlags = acroDict.getAsNumber(PdfName.SigFlags);
        int sigFlagValue = sigFlags != null ? sigFlags.intValue() : 0;
        facts.add(format("AcroForm.SigFlags=%d", sigFlagValue));
        int requiredFlags = FormUtil.SIG_FLAG_SIGNATURES_EXIST | FormUtil.SIG_FLAG_APPEND_ONLY;
        if ((sigFlagValue & requiredFlags) != requiredFlags) {
            blockers.add("AcroForm.SigFlags missing SIGNATURES_EXIST/APPEND_ONLY bits");
        }

        PdfString defaultAppearance = acroDict.getAsString(PdfName.DA);
        facts.add(format("AcroForm.DA=%s", defaultAppearance != null ? defaultAppearance.toUnicodeString() : "null"));
        if (defaultAppearance == null || defaultAppearance.toUnicodeString() == null
                || !FormUtil.DEFAULT_APPEARANCE.equals(defaultAppearance.toUnicodeString())) {
            blockers.add("AcroForm default appearance missing or unexpected");
        }

        PdfDictionary dr = acroDict.getAsDictionary(PdfName.DR);
        if (dr == null) {
            facts.add("AcroForm.DR.present=false");
            blockers.add("AcroForm default resources missing");
        } else {
            facts.add("AcroForm.DR.present=true");
            PdfDictionary fonts = dr.getAsDictionary(PdfName.Font);
            if (fonts == null) {
                facts.add("AcroForm.DR.Font.present=false");
                blockers.add("AcroForm DR lacks Font dictionary");
            } else {
                facts.add("AcroForm.DR.Font.present=true");
                fonts.keySet().stream()
                        .sorted(Comparator.comparing(PdfName::getValue))
                        .forEach(name -> analyzeFontEntry(fonts, name));
            }
        }

        PdfArray fieldArray = acroDict.getAsArray(PdfName.Fields);
        if (fieldArray == null || fieldArray.isEmpty()) {
            facts.add("AcroForm.Fields.count=0");
            blockers.add("AcroForm has no fields");
            return;
        }
        facts.add(format("AcroForm.Fields.count=%d", fieldArray.size()));

        Map<String, PdfFormField> fields = acro.getFormFields();
        List<String> orderedNames = new ArrayList<>(fields.keySet());
        orderedNames.sort(Comparator.naturalOrder());
        for (int i = 0; i < orderedNames.size(); i++) {
            String name = orderedNames.get(i);
            facts.add(format("Field[%d]=%s", i, name));
        }

        for (Map.Entry<String, PdfFormField> entry : fields.entrySet()) {
            String fieldName = entry.getKey();
            PdfFormField field = entry.getValue();
            analyzeField(pdf, fieldName, field, bytes);
        }
    }

    private void analyzeFontEntry(PdfDictionary fonts, PdfName name) {
        PdfObject value = fonts.get(name);
        if (value == null) {
            facts.add(format("AcroForm.DR.Font[%s]=null", name.getValue()));
            blockers.add("Font resource " + name.getValue() + " missing");
            return;
        }
        PdfDictionary fontDict = value.isDictionary() ? (PdfDictionary) value : null;
        PdfName type = fontDict != null ? fontDict.getAsName(PdfName.Type) : null;
        PdfName subtype = fontDict != null ? fontDict.getAsName(PdfName.Subtype) : null;
        facts.add(format("AcroForm.DR.Font[%s].Type=%s", name.getValue(), type != null ? type.getValue() : "null"));
        facts.add(format("AcroForm.DR.Font[%s].Subtype=%s", name.getValue(), subtype != null ? subtype.getValue() : "null"));
        if (fontDict == null) {
            blockers.add("Font resource " + name.getValue() + " is not a dictionary");
        } else if (!PdfName.Font.equals(type)) {
            blockers.add("Font resource " + name.getValue() + " Type is not /Font");
        }
    }

    private void analyzeField(PdfDocument pdf, String fieldName, PdfFormField field, byte[] bytes) {
        PdfDictionary dict = field.getPdfObject();
        PdfName ft = dict.getAsName(PdfName.FT);
        facts.add(format("Field.%s.FT=%s", fieldName, ft != null ? ft.getValue() : "null"));
        facts.add(format("Field.%s.hasKids=%s", fieldName, dict.containsKey(PdfName.Kids)));
        facts.add(format("Field.%s.hasParent=%s", fieldName, dict.containsKey(PdfName.Parent)));

        if (field instanceof PdfSignatureFormField) {
            analyzeSignatureField(pdf, fieldName, (PdfSignatureFormField) field, bytes);
        }
    }

    private void analyzeSignatureField(PdfDocument pdf, String fieldName,
                                       PdfSignatureFormField field, byte[] bytes) {
        PdfDictionary dict = field.getPdfObject();
        if (!PdfName.Sig.equals(dict.getAsName(PdfName.FT))) {
            blockers.add("Signature field " + fieldName + " is not /FT /Sig");
        }

        PdfObject rawV = dict.get(PdfName.V);
        PdfDictionary sigDict = dict.getAsDictionary(PdfName.V);
        boolean vIndirect = false;
        if (rawV instanceof PdfIndirectReference) {
            vIndirect = true;
        } else if (sigDict != null && sigDict.getIndirectReference() != null) {
            vIndirect = true;
        }
        facts.add(format("Signature.%s.V.indirect=%s", fieldName, vIndirect));
        if (!vIndirect) {
            blockers.add("Signature field " + fieldName + " /V must be indirect");
        }

        if (sigDict != null) {
            PdfName type = sigDict.getAsName(PdfName.Type);
            PdfName filter = sigDict.getAsName(PdfName.Filter);
            PdfName subFilter = sigDict.getAsName(PdfName.SubFilter);
            facts.add(format("Signature.%s.Type=%s", fieldName, type != null ? type.getValue() : "null"));
            facts.add(format("Signature.%s.Filter=%s", fieldName, filter != null ? filter.getValue() : "null"));
            facts.add(format("Signature.%s.SubFilter=%s", fieldName, subFilter != null ? subFilter.getValue() : "null"));

            if (!PdfName.Sig.equals(type)) {
                blockers.add("Signature " + fieldName + " Type not /Sig");
            }
            if (filter == null || !PdfName.Adobe_PPKLite.equals(filter)) {
                blockers.add("Signature " + fieldName + " Filter not /Adobe.PPKLite");
            }
            if (subFilter == null || !(PdfName.Adbe_pkcs7_detached.equals(subFilter)
                    || new PdfName("adbe.pkcs7.detached").equals(subFilter)
                    || new PdfName("ETSI.CAdES.detached").equals(subFilter))) {
                blockers.add("Signature " + fieldName + " SubFilter not Acrobat-compatible");
            }

            PdfArray byteRange = sigDict.getAsArray(PdfName.ByteRange);
            if (byteRange == null || byteRange.size() != 4) {
                blockers.add("Signature " + fieldName + " ByteRange invalid");
                facts.add(format("Signature.%s.ByteRange.size=%s", fieldName, byteRange != null ? byteRange.size() : "null"));
            } else {
                long b0 = byteRange.getAsNumber(0).longValue();
                long b1 = byteRange.getAsNumber(1).longValue();
                long b2 = byteRange.getAsNumber(2).longValue();
                long b3 = byteRange.getAsNumber(3).longValue();
                facts.add(format("Signature.%s.ByteRange=[%d,%d,%d,%d]", fieldName, b0, b1, b2, b3));
                if (b0 != 0) {
                    blockers.add("Signature " + fieldName + " ByteRange[0] must be 0");
                }
                if (b0 < 0 || b1 < 0 || b2 < 0 || b3 < 0) {
                    blockers.add("Signature " + fieldName + " ByteRange contains negative values");
                }
                ByteRangeInfo info = analyzeByteRange(bytes, byteRange, sigDict.getAsString(PdfName.Contents));
                facts.add(format("Signature.%s.ByteRangeHoleMatchesContents=%s", fieldName, info.matches));
                if (!info.matches) {
                    blockers.add("Signature " + fieldName + " ByteRange hole mismatch: " + info.message);
                }
            }

            PdfString contents = sigDict.getAsString(PdfName.Contents);
            if (contents != null) {
                facts.add(format("Signature.%s.Contents.length=%d", fieldName, contents.getValueBytes().length));
                facts.add(format("Signature.%s.Contents.isHex=%s", fieldName, contents.isHexWriting()));
            } else {
                blockers.add("Signature " + fieldName + " missing /Contents");
            }
        } else {
            blockers.add("Signature field " + fieldName + " missing /V dictionary");
        }

        List<PdfWidgetAnnotation> widgets = field.getWidgets();
        if (widgets == null || widgets.isEmpty()) {
            blockers.add("Signature field " + fieldName + " has no widget");
            return;
        }

        for (int i = 0; i < widgets.size(); i++) {
            analyzeWidget(pdf, fieldName, widgets.get(i), i);
        }
    }

    private void analyzeWidget(PdfDocument pdf, String fieldName, PdfWidgetAnnotation widget, int index) {
        PdfDictionary widgetDict = widget.getPdfObject();
        PdfName type = widgetDict.getAsName(PdfName.Type);
        PdfName subtype = widgetDict.getAsName(PdfName.Subtype);
        facts.add(format("Widget.%s[%d].Type=%s", fieldName, index, type != null ? type.getValue() : "null"));
        facts.add(format("Widget.%s[%d].Subtype=%s", fieldName, index, subtype != null ? subtype.getValue() : "null"));
        facts.add(format("Widget.%s[%d].hasParent=%s", fieldName, index, widgetDict.containsKey(PdfName.Parent)));

        if (!PdfName.Annot.equals(type)) {
            blockers.add("Widget for " + fieldName + " missing /Type /Annot");
        }
        if (!PdfName.Widget.equals(subtype)) {
            blockers.add("Widget for " + fieldName + " missing /Subtype /Widget");
        }

        PdfArray rectArray = widgetDict.getAsArray(PdfName.Rect);
        Rectangle rect = null;
        if (rectArray != null && rectArray.size() == 4) {
            rect = new Rectangle(rectArray.getAsNumber(0).floatValue(),
                    rectArray.getAsNumber(1).floatValue(),
                    rectArray.getAsNumber(2).floatValue() - rectArray.getAsNumber(0).floatValue(),
                    rectArray.getAsNumber(3).floatValue() - rectArray.getAsNumber(1).floatValue());
        }
        facts.add(format("Widget.%s[%d].rect=%s", fieldName, index, rect != null ? rect.toString() : "null"));

        PdfPage page = widget.getPage();
        int pageNumber = page != null ? pdf.getPageNumber(page) : -1;
        facts.add(format("Widget.%s[%d].page=%d", fieldName, index, pageNumber));
        if (pageNumber < 1) {
            blockers.add("Widget for " + fieldName + " not assigned to a page");
        }

        boolean rectValid = rect != null && rect.getWidth() > 0 && rect.getHeight() > 0;
        boolean rectIntersects = false;
        if (rectValid && page != null) {
            Rectangle pageRect = page.getPageSize();
            rectIntersects = pageRect != null && FormUtil.rectanglesIntersect(rect, pageRect);
        }
        facts.add(format("Widget.%s[%d].rectValid=%s", fieldName, index, rectValid));
        facts.add(format("Widget.%s[%d].rectIntersectsPage=%s", fieldName, index, rectIntersects));
        if (!rectValid) {
            blockers.add("Widget for " + fieldName + " has invalid rectangle");
        } else if (!rectIntersects) {
            blockers.add("Widget for " + fieldName + " rectangle outside page bounds");
        }

        PdfNumber f = widgetDict.getAsNumber(PdfName.F);
        int flags = f != null ? f.intValue() : widget.getFlags();
        boolean printable = (flags & PdfAnnotation.PRINT) != 0;
        boolean hidden = (flags & (PdfAnnotation.INVISIBLE | PdfAnnotation.HIDDEN | PdfAnnotation.NO_VIEW
                | PdfAnnotation.TOGGLE_NO_VIEW)) != 0;
        facts.add(format("Widget.%s[%d].flags=0x%X", fieldName, index, flags));
        facts.add(format("Widget.%s[%d].printable=%s", fieldName, index, printable));
        facts.add(format("Widget.%s[%d].hidden=%s", fieldName, index, hidden));
        if (!printable) {
            blockers.add("Widget for " + fieldName + " missing PRINT flag");
        }
        if (hidden) {
            blockers.add("Widget for " + fieldName + " has hidden flags set");
        }

        PdfArray annots = page != null ? page.getPdfObject().getAsArray(PdfName.Annots) : null;
        boolean inAnnots = annots != null && containsReference(annots, widgetDict);
        facts.add(format("Widget.%s[%d].inPageAnnots=%s", fieldName, index, inAnnots));
        if (!inAnnots) {
            blockers.add("Widget for " + fieldName + " missing from page /Annots");
        }

        PdfDictionary ap = widgetDict.getAsDictionary(PdfName.AP);
        boolean hasAp = ap != null && ap.get(PdfName.N) != null;
        facts.add(format("Widget.%s[%d].hasAPN=%s", fieldName, index, hasAp));
        if (!hasAp) {
            blockers.add("Widget for " + fieldName + " missing /AP(N)");
        }
    }

    private void analyzeTail() throws IOException {
        facts.add("Tail.analysis.start");
        PostSignValidator.TailInfo info = PostSignValidator.locateTail(source, false);
        facts.add(format("Tail.startxref.declared=%d", info.getDeclaredOffset()));
        facts.add(format("Tail.startxref.actual=%d", info.getActualOffset()));
        facts.add(format("Tail.type=%s", info.getType()));
        facts.add(format("Tail.eofOffset=%d", info.getEofOffset()));
        if (!info.isOk()) {
            blockers.add("startxref does not point to final xref/xref-stream");
        }
        try {
            PostSignValidator.strictTailCheck(source);
            facts.add("Tail.strict=true");
        } catch (Exception e) {
            facts.add("Tail.strict=false");
            blockers.add("Tail strict check failed: " + e.getMessage());
        }
        facts.add("Tail.analysis.end");
    }

    private static boolean containsReference(PdfArray array, PdfDictionary dictionary) {
        if (array == null || dictionary == null) {
            return false;
        }
        PdfIndirectReference widgetRef = dictionary.getIndirectReference();
        for (int i = 0; i < array.size(); i++) {
            PdfObject obj = array.get(i);
            if (obj == null) {
                continue;
            }
            if (widgetRef != null && obj.isIndirectReference()
                    && widgetRef.equals(obj.getIndirectReference())) {
                return true;
            }
            if (obj.equals(dictionary)) {
                return true;
            }
        }
        return false;
    }

    private ByteRangeInfo analyzeByteRange(byte[] bytes, PdfArray byteRange, PdfString contents) {
        if (byteRange == null || byteRange.size() != 4) {
            return ByteRangeInfo.failure("ByteRange absent or malformed");
        }
        if (contents == null) {
            return ByteRangeInfo.failure("/Contents missing");
        }
        long start0 = byteRange.getAsNumber(0).longValue();
        long len0 = byteRange.getAsNumber(1).longValue();
        long start1 = byteRange.getAsNumber(2).longValue();
        long len1 = byteRange.getAsNumber(3).longValue();

        long holeStart = start0 + len0;
        long holeEnd = start1;
        if (holeStart < 0 || holeEnd < holeStart || holeEnd > bytes.length) {
            return ByteRangeInfo.failure("Computed hole outside file bounds");
        }
        if (start0 != 0) {
            return ByteRangeInfo.failure("ByteRange[0] not zero");
        }
        if (start1 + len1 > bytes.length) {
            return ByteRangeInfo.failure("ByteRange extends past EOF");
        }

        int ltIndex = indexOf(bytes, (byte) '<', (int) holeStart, (int) holeEnd);
        int gtIndex = lastIndexOf(bytes, (byte) '>', (int) holeStart, (int) holeEnd);
        if (ltIndex < 0 || gtIndex < 0 || gtIndex <= ltIndex) {
            return ByteRangeInfo.failure("Unable to locate <...> contents span");
        }
        int expectedHexLength = contents.getValueBytes().length * 2;
        int actualHexLength = gtIndex - ltIndex - 1;
        if (actualHexLength != expectedHexLength) {
            return ByteRangeInfo.failure("Hex length mismatch: actual=" + actualHexLength
                    + " expected=" + expectedHexLength);
        }
        if (ltIndex != holeStart) {
            return ByteRangeInfo.failure("ByteRange hole start " + holeStart + " != < position " + ltIndex);
        }
        if (gtIndex + 1 != holeEnd) {
            return ByteRangeInfo.failure("ByteRange hole end " + holeEnd + " != > position+1 " + (gtIndex + 1));
        }

        boolean hexEven = actualHexLength % 2 == 0;
        if (!hexEven) {
            return ByteRangeInfo.failure("Hex length is not even");
        }
        // Ensure the hex string contains only hex characters.
        for (int i = ltIndex + 1; i < gtIndex; i++) {
            byte b = bytes[i];
            if (!isHexDigit(b)) {
                return ByteRangeInfo.failure("Non-hex digit in Contents span");
            }
        }
        return ByteRangeInfo.success();
    }

    private static boolean isHexDigit(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'A' && b <= 'F') || (b >= 'a' && b <= 'f');
    }

    private static int indexOf(byte[] data, byte target, int start, int end) {
        for (int i = start; i < end && i < data.length; i++) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static int lastIndexOf(byte[] data, byte target, int start, int end) {
        int actualEnd = Math.min(end, data.length);
        for (int i = actualEnd - 1; i >= start && i >= 0; i--) {
            if (data[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static String format(String template, Object... args) {
        return String.format(Locale.ROOT, template, args);
    }

    private static final class ByteRangeInfo {
        private final boolean matches;
        private final String message;

        private ByteRangeInfo(boolean matches, String message) {
            this.matches = matches;
            this.message = message;
        }

        static ByteRangeInfo success() {
            return new ByteRangeInfo(true, "");
        }

        static ByteRangeInfo failure(String message) {
            return new ByteRangeInfo(false, message);
        }
    }
}
