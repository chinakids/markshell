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
