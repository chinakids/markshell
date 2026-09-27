package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * JVM unit tests for language / colour mapping in {@link CodeHighlighter}.
 * These test the pure static logic only — the Android view-dependent
 * {@code highlight()} method is not invoked here.
 */
public class CodeHighlighterTest {

    private static final int COLOR_KEYWORD  = 0xFF569CD6;
    private static final int COLOR_STRING   = 0xFFCE9178;
    private static final int COLOR_DEFAULT  = 0xFFD4D4D4;
    private static final int COLOR_CLASS    = 0xFF4EC9B0;

    // ── resolveLanguage (package-private static) ───────────────────────────

    @Test
    public void mapsKnownExtensions() {
        assertEquals("json", CodeHighlighter.resolveLanguage("config.json"));
        assertEquals("python", CodeHighlighter.resolveLanguage("app.py"));
        assertEquals("markup", CodeHighlighter.resolveLanguage("index.html"));
        assertEquals("markup", CodeHighlighter.resolveLanguage("page.htm"));
        assertEquals("markup", CodeHighlighter.resolveLanguage("layout.xml"));
        assertEquals("markup", CodeHighlighter.resolveLanguage("icon.svg"));
        assertEquals("css", CodeHighlighter.resolveLanguage("style.css"));
        assertEquals("javascript", CodeHighlighter.resolveLanguage("app.js"));
        assertEquals("javascript", CodeHighlighter.resolveLanguage("mod.mjs"));
        assertEquals("javascript", CodeHighlighter.resolveLanguage("comp.jsx"));
        assertEquals("typescript", CodeHighlighter.resolveLanguage("index.ts"));
        assertEquals("typescript", CodeHighlighter.resolveLanguage("comp.tsx"));
        assertEquals("java", CodeHighlighter.resolveLanguage("Main.java"));
    }

    @Test
    public void matchesCaseInsensitively() {
        assertEquals("json", CodeHighlighter.resolveLanguage("DATA.JSON"));
        assertEquals("java", CodeHighlighter.resolveLanguage("Main.JAVA"));
    }

    @Test
    public void unknownFallsBackToJavascript() {
        assertEquals("javascript", CodeHighlighter.resolveLanguage("notes.txt"));
        assertEquals("javascript", CodeHighlighter.resolveLanguage("Makefile"));
        assertEquals("javascript", CodeHighlighter.resolveLanguage("archive.tar.gz"));
    }

    // ── resolveColor (package-private static) ──────────────────────────────

    @Test
    public void directTokenType() {
        assertEquals(COLOR_KEYWORD, CodeHighlighter.resolveColor("keyword"));
        assertEquals(COLOR_STRING, CodeHighlighter.resolveColor("string"));
    }

    @Test
    public void compoundTypeFallsBackToBase() {
        assertEquals(COLOR_KEYWORD, CodeHighlighter.resolveColor("keyword.control"));
        assertEquals(COLOR_CLASS, CodeHighlighter.resolveColor("class-name.builtin"));
    }

    @Test
    public void unknownTypeFallsBackToDefault() {
        assertEquals(COLOR_DEFAULT, CodeHighlighter.resolveColor("made-up-token"));
    }
}
