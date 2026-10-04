package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ConnectionFormHelper 单测：端口解析（缺省/非法/越界哨兵）、端口范围契约、主机名规范化。
 * 端口范围（1-65535）与 SshConfig.isValid 契约单一语义源（竞品 Termius
 * incorrect_port_value_error「端口值不正确」对标=表单端口预校验标配）。
 */
public class ConnectionFormHelperTest {

    // ── parsePort ───────────────────────────────────────────────────────────

    @Test
    public void parse_nullOrBlankReturnsDefault() {
        assertEquals(22, ConnectionFormHelper.parsePort(null, ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(22, ConnectionFormHelper.parsePort("", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(22, ConnectionFormHelper.parsePort("   ", ConnectionFormHelper.DEFAULT_PORT));
    }

    @Test
    public void parse_defaultPortParameterApplied() {
        assertEquals(2222, ConnectionFormHelper.parsePort("2222", 2222));
        assertEquals(2222, ConnectionFormHelper.parsePort(null, 2222));
    }

    @Test
    public void parse_validPort() {
        assertEquals(22, ConnectionFormHelper.parsePort("22", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(1, ConnectionFormHelper.parsePort("1", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(65535, ConnectionFormHelper.parsePort("65535", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(80, ConnectionFormHelper.parsePort(" 80 ", ConnectionFormHelper.DEFAULT_PORT));
    }

    @Test
    public void parse_outOfRangeReturnsInvalid() {
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("0", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("-1", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("65536", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("99999", ConnectionFormHelper.DEFAULT_PORT));
    }

    @Test
    public void parse_nonNumericReturnsInvalid() {
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("abc", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("22a", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("22.5", ConnectionFormHelper.DEFAULT_PORT));
    }

    @Test
    public void parse_overflowReturnsInvalid() {
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("99999999999999999999", ConnectionFormHelper.DEFAULT_PORT));
        assertEquals(ConnectionFormHelper.INVALID_PORT,
                ConnectionFormHelper.parsePort("2147483647", ConnectionFormHelper.DEFAULT_PORT));
    }

    // ── isValidPort ──────────────────────────────────────────────────────────

    @Test
    public void isValidPort_acceptsRangeBounds() {
        assertTrue(ConnectionFormHelper.isValidPort(1));
        assertTrue(ConnectionFormHelper.isValidPort(22));
        assertTrue(ConnectionFormHelper.isValidPort(65535));
    }

    @Test
    public void isValidPort_rejectsOutOfRange() {
        assertFalse(ConnectionFormHelper.isValidPort(0));
        assertFalse(ConnectionFormHelper.isValidPort(-1));
        assertFalse(ConnectionFormHelper.isValidPort(65536));
        assertFalse(ConnectionFormHelper.isValidPort(99999));
    }

    // ── normalizeHost ────────────────────────────────────────────────────────

    @Test
    public void normalizeHost_nullAndBlank() {
        assertEquals("", ConnectionFormHelper.normalizeHost(null));
        assertEquals("", ConnectionFormHelper.normalizeHost(""));
        assertEquals("", ConnectionFormHelper.normalizeHost("   "));
    }

    @Test
    public void normalizeHost_trimsWhitespace() {
        assertEquals("192.168.0.1", ConnectionFormHelper.normalizeHost(" 192.168.0.1 "));
        assertEquals("my-host.example.com", ConnectionFormHelper.normalizeHost("my-host.example.com"));
    }
}
