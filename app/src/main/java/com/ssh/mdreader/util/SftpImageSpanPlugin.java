package com.ssh.mdreader.util;

import org.commonmark.node.Image;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.MarkwonSpansFactory;
import io.noties.markwon.RenderProps;
import io.noties.markwon.SpanFactory;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.AsyncDrawableSpan;
import io.noties.markwon.image.ImageProps;
import io.noties.markwon.image.ImageSize;

/**
 * 覆盖 Markwon {@code Image} 节点的 span factory（路线图 #9）：在渲染时把
 * 远端相对/绝对路径图片 destination 改写为 {@code markdown-sftp:} 前缀
 * （见 {@link ImageTargetHelper}），其余（http/https/data 等）保持原样。
 *
 * <p><b>为什么必须覆盖</b>：Markwon 的 {@code ImagesPlugin} 只对<b>带 scheme</b>
 * 的 destination 注册 handler（默认 {@code data:}/{@code http(s):}），无 scheme 的
 * 路径会导致 {@code No scheme-handler is found} 而不显示；本 plugin 在 span 生成
 * 时改写 destination，让 <b>{@link SftpImageSchemeHandler}</b> 接管远端图片——不改
 * 源文本、不影响 {@code [x](p)} 链接 span（本 factory 只针对 {@link Image} 节点）。</p>
 *
 * <p><b>使用顺序</b>：必须注册在 {@code ImagesPlugin} <b>之后</b>（
 * {@code MarkwonSpansFactory.Builder} 的 {@code setFactory} 为 map put，后注册覆盖
 * 先注册同名节点 factory）。</p>
 */
public final class SftpImageSpanPlugin extends AbstractMarkwonPlugin {

    /** 当前文档路径提供者（渲染时读取，每次 setMarkdown 取最新值）。 */
    public interface PathProvider {
        /** 当前 Markdown 文件的远端绝对路径；null=未知（此时仅绝对路径图片可解析）。 */
        String currentFilePath();
    }

    private final PathProvider provider;

    private SftpImageSpanPlugin(PathProvider provider) {
        this.provider = provider;
    }

    public static SftpImageSpanPlugin create(PathProvider provider) {
        return new SftpImageSpanPlugin(provider);
    }

    @Override
    public void configureSpansFactory(MarkwonSpansFactory.Builder builder) {
        builder.setFactory(Image.class, new SpanFactory() {
            @Override
            public Object getSpans(MarkwonConfiguration config, RenderProps props) {
                String dest = props.get(ImageProps.DESTINATION);
                // 改写（null=保持原样）；provider 为 null 时容错为无基座解析
                String rewritten = ImageTargetHelper.buildSftpDestination(
                        dest, provider != null ? provider.currentFilePath() : null);
                String effective = rewritten != null ? rewritten : dest;
                ImageSize size = props.get(ImageProps.IMAGE_SIZE);
                boolean replacementTextIsLink = props.get(
                        ImageProps.REPLACEMENT_TEXT_IS_LINK, Boolean.FALSE);
                AsyncDrawable drawable = new AsyncDrawable(effective,
                        config.asyncDrawableLoader(), config.imageSizeResolver(), size);
                return new AsyncDrawableSpan(config.theme(), drawable,
                        AsyncDrawableSpan.ALIGN_BOTTOM, replacementTextIsLink);
            }
        });
    }
}
