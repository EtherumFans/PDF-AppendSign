package com.demo.pdf;

import com.itextpdf.kernel.geom.Rectangle;

/**
 * Utility to compute row geometry for the nursing record forms.
 */
public final class LayoutUtil {

    private LayoutUtil() {
    }

    public static final float MARGIN = 36f;
    public static final float TOP_MARGIN = 90f;
    public static final float ROW_HEIGHT = 120f;
    public static final float COLUMN_GAP = 6f;
    public static final float FIELD_HEIGHT = 28f;
    public static final float FIELD_PADDING = 12f;
    public static final float TIME_WIDTH = 70f;
    public static final float TEXT_WIDTH = 220f;
    public static final float NURSE_WIDTH = 90f;
    public static final float SIGNATURE_WIDTH = 120f;

    public enum FieldSlot {
        TIME,
        TEXT,
        NURSE,
        SIGNATURE
    }

    public static Rectangle getFieldRect(Rectangle pageSize, int rowIndex, FieldSlot slot) {
        if (rowIndex < 1) {
            throw new IllegalArgumentException("Row index must be >= 1");
        }
        float rowTop = rowTop(pageSize, rowIndex);
        float rowBottom = rowTop - ROW_HEIGHT;
        float timeLeft = pageSize.getLeft() + MARGIN;
        float textLeft = timeLeft + TIME_WIDTH + COLUMN_GAP;
        float nurseLeft = textLeft + TEXT_WIDTH + COLUMN_GAP;
        float sigLeft = nurseLeft + NURSE_WIDTH + COLUMN_GAP;
        switch (slot) {
            case TIME:
                return new Rectangle(timeLeft, centerVertical(rowBottom), TIME_WIDTH, FIELD_HEIGHT);
            case TEXT:
                return new Rectangle(textLeft, rowBottom + FIELD_PADDING, TEXT_WIDTH, ROW_HEIGHT - 2 * FIELD_PADDING);
            case NURSE:
                return new Rectangle(nurseLeft, centerVertical(rowBottom), NURSE_WIDTH, FIELD_HEIGHT);
            case SIGNATURE:
                return new Rectangle(sigLeft, rowBottom + FIELD_PADDING / 2, SIGNATURE_WIDTH, ROW_HEIGHT - FIELD_PADDING);
            default:
                throw new IllegalArgumentException("Unsupported slot: " + slot);
        }
    }

    public static Rectangle getRowBox(Rectangle pageSize, int rowIndex) {
        float rowTop = rowTop(pageSize, rowIndex);
        float rowBottom = rowTop - ROW_HEIGHT;
        float width = pageSize.getWidth() - 2 * MARGIN;
        return new Rectangle(pageSize.getLeft() + MARGIN, rowBottom, width, ROW_HEIGHT);
    }

    public static Rectangle getSignatureAppearanceRect(Rectangle pageSize, int rowIndex) {
        return getFieldRect(pageSize, rowIndex, FieldSlot.SIGNATURE);
    }

    private static float rowTop(Rectangle pageSize, int rowIndex) {
        return pageSize.getTop() - TOP_MARGIN - (rowIndex - 1) * ROW_HEIGHT;
    }

    private static float centerVertical(float rowBottom) {
        return rowBottom + (ROW_HEIGHT - FIELD_HEIGHT) / 2f;
    }
}
