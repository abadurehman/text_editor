/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package android.core.text;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.ParagraphStyle;

/**
 * A BoringLayout is a very simple Layout implementation for text that
 * fits on a single line and is all left-to-right characters.
 * You will probably never want to make one of these yourself;
 * if you do, be sure to call {@link #isBoring} first to make sure
 * the text meets the criteria.
 * <p>This class is used by widgets to control text layout. You should not need
 * to use this class directly unless you are implementing your own widget
 * or custom display object, in which case
 * you are encouraged to use a Layout instead of calling
 * {@link android.graphics.Canvas#drawText(CharSequence, int, int, float, float, android.graphics.Paint)
 * Canvas.drawText()} directly.</p>
 */
public class BoringLayout extends Layout implements TextUtils.EllipsizeCallback {
    private static final char FIRST_RIGHT_TO_LEFT = '\u0590';

//    public static BoringLayout make(CharSequence source,
//                        TextPaint paint, int outerwidth,
//                        Alignment align,
//                        float spacingmult, float spacingadd,
//                        Metrics metrics, boolean includepad/*,
//                        android.text.TextUtils.TruncateAt ellipsize, int ellipsizedWidth*/) {
//        return new BoringLayout(source, paint, outerwidth, align,
//                                spacingmult, spacingadd, metrics,
//                                includepad, ellipsize, ellipsizedWidth);
//    }
    private static final TextPaint sTemp =
            new TextPaint();
    /* package */ int mBottom, mDesc;   // for Direct

//    public BoringLayout(CharSequence source,
//                        TextPaint paint, int outerwidth,
//                        Alignment align,
//                        float spacingmult, float spacingadd,
//                        Metrics metrics, boolean includepad,
//                        android.text.TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
//        /*
//         * It is silly to have to call super() and then replaceWith(),
//         * but we can't use "this" for the callback until the call to
//         * super() finishes.
//         */
//        super(source, paint, outerwidth, align, spacingmult, spacingadd);
//
//        boolean trust;
//
//        if (ellipsize == null || ellipsize == android.text.TextUtils.TruncateAt.MARQUEE) {
//            mEllipsizedWidth = outerwidth;
//            mEllipsizedStart = 0;
//            mEllipsizedCount = 0;
//            trust = true;
//        } else {
//            replaceWith(android.text.TextUtils.ellipsize(source, paint, ellipsizedWidth,
//                            ellipsize, true, this),
//                        paint, outerwidth, align, spacingmult,
//                        spacingadd);
//
//
//            mEllipsizedWidth = ellipsizedWidth;
//            trust = false;
//        }
//
//        init(getText(), paint, outerwidth, align, spacingmult, spacingadd,
//             metrics, includepad, trust);
//    }
    private String mDirect;
    private Paint mPaint;
    private int mTopPadding, mBottomPadding;
    private float mMax;
    private int mEllipsizedWidth, mEllipsizedStart, mEllipsizedCount;

    /**
     * Returns a BoringLayout for the specified text, potentially reusing
     * this one if it is already suitable.  The caller must make sure that
     * no one is still using this Layout.
     */
//    public BoringLayout replaceOrMake(CharSequence source, TextPaint paint,
//                                      int outerwidth, Alignment align,
//                                      float spacingmult, float spacingadd,
//                                      Metrics metrics,
//                                      boolean includepad,
//                                      android.text.TextUtils.TruncateAt ellipsize,
//                                      int ellipsizedWidth) {
//        boolean trust;
//
//        if (ellipsize == null || ellipsize == android.text.TextUtils.TruncateAt.MARQUEE) {
//            replaceWith(source, paint, outerwidth, align, spacingmult,
//                        spacingadd);
//
//            mEllipsizedWidth = outerwidth;
//            mEllipsizedStart = 0;
//            mEllipsizedCount = 0;
//            trust = true;
//        } else {
//            replaceWith(android.text.TextUtils.ellipsize(source, paint, ellipsizedWidth,
//                            ellipsize, true, this),
//                        paint, outerwidth, align, spacingmult,
//                        spacingadd);
//
//            mEllipsizedWidth = ellipsizedWidth;
//            trust = false;
//        }
//
//        init(getText(), paint, outerwidth, align, spacingmult, spacingadd,
//             metrics, includepad, trust);
//        return this;
//    }
    public BoringLayout(LayoutContext layoutContext, CharSequence source,
                        TextPaint paint, int outerwidth,
                        Alignment align,
                        float spacingmult, float spacingadd,
                        Metrics metrics, boolean includepad) {
        super(layoutContext, source, paint, outerwidth, align, spacingmult, spacingadd);

        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;

        init(layoutContext, source, paint, outerwidth, align, spacingmult, spacingadd,
                metrics, includepad, true);
    }

