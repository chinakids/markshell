package com.ssh.mdreader.util;

/**
 * 纯函数层：远程路径复制（能力发现循环 #22 复制远程路径）。
 *
 * <p>剪贴板动作本身是 Android API，本类只承载「复制什么」与「提示什么」的
 * 纯文本逻辑，JVM 可测；各入口（文件/目录长按菜单、三个查看器工具栏菜单）
 * 调用 {@link #clipText} 后经 ClipboardManager 写入，并 toast {@link #toastText}。
 *
 * <p>语义决策：
 * <ul>
 *   <li>剪贴板内容=完整远端路径（原样，不 trim 不截断）——运维引用场景需要
 *       精确路径，任何改写都会产生错误信息代价；</li>
 *   <li>toast 文案带「已复制」前缀便于用户确认动作生效；</li>
 *   <li>null/空路径=没有可复制的对象，不走剪贴板（调用方先守卫）。</li>
 * </ul>
 */
public final class ClipPathHelper {

    private ClipPathHelper() {
    }

    /**
     * 要写入剪贴板的文本：完整路径；null/空 → {@code null}（调用方按「无需复制」处理）。
     */
    public static String clipText(String path) {
        if (path == null || path.isEmpty()) return null;
        return path;
    }

    /**
     * 复制后的用户提示文案；null/空路径返回 {@code null}（调用方可不提示）。
     */
    public static String toastText(String path) {
        if (path == null || path.isEmpty()) return null;
        return "已复制路径: " + path;
    }
}
