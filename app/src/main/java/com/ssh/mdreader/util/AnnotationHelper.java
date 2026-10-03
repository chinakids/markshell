package com.ssh.mdreader.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ssh.mdreader.model.AnnotationEntry;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helpers for the annotation system.
 *
 * <p>The new scheme stores annotations in a CSV file only.  The Markdown source
 * file is <em>never modified</em>.  At render time each annotation is located
 * by finding the {@code occurrenceIndex}-th occurrence of {@code originalText}
 * in the rendered TextView output.</p>
 *
 * <p>CSV format (four columns):<br>
 * {@code "id","批注内容","原文片段","出现序号"}</p>
 */
public class AnnotationHelper {

    private static final String TAG = "AnnotationHelper";

    /** CSV header written as the first line of every annotation file. */
    public static final String CSV_HEADER = "\"id\",\"批注内容\",\"原文片段\",\"出现序号\"";

    private AnnotationHelper() {}

    // ── File path ─────────────────────────────────────────────────────────────

    /** Derives the annotation CSV path from the original Markdown file path. */
    @NonNull
    public static String buildAnnotationFilePath(@NonNull String mdFilePath) {
        int lastSlash = Math.max(mdFilePath.lastIndexOf('/'), mdFilePath.lastIndexOf('\\'));
        String dir      = lastSlash >= 0 ? mdFilePath.substring(0, lastSlash + 1) : "";
        String fileName = lastSlash >= 0 ? mdFilePath.substring(lastSlash + 1)    : mdFilePath;
        int dotIdx = fileName.lastIndexOf('.');
        String baseName = dotIdx >= 0 ? fileName.substring(0, dotIdx) : fileName;
        return dir + baseName + "_批注.csv";
    }

    // ── Occurrence helpers ────────────────────────────────────────────────────

    /**
     * Counts how many times {@code needle} appears in {@code text} strictly
     * before index {@code beforeIndex}.
     *
     * @param text        the text to search in
     * @param needle      the substring to count
     * @param beforeIndex exclusive upper bound in {@code text}
     * @return number of non-overlapping occurrences before {@code beforeIndex}
     */
    public static int countOccurrencesBefore(@NonNull String text,
                                              @NonNull String needle,
                                              int beforeIndex) {
        if (needle.isEmpty() || beforeIndex <= 0) return 0;
        int count = 0;
        int from  = 0;
        int limit = Math.min(beforeIndex, text.length());
        while (from < limit) {
            int idx = text.indexOf(needle, from);
            if (idx < 0 || idx >= limit) break;
            count++;
            from = idx + needle.length();
        }
        return count;
    }

    /**
     * Finds the start index of the {@code n}-th (0-based) occurrence of
     * {@code needle} in {@code text}.
     *
     * @return the start index, or {@code -1} if fewer than {@code n+1}
     *         occurrences exist
     */
    public static int findNthOccurrence(@NonNull String text,
                                         @NonNull String needle,
                                         int n) {
        if (needle.isEmpty() || n < 0) return -1;
        int from  = 0;
        int count = 0;
        while (from <= text.length()) {
            int idx = text.indexOf(needle, from);
            if (idx < 0) return -1;
            if (count == n) return idx;
            count++;
            from = idx + needle.length();
        }
        return -1;
    }

    // ── Annotation file serialisation / parsing ───────────────────────────────

