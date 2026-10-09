package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.model.PortForwardRule;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@link PortForwardHelper} 纯函数层单测：端口校验/名称与绑定地址规范化/规则校验、
 * 本地端口冲突检测、启用计划派生（启用/跳过+原因）、展示与报告文本。
 */
public class PortForwardHelperTest {

    private static PortForwardRule rule(String name, int localPort, String remoteHost, int remotePort,
                                        String bind) {
        return new PortForwardRule(name, localPort, remoteHost, remotePort, bind);
    }

    // ── isValidPort ──────────────────────────────────────────────────────────

    @Test
    public void isValidPort_boundaries() {
        assertTrue(PortForwardHelper.isValidPort(1));
        assertTrue(PortForwardHelper.isValidPort(65535));
        assertTrue(PortForwardHelper.isValidPort(22));
    }

    @Test
    public void isValidPort_outOfRange() {
        assertFalse(PortForwardHelper.isValidPort(0));
        assertFalse(PortForwardHelper.isValidPort(65536));
        assertFalse(PortForwardHelper.isValidPort(-1));
    }

    // ── normalize ─────────────────────────────────────────────────────────────

    @Test
    public void normalizeName_trimsAndNullSafe() {
        assertEquals("", PortForwardHelper.normalizeName(null));
        assertEquals("", PortForwardHelper.normalizeName("   "));
        assertEquals("Web 后台", PortForwardHelper.normalizeName("  Web 后台  "));
    }

