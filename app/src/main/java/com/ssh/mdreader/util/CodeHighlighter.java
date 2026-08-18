package com.ssh.mdreader.util;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.noties.prism4j.Prism4j;

/**
 * Professional syntax highlighter powered by Prism4j.
 * Tokenisation is done by Prism4j grammar rules; this class only maps
 * token types to colours and builds the {@link SpannableStringBuilder}.
 *
 * <p>Safe to call from any thread (no Android view dependencies).</p>
 */
public class CodeHighlighter {

    // ── Colour palette (dark-theme, VS Code-inspired) ─────────────────────────
    private static final int COLOR_KEYWORD     = 0xFF569CD6;  // blue
    private static final int COLOR_STRING      = 0xFFCE9178;  // orange-brown
    private static final int COLOR_COMMENT     = 0xFF6A9955;  // green-grey
    private static final int COLOR_NUMBER      = 0xFFB5CEA8;  // light green
    private static final int COLOR_FUNCTION    = 0xFFDCDCAA;  // yellow
    private static final int COLOR_OPERATOR    = 0xFFD4D4D4;  // light grey
    private static final int COLOR_PUNCTUATION = 0xFFD4D4D4;  // light grey
    private static final int COLOR_PROPERTY    = 0xFF9CDCFE;  // light blue
    private static final int COLOR_TAG         = 0xFF569CD6;  // blue
    private static final int COLOR_ATTR_NAME   = 0xFF9CDCFE;  // light blue
    private static final int COLOR_SELECTOR    = 0xFFD7BA7D;  // tan
    private static final int COLOR_BOOLEAN     = 0xFF569CD6;  // blue
    private static final int COLOR_BUILTIN     = 0xFF4EC9B0;  // teal
    private static final int COLOR_CLASS       = 0xFF4EC9B0;  // teal
    private static final int COLOR_REGEX       = 0xFFD16969;  // red-brown
    private static final int COLOR_IMPORTANT   = 0xFF569CD6;  // blue
    private static final int COLOR_CONSTANT    = 0xFF4FC1FF;  // bright blue
    private static final int COLOR_NAMESPACE   = 0xFF4EC9B0;  // teal
    private static final int COLOR_VARIABLE    = 0xFF9CDCFE;  // light blue
    private static final int COLOR_DEFAULT     = 0xFFD4D4D4;  // light grey

    /** Maps Prism4j token types to colours. */
    private static final Map<String, Integer> TYPE_COLORS = new HashMap<>();
    static {
        TYPE_COLORS.put("keyword",     COLOR_KEYWORD);
        TYPE_COLORS.put("string",      COLOR_STRING);
        TYPE_COLORS.put("char",        COLOR_STRING);
        TYPE_COLORS.put("comment",     COLOR_COMMENT);
        TYPE_COLORS.put("number",      COLOR_NUMBER);
        TYPE_COLORS.put("function",    COLOR_FUNCTION);
        TYPE_COLORS.put("operator",    COLOR_OPERATOR);
        TYPE_COLORS.put("punctuation", COLOR_PUNCTUATION);
        TYPE_COLORS.put("property",    COLOR_PROPERTY);
        TYPE_COLORS.put("tag",         COLOR_TAG);
        TYPE_COLORS.put("attr-name",   COLOR_ATTR_NAME);
        TYPE_COLORS.put("attr-value",  COLOR_STRING);
        TYPE_COLORS.put("selector",    COLOR_SELECTOR);
        TYPE_COLORS.put("boolean",     COLOR_BOOLEAN);
        TYPE_COLORS.put("builtin",     COLOR_BUILTIN);
        TYPE_COLORS.put("class-name",  COLOR_CLASS);
        TYPE_COLORS.put("regex",       COLOR_REGEX);
        TYPE_COLORS.put("important",   COLOR_IMPORTANT);
        TYPE_COLORS.put("constant",    COLOR_CONSTANT);
        TYPE_COLORS.put("namespace",   COLOR_NAMESPACE);
        TYPE_COLORS.put("variable",    COLOR_VARIABLE);
        TYPE_COLORS.put("entity",      COLOR_BUILTIN);
        TYPE_COLORS.put("url",         COLOR_STRING);
        TYPE_COLORS.put("parameter",   COLOR_VARIABLE);
        TYPE_COLORS.put("null",        COLOR_BOOLEAN);
        TYPE_COLORS.put("nil",         COLOR_BOOLEAN);
        TYPE_COLORS.put("decorator",   COLOR_FUNCTION);
        TYPE_COLORS.put("annotation",  COLOR_FUNCTION);
        TYPE_COLORS.put("atrule",      COLOR_KEYWORD);
        TYPE_COLORS.put("rule",        COLOR_SELECTOR);
        TYPE_COLORS.put("prolog",      COLOR_COMMENT);
        TYPE_COLORS.put("doctype",     COLOR_COMMENT);
        TYPE_COLORS.put("cdata",       COLOR_COMMENT);
    }

