package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.SshConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@link ConnectionSearchHelper} 纯函数层单测（JVM，无 Android 依赖）。 */
public class ConnectionSearchHelperTest {

    // ── normalize / active ────────────────────────────────────────────────────

    @Test
    public void normalize_nullBecomesEmpty() {
        assertEquals("", ConnectionSearchHelper.normalize(null));
    }

    @Test
    public void normalize_keepsAsIs_noTrim() {
        assertEquals(" prod ", ConnectionSearchHelper.normalize(" prod "));
    }

    @Test
    public void active_emptyOrNullFalse() {
        assertFalse(ConnectionSearchHelper.active(null));
        assertFalse(ConnectionSearchHelper.active(""));
    }

    @Test
    public void active_whitespaceIsActive() {
        assertTrue(ConnectionSearchHelper.active(" "));
    }

    // ── matches 基本语义 ──────────────────────────────────────────────────────

    private SshConfig config(String alias, String host, int port, String user, String group) {
        SshConfig c = new SshConfig(alias, host, port, user, "p", "/");
        c.setGroup(group);
        return c;
    }

    private SshConfig dev() {
        return config("web-dev-01", "192.168.1.10", 22, "root", "dev");
    }

    private SshConfig staging() {
        return config(null, "staging.example.com", 2222, "deploy", "staging");
    }

    @Test
    public void matches_nullQueryPassAll() {
        assertTrue(ConnectionSearchHelper.matches(dev(), null));
        assertTrue(ConnectionSearchHelper.matches(dev(), ""));
        assertTrue(ConnectionSearchHelper.matches(null, null));
    }

    @Test
    public void matches_nullConfigRejectedWithActiveQuery() {
        assertFalse(ConnectionSearchHelper.matches(null, "x"));
    }

    @Test
    public void matches_aliasHit() {
        assertTrue(ConnectionSearchHelper.matches(dev(), "web-dev"));
        assertTrue(ConnectionSearchHelper.matches(dev(), "WEB-DEV-01"));
        assertFalse(ConnectionSearchHelper.matches(dev(), "nonexistent"));
    }

    @Test
    public void matches_hostHit() {
        assertTrue(ConnectionSearchHelper.matches(dev(), "192.168.1"));
        assertTrue(ConnectionSearchHelper.matches(staging(), "STAGING.example"));
        assertFalse(ConnectionSearchHelper.matches(staging(), "192.168"));
    }

    @Test
    public void matches_hostPortHit() {
        // host:port 组合串（端口可搜）与 host 单独命中
        assertTrue(ConnectionSearchHelper.matches(staging(), "2222"));
        assertTrue(ConnectionSearchHelper.matches(staging(), "example.com:2222"));
        assertTrue(ConnectionSearchHelper.matches(staging(), "STAGING.EXAMPLE.COM:2222"));
        assertTrue(ConnectionSearchHelper.matches(staging(), ":2222"));
        assertFalse(ConnectionSearchHelper.matches(staging(), ":23"));
    }

    @Test
    public void matches_usernameHit() {
        assertTrue(ConnectionSearchHelper.matches(staging(), "deploy"));
        assertTrue(ConnectionSearchHelper.matches(staging(), "DEPLOY"));
        assertFalse(ConnectionSearchHelper.matches(staging(), "root"));
    }

    @Test
    public void matches_groupHit() {
        assertTrue(ConnectionSearchHelper.matches(dev(), "dev"));
        assertFalse(ConnectionSearchHelper.matches(dev(), "生产"));
        assertTrue(ConnectionSearchHelper.matches(config("web-prod", "10.0.0.1", 22, "root", "生产"), "生产"));
        assertFalse(ConnectionSearchHelper.matches(dev(), "staging"));
    }

    @Test
    public void matches_substringSemantics() {
        assertTrue(ConnectionSearchHelper.matches(dev(), "web"));
        assertTrue(ConnectionSearchHelper.matches(dev(), "168.1")); // 192.168.1.10 中部子串
        assertFalse(ConnectionSearchHelper.matches(dev(), "xxx"));
    }

    @Test
    public void matches_leadingTrailingSpaceLiteral() {
        // 查询串不 trim：首尾空格按字面参与匹配
        assertTrue(ConnectionSearchHelper.matches(config("a b", "h", 22, "u", "g"), "a "));
        assertFalse(ConnectionSearchHelper.matches(config("ab", "h", 22, "u", "g"), "a "));
    }

    @Test
    public void matches_nullFieldsDefensive() {
        // 字段 null 不参与匹配，其余字段仍可命中
        SshConfig c = new SshConfig(null, "host-a", 22, null, null, null);
        c.setGroup(null);
        assertTrue(ConnectionSearchHelper.matches(c, "host-a"));
        assertTrue(ConnectionSearchHelper.matches(c, "host-a:22"));
        assertFalse(ConnectionSearchHelper.matches(c, "root"));
        assertTrue(ConnectionSearchHelper.matches(c, ""));
        // host 为 null：host:port 目标不参与
        SshConfig c2 = new SshConfig(null, null, 22, null, null, null);
        assertFalse(ConnectionSearchHelper.matches(c2, ":22"));
        assertFalse(ConnectionSearchHelper.matches(c2, "x"));
    }

