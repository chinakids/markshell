package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 批注原文的多模式一次扫描定位器（Aho–Corasick 自动机，纯 Java、零依赖、JVM 可测）。
 *
 * <p><b>背景（专项 B3）</b>：原 {@link AnnotationHelper#findNthOccurrence} 对每条目分别从
 * 文本头重新 indexOf 扫描，渲染 N 条批注 = O(N×M)（M = 文本长度）；长文 + 多批注时延迟明显。
 * 本类把全部批注原文（needle）一次性构造成 AC 自动机，对文本<b>单次扫描</b>输出所有 needle
 * 的<b>非重叠</b>出现起点列表——构建+扫描 O(M + 命中数 + Σ长度)，查询 O(1)（map 查找）。
 * </p>
 *
 * <p><b>语义契约</b>：必须与 {@link AnnotationHelper#findNthOccurrence} 逐字符一致——
 * 非重叠出现（相邻允许、重叠跳过）；空/null needle、负序号或第 n 次不存在 → -1。AC 天然产出
 * <b>全部</b>（含重叠）出现，本类在扫描时按「上一保留起点 + needle 长度」贪心过滤，与
 * {@code String.indexOf(from = 上一命中末尾)} 的语义等价（对拍单测保证）。</p>
 *
 * <p>结果只对构建时的 text 有效；文本变化必须重建（{@link #build}）。同一 needle 多条批注
 * 共享一份结果（去重索引）。</p>
 */
public final class AnnotationOccurrenceIndex {

    private AnnotationOccurrenceIndex() {}

    /** 一次扫描索引（持有全部 needle 的非重叠出现起点；只对构建时的 text 有效）。 */
    public static final class Locator {
        private final String text;
        private final Map<String, int[]> startsByNeedle;

        Locator(@NonNull String text, @NonNull Map<String, int[]> startsByNeedle) {
            this.text = text;
            this.startsByNeedle = startsByNeedle;
        }

        /** 构建时文本长度（span 覆盖越界防御判定用）。 */
        public int textLength() {
            return text.length();
        }

        /**
         * 第 {@code n} 次（0 基）<b>非重叠</b>出现的起点字符偏移；无出现/未知/空 needle 或
         * n&lt;0 → -1。与 {@link AnnotationHelper#findNthOccurrence} 语义一致。
         */
        public int occurrenceStart(@Nullable String needle, int n) {
            if (needle == null || n < 0) return -1;
            int[] starts = startsByNeedle.get(needle);
            if (starts == null || n >= starts.length) return -1;
            return starts[n];
        }

        /** 指定 needle 的全部非重叠出现起点（升序）；未索引/空/null needle → 空列表。 */
        @NonNull
        public List<Integer> occurrences(@Nullable String needle) {
            if (needle == null) return Collections.emptyList();
            int[] arr = startsByNeedle.get(needle);
            if (arr == null) return Collections.emptyList();
            List<Integer> list = new ArrayList<>(arr.length);
            for (int v : arr) list.add(v);
            return list;
        }
    }

    /**
     * 构建 AC 索引并对 {@code text} 做一次扫描（null 按空串处理：无任何出现）。
     *
     * @param text    待定位的渲染文本（可为空串/null）
     * @param needles 全部批注原文片段（可为空集合；null/空 needle 跳过=永不匹配；重复去重）
     */
    @NonNull
    public static Locator build(@NonNull String text, @NonNull Collection<String> needles) {
        String t = text == null ? "" : text;
        Node root = new Node();

        // 去重后插入 trie（同原文多条批注 → 索引一份、结果共享）
        Set<String> distinct = new LinkedHashSet<>();
        for (String needle : needles) {
            if (needle != null && !needle.isEmpty()) distinct.add(needle);
        }
        Map<String, List<Integer>> acc = new HashMap<>();
        for (String needle : distinct) {
            Node node = root;
            for (int i = 0; i < needle.length(); i++) {
                char c = needle.charAt(i);
                Node child = node.children.get(c);
                if (child == null) {
                    child = new Node();
                    node.children.put(c, child);
                }
                node = child;
            }
            node.outputs.add(needle);
            acc.put(needle, new ArrayList<>());
        }

        // BFS 构建失败链接（root 层 children 的 fail = root；其余按「最长真后缀」走 fail 链）
        ArrayDeque<Node> queue = new ArrayDeque<>();
        for (Node child : root.children.values()) {
            child.fail = root;
            queue.add(child);
        }
        while (!queue.isEmpty()) {
            Node cur = queue.poll();
            for (Map.Entry<Character, Node> e : cur.children.entrySet()) {
                char c = e.getKey();
                Node child = e.getValue();
                Node f = cur.fail;
                while (f != null && !f.children.containsKey(c)) f = f.fail;
                child.fail = (f != null) ? f.children.get(c) : root;
                queue.add(child);
            }
        }

        // 单次扫描：标准 AC 匹配；输出经失败链收集；非重叠过滤与 findNthOccurrence 等价
        Node state = root;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            while (state != root && !state.children.containsKey(c)) state = state.fail;
            Node next = state.children.get(c);
            if (next != null) state = next;
            for (Node out = state; out != root; out = out.fail) {
                for (String needle : out.outputs) {
                    int start = i - needle.length() + 1;
                    List<Integer> list = acc.get(needle);
                    if (list == null) continue; // 防御：不应发生
                    if (!list.isEmpty()
                            && start < list.get(list.size() - 1) + needle.length()) {
                        continue; // 重叠出现 → 跳过（等价于 indexOf(from=末命中+len)）
                    }
                    list.add(start);
                }
            }
        }

        Map<String, int[]> startsByNeedle = new HashMap<>();
        for (Map.Entry<String, List<Integer>> e : acc.entrySet()) {
            List<Integer> list = e.getValue();
            int[] arr = new int[list.size()];
            for (int k = 0; k < arr.length; k++) arr[k] = list.get(k);
            startsByNeedle.put(e.getKey(), arr);
        }
        return new Locator(t, startsByNeedle);
    }

    /** AC 自动机节点。 */
    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        /** 以本节点结尾的 needle（空 = 非终止节点）。 */
        final List<String> outputs = new ArrayList<>();
        /** 失败链接（BFS 后除 root 外均非 null；扫描时输出经此链收集）。 */
        Node fail;
    }
}
