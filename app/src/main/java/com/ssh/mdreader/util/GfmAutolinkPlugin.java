package com.ssh.mdreader.util;

import androidx.annotation.NonNull;

import java.util.Collections;

import io.noties.markwon.AbstractMarkwonPlugin;

import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.parser.Parser;

/**
 * GFM 自动链接插件（子集：带 scheme 的裸 URL + 邮箱）。
 *
 * <p><b>背景（路线图 GFM 补齐，2026-10-07）</b>：Markdown 阅读器对裸 URL
 * （「看 https://example.com 的说明」）零链接——只有 {@code [x](y)} 显式链接可点；
 * 竞品 markor（flexmark ext-autolink）为 GFM 子集标配。Markwon 官方<b>无</b>
 * {@code ext-autolink} 模块（Maven Central 实证：markwon 组仅有 ext-strikethrough/
 * ext-tables/ext-tasklist/linkify 等），等效方案=commonmark 官方
 * {@code commonmark-ext-autolink}，本插件仅注册该解析器扩展——自动链接产出的是
 * 标准 {@code Link} 节点，渲染/点击走既有 {@link LinkTargetHelper} 通路（与
 * 显式链接同源，阅读器 LinkTapListener 已接线），零新增渲染逻辑。</p>
 *
 * <p><b>行为实证（JVM 探针 2026-10-07 + 字节码 2026-10-10，commonmark
 * {@code 0.13.0} + {@code AutolinkExtension}）</b>：{@code http://}/{@code https://}/
 * {@code ftp://} 裸 URL 与邮箱自动生成 Link（邮箱加 {@code mailto:} 前缀）；
 * <b>{@code www.} 裸域由官方扩展缺失、本插件 via
 * {@link WwwAutolinkPostProcessor} 补齐</b>（官方 0.13.0 的
 * {@code AutolinkPostProcessor} 构造器仅启用 {@code EnumSet.of(URL, EMAIL)}，
 * javap 字节码实证——底层 nibor autolink 0.10.0 库支持 {@code WWW} 类型但未启用；
 * 规则实现见 {@link WwwAutolinkHelper}，与 GFM spec 对齐）；行内代码/围栏代码块内
 * 不链接；已有 Link 内文本不重复链接；数字/版本串（{@code 123.456.789}、
 * {@code host=1.2.3.4 port=22}）不误链。</p>
 *
 * <p><b>注册顺序约束（重要）</b>：本插件必须注册在 {@code TaskListPlugin}
 * <b>之后</b>——autolink 与任务清单同为解析后置处理器
 * （{@code Parser} 按注册顺序串行 process，commonmark {@code Parser.java:106} 实证）。
 * 若 autolink 先跑，「{@code - [ ] http://x.com}」行的 URL 会把任务行 Text 节点提前
 * 拆成 {@code Text + Link} 兄弟节点，任务清单正则 {@code ^\[([xX\s])\]\s+(.*)}
 * 的 {@code matches()} 语义与后续 {@code moveChildren} 会把 URL 移出段落（渲染错行、
 * 阻断零宽标记定位）。任务清单先跑则 URL 留在任务段 Text 内，autolink 再拆为
 * {@code Text(标记) + Link} 同段落=正常。</p>
 *
 * <p>删除线为 {@code ~~text~~}（{@code io.noties.markwon.ext.strikethrough.StrikethroughPlugin}），
 * 属 delimiter 解析期（非后置处理器），与 autolink 无顺序冲突：
 * {@code ~~http://x.com~~} 产出 Strikethrough({@code Link})，删除线+可点链接共存。</p>
 */
public final class GfmAutolinkPlugin extends AbstractMarkwonPlugin {

    private GfmAutolinkPlugin() {
    }

    @NonNull
    public static GfmAutolinkPlugin create() {
        return new GfmAutolinkPlugin();
    }

    @Override
    public void configureParser(@NonNull Parser.Builder builder) {
        builder.extensions(Collections.singletonList(AutolinkExtension.create()));
        // 官方 0.13.0 AutolinkExtension 只启用 URL/EMAIL（javap 实证），www 裸域
        // 由本插件补齐（GFM 规则见 WwwAutolinkHelper；顺序与 AutolinkExtension 无交叉：
        // 官方不碰 www，本层 inLink 守卫防嵌套）。
        builder.postProcessor(new WwwAutolinkPostProcessor());
    }
}
