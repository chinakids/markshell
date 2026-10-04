package com.ssh.mdreader.util;

/**
 * 大文件打开护栏的纯函数层（零 Android 依赖，JVM 可测）。
 *
 * <p>走查命中（能力发现第二十三轮）：FileBrowser 各文件打开入口（列表点击/历史/全局搜索/
 * 前往路径）均无任何大小检查，而文本加载路径（Text/Code/CSV 查看器、Markdown 阅读器、
 * 两栏预览）都是 {@code SshManager.readFile} 全量读入 + TextView 全量渲染——远端大日志/
 * 备份/dump（几十 MB）点击即全量加载，存在卡顿/ANR/内存风险且用户无任何预知（无法绕过）。
 *
 * <p>竞品实证（markor MainActivity 源码 385-394 行）：打开文件前
 * {@code file.length() &gt; LARGE_FILE_TOAST_THRESHOLD_BYTES}（128L * 1024L = 128KB）→
 * {@code Toast LENGTH_LONG}「打开大文件(%s)，这可能需要一点时间」（非阻断提示）。</p>
 *
 * <p>本类实现护栏的判定与文案：</p>
 * <ul>
 *   <li>{@link #isLargeFile(long)}：大小超过阈值 → true；大小未知（&lt;=0）→ false
 *       （未知不提示，path-only 入口（历史/链接）行为零变化，护栏保守化）；</li>
 *   <li>阈值取舍：markor 128KB 是本地便签编辑器场景（本项目 1MB 配置文件也弹=过度打扰），
 *       4MB 为「有明显卡顿风险」的文本加载护栏阈值（本卡产品决策落档，非竞品键值）；</li>
 *   <li>{@link #buildConfirmMessage(long)}：确认对话框文案，可读大小委托
 *       {@link DownloadHelper#formatBytes(long)}（单一语义源，与列表/属性显示一致）。</li>
 * </ul>
 */
public final class LargeFileHelper {

    /** 文本文件加载护栏阈值：4MB（严格大于才提示）。 */
    public static final long LARGE_FILE_THRESHOLD_BYTES = 4L * 1024 * 1024;

    private LargeFileHelper() {
    }

    /** 大小是否触发大文件护栏；未知（&lt;=0）不提示（保守放行）。 */
    public static boolean isLargeFile(long size) {
        return size > LARGE_FILE_THRESHOLD_BYTES;
    }

    /** 大文件确认对话框文案：可读大小 + 卡顿/内存说明。 */
    public static String buildConfirmMessage(long size) {
        return "文件较大（" + DownloadHelper.formatBytes(size) + "）\n"
                + "完全加载到查看器可能会卡顿并占用较多内存。\n"
                + "仍要打开吗？";
    }
}
