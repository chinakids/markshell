package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import io.noties.prism4j.Prism4j;

/**
 * JVM unit tests for {@link BashGrammar} (第 50 轮：bash/shell 自定义语法高亮)。
 *
 * <p>覆盖：别名归一化（sh/zsh/shell→bash）、grammar 可解析、以及真实 token 化输出
 * （comment/string/variable/keyword/builtin/function/operator 等与官方 Prism bash
 * grammar 行为一致）。token 化断言用 {@link CodeHighlighter#getPrism4j()} 走完整
 * {@link CustomGrammarLocator} 链路=文内代码块与代码查看器同源。
 */
public class BashGrammarTest {

    /** 汇总 (type, alias, matchedText) 三元组，便于断言。 */
    private static List<String[]> collect(Prism4j prism, String code, String lang) {
        Prism4j.Grammar g = prism.grammar(lang);
        assertNotNull("grammar 不应为 null: " + lang, g);
        List<String[]> out = new ArrayList<>();
        walk(out, prism.tokenize(code, g));
        return out;
    }

    private static void walk(List<String[]> out, List<? extends Prism4j.Node> nodes) {
        for (Prism4j.Node n : nodes) {
            if (n instanceof Prism4j.Syntax) {
                Prism4j.Syntax s = (Prism4j.Syntax) n;
                out.add(new String[]{s.type(), s.alias(), s.matchedString()});
                walk(out, s.children());
            }
        }
    }

    private static boolean has(List<String[]> tokens, String type, String text) {
        for (String[] t : tokens) {
            if (type.equals(t[0]) && text.equals(t[2])) return true;
        }
        return false;
    }

    private static boolean hasAliased(List<String[]> tokens, String alias, String text) {
        for (String[] t : tokens) {
            if (alias.equals(t[1]) && text.equals(t[2])) return true;
        }
        return false;
    }

    // ── 别名归一化（CustomGrammarLocator.normalizeLanguage）─────────────────────

    @Test
    public void shellAliasesNormalizeToBash() {
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("bash"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("sh"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("zsh"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("shell"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage("SH"));
        assertEquals("bash", CustomGrammarLocator.normalizeLanguage(" Shell "));
    }

    // ── grammar 可解析（原文直显降级不再触发）──────────────────────────────────

    @Test
    public void bashGrammarResolvableAndAliasesWork() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        assertNotNull(prism.grammar("bash"));
        assertNotNull(prism.grammar("sh"));
        assertNotNull(prism.grammar("zsh"));
        assertNotNull(prism.grammar("shell"));
        // 既有高亮不受影响
        assertNotNull(prism.grammar("java"));
        assertNotNull(prism.grammar("python"));
    }

    // ── 真实 token 化（对齐官方 Prism bash grammar 行为）───────────────────────

    @Test
    public void tokenizesShebangCommentStringAndEnvironment() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        String code = "#!/bin/bash\necho \"hi $USER\" # note\n";
        List<String[]> t = collect(prism, code, "bash");
        assertTrue("shebang 应识别为 important", hasAliased(t, "important", "#!/bin/bash"));
        assertTrue("echo 应为 builtin", has(t, "builtin", "echo"));
        assertTrue("双引号字符串内 $USER 应为 environment",
                hasAliased(t, "constant", "$USER"));
        assertTrue("行尾注释应为 comment", has(t, "comment", "# note"));
    }

    @Test
    public void tokenizesLoopKeywordsAndParameters() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        String code = "for f in *.txt; do\n  ls -la \"$f\"\ndone\n";
        List<String[]> t = collect(prism, code, "bash");
        assertTrue("for 应为 keyword", has(t, "keyword", "for"));
        assertTrue("循环变量 f 应为 variable", hasAliased(t, "variable", "f"));
        assertTrue("ls 应为 function", has(t, "function", "ls"));
        assertTrue("-la 应为 parameter(variable)", hasAliased(t, "variable", "-la"));
        assertTrue("done 应为 keyword", has(t, "keyword", "done"));
    }

    @Test
    public void tokenizesAssignLeftAndRedirection() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        String code = "name=world\ncat > x.log 2>&1\n";
        List<String[]> t = collect(prism, code, "bash");
        assertTrue("赋值左值应为 assign-left", hasAliased(t, "variable", "name"));
        assertTrue("2> 重定向内 2 应为 file-descriptor", hasAliased(t, "important", "2"));
    }

    @Test
    public void tokenizesCommandSubstitution() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        String code = "result=$(date +%s)\n";
        List<String[]> t = collect(prism, code, "bash");
        assertTrue("命令替换整体应为 variable", has(t, "variable", "$(date +%s)"));
        assertTrue("赋值左值 result 应为 assign-left", hasAliased(t, "variable", "result"));
    }

    @Test
    public void tokenizesFunctionDeclarationAndHeredoc() {
        Prism4j prism = CodeHighlighter.getPrism4j();
        String code = "function greet() {\n  echo hi\n}\ncat <<EOF\nhello $NAME\nEOF\n";
        List<String[]> t = collect(prism, code, "bash");
        assertTrue("函数名 greet 应为 function", hasAliased(t, "function", "greet"));
        assertTrue("heredoc 体应为 string", t.stream().anyMatch(x ->
                "string".equals(x[0]) && x[2].startsWith("EOF\n")));
        assertTrue("heredoc 内 $NAME 应为 variable", has(t, "variable", "$NAME"));
        assertFalse("heredoc 标记 EOF 不应误判为 function",
                t.stream().anyMatch(x -> "function".equals(x[0]) && "EOF".equals(x[2])));
    }
}
