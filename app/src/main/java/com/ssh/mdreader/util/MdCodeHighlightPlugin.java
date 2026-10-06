package com.ssh.mdreader.util;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import io.noties.markwon.syntax.Prism4jThemeDarkula;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;

/**
 * Markdown 代码块语法高亮接线封装（能力发现循环第 49 轮）。
 *
 * <p><b>背景（判据③/⑤）</b>：{@code io.noties.markwon:syntax-highlight} 依赖自 v1.0 已在
 * {@code build.gradle} 引入，但阅读器/两栏预览两处 {@code Markwon.builder} 均未注册
 * {@code SyntaxHighlightPlugin}=「引了依赖未接线」的半成品；同时代码查看器
 * ({@link CodeHighlighter}) 已用 Prism4j 高亮，Markdown 文档中的 {@code ```lang} 代码块
 * 却一律等宽灰字=同产品内同类能力不一致（运维文档/README 代码块是用户流正餐）。
 *
 * <p><b>机制（Markwon 4.6.2 源码实证）</b>：{@code CorePlugin} 渲染围栏代码块时调用
 * {@code configuration.syntaxHighlight().highlight(info, code)}（core-4.6.2
 * {@code CorePlugin.java:367}）；{@code SyntaxHighlightPlugin.create(Prism4j, Prism4jTheme)}
 * 经 {@code configureConfiguration} 注册该接口，并经 {@code configureTheme} 把代码块
 * 文字/背景色设为 theme 现值。{@code Prism4jSyntaxHighlight.highlight}：info 为 null
 * （无语言标注，如 LICENCE 代码块）不高亮、grammar 不存在原样返回=行为符合预期。
 *
 * <p><b>主题决策</b>：项目配色资源仅深色系一档（values 与 values-night 引用同一组
 * {@code md_theme_*} 深色值）——代码块恒用 {@code Prism4jThemeDarkula}，背景色复用
 * {@code colors.xml} 中 0 引用的 {@code markdown_code_bg}（#2A2A2A，作者遗留规划颜色，
 * 本处复活并与全 app 深色系一致）；若未来补浅色资源再按夜间模式切换主题（范围裁剪记录）。
 */
public final class MdCodeHighlightPlugin {

    private MdCodeHighlightPlugin() {
    }

    /**
     * 创建 Markdown 代码块高亮插件。
     *
     * @param codeBackgroundColor 代码块背景色（传 {@code R.color.markdown_code_bg}）
     * @return 已按当前视觉风格配置好的插件，两处 builder 共用=单一语义源
     */
    @NonNull
    public static SyntaxHighlightPlugin create(@ColorInt int codeBackgroundColor) {
        Prism4j prism4j = CodeHighlighter.getPrism4j();
        // Darkula：暗底浅字，与全 app 深色系资源一致；背景由调用方传入
        // （markdown_code_bg），文字色沿用主题默认 0xFFa9b7c6。
        return SyntaxHighlightPlugin.create(prism4j,
                Prism4jThemeDarkula.create(codeBackgroundColor));
    }
}