    /**
     * Parses the annotation CSV content into entries.
     * Skips the header row and malformed lines.
     * Compatible with legacy three-column files (occurrenceIndex defaults to 0).
     */
    @NonNull
    public static List<AnnotationEntry> parseAnnotationFile(@NonNull String content) {
        List<AnnotationEntry> list = new ArrayList<>();
        String[] lines = content.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.isEmpty()) continue;
            if (i == 0 && isHeaderRow(trimmed)) continue;
            AnnotationEntry entry = AnnotationEntry.parse(trimmed);
            if (entry != null) {
                list.add(entry);
            } else if (!isLegacyFormatLine(trimmed)) {
                // 既不是遗留格式也不是空行 → 数据损坏，记日志便于诊断
                Log.w(TAG, "跳过无法解析的批注行（第 " + (i + 1) + " 行）: " + trimmed);
            }
        }
        return list;
    }

    private static boolean isHeaderRow(@NonNull String line) {
        String norm = line.toLowerCase().replaceAll("[\"\\s]", "");
        return norm.startsWith("id,") || norm.equals("id");
    }

    /** True for legacy line/col format rows (e.g. "L3:5-…") skipped by design. */
    private static boolean isLegacyFormatLine(@NonNull String line) {
        return line.startsWith("L") && line.contains(":");
    }

    /** Serialises the annotations list to CSV with a header on the first line. */
    @NonNull
    public static String formatAnnotationFile(@NonNull List<AnnotationEntry> annotations) {
        StringBuilder sb = new StringBuilder();
        sb.append(CSV_HEADER).append("\n");
        for (AnnotationEntry a : annotations) {
            sb.append(a.format()).append("\n");
        }
        return sb.toString();
    }

    // ── ID generation ─────────────────────────────────────────────────────────

    /** Generates a short unique ID as a base-36 timestamp string. */
    @NonNull
    public static String generateId() {
        return Long.toString(System.currentTimeMillis(), 36);
    }

    // ── Locatability（编辑后批注重定位：失效判定，与 applyAnnotationSpans 跳过语义一致）──

    /**
     * 单条批注能否在给定渲染文本中按 {@link AnnotationEntry#occurrenceIndex}
     * 精确定位。语义与 {@link AnnotationOverlayHelper#applyAnnotationSpans()}
     * 的跳过条件完全一致（原文为空 / 找不到指定次数出现 / 区间越界 → 不可定位）。
     * 单条查询用；批量路径（{@link #countNonFindable} / {@link #nonFindableIds} /
     * {@link #drawerStatuses}）走一次扫描索引 {@link #buildLocator}，避免每条目整文重扫。
     *
     * @return true = 可定位；false = 失效（未找到原文）
     */
    public static boolean isFindable(@Nullable AnnotationEntry entry, @Nullable String plainText) {
        if (entry == null) return false;
        if (entry.originalText == null || entry.originalText.isEmpty()) return false;
        if (plainText == null || plainText.isEmpty()) return false;
        int start = findNthOccurrence(plainText, entry.originalText, entry.occurrenceIndex);
        if (start < 0) return false;
        // 防御：findNthOccurrence 理论不越界；与 span 覆盖的越界检查保持一致
        return start + entry.originalText.length() <= plainText.length();
    }

    /** 无法定位（失效）的批注条数。 */
    public static int countNonFindable(@Nullable List<AnnotationEntry> annotations,
                                       @Nullable String plainText) {
        if (annotations == null || annotations.isEmpty()) return 0;
        int count = 0;
        AnnotationOccurrenceIndex.Locator locator = buildLocator(annotations, plainText);
        for (AnnotationEntry e : annotations) {
            if (e == null || !isFindable(e, locator)) count++;
        }
        return count;
    }

    /** 无法定位（失效）条目的 id 集合（提示/抽屉标注用）。 */
    @NonNull
    public static Set<String> nonFindableIds(@Nullable List<AnnotationEntry> annotations,
                                             @Nullable String plainText) {
        if (annotations == null) return new HashSet<>();
        return nonFindableIds(annotations, buildLocator(annotations, plainText));
    }

    /**
     * 抽屉条目的定位标注状态。
     */
    public enum AnnotationStatus {
        /** 可定位（正常显示）。 */
        OK,
        /** 无法定位（未找到原文，可能因编辑改动失效）。 */
        FAILED
    }

    /**
     * 抽屉列表每条目的定位状态（与 {@code annotations} 同序）——UI 据此标注
     * 「未找到原文」等；判定语义与 {@link #isFindable} 一致（即与
     * {@link AnnotationOverlayHelper#applyAnnotationSpans()} 跳过语义一致）。
     */
    @NonNull
    public static List<AnnotationStatus> drawerStatuses(
            @Nullable List<AnnotationEntry> annotations, @Nullable String plainText) {
        if (annotations == null) return new ArrayList<>();
        return drawerStatuses(annotations, buildLocator(annotations, plainText));
    }

    // ── Occurrence index（专项 B3：O(N×M) → 一次扫描 O(M+命中数)；文本须与索引同源）──────────

    /**
     * 由批注列表构建「一次扫描」定位器（Aho–Corasick 多模式，见 {@link AnnotationOccurrenceIndex}）。
     * 只索引非空原文（null/空跳过=永不匹配），重复原文去重（共享结果）。
     * {@code plainText} 为 null 时按空文本处理（全部不可定位，与字符串版
     * {@link #isFindable(AnnotationEntry, String)} 的 null/空文本分支语义一致）。
     * <b>结果只对本次文本有效</b>：每次调用方基于最新渲染文本重建（一次构建全程复用）。
     */
    @NonNull
    public static AnnotationOccurrenceIndex.Locator buildLocator(
            @Nullable List<AnnotationEntry> annotations, @Nullable String plainText) {
        List<String> needles = new ArrayList<>();
        if (annotations != null) {
            for (AnnotationEntry e : annotations) {
                if (e != null && e.originalText != null && !e.originalText.isEmpty()) {
                    needles.add(e.originalText);
                }
            }
        }
        return AnnotationOccurrenceIndex.build(plainText == null ? "" : plainText, needles);
    }

    /** 与 {@link #isFindable(AnnotationEntry, String)} 语义逐条一致，但查询走一次扫描索引（O(1)）。 */
    public static boolean isFindable(@Nullable AnnotationEntry entry,
                                     @NonNull AnnotationOccurrenceIndex.Locator locator) {
        if (entry == null) return false;
        if (entry.originalText == null || entry.originalText.isEmpty()) return false;
        int start = locator.occurrenceStart(entry.originalText, entry.occurrenceIndex);
        if (start < 0) return false;
        // 防御：与字符串版越界检查保持一致（索引实现在文本内找到，理论不越界）
        return start + entry.originalText.length() <= locator.textLength();
    }

    /** locator 版：一次构建复用（{@link AnnotationOverlayHelper#applyAnnotationSpans()} 等热点路径）。 */
    @NonNull
    public static Set<String> nonFindableIds(@Nullable List<AnnotationEntry> annotations,
                                             @NonNull AnnotationOccurrenceIndex.Locator locator) {
        Set<String> ids = new HashSet<>();
        if (annotations == null) return ids;
        for (AnnotationEntry e : annotations) {
            if (e != null && !isFindable(e, locator)) ids.add(e.id);
        }
        return ids;
    }

    /** locator 版：与本类字符串版语义一致，索引一次构建复用。 */
    @NonNull
    public static List<AnnotationStatus> drawerStatuses(
            @Nullable List<AnnotationEntry> annotations,
            @NonNull AnnotationOccurrenceIndex.Locator locator) {
        List<AnnotationStatus> result = new ArrayList<>();
        if (annotations == null) return result;
        Set<String> failed = nonFindableIds(annotations, locator);
        for (AnnotationEntry e : annotations) {
            result.add(e != null && failed.contains(e.id)
                    ? AnnotationStatus.FAILED : AnnotationStatus.OK);
        }
        return result;
    }

    // ── Export ────────────────────────────────────────────────────────────────

    /** 批注导出格式（纯函数 {@link #buildExportText}，无 Android 依赖）。 */
    public enum ExportFormat { TEXT, HTML, MARKDOWN }

    /**
     * 由批注 CSV 路径反推原 Markdown 文件路径（仅用于导出时展示来源；不校验存在性）。
     * 与 {@link #buildAnnotationFilePath} 互逆。
     */
    @NonNull
    public static String buildMarkdownFilePath(@NonNull String annotationCsvPath) {
        final String suffix = "_批注.csv";
        if (annotationCsvPath.endsWith(suffix)) {
            return annotationCsvPath.substring(0, annotationCsvPath.length() - suffix.length()) + ".md";
        }
        return annotationCsvPath;
    }

    /** 导出批注为文本；来源名省略。 */
    @NonNull
    public static String buildExportText(@NonNull List<AnnotationEntry> annotations,
                                         @NonNull ExportFormat format) {
        return buildExportText(annotations, null, format);
    }

    /**
     * 导出批注为指定格式的纯文本内容（分享/复制用）。
     *
     * <ul>
     *   <li>{@link ExportFormat#TEXT}：纯文本报告，每条含「原文（第 N 次出现）」与批注；</li>
     *   <li>{@link ExportFormat#HTML}：独立 {@code <blockquote>} 批注块（内容做 HTML 转义）；</li>
     *   <li>{@link ExportFormat#MARKDOWN}：{@code > **批注于 <path>**：原文"…" → 批注"…"} 附录行。</li>
     * </ul>
     *
     * <p>不修改源文档；位置以 {@link AnnotationEntry#occurrenceIndex} 的出现序号标注。
     * 条目文本中的换行会被规范化为字面 {@code \n}，避免破坏导出格式。</p>
     */
    @NonNull
    public static String buildExportText(@NonNull List<AnnotationEntry> annotations,
                                         @Nullable String sourceName,
                                         @NonNull ExportFormat format) {
        switch (format) {
            case TEXT:     return buildTextExport(annotations, sourceName);
            case HTML:     return buildHtmlExport(annotations, sourceName);
            case MARKDOWN: return buildMarkdownExport(annotations, sourceName);
        }
        throw new IllegalArgumentException("未知导出格式: " + format);
    }

    private static String buildTextExport(@NonNull List<AnnotationEntry> annotations,
                                          @Nullable String sourceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("批注导出（共 ").append(annotations.size()).append(" 条）\n");
        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("来源：").append(sourceName).append('\n');
        }
        sb.append("================================\n");
        int index = 1;
        for (AnnotationEntry a : annotations) {
            sb.append('\n').append('[').append(index).append("] 原文（第 ")
                    .append(a.occurrenceIndex + 1).append(" 次出现）：「")
                    .append(normalizeForExport(a.originalText)).append("」\n")
                    .append("    批注：「").append(normalizeForExport(a.text)).append("」\n");
            index++;
        }
        return sb.toString();
    }

    private static String buildHtmlExport(@NonNull List<AnnotationEntry> annotations,
                                          @Nullable String sourceName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- 批注导出（共 ").append(annotations.size()).append(" 条）");
        if (sourceName != null && !sourceName.isEmpty()) {
            sb.append("；来源：").append(escapeHtml(sourceName));
        }
        sb.append(" -->\n");
        int index = 1;
        for (AnnotationEntry a : annotations) {
            sb.append("<blockquote>\n")
                    .append("<p><strong>批注 ").append(index).append("</strong>：")
                    .append(escapeHtml(normalizeForExport(a.text))).append("</p>\n")
                    .append("<p>原文（第 ").append(a.occurrenceIndex + 1).append(" 次出现）：")
                    .append(escapeHtml(normalizeForExport(a.originalText))).append("</p>\n")
                    .append("</blockquote>\n");
            index++;
        }
        return sb.toString();
    }

    private static String buildMarkdownExport(@NonNull List<AnnotationEntry> annotations,
                                              @Nullable String sourceName) {
        String where = (sourceName != null && !sourceName.isEmpty())
                ? "批注于 " + sourceName : "批注";
        StringBuilder sb = new StringBuilder();
        for (AnnotationEntry a : annotations) {
            sb.append("> **").append(where).append("**：原文「")
                    .append(normalizeForExport(a.originalText)).append("」 → 批注「")
                    .append(normalizeForExport(a.text)).append("」\n");
        }
        return sb.toString();
    }

    /** 条目文本规范化：统一换行为字面 {@code \n}（两字符），防止破坏导出结构。 */
    @NonNull
    private static String normalizeForExport(@Nullable String s) {
        if (s == null) return "";
        return s.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n");
    }

    /** HTML 转义 &lt;&gt;&amp; 与引号，供 HTML 导出使用。 */
    @NonNull
    private static String escapeHtml(@NonNull String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