    @Test
    public void matches_portZeroExcludedFromHostPort() {
        // port<=0 时 host:port 目标不参与（按 host 命名命中仍可）
        SshConfig c = config("x", "host-a", 0, "u", null);
        assertFalse(ConnectionSearchHelper.matches(c, ":0"));
        assertTrue(ConnectionSearchHelper.matches(c, "host-a"));
    }

    // ── filter ────────────────────────────────────────────────────────────────

    @Test
    public void filter_emptyQueryReturnsSameList() {
        List<SshConfig> list = new ArrayList<>();
        list.add(dev());
        assertSame(list, ConnectionSearchHelper.filter(list, ""));
        assertSame(list, ConnectionSearchHelper.filter(list, null));
    }

    @Test
    public void filter_nullInputReturnsEmpty() {
        assertEquals(0, ConnectionSearchHelper.filter(null, "x").size());
    }

    @Test
    public void filter_keepsOrderAndRefs_doesNotMutate() {
        List<SshConfig> list = new ArrayList<>();
        SshConfig devRef = dev();
        SshConfig stagingRef = staging();
        list.add(devRef);
        list.add(stagingRef);
        list.add(config("db-prod", "10.0.0.5", 22, "admin", "prod"));
        List<SshConfig> before = new ArrayList<>(list);
        List<SshConfig> result = ConnectionSearchHelper.filter(list, "staging");
        assertEquals(1, result.size());
        assertSame(stagingRef, result.get(0));
        assertEquals(before, list); // 入参未变
    }

    @Test
    public void filter_multiFieldUnion() {
        // 同一查询可经不同字段命中（别名 or 分组）：并集语义
        List<SshConfig> list = new ArrayList<>();
        list.add(dev());
        list.add(staging());
        list.add(config("db-prod", "10.0.0.5", 22, "admin", "prod"));
        List<SshConfig> result = ConnectionSearchHelper.filter(list, "prod");
        assertEquals(1, result.size()); // 仅 db-prod（别名+组双重命中算一条）
        assertSame(list.get(2), result.get(0));

        List<SshConfig> union = new ArrayList<>();
        union.add(dev());
        union.add(staging()); // 组 staging + host 均命中
        union.add(config("staging-db", "10.0.0.5", 22, "admin", "prod")); // 别名命中
        List<SshConfig> result2 = ConnectionSearchHelper.filter(union, "staging");
        assertEquals(2, result2.size());
        assertSame(union.get(1), result2.get(0));
        assertSame(union.get(2), result2.get(1));
    }

    // ── visibleGroupNames ─────────────────────────────────────────────────────

    @Test
    public void visibleGroupNames_dropsEmptyGroups() {
        Map<String, List<SshConfig>> buckets = new LinkedHashMap<>();
        buckets.put("dev", new ArrayList<SshConfig>() {{ add(dev()); }});
        buckets.put("prod", new ArrayList<>()); // 空组（无匹配）→ 剔除
        List<String> names = new ArrayList<>();
        names.add("dev");
        names.add("prod");
        names.add("staging");
        List<String> visible = ConnectionSearchHelper.visibleGroupNames(names, buckets);
        assertEquals(1, visible.size());
        assertEquals("dev", visible.get(0));
    }

    @Test
    public void visibleGroupNames_preservesOrder() {
        Map<String, List<SshConfig>> buckets = new LinkedHashMap<>();
        buckets.put("b", new ArrayList<SshConfig>() {{ add(config("c1", "h", 22, "u", null)); }});
        buckets.put("a", new ArrayList<SshConfig>() {{ add(config("c2", "h", 22, "u", null)); }});
        List<String> names = new ArrayList<>();
        names.add("a");
        names.add("b");
        List<String> visible = ConnectionSearchHelper.visibleGroupNames(names, buckets);
        assertEquals("a", visible.get(0));
        assertEquals("b", visible.get(1));
    }

    @Test
    public void visibleGroupNames_nullDefensive() {
        assertEquals(0, ConnectionSearchHelper.visibleGroupNames(null, null).size());
        assertEquals(0, ConnectionSearchHelper.visibleGroupNames(
                new ArrayList<>(), new LinkedHashMap<>()).size());
    }

    // ── indexOfIgnoreCase 委托（单一语义源=FileFilterHelper） ──────────────────

    @Test
    public void indexOf_delegatesToFileFilterHelper() {
        assertEquals(0, ConnectionSearchHelper.indexOfIgnoreCase("abc", "a"));
        assertEquals(1, ConnectionSearchHelper.indexOfIgnoreCase("abc", "B"));
        assertEquals(-1, ConnectionSearchHelper.indexOfIgnoreCase("abc", "d"));
    }
}
