package com.ssh.mdreader.util;

import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;

/**
 * 编辑器回车自动续行输入过滤器（能力发现 #50：走查实锤编辑域「按回车无任何续行/缩进」缺口；
 * markor 同款=HighlightingEditor AutoTextFormatter InputFilter 链）。
 *
 * <p>触发条件=插入序列首/末字符为换行（markor GsTextUtils.isNewLine 同型：按回车=单个
 * {@code \n}；粘贴多行文本也命中）。命中后调用 {@link MarkdownFormatHelper#newlineContinuation}
 * 计算续行内容并拼接在输入之后（完整替换输入段）；业务语义全部在纯函数层（列表续前缀/编号递增/
 * 缩进保留），本类只做 Android 接线，零业务逻辑。</p>
 */
public final class MarkdownAutoIndentFilter implements InputFilter {

    @Override
    public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
        if (start >= source.length() || dstart > dest.length()) return null;
        // markor isNewLine：输入段首/末字符为换行（顺序键=存在性证据；语义=源码 GsTextUtils）
        if (source.charAt(start) != '\n' && source.charAt(end - 1) != '\n') return null;
        String cont = MarkdownFormatHelper.newlineContinuation(dest.toString(), dstart);
        if (cont.isEmpty()) return null;
        return TextUtils.concat(source.subSequence(start, end), cont);
    }
}