    public static BoringLayout make(LayoutContext layoutContext, CharSequence source,
                                    TextPaint paint, int outerwidth,
                                    Alignment align,
                                    float spacingmult, float spacingadd,
                                    Metrics metrics, boolean includepad) {
        return new BoringLayout(layoutContext, source, paint, outerwidth, align,
                spacingmult, spacingadd, metrics,
                includepad);
    }

    /**
     * Returns null if not boring; the width, ascent, and descent if boring.
     */
    public static Metrics isBoring(LayoutContext layoutContext, CharSequence text,
                                   TextPaint paint) {
        return isBoring(layoutContext, text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, null);
    }

    /**
     * Returns null if not boring; the width, ascent, and descent if boring.
     *
     * @hide
     */
    public static Metrics isBoring(LayoutContext layoutContext, CharSequence text,
                                   TextPaint paint,
                                   TextDirectionHeuristic textDir) {
        return isBoring(layoutContext, text, paint, textDir, null);
    }

    /**
     * Returns null if not boring; the width, ascent, and descent in the
     * provided Metrics object (or a new one if the provided one was null)
     * if boring.
     */
    public static Metrics isBoring(LayoutContext layoutContext, CharSequence text, TextPaint paint, Metrics metrics) {
        return isBoring(layoutContext, text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, metrics);
    }

    /**
     * Returns null if not boring; the width, ascent, and descent in the
     * provided Metrics object (or a new one if the provided one was null)
     * if boring.
     *
     * @hide
     */
    public static Metrics isBoring(LayoutContext layoutContext, CharSequence text, TextPaint paint,
                                   TextDirectionHeuristic textDir, Metrics metrics) {
        char[] temp = TextUtils.obtain(500);
        int length = text.length();
        boolean boring = true;

        outer:
        for (int i = 0; i < length; i += 500) {
            int j = i + 500;

            if (j > length)
                j = length;

            TextUtils.getChars(text, i, j, temp, 0);

            int n = j - i;

            for (int a = 0; a < n; a++) {
                char c = temp[a];

                if (c == '\n' || c == '\t' || c >= FIRST_RIGHT_TO_LEFT) {
                    boring = false;
                    break outer;
                }
            }

            if (textDir != null && textDir.isRtl(temp, 0, n)) {
                boring = false;
                break outer;
            }
        }

        TextUtils.recycle(temp);

        if (boring && text instanceof Spanned) {
            Spanned sp = (Spanned) text;
            Object[] styles = sp.getSpans(0, length, ParagraphStyle.class);
            if (styles.length > 0) {
                boring = false;
            }
        }

        if (boring) {
            Metrics fm = metrics;
            if (fm == null) {
                fm = new Metrics();
            }

            TextLine line = TextLine.obtain();
            line.set(layoutContext, paint, text, 0, length, DIR_LEFT_TO_RIGHT,
                    DIRS_ALL_LEFT_TO_RIGHT, false, null);
            fm.width = (int) Math.ceil(line.metrics(fm));
            TextLine.recycle(line);

            return fm;
        } else {
            return null;
        }
    }

    /**
     * Returns a BoringLayout for the specified text, potentially reusing
     * this one if it is already suitable.  The caller must make sure that
     * no one is still using this Layout.
     */
    public BoringLayout replaceOrMake(LayoutContext layoutContext, CharSequence source, TextPaint paint,
                                      int outerwidth, Alignment align,
                                      float spacingmult, float spacingadd,
                                      Metrics metrics,
                                      boolean includepad) {
        replaceWith(source, paint, outerwidth, align, spacingmult,
                spacingadd);

        mEllipsizedWidth = outerwidth;
        mEllipsizedStart = 0;
        mEllipsizedCount = 0;

        init(layoutContext, source, paint, outerwidth, align, spacingmult, spacingadd,
                metrics, includepad, true);
        return this;
    }

