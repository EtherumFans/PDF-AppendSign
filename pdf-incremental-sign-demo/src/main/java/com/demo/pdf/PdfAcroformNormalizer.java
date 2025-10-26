package com.demo.pdf;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.*;

public class PdfAcroformNormalizer {

    /** 仅注册 Type1 标准字体，确保 DR/Font 字典完整（含 Subtype=/Type1）。 */
    public static void normalizeToHelvOnly(PdfDocument doc) {
        PdfAcroForm af = PdfAcroForm.getAcroForm(doc, true);
        // 禁止 Acrobat 触发重算外观的 NeedAppearances
        af.getPdfObject().remove(PdfName.NeedAppearances);

        // 确保 DR/Font 存在
        PdfDictionary acro = af.getPdfObject();
        PdfDictionary dr = acro.getAsDictionary(PdfName.DR);
        if (dr == null) {
            dr = new PdfDictionary();
            acro.put(PdfName.DR, dr);
        }
        PdfDictionary fonts = dr.getAsDictionary(PdfName.Font);
        if (fonts == null) {
            fonts = new PdfDictionary();
            dr.put(PdfName.Font, fonts);
        } else {
            // 移除所有非 Font 的脏条目
            java.util.List<PdfName> rm = new java.util.ArrayList<>();
            for (PdfName k : fonts.keySet()) {
                PdfObject fo = fonts.get(k);
                PdfDictionary fd = fo instanceof PdfDictionary ? (PdfDictionary) fo : null;
                PdfName type = (fd != null) ? fd.getAsName(PdfName.Type) : null;
                if (type == null || !PdfName.Font.equals(type)) rm.add(k);
            }
            for (PdfName k : rm) fonts.remove(k);
        }

        // 手工构造标准 Type1 字典（关键：Subtype=/Type1，且作为间接对象）
        PdfDictionary helv = new PdfDictionary();
        helv.put(PdfName.Type, PdfName.Font);
        helv.put(PdfName.Subtype, PdfName.Type1);
        helv.put(PdfName.BaseFont, PdfName.Helvetica);
        helv.makeIndirect(doc);

        PdfDictionary zapf = new PdfDictionary();
        zapf.put(PdfName.Type, PdfName.Font);
        zapf.put(PdfName.Subtype, PdfName.Type1);
        zapf.put(PdfName.BaseFont, PdfName.ZapfDingbats);
        zapf.makeIndirect(doc);

        // 注册别名：Helv、ZaDb，以及兼容旧 DA 的 F1→Helv
        fonts.put(new PdfName("Helv"), helv);
        fonts.put(new PdfName("ZaDb"), zapf);
        fonts.put(new PdfName("F1"),   helv);

        // 默认外观固定为 Helvetica；创建字段阶段更稳
        af.setDefaultAppearance(new PdfString("/Helv 12 Tf 0 g"));

        // SigFlags（可选，利于 Acrobat UI）
        af.setSignatureFlags(PdfAcroForm.SIGNATURE_EXIST | PdfAcroForm.APPEND_ONLY);
        af.setModified();
    }
}
