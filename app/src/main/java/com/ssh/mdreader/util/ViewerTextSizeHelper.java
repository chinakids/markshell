package com.ssh.mdreader.util;

/**
 * 查看器缩放字号纯函数层（零 Android 依赖，JVM 可测）。
 *
 * <p>走查命中（能力发现第二十七轮）：CodeViewerActivity / TextViewerActivity 双指缩放
 * 均硬编码 {@code currentTextSize = 14f} 且无任何持久化——每次打开查看器/换文件字号
 * 重置为默认，而 Markdown 阅读器缩放字号经 {@link PreferenceManager#saveFontSize}
 * 持久化（KEY_FONT_SIZE）=同类能力不一致。竞品实证（markor AppSettings.java，
 * gsantner/markor master）：查看器/编辑器字号是持久化状态——{@code pref_key__view_font_size}
 * 全局设置项（默认 -1 回退编辑器字号）+ {@code PREF_PREFIX_VIEW_FONT_SIZE + path}
 * 按文档覆盖（set/getDocumentViewFontSize）与 {@code PREF_PREFIX_FONT_SIZE + path}
 * 编辑器按文档字号（set/getDocumentFontSize）——字号绝非 per-session 状态。</p>
 *
 * <p>本类负责 clamp 与合法性判定（单一语义源：行号槽与正文双 TextView 共用同一 clamp，
 * 防 8/32 边界两处漂移）；持久化键与读写见 {@link PreferenceManager}
 * （{@code getViewerTextSize}/{@code saveViewerTextSize}，全局一键=markor
 * {@code pref_key__view_font_size} 等价物；按文档 per-path 覆盖为后续观察项）。</p>
 */
public final class ViewerTextSizeHelper {

    /** 默认字号（sp）：与历史行为一致（两查看器原硬编码 14f）。 */
    public static final float DEFAULT_TEXT_SIZE = 14f;
    /** 最小字号（sp）：与两查看器原 MIN_TEXT_SIZE 一致。 */
    public static final float MIN_TEXT_SIZE = 8f;
    /** 最大字号（sp）：与两查看器原 MAX_TEXT_SIZE 一致。 */
    public static final float MAX_TEXT_SIZE = 32f;
    /** 缩放变化应用阈值：与原实现 {@code Math.abs(newSize - currentTextSize) > 0.5f} 一致。 */
    public static final float APPLY_MIN_DELTA = 0.5f;

    private ViewerTextSizeHelper() {
    }

    /** 字号是否在合法区间 [MIN, MAX]。 */
    public static boolean isValid(float size) {
        return size >= MIN_TEXT_SIZE && size <= MAX_TEXT_SIZE;
    }

    /**
     * 夹取字号到 [MIN_TEXT_SIZE, MAX_TEXT_SIZE]；NaN/非法输入回退默认字号。
     * 两查看器（正文/行号槽同步用）与本层共用=单一语义源。
     */
    public static float clamp(float size) {
        if (Float.isNaN(size)) {
            return DEFAULT_TEXT_SIZE;
        }
        return Math.max(MIN_TEXT_SIZE, Math.min(MAX_TEXT_SIZE, size));
    }

    /** 缩放是否达到应用阈值（防抖动）；与原实现 {@code > 0.5f} 严格语义一致。 */
    public static boolean shouldApply(float from, float to) {
        return Math.abs(to - from) > APPLY_MIN_DELTA;
    }
}
