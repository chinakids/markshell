package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

/**
 * JVM unit tests for Markdown code-block highlight wiring
 * (第 49 轮：MdCodeHighlightPlugin + CustomGrammarLocator 别名归一化)。
 */
public class MdCodeHighlightPluginTest {

    // ── MdCodeHighlightPlugin.create ─────────────────────────────────────────

    @Test
    public void createReturnsNonNullPlugin() {
        assertNotNull(MdCodeHighlightPlugin.create(0xFF2A2A2A));
    }

    @Test
    public void prism4jInstanceIsSharedSingleton() {
        // CodeHighlighter 与 MdCodeHighlightPlugin 共用同一 Prism4j 实例（单一 locator 缓存）
        assertNotNull(CodeHighlighter.getPrism4j());
        assertSame(CodeHighlighter.getPrism4j(), CodeHighlighter.getPrism4j());
    }

    // ── CustomGrammarLocator.normalizeLanguage 别名归一化 ─────────────────────

    @Test
    public void normalizesCommonAliases() {
        assertEquals("javascript", CustomGrammarLocator.normalizeLanguage("js"));
        assertEquals("javascript", CustomGrammarLocator.normalizeLanguage("jsx"));
        assertEquals("python", CustomGrammarLocator.normalizeLanguage("py"));
        assertEquals("yaml", CustomGrammarLocator.normalizeLanguage("yml"));
        assertEquals("cpp", CustomGrammarLocator.normalizeLanguage("c++"));
        assertEquals("cpp", CustomGrammarLocator.normalizeLanguage("cxx"));
        assertEquals("csharp", CustomGrammarLocator.normalizeLanguage("cs"));
        assertEquals("go", CustomGrammarLocator.normalizeLanguage("golang"));
        assertEquals("kotlin", CustomGrammarLocator.normalizeLanguage("kts"));
        assertEquals("markup", CustomGrammarLocator.normalizeLanguage("html"));
        assertEquals("markup", CustomGrammarLocator.normalizeLanguage("xml"));
        assertEquals("json", CustomGrammarLocator.normalizeLanguage("jsonc"));
        assertEquals("markdown", CustomGrammarLocator.normalizeLanguage("md"));
        assertEquals("typescript", CustomGrammarLocator.normalizeLanguage("ts"));
        assertEquals("typescript", CustomGrammarLocator.normalizeLanguage("tsx"));
        // 第 50 轮：bash/shell 自定义 grammar（别名归一化到正式名 bash）
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("bash"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("sh"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("zsh"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("shell"));
    }

    @Test
    public void normalizesCaseAndWhitespace() {
        assertEquals("javascript", CustomGrammarLocator.normalizeLanguage(" JS "));
        assertEquals("python", CustomGrammarLocator.normalizeLanguage("Py"));
        assertEquals("yaml", CustomGrammarLocator.normalizeLanguage("YML"));
        assertEquals("cpp", CustomGrammarLocator.normalizeLanguage("C++"));
    }

    @Test
    public void unknownLanguageKeptAsLowerTrimmed() {
        // 第 50 轮起 bash/sh/zsh/shell 已归一化为 bash（自定义 grammar），
        // 此处保留尚不支持语言的诚实降级语义=小写原样返回（grammar 查不到=原文直显）
        assertEquals("plaintext", CustomGrammarLocator.normalizeLanguage("plaintext"));
        assertEquals("text", CustomGrammarLocator.normalizeLanguage("TEXT"));
        assertEquals("diff", CustomGrammarLocator.normalizeLanguage(" DIFF "));
        assertNull(CustomGrammarLocator.normalizeLanguage(null));
    }

    @Test
    public void officialNamesPassThrough() {
        assertEquals("java", CustomGrammarLocator.normalizeLanguage("java"));
        assertEquals("python", CustomGrammarLocator.normalizeLanguage("python"));
        assertEquals("sql", CustomGrammarLocator.normalizeLanguage("sql"));
        assertEquals("yaml", CustomGrammarLocator.normalizeLanguage("yaml"));
        assertEquals("kotlin", CustomGrammarLocator.normalizeLanguage("kotlin"));
        assertEquals("go", CustomGrammarLocator.normalizeLanguage("go"));
        assertEquals("csharp", CustomGrammarLocator.normalizeLanguage("csharp"));
        assertEquals("c", CustomGrammarLocator.normalizeLanguage("c"));
    }

    // ── Prism4j grammar 解析链（第 49 轮新增语言 + 别名归一化后真实可查）─

    @Test
    public void newGrammarsResolvableAndAliasesWork() {
        io.noties.prism4j.Prism4j prism = CodeHighlighter.getPrism4j();
        // 本轮新增语言模板在 bundle 内可实例化（非 null grammar）
        assertNotNull(prism.grammar("c"));
        assertNotNull(prism.grammar("cpp"));
        assertNotNull(prism.grammar("csharp"));
        assertNotNull(prism.grammar("go"));
        assertNotNull(prism.grammar("kotlin"));
        assertNotNull(prism.grammar("sql"));
        assertNotNull(prism.grammar("yaml"));
        // 既有语言不受影响
        assertNotNull(prism.grammar("java"));
        assertNotNull(prism.grammar("python"));
        assertNotNull(prism.grammar("javascript"));
        assertNotNull(prism.grammar("markup"));
        assertNotNull(prism.grammar("typescript"));
        // 别名经 locator 归一化后真实可查（js→javascript 等）
        assertSame(prism.grammar("javascript"), prism.grammar("js"));
        assertSame(prism.grammar("python"), prism.grammar("py"));
        assertSame(prism.grammar("yaml"), prism.grammar("yml"));
        assertSame(prism.grammar("cpp"), prism.grammar("c++"));
    }
}
