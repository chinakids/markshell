package com.ssh.mdreader.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 「前往路径」纯函数层（无 Android 依赖，便于 JVM 单测）。
 *
 * <p>把用户输入解析为可 stat 的目标路径（绝对路径）：
 * <ul>
 *   <li>以 {@code /} 开头=绝对路径，原样规范化；否则相对当前目录拼接；</li>
 *   <li>支持 {@code .}/{@code ..} 段解析（{@code ..} 到根目录止步，不回退越过根）；</li>
 *   <li>复用 {@link BookmarkHelper#normalizePath} 做规范化（去空白/反斜杠转义/折叠斜杠/去尾斜杠），
 *       不重复实现；{@code ..} 段栈式消解是本层唯一新增语义（书签路径不含相对段，无需此层）。</li>
 * </ul>
 *
 * <p>约定：空输入返回 null（调用方提示后再引导）；返回恒为绝对路径（根为 {@code "/"}）。
 * 所有方法均为纯函数——不修改入参。</p>
 */
public final class GoToPathHelper {

    private GoToPathHelper() {}

    /**
     * 解析「前往路径」输入为绝对目标路径。
     *
     * @param currentPath 当前目录（绝对路径；null/空按根目录 {@code "/"} 处理）
     * @param input       用户输入（null/空白→返回 null）
     * @return 规范化后的绝对路径，或 null（输入无效）
     */
    @Nullable
    public static String resolve(@Nullable String currentPath, @Nullable String input) {
        if (input == null) return null;
        String rel = input.trim().replace('\\', '/');
        if (rel.isEmpty()) return null;

        String combined;
        if (rel.startsWith("/")) {
            combined = rel;
        } else {
            String base = normalizeBase(currentPath);
            combined = base.equals("/") ? "/" + rel : base + "/" + rel;
        }
        return collapseDots(BookmarkHelper.normalizePath(combined));
    }

    /** 目标路径的末级名称（供构造 RemoteFile 用）；根目录/空路径返回空串。 */
    @NonNull
    public static String fileName(@Nullable String path) {
        if (path == null) return "";
        String p = path;
        while (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        int idx = p.lastIndexOf('/');
        if (idx < 0) return p;
        return p.substring(idx + 1);
    }

    /** 当前目录规范化：null/空 → 根；否则委托 BookmarkHelper.normalizePath（防御脏状态）。 */
    @NonNull
    private static String normalizeBase(@Nullable String currentPath) {
        if (currentPath == null) return "/";
        String n = BookmarkHelper.normalizePath(currentPath);
        return n.isEmpty() ? "/" : n;
    }

    /**
     * 栈式消解 {@code .}/{@code ..} 段（输入须已规范化且为绝对路径）。
     * {@code ..} 在根目录止步（root 的父仍是 root）；到根时结果为 {@code "/"}。
     */
    @NonNull
    private static String collapseDots(@NonNull String path) {
        if (path.isEmpty()) return "/";
        String[] segs = path.split("/");
        java.util.ArrayDeque<String> stack = new java.util.ArrayDeque<>();
        for (String s : segs) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!stack.isEmpty()) stack.removeLast();
                continue;
            }
            stack.addLast(s);
        }
        if (stack.isEmpty()) return "/";
        StringBuilder sb = new StringBuilder();
        for (String s : stack) {
            if (sb.length() > 0) sb.append('/');
            sb.append(s);
        }
        return "/" + sb;
    }
}