    /* package */ void init(LayoutContext layoutContext, CharSequence source,
                            TextPaint paint, int outerwidth,
                            Alignment align,
                            float spacingmult, float spacingadd,
                            Metrics metrics, boolean includepad,
                            boolean trustWidth) {
        int spacing;

        if (source instanceof String && align == Alignment.ALIGN_NORMAL) {
            mDirect = source.toString();
        } else {
            mDirect = null;
        }

        mPaint = paint;

        if (includepad) {
            spacing = metrics.bottom - metrics.top;
        } else {
            spacing = metrics.descent - metrics.ascent;
        }

        mBottom = spacing;

        if (includepad) {
            mDesc = spacing + metrics.top;
        } else {
            mDesc = spacing + metrics.ascent;
        }

        if (trustWidth) {
            mMax = metrics.width;
        } else {
            /*
             * If we have ellipsized, we have to actually calculate the
             * width because the width that was passed in was for the
             * full text, not the ellipsized form.
             */
            TextLine line = TextLine.obtain();
            line.set(layoutContext, paint, source, 0, source.length(), DIR_LEFT_TO_RIGHT,
                    DIRS_ALL_LEFT_TO_RIGHT, false, null);
            mMax = (int) Math.ceil(line.metrics(null));
            TextLine.recycle(line);
        }

        if (includepad) {
            mTopPadding = metrics.top - metrics.ascent;
            mBottomPadding = metrics.bottom - metrics.descent;
        }
    }

    @Override
    public int getHeight() {
        return mBottom;
    }

    @Override
    public int getLineCount() {
        return 1;
    }

    @Override
    public int getLineTop(int line) {
        if (line == 0)
            return 0;
        else
            return mBottom;
    }

    @Override
    public int getLineDescent(int line) {
        return mDesc;
    }

    @Override
    public int getLineStart(int line) {
        if (line == 0)
            return 0;
        else
            return getText().length();
    }

    @Override
    public int getParagraphDirection(int line) {
        return DIR_LEFT_TO_RIGHT;
    }

    @Override
    public boolean getLineContainsTab(int line) {
        return false;
    }

    @Override
    public boolean isRealNewLine(int line) {
        return false;
    }

    @Override
    public int getRealLine(int line) {
        return 0;
    }

    @Override
    public int realLineToVirtualLine(int line) {
        return -1;
    }

    @Override
    public float getLineMax(int line) {
        return mMax;
    }

    @Override
    public final Directions getLineDirections(int line) {
        return DIRS_ALL_LEFT_TO_RIGHT;
    }

    @Override
    public int getTopPadding() {
        return mTopPadding;
    }

    @Override
    public int getBottomPadding() {
        return mBottomPadding;
    }

    @Override
    public int getEllipsisCount(int line) {
        return mEllipsizedCount;
    }

    @Override
    public int getEllipsisStart(int line) {
        return mEllipsizedStart;
    }

    @Override
    public int getEllipsizedWidth() {
        return mEllipsizedWidth;
    }

    // Override draw so it will be faster.
    @Override
    public void draw(Canvas c, Path highlight, Paint highlightpaint,
                     int cursorOffset) {
        if (mDirect != null && highlight == null) {
            c.drawText(mDirect, 0, mBottom - mDesc, mPaint);
        } else {
            super.draw(c, highlight, highlightpaint, cursorOffset);
        }
    }

    /**
     * Callback for the ellipsizer to report what region it ellipsized.
     */
    public void ellipsized(int start, int end) {
        mEllipsizedStart = start;
        mEllipsizedCount = end - start;
    }

    public static class Metrics extends Paint.FontMetricsInt {
        public int width;

        @Override
        public String toString() {
            return super.toString() + " width=" + width;
        }
    }
}
