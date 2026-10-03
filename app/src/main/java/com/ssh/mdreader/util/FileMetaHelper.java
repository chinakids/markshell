package com.ssh.mdreader.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * 文件列表元信息格式化（纯函数层，无 Android 依赖，JVM 可测）。
 *
 * <p>RemoteFile.mtime 来自 SFTP attrs.getMTime()（Unix 秒，迭代12 为按修改时间排序而取），
 * 此前全仓无任何展示点——按修改时间排序却看不见时间。本类负责把 mtime 格式化为
 * 列表描述中的可读字符串：未知（&lt;=0，SFTP 未返回）显示占位符「—」，其余按
 * {@code yyyy-MM-dd HH:mm}（设备默认时区，全年份显式，含时分用于时效检查）。
 * 时区经参数注入以便单测固定黄金值（默认重载取 TimeZone.getDefault()）。</p>
 */
public final class FileMetaHelper {

    /** mtime 未知（SFTP attrs 未返回/为 0）的显示占位符。 */
    static final String UNKNOWN_TIME = "—";
    static final String TIME_PATTERN = "yyyy-MM-dd HH:mm";

    private FileMetaHelper() {
    }

    /** 按设备默认时区格式化 mtime（Unix 秒）。 */
    public static String formatMtime(long mtimeSeconds) {
        return formatMtime(mtimeSeconds, TimeZone.getDefault());
    }

    /** 按指定时区格式化 mtime（Unix 秒）；未知（&lt;=0）返回占位符。 */
    public static String formatMtime(long mtimeSeconds, TimeZone timeZone) {
        if (mtimeSeconds <= 0) {
            return UNKNOWN_TIME;
        }
        SimpleDateFormat fmt = new SimpleDateFormat(TIME_PATTERN, Locale.getDefault());
        fmt.setTimeZone(timeZone);
        return fmt.format(new Date(mtimeSeconds * 1000L));
    }
}
