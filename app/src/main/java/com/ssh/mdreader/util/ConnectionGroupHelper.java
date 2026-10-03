package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.SshConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 连接分组纯函数层（无 Android 依赖，可 JVM 单测）。
 *
 * <p>分组模型：{@link SshConfig} 上的 {@code group} 字符串字段，null/空白=未分组。
 * 分组元数据（组列表 CRUD）由 {@link PreferenceManager} 持久化；本类只负责
 * 组名规范化/去重、按组分桶、重命名/删除组时对连接列表的纯变换。</p>
 *
 * <p>约定：组名一律经 {@link #normalizeGroupName} 规范化（去首尾空白；空白=未分组）；
 * 所有方法均为纯函数——不修改入参列表，返回新列表/新映射。</p>
 */
public final class ConnectionGroupHelper {

    /** 未分组在分桶结果中的 key（组名规范化后的空串）。 */
    public static final String UNGROUPED = "";

    private ConnectionGroupHelper() {}

    // ── 组名规范化 ───────────────────────────────────────────────────────────

    /**
     * 规范化组名：去首尾空白；null/空白返回 {@link #UNGROUPED}（=未分组）。
     * 组名内部空白（如「生产 环境」）允许保留。
     */
    @NonNull
    public static String normalizeGroupName(@Nullable String name) {
        if (name == null) return UNGROUPED;
        return name.trim();
    }

    /** 指定连接的规范化组名（未分组=空串）。 */
    @NonNull
    public static String groupOf(@Nullable SshConfig config) {
        if (config == null) return UNGROUPED;
        return normalizeGroupName(config.getGroup());
    }

    /** 是否为未分组连接（组名 null/空白）。 */
    public static boolean isUngrouped(@Nullable SshConfig config) {
        return groupOf(config).isEmpty();
    }

    // ── 组名去重 ─────────────────────────────────────────────────────────────

    /**
     * 规范化并去重组名列表（保留首次出现顺序）；空白组名（未分组）剔除；
     * null 入参返回空列表。不修改入参列表。
     */
    @NonNull
    public static List<String> dedupeGroupNames(@Nullable List<String> names) {
        List<String> result = new ArrayList<>();
        if (names == null) return result;
        for (String n : names) {
            String normalized = normalizeGroupName(n);
            if (!normalized.isEmpty() && !result.contains(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    // ── 分桶 ─────────────────────────────────────────────────────────────────

    /**
     * 按组名分桶：key=规范化组名（{@link #UNGROUPED}=未分组），保持连接原顺序；
     * 桶内列表为原列表元素的拷贝（元素引用不变），不修改入参。
     * 仅当某组至少有一条连接时才建桶；空列表返回空映射；null 入参视为空列表。
     */
    @NonNull
    public static Map<String, List<SshConfig>> bucketByGroup(@Nullable List<SshConfig> connections) {
        Map<String, List<SshConfig>> buckets = new LinkedHashMap<>();
        if (connections == null) return buckets;
        for (SshConfig c : connections) {
            buckets.computeIfAbsent(groupOf(c), k -> new ArrayList<>()).add(c);
        }
        return buckets;
    }

    // ── 组变换（重命名/删除） ───────────────────────────────────────────────

    /**
     * 重命名组：组名 == oldName（规范化后比较）的连接全部改为 newName（规范化后写入，
     * newName 为空=退回未分组）。oldName 为空（=未分组区）不做变换（未分组不是组）。
     * 若 oldName==newName（规范化后）或无匹配连接，返回与原列表等价的拷贝。
     * 不修改入参列表。
     */
    @NonNull
    public static List<SshConfig> renameGroupInList(@Nullable List<SshConfig> connections,
                                                    @Nullable String oldName,
                                                    @Nullable String newName) {
        String old = normalizeGroupName(oldName);
        String target = normalizeGroupName(newName);
        List<SshConfig> result = new ArrayList<>();
        if (connections == null) return result;
        if (old.isEmpty() || old.equals(target)) {
            result.addAll(connections);
            return result;
        }
        for (SshConfig c : connections) {
            if (old.equals(groupOf(c))) {
                SshConfig updated = copyWithGroup(c, target.isEmpty() ? null : target);
                result.add(updated);
            } else {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 删除组：组名 == groupName（规范化后比较）的连接退回未分组（group 置 null），
     * 其余连接原样保留。groupName 为空时无操作。不修改入参列表。
     */
    @NonNull
    public static List<SshConfig> ungroupAllInList(@Nullable List<SshConfig> connections,
                                                   @Nullable String groupName) {
        String group = normalizeGroupName(groupName);
        List<SshConfig> result = new ArrayList<>();
        if (connections == null) return result;
        if (group.isEmpty()) {
            result.addAll(connections);
            return result;
        }
        for (SshConfig c : connections) {
            if (group.equals(groupOf(c))) {
                result.add(copyWithGroup(c, null));
            } else {
                result.add(c);
            }
        }
        return result;
    }

    /**
     * 合并组名展示序列：metadata（分组元数据，保序，可含空组）在前，
     * 再追加连接中派生但 metadata 没有的组名（按首次出现顺序）；空白剔除。
     * 供列表页决定「展示哪些组头」。
     */
    @NonNull
    public static List<String> mergeGroupNames(@Nullable List<String> metadata,
                                               @Nullable List<SshConfig> connections) {
        LinkedHashSet<String> names = new LinkedHashSet<>(dedupeGroupNames(metadata));
        if (connections != null) {
            for (SshConfig c : connections) {
                String g = groupOf(c);
                if (!g.isEmpty()) {
                    names.add(g);
                }
            }
        }
        return new ArrayList<>(names);
    }

    // ── 内部工具 ─────────────────────────────────────────────────────────────

    /** 拷贝连接并替换分组字段（其余字段引用共享；SshConfig 为可变 bean，仅复制分组值）。 */
    @NonNull
    private static SshConfig copyWithGroup(@NonNull SshConfig source, @Nullable String group) {
        // SshConfig 浅拷贝足够：group 为不可变 String，其余字段不变
        SshConfig copy = new SshConfig(source.getAlias(), source.getHost(), source.getPort(),
                source.getUsername(), source.getPassword(), source.getRemotePath());
        copy.setAuthMode(source.getAuthMode());
        copy.setPrivateKey(source.getPrivateKey());
        copy.setKeyPassphrase(source.getKeyPassphrase());
        copy.setGroup(group);
        return copy;
    }
}