    // ── Prism4j singleton (thread-safe after init) ────────────────────────────
    private static volatile Prism4j prism4j;

    private static Prism4j getPrism4j() {
        if (prism4j == null) {
            synchronized (CodeHighlighter.class) {
                if (prism4j == null) {
                    prism4j = new Prism4j(new CustomGrammarLocator());
                }
            }
        }
        return prism4j;
    }

    // ── File-extension → Prism4j language mapping ─────────────────────────────

    /**
     * Resolves a file name to a Prism4j language identifier.
     * Falls back to a similar language when exact match is unavailable
     * (e.g. TypeScript → JavaScript).
     */
    @NonNull
    private static String resolveLanguage(@NonNull String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".json"))                     return "json";
        if (lower.endsWith(".py"))                       return "python";
        if (lower.endsWith(".html") || lower.endsWith(".htm"))
                                                         return "markup";
        if (lower.endsWith(".xml") || lower.endsWith(".svg"))
                                                         return "markup";
        if (lower.endsWith(".css"))                      return "css";
        if (lower.endsWith(".js") || lower.endsWith(".mjs"))
                                                         return "javascript";
        if (lower.endsWith(".jsx"))                      return "javascript";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx"))
                                                         return "typescript";
        if (lower.endsWith(".java"))                     return "java";
        return "javascript"; // sensible default
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Highlight the source code and return a {@link SpannableStringBuilder}.
     * Safe to call from any thread.
     *
     * @param code     raw source code
     * @param fileName file name (used to resolve language)
     */
    @NonNull
    public static SpannableStringBuilder highlight(@NonNull String code,
                                                    @NonNull String fileName) {
        SpannableStringBuilder ssb = new SpannableStringBuilder(code);
        try {
            Prism4j prism = getPrism4j();
            String language = resolveLanguage(fileName);

            Prism4j.Grammar grammar = prism.grammar(language);
            if (grammar == null) {
                // No grammar for this language — return un-highlighted
                return ssb;
            }

            List<Prism4j.Node> nodes = prism.tokenize(code, grammar);
            applyNodes(ssb, nodes, 0);
        } catch (Throwable e) {
            // Tokenization failed (e.g. malformed HTML, StackOverflowError on
            // deeply nested markup) — return plain text without highlighting
        }
        return ssb;
    }

    // ── Recursive node traversal ──────────────────────────────────────────────

    /**
     * Walks the token tree produced by Prism4j, applying colour spans.
     *
     * @param ssb    the SpannableStringBuilder being built
     * @param nodes  list of Prism4j nodes at this level
     * @param offset current character offset in the source text
     * @return updated offset after processing all nodes
     */
    private static int applyNodes(@NonNull SpannableStringBuilder ssb,
                                   @NonNull List<? extends Prism4j.Node> nodes,
                                   int offset) {
        for (Prism4j.Node node : nodes) {
            if (node instanceof Prism4j.Text) {
                offset += ((Prism4j.Text) node).literal().length();
            } else if (node instanceof Prism4j.Syntax) {
                Prism4j.Syntax syntax = (Prism4j.Syntax) node;
                int start = offset;
                offset = applyNodes(ssb, syntax.children(), offset);

                // Determine colour from token type (may contain dots: "keyword.control")
                int color = resolveColor(syntax.type());
                ssb.setSpan(new ForegroundColorSpan(color),
                        start, offset, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return offset;
    }

    /**
     * Resolves a Prism4j token type string to an ARGB colour.
     * Token types can be compound (e.g. {@code "keyword.control"});
     * we try the full type first, then the base type before the dot.
     */
    private static int resolveColor(@NonNull String type) {
        Integer color = TYPE_COLORS.get(type);
        if (color != null) return color;

        // Try base type: "keyword.control" → "keyword"
        int dot = type.indexOf('.');
        if (dot > 0) {
            color = TYPE_COLORS.get(type.substring(0, dot));
            if (color != null) return color;
        }
        return COLOR_DEFAULT;
    }
}
