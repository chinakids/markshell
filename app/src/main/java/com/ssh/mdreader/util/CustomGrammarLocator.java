package com.ssh.mdreader.util;

import io.noties.prism4j.GrammarLocator;
import io.noties.prism4j.Prism4j;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Wrapper GrammarLocator that delegates to the generated PrismGrammarLocator
 * for most languages, but provides custom TypeScript grammar.
 *
 * <p>第 49 轮起新增语言别名归一化（{@link #normalizeLanguage}）：Markdown 代码块
 * 语言标注的常见别名（js/py/yml/c++/cs/…）先归一化到 Prism4j grammar 正式名再查表，
 * 避免 {@code ```js} 这类高频标注因 grammar 名不匹配而静默不高亮。
 */
public class CustomGrammarLocator implements GrammarLocator {

    private final PrismGrammarLocator generatedLocator;

    public CustomGrammarLocator() {
        this.generatedLocator = new PrismGrammarLocator();
    }

    @Override
    public Prism4j.Grammar grammar(Prism4j prism4j, String language) {
        final String name = normalizeLanguage(language);

        // Handle TypeScript with custom grammar（别名 ts/tsx 已归一化为 typescript）
        if ("typescript".equals(name)) {
            return TypeScriptGrammar.create(prism4j);
        }

        // Delegate to generated locator for all other languages
        return generatedLocator.grammar(prism4j, name);
    }

    @Override
    public Set<String> languages() {
        Set<String> langs = new HashSet<>(generatedLocator.languages());
        langs.add("typescript");
        langs.add("ts");
        langs.add("tsx");
        return langs;
    }

    /**
     * 语言标注别名归一化（纯函数，JVM 可测）：小写+去首尾空白；常见别名映射到
     * Prism4j bundler 正式 grammar 名，未知/暂未支持的（bash/sh/plaintext 等）
     * 保持小写原样返回（grammar 不存在时渲染原文=与未接线行为一致，诚实降级）。
     */
    static String normalizeLanguage(String raw) {
        if (raw == null) return null;
        String name = raw.trim().toLowerCase(Locale.ROOT);
        switch (name) {
            case "js":
            case "jsx":
                return "javascript";
            case "py":
                return "python";
            case "yml":
                return "yaml";
            case "c++":
            case "cxx":
                return "cpp";
            case "cs":
                return "csharp";
            case "golang":
                return "go";
            case "kts":
                return "kotlin";
            case "html":
            case "htm":
            case "xml":
            case "svg":
                return "markup";
            case "jsonc":
                return "json";
            case "md":
                return "markdown";
            case "ts":
            case "tsx":
                return "typescript";
            default:
                return name;
        }
    }
}
