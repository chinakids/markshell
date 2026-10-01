package com.ssh.mdreader.ssh;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.jcraft.jsch.SftpException;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketException;

/**
 * 断链判定逻辑单测：只读操作失败重试前先区分「链路已断」与
 * 「文件不存在/权限不足」等业务错误，避免把业务错误误判为断线。
 */
public class SshManagerTest {

    @Test
    public void ioException_isConnectionGone() {
        assertTrue(SshManager.isConnectionGone(new IOException("Premature EOF")));
        assertTrue(SshManager.isConnectionGone(new SocketException("Connection reset")));
    }

    @Test
    public void sftpChannelClosed_isConnectionGone() throws Exception {
        assertTrue(SshManager.isConnectionGone(new SftpException(4, "Channel is closed")));
        assertTrue(SshManager.isConnectionGone(new SftpException(6, "Connection is closed by foreign host")));
        assertTrue(SshManager.isConnectionGone(new SftpException(4, "connection lost")));
        assertTrue(SshManager.isConnectionGone(new SftpException(4, null)));
    }

    @Test
    public void businessError_isNotConnectionGone() throws Exception {
        assertFalse(SshManager.isConnectionGone(new SftpException(2, "No such file")));
        assertFalse(SshManager.isConnectionGone(new SftpException(3, "Permission denied")));
        assertFalse(SshManager.isConnectionGone(new RuntimeException("unexpected")));
    }

    @Test
    public void buildRenamePath_joinsSameDirectory() {
        assertEquals("/a/b/d.txt", SshManager.buildRenamePath("/a/b/c.txt", "d.txt"));
        assertEquals("/tmp/g.log", SshManager.buildRenamePath("/tmp/f.log", "g.log"));
        // 根目录下文件：保留前导斜杠
        assertEquals("/x", SshManager.buildRenamePath("/file", "x"));
    }

    @Test
    public void buildRenamePath_preservesNewNameAsIs() {
        // 新名自身含路径（移动场景由 UI 拦截，底层按完整新名处理）
        assertEquals("/a/b/sub/d.txt", SshManager.buildRenamePath("/a/b/c.txt", "sub/d.txt"));
        // 无斜杠的异常路径：直接返回新名
        assertEquals("d.txt", SshManager.buildRenamePath("c.txt", "d.txt"));
    }

    @Test
    public void sanitizeHeartbeat_rejectsOutOfRange() {
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(0));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(999));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(-100));
        assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, SshManager.sanitizeHeartbeat(60_001));
    }

    @Test
    public void sanitizeHeartbeat_keepsValidValues() {
        assertEquals(1_000, SshManager.sanitizeHeartbeat(1_000));
        assertEquals(10_000, SshManager.sanitizeHeartbeat(10_000));
        assertEquals(60_000, SshManager.sanitizeHeartbeat(60_000));
    }

    @Test
    public void parseOctalMode_validModes() {
        assertEquals(0x1ED, SshManager.parseOctalMode("755"));   // 0755
        assertEquals(0x1A4, SshManager.parseOctalMode("644"));   // 0644
        assertEquals(0x3FF, SshManager.parseOctalMode("1777"));  // 01777（setuid 场景）
        assertEquals(0x1A4, SshManager.parseOctalMode("0644"));  // 前导 0 合法
        assertEquals(0, SshManager.parseOctalMode("000"));
    }

    @Test
    public void parseOctalMode_rejectsInvalid() {
        assertEquals(-1, SshManager.parseOctalMode("888"));   // 非八进制位
        assertEquals(-1, SshManager.parseOctalMode("75"));    // 位数不足
        assertEquals(-1, SshManager.parseOctalMode("1"));
        assertEquals(-1, SshManager.parseOctalMode("0"));     // 唯一 0 位不足（须写 000）
        assertEquals(-1, SshManager.parseOctalMode(""));      // 空输入
        assertEquals(-1, SshManager.parseOctalMode(null));
        assertEquals(-1, SshManager.parseOctalMode("755 "));  // 多余空格
        assertEquals(-1, SshManager.parseOctalMode("07775")); // 位数超限
        assertEquals(-1, SshManager.parseOctalMode("abc"));
    }

    @Test
    public void setHeartbeatInterval_sanitizesAndStores() {
        SshManager mgr = SshManager.getInstance();
        int original = mgr.getHeartbeatIntervalMs();
        try {
            mgr.setHeartbeatIntervalMs(30_000);
            assertEquals(30_000, mgr.getHeartbeatIntervalMs());
            // 非法值（0）应回退默认，不污染运行态
            mgr.setHeartbeatIntervalMs(0);
            assertEquals(SshManager.DEFAULT_HEARTBEAT_MS, mgr.getHeartbeatIntervalMs());
        } finally {
            mgr.setHeartbeatIntervalMs(original);
        }
    }
}