    @Test
    public void normalizeBindAddress_trimsAndNullSafe() {
        assertEquals("", PortForwardHelper.normalizeBindAddress(null));
        assertEquals("127.0.0.1", PortForwardHelper.normalizeBindAddress("  127.0.0.1  "));
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Test
    public void validate_nullRule_rejected() {
        assertTrue(PortForwardHelper.validate(null) != null);
    }

    @Test
    public void validate_invalidLocalPort_rejected() {
        assertTrue(PortForwardHelper.validate(rule("", 0, "h", 80, "")) != null);
        assertTrue(PortForwardHelper.validate(rule("", 65536, "h", 80, "")) != null);
    }

    @Test
    public void validate_emptyRemoteHost_rejected() {
        assertTrue(PortForwardHelper.validate(rule("", 8080, "", 80, "")) != null);
        assertTrue(PortForwardHelper.validate(rule("", 8080, "   ", 80, "")) != null);
        assertTrue(PortForwardHelper.validate(rule("", 8080, null, 80, "")) != null);
    }

    @Test
    public void validate_invalidRemotePort_rejected() {
        assertTrue(PortForwardHelper.validate(rule("", 8080, "h", 0, "")) != null);
        assertTrue(PortForwardHelper.validate(rule("", 8080, "h", 65536, "")) != null);
    }

    @Test
    public void validate_legalRule_passes() {
        assertNull(PortForwardHelper.validate(rule("", 8080, "127.0.0.1", 80, "")));
        assertNull(PortForwardHelper.validate(rule("web", 3306, "db.internal", 3306, "0.0.0.0")));
    }

    // ── findLocalPortConflict ────────────────────────────────────────────────

    @Test
    public void findLocalPortConflict_noConflict_returnsMinusOne() {
        List<PortForwardRule> rules = Arrays.asList(
                rule("a", 8080, "h", 80, ""),
                rule("b", 8081, "h", 81, ""));
        assertEquals(-1, PortForwardHelper.findLocalPortConflict(rules));
    }

    @Test
    public void findLocalPortConflict_duplicate_returnsPort() {
        List<PortForwardRule> rules = Arrays.asList(
                rule("a", 8080, "h", 80, ""),
                rule("b", 8081, "h", 81, ""),
                rule("c", 8080, "h", 82, ""));
        assertEquals(8080, PortForwardHelper.findLocalPortConflict(rules));
    }

    @Test
    public void findLocalPortConflict_nullSafe() {
        assertEquals(-1, PortForwardHelper.findLocalPortConflict(null));
        List<PortForwardRule> rules = new ArrayList<>();
        rules.add(null);
        rules.add(rule("a", 8080, "h", 80, ""));
        assertEquals(-1, PortForwardHelper.findLocalPortConflict(rules));
    }

    // ── planEnable ───────────────────────────────────────────────────────────

    @Test
    public void planEnable_nullAndEmpty_produceEmptyPlan() {
        assertTrue(PortForwardHelper.planEnable(null).isEmpty());
        assertTrue(PortForwardHelper.planEnable(new ArrayList<>()).isEmpty());
    }

    @Test
    public void planEnable_allLegal_enabledInOrder() {
        List<PortForwardRule> rules = Arrays.asList(
                rule("a", 8080, "h", 80, ""),
                rule("b", 8081, "h", 81, ""));
        List<PortForwardHelper.EnablePlan> plan = PortForwardHelper.planEnable(rules);
        assertEquals(2, plan.size());
        assertEquals(PortForwardHelper.PlanStatus.ENABLED, plan.get(0).status);
        assertEquals(PortForwardHelper.PlanStatus.ENABLED, plan.get(1).status);
        assertSame(rules.get(0), plan.get(0).rule);
        assertSame(rules.get(1), plan.get(1).rule);
    }

    @Test
    public void planEnable_invalidRule_skippedWithReason() {
        List<PortForwardRule> rules = Arrays.asList(
                rule("bad", 0, "h", 80, ""),
                rule("ok", 8080, "h", 80, ""));
        List<PortForwardHelper.EnablePlan> plan = PortForwardHelper.planEnable(rules);
        assertEquals(2, plan.size());
        assertEquals(PortForwardHelper.PlanStatus.SKIPPED_INVALID, plan.get(0).status);
        assertTrue(plan.get(0).reason != null && plan.get(0).reason.contains("本地端口"));
        assertEquals(PortForwardHelper.PlanStatus.ENABLED, plan.get(1).status);
    }

    @Test
    public void planEnable_duplicateLocalPort_keepsFirstSkipsRest() {
        List<PortForwardRule> rules = Arrays.asList(
                rule("first", 8080, "h", 80, ""),
                rule("second", 8080, "h", 81, ""),
                rule("third", 8081, "h", 82, ""));
        List<PortForwardHelper.EnablePlan> plan = PortForwardHelper.planEnable(rules);
        assertEquals(3, plan.size());
        assertEquals(PortForwardHelper.PlanStatus.ENABLED, plan.get(0).status);
        assertEquals(PortForwardHelper.PlanStatus.SKIPPED_DUPLICATE_LOCAL_PORT, plan.get(1).status);
        assertTrue(plan.get(1).reason.contains("8080"));
        assertEquals(PortForwardHelper.PlanStatus.ENABLED, plan.get(2).status);
    }

    @Test
    public void planEnable_nullEntry_skippedAsInvalid() {
        List<PortForwardRule> rules = new ArrayList<>();
        rules.add(null);
        rules.add(rule("ok", 8080, "h", 80, ""));
        List<PortForwardHelper.EnablePlan> plan = PortForwardHelper.planEnable(rules);
        assertEquals(2, plan.size());
        assertEquals(PortForwardHelper.PlanStatus.SKIPPED_INVALID, plan.get(0).status);
        assertNull(plan.get(0).rule);
    }

    // ── describe ─────────────────────────────────────────────────────────────

    @Test
    public void describe_namedRule_includesName() {
        assertEquals("Web 后台（8080→127.0.0.1:80）",
                PortForwardHelper.describe(rule("Web 后台", 8080, "127.0.0.1", 80, "")));
    }

    @Test
    public void describe_unnamedRule_omitsName() {
        assertEquals("8080→db.internal:3306",
                PortForwardHelper.describe(rule("   ", 8080, "db.internal", 3306, "")));
    }

    @Test
    public void describe_customBindAddress_included() {
        assertEquals("web（0.0.0.0:8080→h:80）",
                PortForwardHelper.describe(rule("web", 8080, "h", 80, "0.0.0.0")));
    }

    @Test
    public void describe_defaultBindAddress_notAnnotated() {
        assertEquals("8080→h:80",
                PortForwardHelper.describe(rule("", 8080, "h", 80, "127.0.0.1")));
    }

    @Test
    public void describe_nullRule() {
        assertEquals("（无效规则）", PortForwardHelper.describe(null));
    }

    // ── buildReport ──────────────────────────────────────────────────────────

    @Test
    public void buildReport_allEnabledNoIssues_returnsNull() {
        assertNull(PortForwardHelper.buildReport(2, new ArrayList<>(), new ArrayList<>()));
        assertNull(PortForwardHelper.buildReport(0, new ArrayList<>(), new ArrayList<>()));
    }

    @Test
    public void buildReport_withFailures_listsDetails() {
        String report = PortForwardHelper.buildReport(1,
                Arrays.asList("s1"),
                Arrays.asList("f1"));
        assertTrue(report.contains("已启用 1 条"));
        assertTrue(report.contains("跳过 1 条"));
        assertTrue(report.contains("失败 1 条"));
        assertTrue(report.contains("s1"));
        assertTrue(report.contains("f1"));
    }

    @Test
    public void buildReport_truncatesLongFailureLists() {
        List<String> failures = new ArrayList<>();
        for (int i = 0; i < 7; i++) failures.add("f" + i);
        String report = PortForwardHelper.buildReport(0, new ArrayList<>(), failures);
        assertTrue(report.contains("失败 7 条"));
        assertTrue(report.contains("f0"));
        assertTrue(!report.contains("f6")); // 仅列举前 5 条
        assertTrue(report.contains("等7 条"));
    }

    // ── sameEffectiveRule / sameEffectiveRules（连接复用判定） ─────────────────

    @Test
    public void sameEffectiveRule_ignoresDisplayNameAndBindNormalization() {
        // 仅展示名不同=等价；绑定地址「空」与「127.0.0.1」应用结果相同=等价
        assertTrue(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "db.internal", 5432, ""),
                rule("B", 8080, " db.internal ", 5432, "127.0.0.1")));
        // 远端主机大小写不敏感（主机名/IP 解析不受大小写影响）
        assertTrue(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "DB.Internal", 5432, "0.0.0.0"),
                rule("A", 8080, "db.internal", 5432, "0.0.0.0")));
    }

    @Test
    public void sameEffectiveRule_effectiveFieldsDiffer() {
        assertFalse(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "h", 5432, ""),
                rule("A", 8081, "h", 5432, "")));
        assertFalse(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "h", 5432, ""),
                rule("A", 8080, "h", 5433, "")));
        assertFalse(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "h", 5432, ""),
                rule("A", 8080, "h2", 5432, "")));
        assertFalse(PortForwardHelper.sameEffectiveRule(
                rule("A", 8080, "h", 5432, "0.0.0.0"),
                rule("A", 8080, "h", 5432, "127.0.0.1")));
    }

    @Test
    public void sameEffectiveRule_nullEntries() {
        assertFalse(PortForwardHelper.sameEffectiveRule(null, rule("A", 1, "h", 1, "")));
        assertTrue(PortForwardHelper.sameEffectiveRule(null, null));
    }

    @Test
    public void sameEffectiveRules_nullOrEmptyEquivalent() {
        assertTrue(PortForwardHelper.sameEffectiveRules(null, null));
        assertTrue(PortForwardHelper.sameEffectiveRules(null, new ArrayList<>()));
        assertTrue(PortForwardHelper.sameEffectiveRules(new ArrayList<>(), null));
    }

    @Test
    public void sameEffectiveRules_orderSensitiveAndSizeChecked() {
        List<PortForwardRule> a = Arrays.asList(rule("A", 8080, "h", 1, ""), rule("B", 8081, "h", 2, ""));
        List<PortForwardRule> reversed = Arrays.asList(rule("A", 8081, "h", 2, ""), rule("B", 8080, "h", 1, ""));
        assertFalse(PortForwardHelper.sameEffectiveRules(a, reversed)); // 顺序=启用序，敏感
        assertFalse(PortForwardHelper.sameEffectiveRules(
                a, Arrays.asList(rule("A", 8080, "h", 1, "")))); // 长度不同
        assertTrue(PortForwardHelper.sameEffectiveRules(a, new ArrayList<>(a))); // 同序同字段=等价
    }
}
