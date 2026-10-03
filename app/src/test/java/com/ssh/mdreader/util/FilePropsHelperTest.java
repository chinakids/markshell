package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** FilePropsHelper 纯函数层单测：权限符号串/八进制格式化（JVM，无 Android 依赖）。 */
public class FilePropsHelperTest {

    // ── formatMode：常规九位 ──────────────────────────────────────────────────

    @Test
    public void formatMode_0644() {
        assertEquals("rw-r--r--", FilePropsHelper.formatMode(0644));
    }

    @Test
    public void formatMode_0755() {
        assertEquals("rwxr-xr-x", FilePropsHelper.formatMode(0755));
    }

    @Test
    public void formatMode_0777() {
        assertEquals("rwxrwxrwx", FilePropsHelper.formatMode(0777));
    }

    @Test
    public void formatMode_0000() {
        assertEquals("---------", FilePropsHelper.formatMode(0000));
    }

    @Test
    public void formatMode_zeroValue() {
        assertEquals("---------", FilePropsHelper.formatMode(0));
    }

    // ── formatMode：文件类型高位被掩码（JSch st_mode 含 S_IFMT 位） ────────────

    @Test
    public void formatMode_regularFileTypeBit() {
        // 0100644 = 常规文件类型位(0x8000) | 0644，应仅展示权限位
        assertEquals("rw-r--r--", FilePropsHelper.formatMode(0100644));
    }

    @Test
    public void formatMode_directoryTypeBit() {
        // 040755 = 目录类型位 | 0755
        assertEquals("rwxr-xr-x", FilePropsHelper.formatMode(040755));
    }

    // ── formatMode：setuid/setgid/sticky 特殊位 ────────────────────────────────

    @Test
    public void formatMode_setuidWithExec() {
        assertEquals("rwsr-xr-x", FilePropsHelper.formatMode(04755));
    }

    @Test
    public void formatMode_setuidWithoutExec() {
        assertEquals("rwSr--r--", FilePropsHelper.formatMode(04644));
    }

    @Test
    public void formatMode_setgidWithExec() {
        assertEquals("rwxr-sr-x", FilePropsHelper.formatMode(02755));
    }

    @Test
    public void formatMode_stickyWithExec() {
        assertEquals("rwxrwxrwt", FilePropsHelper.formatMode(01777));
    }

    @Test
    public void formatMode_stickyWithoutExec() {
        assertEquals("rw-r--r-T", FilePropsHelper.formatMode(01644));
    }

    // ── formatMode：负值防御 ──────────────────────────────────────────────────

    @Test
    public void formatMode_negative() {
        assertEquals("---------", FilePropsHelper.formatMode(-1));
    }

    // ── formatModeWithOctal：符号 + 四位零填充 ─────────────────────────────────

    @Test
    public void formatModeWithOctal_0644() {
        assertEquals("rw-r--r-- (0644)", FilePropsHelper.formatModeWithOctal(0644));
    }

    @Test
    public void formatModeWithOctal_typeBitMasked() {
        assertEquals("rw-r--r-- (0644)", FilePropsHelper.formatModeWithOctal(0100644));
    }

    @Test
    public void formatModeWithOctal_sticky() {
        assertEquals("rwxrwxrwt (1777)", FilePropsHelper.formatModeWithOctal(01777));
    }

    @Test
    public void formatModeWithOctal_zero() {
        assertEquals("--------- (0000)", FilePropsHelper.formatModeWithOctal(0));
    }

    @Test
    public void formatModeWithOctal_negative() {
        assertEquals("--------- (0000)", FilePropsHelper.formatModeWithOctal(-1));
    }
}
