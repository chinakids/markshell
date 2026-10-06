package com.ssh.mdreader.util;

/**
 * 查看器「打开后跳到底部」决策纯函数层（零 android.* 依赖，JVM 可测）。
 *
 * <p>走查 #34：markor {@code pref_key__editor_start_editing_on_bottom}（AppSettings.java:367
 * {@code getBool(..., true)}=默认开启；preferences_master.xml:377-379 设置项 title「Start on bottom」、
 * summary「Upon loading a document, position cursor at its end」）——全局布尔设置+加载后定位末尾，
 * 属配置类（持久化设置+加载后标准滚动 API），按教训㊼/㊿a 从触发制观察转正实施。
 *
 * <p>本类只做「本次打开采用哪种起点」的决策；UI 层（Text/Code/CSV 查看器）负责按模式
 * 应用滚动/进度恢复。三种模式：
 * <ul>
 *   <li>{@link StartMode#SESSION_RESTORE}：旋转/进程重建恢复态——同会话滚动位置权威优先
 *       （走查 #40 既有口径，不受设置影响，旋转不清会话位置）；</li>
 *   <li>{@link StartMode#START_AT_BOTTOM}：全新打开且设置开启——加载后跳到底部，
 *       覆盖持久化阅读进度恢复（显式「尾读」模式优先，日志/转储场景）；</li>
 *   <li>{@link StartMode#DEFAULT}：既有行为——持久化阅读进度>顶部（未读）。
 *       关闭设置即回到该语义。</li>
 * </ul>
 */
public final class ViewerStartHelper {

    /** 命名与 {@link ViewerStartHelper#resolveStartMode} 返回值一一对应。 */
    public enum StartMode {
        /** 旋转/重建恢复态：按 onSaveInstanceState 的会话内滚动位置恢复。 */
        SESSION_RESTORE,
        /** 全新打开且「跳到底部」开启：加载后定位末尾。 */
        START_AT_BOTTOM,
        /** 全新打开且「跳到底部」关闭：持久化阅读进度>顶部（既有行为）。 */
        DEFAULT
    }

    private ViewerStartHelper() {
    }

    /**
     * 解析本次打开的起点模式。
     *
     * @param isRotationRestore 是否为旋转/进程重建恢复态（savedInstanceState != null）
     * @param startAtBottomEnabled 「打开后跳到底部」设置是否开启
     * @return 模式；旋转恢复态恒 {@link StartMode#SESSION_RESTORE}（同会话权威，见类注）
     */
    public static StartMode resolveStartMode(boolean isRotationRestore, boolean startAtBottomEnabled) {
        if (isRotationRestore) {
            return StartMode.SESSION_RESTORE;
        }
        if (startAtBottomEnabled) {
            return StartMode.START_AT_BOTTOM;
        }
        return StartMode.DEFAULT;
    }
}
