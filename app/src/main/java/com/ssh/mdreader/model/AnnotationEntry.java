package com.ssh.mdreader.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A single annotation record.
 *
 * <p>Stored as a four-column CSV row:
 * {@code "id","批注内容","原文片段","出现序号"}</p>
 *
 * <p>The Markdown source file is never modified.  Position is resolved at
 * render time by finding the {@code occurrenceIndex}-th occurrence of
 * {@code originalText} in the rendered TextView output.</p>
 */
public class AnnotationEntry {

    /** Unique short identifier (base-36 timestamp). */
    public final String id;
    /** The annotation note entered by the user. */
    public final String text;
    /** The original rendered text being annotated. */
    public final String originalText;
    /**
     * 0-based index: which occurrence of {@code originalText} in the rendered
     * document this annotation is attached to.  Used to disambiguate when the
     * same text appears multiple times.
     */
    public final int occurrenceIndex;

    public AnnotationEntry(@NonNull String id, @NonNull String text,
                            @NonNull String originalText, int occurrenceIndex) {
        this.id              = id;
        this.text            = text;
        this.originalText    = originalText;
        this.occurrenceIndex = occurrenceIndex;
    }

    /**
     * 返回批注内容替换后的等价条目（id / originalText / occurrenceIndex 均不变）——
     * 编辑接口：正文定位只依赖原文片段与出现序号，改内容不影响定位与失效状态。
     */
    @NonNull
    public AnnotationEntry withText(@NonNull String newText) {
        return new AnnotationEntry(id, newText, originalText, occurrenceIndex);
    }

    /** Serialises to four-column CSV: "id","text","originalText","occurrenceIndex" */
    @NonNull
    public String format() {
        return csvQuote(id) + ","
                + csvQuote(text) + ","
                + csvQuote(originalText) + ","
                + occurrenceIndex;
    }

    @NonNull
    private static String csvQuote(@NonNull String s) {
        // Escape order matters: backslash first, then newline, then quotes.
        // Newline is stored as the two-char literal "\n" so CSV line-based
        // parsing (split by '\n') stays symmetric. Old files never contain
        // these escape literals, so parsing stays backward compatible.
        return "\"" + s.replace("\\", "\\\\")
                .replace("\n", "\\n")
                .replace("\"", "\"\"") + "\"";
    }

    /**
     * Parses one CSV line.
     * <ul>
     *   <li>New format (4 columns): "id","text","originalText",occurrenceIndex</li>
     *   <li>Legacy 3-column format: "id","text","originalText" (occurrenceIndex defaults to 0)</li>
     *   <li>Legacy line/col format "L3:5-…": returns null (skipped).</li>
     * </ul>
     */
    @Nullable
    public static AnnotationEntry parse(@NonNull String line) {
        if (line.isEmpty()) return null;
        try {
            List<String> fields = parseCsvLine(line);
            if (fields.size() < 2) return null;

            String first = fields.get(0);
            // Skip legacy line/col format entries
            if (first.startsWith("L") && first.contains(":")) return null;

            String id           = first;
            String text         = fields.get(1);
            String originalText = fields.size() >= 3 ? fields.get(2) : "";
            int    occurrence   = 0;
            if (fields.size() >= 4) {
                try { occurrence = Integer.parseInt(fields.get(3).trim()); }
                catch (NumberFormatException ignored) {}
            }
            if (id.isEmpty()) return null;

            return new AnnotationEntry(id, text, originalText, occurrence);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    private static List<String> parseCsvLine(@NonNull String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(sb.toString());
                sb.setLength(0);
            } else if (c == '\\' && i + 1 < line.length()
                    && (line.charAt(i + 1) == 'n' || line.charAt(i + 1) == '\\')) {
                // Symmetric unescape: "\n" -> newline, "\\" -> backslash
                char next = line.charAt(i + 1);
                sb.append(next == 'n' ? '\n' : '\\');
                i++;
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        return fields;
    }
}
