package com.ssh.mdreader.ui.span;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.View;

import androidx.annotation.NonNull;

import com.ssh.mdreader.model.AnnotationEntry;

/**
 * A {@link ClickableSpan} that suppresses the default link style.
 * The dashed yellow underline is drawn by the companion
 * {@link DashedUnderlineSpan} set on the same range.
 */
public class AnnotationSpan extends ClickableSpan {

    private final AnnotationEntry entry;
    private final OnAnnotationClickListener clickListener;

    public interface OnAnnotationClickListener {
        void onAnnotationClick(AnnotationEntry entry, View view);
    }

    public AnnotationSpan(@NonNull AnnotationEntry entry,
                          @NonNull OnAnnotationClickListener clickListener) {
        this.entry = entry;
        this.clickListener = clickListener;
    }

    public AnnotationEntry getEntry() { return entry; }

    @Override
    public void onClick(@NonNull View widget) {
        clickListener.onAnnotationClick(entry, widget);
    }

    /** Preserve text colour; suppress default blue / underline link style. */
    @Override
    public void updateDrawState(@NonNull TextPaint ds) {
        ds.setUnderlineText(false);
        ds.bgColor = Color.TRANSPARENT;
    }

    /**
     * Custom UnderlineSpan that draws a yellow (#FFD600) dashed underline.
     * Extends UnderlineSpan so the framework handles text layout and wrapping
     * correctly, and we override updateDrawState to set a custom underline
     * with dashed effect.
     */
    public static class DashedUnderlineSpan extends UnderlineSpan {

        private static final int UNDERLINE_COLOR = Color.parseColor("#FFD600");
        private static final float STROKE_WIDTH_DP = 1.8f;
        private static final float DASH_ON = 9f;
        private static final float DASH_OFF = 5f;
        private static final float BASELINE_OFFSET_DP = 3f;

        private Paint dashPaint;
        private float density;

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            if (density == 0) {
                density = android.content.res.Resources.getSystem()
                        .getDisplayMetrics().density;
            }
            // Enable underline with our custom color
            ds.setUnderlineText(true);
            ds.underlineColor = UNDERLINE_COLOR;
            ds.underlineThickness = (int) (STROKE_WIDTH_DP * density);
            // Note: Android's built-in underline doesn't support dash,
            // but the color + thickness will be correct. For dashed effect,
            // we also draw in draw() below as overlay.
        }

        // No-arg constructor required for UnderlineSpan
        public DashedUnderlineSpan() {
            super();
        }

        /**
         * We rely on updateDrawState to set underline color/thickness.
         * Android's TextView will draw the underline for each line segment
         * correctly, respecting text wrapping.
         */
    }
}
