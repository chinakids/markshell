package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.SshConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 连接列表快速过滤/搜索纯函数层（JVM 可测，无 Android 依赖）。
 *
 * <p><b>背景（能力发现循环第二十一轮 #26）</b>：MainActivity 首页连接列表与
 * SavedConnectionsActivity 连接列表均无任何过滤/搜索入口——连接数一多（几十台
 * dev/staging 服务器）只能人眼翻找。竞品实证（多键穷举，教训㊴b）：Termius
 * {@code search_hosts_and_groups_placeholder}「搜索主机和分组」/
 * {@code empty_hint_hosts_search}「未找到主机或组。」/ {@code search_menu_title}「搜索」；
 * JuiceSSH {@code smart_search}「智能搜索」/ {@code add_filter}「新建过滤器」——
 * 主机列表搜索=商业 SSH 客户端标配。</p>
 *
 * <p><b>语义（文档化）</b>：</p>
 * <ul>
 *   <li>{@code query} 为 null/空 → 不过滤（全部通过）。</li>
 *   <li>匹配=连接任一目标字段<b>包含</b>查询串（子串语义，非前缀），大小写不敏感
 *       （{@link String#regionMatches} 逐字符，复用 {@link FileFilterHelper} 同源实现）。</li>
 *   <li>匹配目标=别名（alias）、主机（host）、用户名（username）、{@code host:port}
 *       （让端口也可搜）、分组名（group）——与 Termius「搜索主机和分组」语义对齐。</li>
 *   <li>查询串<b>不 trim</b>（与文件筛选/查找栏一致：所见即所搜；首尾空格按字面参与匹配）。</li>
 *   <li>{@code filter} 不修改入参列表：active 查询返回新列表（元素引用不变、顺序保持）；
 *       空查询返回原列表引用（零拷贝）。</li>
 * </ul>
 *
 * <p><b>约定</b>：过滤只作用于内存中的连接列表（本地配置，无 SFTP 参与），
 * 与文件筛选（{@link FileFilterHelper}）同模式。</p>
 */
public final class ConnectionSearchHelper {

    private ConnectionSearchHelper() {
    }

    /** 规范化查询串：null → 空串（其余原样，不 trim）。 */
    @NonNull
    public static String normalize(@Nullable String query) {
        return query == null ? "" : query;
    }

    /** 是否有生效中的搜索：query 非空即生效。 */
    public static boolean active(@Nullable String query) {
        return query != null && !query.isEmpty();
    }

    /**
     * 连接是否命中搜索（子串、大小写不敏感，匹配 alias/host/username/host:port/group
     * 任一字段）。空/null 查询恒为 true（不过滤）；config 为 null 恒为 false（防御）；
     * 各字段 null 视为不参与匹配（防御，正常数据均非空）。
     */
    public static boolean matches(@Nullable SshConfig config, @Nullable String query) {
        if (!active(query)) return true;
        if (config == null) return false;
        String q = normalize(query);
        if (contains(config.getAlias(), q)) return true;
        if (contains(config.getHost(), q)) return true;
        if (contains(config.getUsername(), q)) return true;
        if (hostPortMatches(config, q)) return true;
        return contains(config.getGroup(), q);
    }

    /**
     * 按搜索过滤连接列表：active 查询→返回保序新列表（元素引用不变，不修改入参）；
     * 空/null 查询→返回原列表引用。null 入参视为空列表。
     */
    @NonNull
    public static List<SshConfig> filter(@Nullable List<SshConfig> connections,
                                         @Nullable String query) {
        if (connections == null) return new ArrayList<>();
        if (!active(query)) return connections;
        List<SshConfig> result = new ArrayList<>();
        for (SshConfig c : connections) {
            if (matches(c, query)) {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 过滤激活时剔除「无匹配连接」的组名（保序），供列表页决定展示哪些组头；
     * 未分组的空串 key 由展示层自行处理。buckets null/空 → 空列表（防御）。
     * 不修改入参。
     */
    @NonNull
    public static List<String> visibleGroupNames(@Nullable List<String> groupNames,
                                                 @Nullable Map<String, List<SshConfig>> buckets) {
        List<String> result = new ArrayList<>();
        if (groupNames == null || buckets == null) return result;
        for (String g : groupNames) {
            List<SshConfig> members = buckets.get(g);
            if (members != null && !members.isEmpty()) {
                result.add(g);
            }
        }
        return result;
    }

    // ── 内部 ──────────────────────────────────────────────────────────────────

    /** host:port 组合目标：host 或 port 无效（null/<=0）时不参与匹配。 */
    private static boolean hostPortMatches(@NonNull SshConfig config, @NonNull String query) {
        String host = config.getHost();
        if (host == null || host.isEmpty()) return false;
        int port = config.getPort();
        if (port <= 0) return false;
        return indexOfIgnoreCase(host + ":" + port, query) >= 0;
    }

    /** 字段命中判定：源串 null → false（防御）；调用方已保证 q 非空。 */
    private static boolean contains(@Nullable String text, @NonNull String q) {
        return text != null && indexOfIgnoreCase(text, q) >= 0;
    }

    /** 大小写不敏感子串查找（regionMatches 逐字符，零分配）。找不到返回 -1。 */
    static int indexOfIgnoreCase(String text, String needle) {
        // 委托 FileFilterHelper 单一语义源（find_helper 同源实现，避免双份逻辑漂移）
        return FileFilterHelper.indexOfIgnoreCase(text, needle);
    }
}
