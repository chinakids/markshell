package com.ssh.mdreader.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.ssh.mdreader.util.LineNumberHelper;

/**
 * 文本查看器行号槽（能力发现循环 #18）。
 *
 * <p>与内容 TextView 同槽（同一 ScrollView 子 LinearLayout 内）整体滚动；按内容
 * {@link Layout} 基线绘制行号——仅在每一逻辑行首视觉行绘制（长行换行时行号仍对齐
 * 逻辑行行首，markor {@code LineNumbersView} 同型实证：逻辑行起点判定=
 * {@code text.charAt(layout.getLineStart(i)-1)=='\n'}），字体/字号/行高实时从目标
 * TextView 读取，与内容逐像素对齐（无 wrap 错位问题——列式行号仅在无换行时成立）。
 * </p>
 *
 * <p>宽度=位数字符宽+左右留白（随内容行数自动重测）；内容/字号变化后由宿主调用
 * {@link #refresh()} 触发重测+重绘。纯行数/位数计算在 {@link LineNumberHelper}（JVM 可测）。
 * </p>
 */
public class LineNumberGutterView extends View {

    private TextView target;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int maxLineCount = 1;
    private static final int PADDING_DP = 8;
    private static final int MIN_WIDTH_DP = 36;

    public LineNumberGutterView(Context context) {
        super(context);
    }

    public LineNumberGutterView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public LineNumberGutterView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /** 绑定目标内容 TextView（一次）；并立刻按其当前文本刷新行数。 */
    public void attach(TextView view) {
        target = view;
        refresh();
    }

    /** 内容/字号变化后调用：重算行数 → 宽度随位数重测 → 重绘。 */
    public void refresh() {
        maxLineCount = LineNumberHelper.countLines(target != null ? target.getText() : null);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float textSize = 14f * getResources().getDisplayMetrics().scaledDensity;
        if (target != null) {
            textSize = target.getTextSize();
        }
        paint.setTextSize(textSize);
        if (target != null) {
            paint.setTypeface(target.getTypeface());
        }
        int digits = LineNumberHelper.digits(maxLineCount);
        float charWidth = paint.measureText("0");
        int width = (int) Math.max(dp(MIN_WIDTH_DP), dp(PADDING_DP) + digits * charWidth + dp(PADDING_DP));
        // 高度交给父级：match_parent 时 LinearLayout 二次测量以 EXACTLY(内容高) 传入
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(0, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (target == null) {
            return;
        }
        Layout layout = target.getLayout();
        if (layout == null) {
            return;
        }
        CharSequence text = target.getText();
        paint.setColor(0xFF666666);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(target.getTextSize());
        paint.setTypeface(target.getTypeface());
        int x = getWidth() - dp(PADDING_DP);
        int padTop = target.getPaddingTop();
        int number = 0;
        int lineCount = layout.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            boolean logicalStart = i == 0 || text.charAt(layout.getLineStart(i) - 1) == '\n';
            if (logicalStart) {
                number++;
                canvas.drawText(String.valueOf(number), x, layout.getLineBaseline(i) + padTop, paint);
                if (number >= maxLineCount) {
                    break;
                }
            }
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
