package com.ssh.mdreader.util;

import androidx.annotation.NonNull;

import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Link;
import org.commonmark.node.Node;
import org.commonmark.node.Text;
import org.commonmark.parser.PostProcessor;

import java.util.List;

/**
 * GFM {@code www.} 裸域自动链后处理器（commonmark 解析后置阶段）。
 *
 * <p><b>背景（迭代110，2026-10-10）</b>：官方 commonmark-ext-autolink 0.13.0 仅
 * 启用 URL/EMAIL 两类（见 {@link WwwAutolinkHelper} 背景），本处理器补齐 WWW：
 * 对每个 {@link Text} 节点按 {@link WwwAutolinkHelper#findLinks} 规则拆分——
 * 匹配区间前/后保留原 {@code Text}，匹配区间生成 {@code Link}（目标
 * {@code http://}+原文，显示文本=原文，与 GFM 一致）——节点拆分与官方
 * {@code AutolinkPostProcessor} 同构（{@code insertAfter} 顺序插入后
 * {@code unlink} 原节点，本地 javap 字节码实证）。</p>
 *
 * <p><b>守卫</b>：{@code inLink} 计数跳过已存在 Link 的文本（不嵌套链接，同官方）；
 * Code/FencedCodeBlock/Image 等非 Text 节点天然不处理；行内代码内 {@code www.}
 * 不误链（code 节点不是 Text）。</p>
 *
 * <p><b>与任务清单/大纲零宽标记共存</b>：本处理与 {@link TaskCheckboxHelper}/
 * {@link TocHelper} 同为渲染前流程，标记字符 U+200B/U+200C 在解析后仍留在 Text
 * 内；{@link WwwAutolinkHelper#findLinks} 的路径收集已显式排除两标记字符，拆分
 * 时标记所在区间保持原位（Text 片段），行定位解码不受影响。</p>
 */
public final class WwwAutolinkPostProcessor implements PostProcessor {

    @NonNull
    @Override
    public Node process(@NonNull Node node) {
        node.accept(new WwwVisitor());
        return node;
    }

    private static final class WwwVisitor extends AbstractVisitor {

        /** 已进入的 Link 嵌套深度（>0 时不再处理 Text，防嵌套链接）。 */
        private int inLink = 0;

        @Override
        public void visit(@NonNull Link link) {
            inLink++;
            super.visit(link);
            inLink--;
        }

        @Override
        public void visit(@NonNull Text text) {
            if (inLink > 0) {
                return;
            }
            String literal = text.getLiteral();
            List<WwwAutolinkHelper.WwwLink> links = WwwAutolinkHelper.findLinks(literal);
            if (links.isEmpty()) {
                return;
            }
            // 与官方 AutolinkPostProcessor 同构的拆分：按序 insertAfter 新节点，最后摘下原文
            Node prev = text;
            int cursor = 0;
            for (WwwAutolinkHelper.WwwLink link : links) {
                if (link.begin > cursor) {
                    Text lead = new Text(literal.substring(cursor, link.begin));
                    prev.insertAfter(lead);
                    prev = lead;
                }
                Link linkNode = new Link(link.target, null);
                linkNode.appendChild(new Text(literal.substring(link.begin, link.end)));
                prev.insertAfter(linkNode);
                prev = linkNode;
                cursor = link.end;
            }
            if (cursor < literal.length()) {
                Text tail = new Text(literal.substring(cursor));
                prev.insertAfter(tail);
            }
            text.unlink();
        }
    }
}
