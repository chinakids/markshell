package com.ssh.mdreader.util;

import com.ssh.mdreader.model.PortForwardRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 端口转发规则的校验/启用计划纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p>职责：规则字段校验、同列表本地端口冲突检测、启用计划派生（多规则→启用/跳过结果+原因）、
 * 展示文本与报告文本。真实系统级端口占用无法在纯函数内感知：由 JSch
 * {@code setPortForwardingL} 绑定失败运行时捕获，SshManager 降级记录不阻断主连接。</p>
 *
 * <p>语义约定：
 * <ul>
 *   <li>本地端口/远端端口合法范围 1-65535；远端主机 trim 后非空。</li>
 *   <li>本地端口是规则的唯一标识：同列表重复出现时仅首条启用，其余按
 *       {@link PlanStatus#SKIPPED_DUPLICATE_LOCAL_PORT} 跳过（同一会话内两个转发绑同一端口必然失败）。</li>
 *   <li>绑定地址留空=使用 {@link #DEFAULT_BIND_ADDRESS}（127.0.0.1，仅本机回环——安全默认，
 *       JSch 无绑定地址时监听 0.0.0.0 会把隧道暴露到局域网）。</li>
 * </ul>
 */
public final class PortForwardHelper {

    private PortForwardHelper() {
    }

    public static final int MIN_PORT = 1;
    public static final int MAX_PORT = 65535;
    /** 默认绑定地址：仅监听本机回环（安全默认）。 */
    public static final String DEFAULT_BIND_ADDRESS = "127.0.0.1";

    public static boolean isValidPort(int port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }

    /** 名称规范化：trim；null/空白→空串（未命名，展示时退化为主体信息）。 */
    public static String normalizeName(String name) {
        return name == null ? "" : name.trim();
    }

    /** 绑定地址规范化：trim；null/空白→空串（表示用默认绑定）。 */
    public static String normalizeBindAddress(String bind) {
        return bind == null ? "" : bind.trim();
    }

    /**
     * 规则校验（纯函数）：通过返回 null；否则返回面向用户的中文错误消息。
     */
    public static String validate(PortForwardRule rule) {
        if (rule == null) return "规则不能为空";
        if (!isValidPort(rule.getLocalPort())) {
            return "本地端口需为 " + MIN_PORT + "-" + MAX_PORT;
        }
        String host = rule.getRemoteHost();
        if (host == null || host.trim().isEmpty()) return "请填写远端主机";
        if (!isValidPort(rule.getRemotePort())) {
            return "远端端口需为 " + MIN_PORT + "-" + MAX_PORT;
        }
        return null;
    }

    /**
     * 两条规则是否「生效等价」（纯函数，供连接复用判定 {@code reuseEligible} 使用）：
     * 展示名 name 仅用于展示、不参与隧道建立，比较时忽略；生效字段=本地端口、远端主机
     * （trim+忽略大小写，主机名/IP 解析对大小写不敏感）、远端端口、绑定地址（normalize 后
     * 空串=未绑定=将用默认 127.0.0.1，与显式 DEFAULT_BIND_ADDRESS 等价——两者应用结果相同）。
     * null 条目按「无效条目」参与比较（两个 null 互为相等）。
     */
    public static boolean sameEffectiveRule(PortForwardRule a, PortForwardRule b) {
        if (a == null || b == null) return a == b;
        if (a.getLocalPort() != b.getLocalPort()) return false;
        String h = a.getRemoteHost(), h2 = b.getRemoteHost();
        if (h == null || h2 == null) {
            if (h != h2) return false;
        } else if (!h.trim().equalsIgnoreCase(h2.trim())) {
            return false;
        }
        if (a.getRemotePort() != b.getRemotePort()) return false;
        return effectiveBind(a.getBindAddress()).equals(effectiveBind(b.getBindAddress()));
    }

    /** 绑定地址生效值：normalize 后空串回退 {@link #DEFAULT_BIND_ADDRESS}（与 apply 时一致）。 */
    private static String effectiveBind(String bind) {
        String n = normalizeBindAddress(bind);
        return n.isEmpty() ? DEFAULT_BIND_ADDRESS : n;
    }

    /**
     * 两个规则列表是否「生效等价」（纯函数，保序逐条比较）：null 与空列表等价；长度不同
     * 即不等。顺序敏感（applyPortForwardsSync 按序启用，同本地端口重复时首条优先——顺序
     * 变化会改变实际启用集，故不等）。
     */
    public static boolean sameEffectiveRules(List<PortForwardRule> a, List<PortForwardRule> b) {
        if (a == null || a.isEmpty()) return b == null || b.isEmpty();
        if (b == null || b.isEmpty()) return false;
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!sameEffectiveRule(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    /** 同列表内本地端口冲突检测（纯函数）：返回第一个重复出现的本地端口；无冲突返回 -1。
     * 输入含 null 条目时跳过（null 条目无法参与冲突，由调用方清洗后再校验）。
     */
    public static int findLocalPortConflict(List<PortForwardRule> rules) {
        if (rules == null) return -1;
        Set<Integer> seen = new HashSet<>();
        for (PortForwardRule rule : rules) {
            if (rule == null) continue;
            int port = rule.getLocalPort();
            if (!seen.add(port)) return port;
        }
        return -1;
    }

    /** 启用计划中的单条结果状态。 */
    public enum PlanStatus {
        /** 可启用。 */
        ENABLED,
        /** 字段非法（validate 不通过），跳过。 */
        SKIPPED_INVALID,
        /** 本地端口与其他启用规则重复，跳过（首条优先）。 */
        SKIPPED_DUPLICATE_LOCAL_PORT
    }

    /** 单条规则在启用计划中的结果（规则 + 状态 + 原因；ENABLED 时 reason 为 null）。 */
    public static final class EnablePlan {
        public final PortForwardRule rule;
        public final PlanStatus status;
        public final String reason;

        EnablePlan(PortForwardRule rule, PlanStatus status, String reason) {
            this.rule = rule;
            this.status = status;
            this.reason = reason;
        }
    }

    /**
     * 启用计划派生（纯函数，输出与输入同序）：对每个规则给出 ENABLED / SKIPPED_INVALID /
     * SKIPPED_DUPLICATE_LOCAL_PORT 及原因。本地端口重复仅保留首个出现的规则（详见类注释）。
     * null 输入或 null 条目按空/跳过处理（null 条目记 SKIPPED_INVALID）。
     */
    public static List<EnablePlan> planEnable(List<PortForwardRule> rules) {
        List<EnablePlan> plan = new ArrayList<>();
        if (rules == null) return plan;
        Set<Integer> portsSeen = new HashSet<>();
        for (PortForwardRule rule : rules) {
            if (rule == null) {
                plan.add(new EnablePlan(null, PlanStatus.SKIPPED_INVALID, "规则不能为空"));
                continue;
            }
            String error = validate(rule);
            if (error != null) {
                plan.add(new EnablePlan(rule, PlanStatus.SKIPPED_INVALID, error));
                continue;
            }
            int localPort = rule.getLocalPort();
            if (!portsSeen.add(localPort)) {
                plan.add(new EnablePlan(rule, PlanStatus.SKIPPED_DUPLICATE_LOCAL_PORT,
                        "本地端口 " + localPort + " 与前面规则冲突，未重复启用"));
                continue;
            }
            plan.add(new EnablePlan(rule, PlanStatus.ENABLED, null));
        }
        return plan;
    }

    /**
     * 规则的展示文本（纯函数）：「名称（本地端口→远端主机:远端端口）」；未命名时省略名称；
     * 绑定地址非默认时标注「@绑定地址」。
     */
    public static String describe(PortForwardRule rule) {
        if (rule == null) return "（无效规则）";
        StringBuilder sb = new StringBuilder();
        String name = normalizeName(rule.getName());
        String body = rule.getLocalPort() + "→" + rule.getRemoteHost() + ":" + rule.getRemotePort();
        String bind = normalizeBindAddress(rule.getBindAddress());
        if (!bind.isEmpty() && !DEFAULT_BIND_ADDRESS.equals(bind)) {
            body = bind + ":" + body;
        }
        if (name.isEmpty()) {
            sb.append(body);
        } else {
            sb.append(name).append("（").append(body).append("）");
        }
        return sb.toString();
    }

    /**
     * 启用摘要报告（纯函数）：无跳过且无失败时返回 null（全部成功不提示）；否则返回面向
     * 用户的摘要（已启用 N 条 + 跳过/失败明细，最多列举前 5 条）。
     */
    public static String buildReport(int enabledCount, List<String> skippedMessages,
                                     List<String> failedMessages) {
        if (skippedMessages == null) skippedMessages = new ArrayList<>();
        if (failedMessages == null) failedMessages = new ArrayList<>();
        if (skippedMessages.isEmpty() && failedMessages.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("端口转发：");
        if (enabledCount > 0) {
            sb.append("已启用 ").append(enabledCount).append(" 条");
        } else {
            sb.append("未启用");
        }
        appendDetail(sb, "跳过", skippedMessages);
        appendDetail(sb, "失败", failedMessages);
        return sb.toString();
    }

    private static void appendDetail(StringBuilder sb, String label, List<String> messages) {
        if (messages.isEmpty()) return;
        sb.append("，").append(label).append(" ").append(messages.size()).append(" 条");
        int shown = Math.min(messages.size(), 5);
        for (int i = 0; i < shown; i++) {
            sb.append("（").append(messages.get(i)).append("）");
        }
        if (messages.size() > shown) {
            sb.append("等").append(messages.size()).append(" 条");
        }
    }
}
