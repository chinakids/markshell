package com.ssh.mdreader.util;

import io.noties.prism4j.GrammarUtils;
import io.noties.prism4j.Prism4j;

import java.util.regex.Pattern;

import static io.noties.prism4j.Prism4j.pattern;
import static io.noties.prism4j.Prism4j.token;

/**
 * Custom TypeScript grammar that extends JavaScript.
 * Adds TypeScript-specific keywords, type annotations, and generics.
 */
public class TypeScriptGrammar {

    public static Prism4j.Grammar create(Prism4j prism4j) {
        // Get JavaScript grammar as base
        Prism4j.Grammar jsGrammar = GrammarUtils.require(prism4j, "javascript");

        // Extend JavaScript with TypeScript-specific tokens
        Prism4j.Grammar tsGrammar = GrammarUtils.extend(
            jsGrammar,
            "typescript",
            // TypeScript keywords (added to JavaScript keywords)
            token("keyword", pattern(Pattern.compile(
                "\\b(?:abstract|as|asserts|async|await|break|case|catch|class|const|continue|" +
                "debugger|declare|default|delete|do|else|enum|export|extends|finally|for|from|" +
                "function|get|if|implements|import|in|infer|instanceof|interface|is|keyof|let|" +
                "module|namespace|new|null|of|package|private|protected|public|readonly|require|" +
                "return|satisfies|set|static|super|switch|this|throw|try|type|typeof|undefined|" +
                "unique|unknown|var|void|while|with|yield)\\b"
            ))),
            // Type annotations (generic parameters)
            token("generics", pattern(Pattern.compile(
                "<[^<>]*(?:<[^<>]*>[^<>]*)*>"
            ), false, false, "punctuation")),
            // Type names (PascalCase identifiers used as types)
            token("type-name", pattern(Pattern.compile(
                "\\b[A-Z][a-zA-Z0-9]*\\b"
            ), false, false, "class-name")),
            // Built-in types
            token("builtin-type", pattern(Pattern.compile(
                "\\b(?:any|boolean|number|string|symbol|bigint|object|void|never|unknown|null|undefined)\\b"
            ), false, false, "keyword"))
        );

        // Add type annotation pattern (colon followed by type)
        GrammarUtils.insertBeforeToken(tsGrammar, "punctuation",
            token("type-annotation", pattern(Pattern.compile(
                ":(?:\\s*(?:[^\\s<>\\[\\]{}(),;=]+(?:<[^>]+>)?(?:\\[\\])?)(?:\\s*\\|\\s*[^\\s<>\\[\\]{}(),;=]+(?:<[^>]+>)?(?:\\[\\])?)*)"
            ), false, false, "keyword"))
        );

        return tsGrammar;
    }
}
