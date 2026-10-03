package com.ssh.mdreader.util;

/**
 * 连接复制（#28）纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p>语义：复制 = 以现有配置为新连接表单预填（改端口/用户/别名后另存，
 * 不覆盖原连接）。本类负责复制时的默认新别名派生——对齐竞品 ConnectBot
 * {@code host_duplicate_nickname}（“%1$s (copy)”）与 Termius/JuiceSSH 的
 * duplicate/clone 语义（克隆后自动带上副本标识，便于区分）。
 *
 * <p>重复复制语义：别名已带副本标识时继续追加（与 ConnectBot “x (copy) (copy)”
 * 行为一致）——不做去重/递增计数，用户可在表单中自行改名。
 */
public final class ConnectionCopyHelper {

    private ConnectionCopyHelper() {
    }

    /** 副本标识后缀（全角括号，与项目既有中文文案风格一致）。 */
    public static final String DUPLICATE_SUFFIX = "（副本）";

    /**
     * 派生复制连接的新别名（纯函数）：null/空白 → 空串（无别名，与原配置一致）；
     * 非空 → 去首尾空白后追加 {@link #DUPLICATE_SUFFIX}。
     */
    public static String duplicateName(String alias) {
        if (alias == null) return "";
        String trimmed = alias.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed + DUPLICATE_SUFFIX;
    }
}
