package com.jcraft.jsch;

import java.lang.reflect.Constructor;

/**
 * Test-only factory for building real {@link ChannelSftp.LsEntry} /
 * {@link SftpATTRS} instances in unit tests, without a live SFTP connection.
 *
 * <p>JSch exposes no public constructor for either type ({@code LsEntry} has a
 * package-private constructor, {@code SftpATTRS} a private one), so this helper
 * lives in the {@code com.jcraft.jsch} package: the package-private
 * {@code LsEntry} constructor is reachable from here, and {@code SftpATTRS} is
 * materialized via reflection and then configured through its public setters.
 * The instance is fully real (no mocking library involved).</p>
 */
public final class LsTestFactory {

    private LsTestFactory() {
    }

    /** Builds a real LsEntry (outer channel unused by getters; never connected). */
    public static ChannelSftp.LsEntry entry(String name, int permissions, long size, int mtime) {
        SftpATTRS attrs = attrs(permissions, size, mtime);
        ChannelSftp channel = new ChannelSftp();
        return channel.new LsEntry(name, "", attrs);
    }

    /** Builds a real SftpATTRS in the same state JSch fills after parsing a server ls reply. */
    public static SftpATTRS attrs(int permissions, long size, int mtime) {
        try {
            Constructor<SftpATTRS> c = SftpATTRS.class.getDeclaredConstructor();
            c.setAccessible(true);
            SftpATTRS attrs = c.newInstance();
            attrs.setSIZE(size);
            attrs.setACMODTIME(mtime, mtime);
            // setPERMISSIONS() 只保留低 12 位权限位（掩掉类型位），无法表达目录/链接；
            // 服务端解析路径是直接写 permissions 字段（含类型位），此处同步模拟。
            attrs.flags |= SftpATTRS.SSH_FILEXFER_ATTR_PERMISSIONS;
            attrs.permissions = permissions;
            return attrs;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to construct SftpATTRS", e);
        }
    }
}
