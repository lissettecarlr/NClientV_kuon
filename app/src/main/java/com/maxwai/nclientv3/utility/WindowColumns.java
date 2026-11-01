package com.maxwai.nclientv3.utility;

import android.content.Context;

import androidx.annotation.NonNull;

/**
 * Custom (fork only) feature: wide foldables.
 * <p>
 * The number of columns of the lists is a setting for "portrait" and for "landscape", which assumes
 * the usual phone widths. A foldable opened in portrait (about 630-700 dp wide) is neither a phone
 * nor a landscape phone, and two columns make cards that are twice as big as on a phone.
 * <p>
 * The setting is taken as the number of columns on the usual phone width, and more columns are
 * added when the window is wider, so the cards keep about the size the user chose. It never gives
 * fewer columns than the setting.
 */
public final class WindowColumns {
    /**
     * Width the column settings are made for: a usual phone, in portrait and in landscape
     */
    private static final int PORTRAIT_REFERENCE_DP = 411;
    private static final int LANDSCAPE_REFERENCE_DP = 891;
    /**
     * A window a little bit narrower than N times the reference still gets N times the columns
     */
    private static final float TOLERANCE = 0.1f;

    private WindowColumns() {
    }

    /**
     * @param columns   number of columns chosen by the user for this orientation, 0 means "not a grid"
     * @param landscape orientation the setting is for
     */
    public static int adjust(@NonNull Context context, int columns, boolean landscape) {
        if (columns <= 0) return columns;
        int widthDp = context.getResources().getConfiguration().screenWidthDp;
        if (widthDp <= 0) return columns;
        int reference = landscape ? LANDSCAPE_REFERENCE_DP : PORTRAIT_REFERENCE_DP;
        int scaled = (int) Math.floor(columns * (double) widthDp / reference + TOLERANCE);
        return Math.max(columns, scaled);
    }
}
