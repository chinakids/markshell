package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 阅读进度记忆纯函数层（零 Android 依赖，JVM 单测）。
 *
 * <p>键=按服务器隔离的文件路径复合键（{@code host:port@username|path}），路径经
 * {@link BookmarkHelper#normalizePath} 规范化（与书签/最近打开同语义）。进度值=内容
 * 加载完成后 TextView/ScrollView 的滚动像素 Y（与 Activity 旋转恢复
 * {@code onSaveInstanceState} 存 {@code KEY_SCROLL_Y} 的口径完全一致，仅跨会话时长不同）。</p>
 *
 * <p>所有方法均为纯函数：不修改入参 Map，返回新 Map。容量上限 {@link #MAX_ENTRIES}，
 * 溢出丢弃最先插入（最旧）条目（与最近打开「自清理」同产品决策）。y &lt;= 0（回顶部）
 * 等价于清除条目（不占容量，恢复语义=无记录回顶部）。</p>
 *
 * <p>键分隔符假设：SSH 用户名/主机惯例不含字符 {@code |}；如出现极端用户名含
 * {@code |} 可能产生键歧义（成本过高的转义不在本轮范围，如实标注）。</p>
 */
public final class ReadingProgressHelper {

    /** 全局进度条目上限：超出丢弃最旧。 */
    public static final int MAX_ENTRIES = 100;

    private ReadingProgressHelper() {}

    /** 构造存储键：主机/用户为 null 按空串参与，路径规范化（null→空串）。 */
    @NonNull
    public static String key(@Nullable String host, int port, @Nullable String username,
                             @Nullable String path) {
        String h = host == null ? "" : host;
        String u = username == null ? "" : username;
        String p = BookmarkHelper.normalizePath(path);
        return h + ":" + port + "@" + u + "|" + p;
    }

    /** 读取进度：未记录返回 0（与「无恢复需求」同值）。 */
    public static int get(@Nullable Map<String, Integer> progress, @NonNull String key) {
        if (progress == null) return 0;
        Integer v = progress.get(key);
        return v == null ? 0 : v;
    }

    /**
     * 记录进度：y &lt;= 0 移除条目；否则插入/更新（更新=原有键移除后重插=移到队尾，
     * 即最近使用优先保留）；超出 {@link #MAX_ENTRIES} 丢弃队首（最旧）条目；
     * 返回新 Map，入参不变。
     */
    @NonNull
    public static Map<String, Integer> upsert(@Nullable Map<String, Integer> progress,
                                              @NonNull String key, int y) {
        LinkedHashMap<String, Integer> result = new LinkedHashMap<>();
        if (progress != null) {
            result.putAll(progress);
        }
        result.remove(key);
        if (y > 0) {
            result.put(key, y);
        }
        while (result.size() > MAX_ENTRIES) {
            String oldest = result.keySet().iterator().next();
            result.remove(oldest);
        }
        return result;
    }

    /**
     * 进度合法化：负值→0；超过 {@code maxScrollY}→截断到最大值（文件变短/字号变化等
     * 容器侧兜底）；{@code maxScrollY < 0}（无内容）→0。
     */
    public static int clamp(int y, int maxScrollY) {
        if (y < 0) return 0;
        if (maxScrollY < 0) return 0;
        return y > maxScrollY ? maxScrollY : y;
    }

    /** 是否需要恢复：进度大于 0。 */
    public static boolean isRestorable(int y) {
        return y > 0;
    }
}
