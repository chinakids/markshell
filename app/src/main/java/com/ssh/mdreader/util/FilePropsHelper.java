package com.ssh.mdreader.util;

/**
 * 文件/目录属性信息格式化（纯函数层，无 Android 依赖，JVM 可测）。
 *
 * <p>RemoteFile.permissions 来自 SFTP attrs.getPermissions()（JSch 返回的完整 st_mode，
 * 含文件类型高位，与 FileOpsHelper.chmodFile 预填 {@code &amp; 0777} 同源说明）。
 * 本类负责把权限值格式化为 POSIX 符号串与八进制文本：符号串取权限位
 *（{@code &amp; 07777}：读/写/执行 9 位 + setuid/setgid/sticky 3 位），八进制按
 * Material Files file_properties_permission_mode_format「%1$s (%2$04o)」语义零填充 4 位。</p>
 */
public final class FilePropsHelper {

    /** 权限位掩码：rwx 9 位 + setuid/setgid/sticky 3 位（八进制 07777）。 */
    static final int MODE_MASK = 07777;

    private FilePropsHelper() {
    }

    /**
     * POSIX 符号权限串（9 字符，如 {@code rwxr-xr-x}）。setuid/setgid/sticky 与执行位组合
     * 按 {@code ls -l} 语义（有执行 s/t、无执行 S/T），与 Material Files toModeString 行为一致。
     * 权限值为负（异常输入）按 0 处理。
     */
    public static String formatMode(int permissions) {
        int m = permissions < 0 ? 0 : (permissions & MODE_MASK);
        char[] s = new char[9];
        s[0] = bit(m, 0400) ? 'r' : '-';
        s[1] = bit(m, 0200) ? 'w' : '-';
        if (bit(m, 0100)) {
            s[2] = bit(m, 04000) ? 's' : 'x';
        } else {
            s[2] = bit(m, 04000) ? 'S' : '-';
        }
        s[3] = bit(m, 040) ? 'r' : '-';
        s[4] = bit(m, 020) ? 'w' : '-';
        if (bit(m, 010)) {
            s[5] = bit(m, 02000) ? 's' : 'x';
        } else {
            s[5] = bit(m, 02000) ? 'S' : '-';
        }
        s[6] = bit(m, 04) ? 'r' : '-';
        s[7] = bit(m, 02) ? 'w' : '-';
        if (bit(m, 01)) {
            s[8] = bit(m, 01000) ? 't' : 'x';
        } else {
            s[8] = bit(m, 01000) ? 'T' : '-';
        }
        return new String(s);
    }

    /**
     * 符号串 + 四位零填充八进制，如 {@code rw-r--r-- (0644)}；
     * 与 Material Files file_properties_permission_mode_format 对齐。
     */
    public static String formatModeWithOctal(int permissions) {
        int m = permissions < 0 ? 0 : (permissions & MODE_MASK);
        return formatMode(permissions) + " (" + String.format("%04o", m) + ")";
    }

    private static boolean bit(int m, int flag) {
        return (m & flag) != 0;
    }
}
