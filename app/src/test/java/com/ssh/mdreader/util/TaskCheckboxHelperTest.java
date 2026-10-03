package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ssh.mdreader.util.TaskCheckboxHelper.MarkedSource;
import com.ssh.mdreader.util.TaskCheckboxHelper.TaskLine;

import org.junit.Test;

import java.util.List;

/**
 * {@link TaskCheckboxHelper} 纯函数层单测：任务行扫描（marker 谓词对齐/围栏代码跳过/CRLF）、
 * 零宽标记注入、标记解码与任务状态翻转（只改一个字符）。
 */
public class TaskCheckboxHelperTest {

    // ── scan ─────────────────────────────────────────────────────────────────

    @Test
    public void scan_nullOrEmpty_returnsEmpty() {
        assertTrue(TaskCheckboxHelper.scan(null).isEmpty());
        assertTrue(TaskCheckboxHelper.scan("").isEmpty());
    }

    @Test
    public void scan_noTaskLines_returnsEmpty() {
        assertTrue(TaskCheckboxHelper.scan("plain text\n# heading\n").isEmpty());
    }

    @Test
    public void scan_simple_unchecked() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [ ] do a thing");
        assertEquals(1, lines.size());
        TaskLine l = lines.get(0);
        assertEquals(0, l.lineIndex);
        assertEquals(3, l.stateCharIndex);          // "- [ ]" → 状态在偏移 3
        assertEquals(6, l.insertCharIndex);         // "] " 后的空白段终点
        assertFalse(l.checked);
    }

    @Test
    public void scan_checked_xAndX() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [x] done\n- [X] cap");
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).checked);
        assertTrue(lines.get(1).checked);
        assertEquals(14, lines.get(1).stateCharIndex); // "- [x] done\n" = 11 字符，状态在行首 +3
    }

    @Test
    public void scan_starAndPlusMarkers() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("* [ ] star\n+ [ ] plus");
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).lineIndex);
        assertEquals(1, lines.get(1).lineIndex);
    }

    @Test
    public void scan_indentAndBlockquote() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("  - [ ] nested\n> - [ ] quoted");
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).lineIndex);
        assertFalse(lines.get(0).checked);
        assertEquals(1, lines.get(1).lineIndex);
    }

    @Test
    public void scan_noWhitespaceAfterBracket_notTask() {
        // 与 Markwon 谓词对齐：']' 后无空白 = 不是任务项
        assertTrue(TaskCheckboxHelper.scan("- [ ]").isEmpty());
        assertTrue(TaskCheckboxHelper.scan("- [ ]foo").isEmpty());
    }

    @Test
    public void scan_trailingWhitespace_isTask() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [ ] ");
        assertEquals(1, lines.size());
        assertFalse(lines.get(0).checked);
        assertEquals(6, lines.get(0).insertCharIndex); // 尾部空白段终点 = 行尾
    }

    @Test
    public void scan_tabAfterBracket_isTask() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [ ]\titem");
        assertEquals(1, lines.size());
        assertEquals(6, lines.get(0).insertCharIndex); // ']' 后 tab 段终点
    }

    @Test
    public void scan_inlineMarkupStillTask() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [ ] **bold** and [link](u)");
        assertEquals(1, lines.size());
    }

    @Test
    public void scan_fencedCodeTaskLinesSkipped() {
        String src = "```\n- [ ] fake\n```\n- [ ] real";
        List<TaskLine> lines = TaskCheckboxHelper.scan(src);
        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).lineIndex);
    }

    @Test
    public void scan_tildeFencedCodeTaskLinesSkipped() {
        String src = "~~~\n- [ ] fake\n~~~\n- [ ] real";
        List<TaskLine> lines = TaskCheckboxHelper.scan(src);
        assertEquals(1, lines.size());
        assertEquals(3, lines.get(0).lineIndex);
    }

    @Test
    public void scan_crlfLineNumbersAndOffsets() {
        String src = "- [ ] a\r\n- [x] b\r\n";
        List<TaskLine> lines = TaskCheckboxHelper.scan(src);
        assertEquals(2, lines.size());
        assertEquals(0, lines.get(0).lineIndex);
        assertEquals(3, lines.get(0).stateCharIndex);
        assertEquals(1, lines.get(1).lineIndex);
        assertEquals(3 + 9, lines.get(1).stateCharIndex); // "- [ ] a\r\n" 长 9
    }

    @Test
    public void scan_lastLineWithoutTrailingNewline() {
        List<TaskLine> lines = TaskCheckboxHelper.scan("text\n- [ ] last");
        assertEquals(1, lines.size());
        assertEquals(1, lines.get(0).lineIndex);
    }

    @Test
    public void scan_tabInsideBrackets_isTaskUnchecked() {
        // Markwon [xX\s] 类含 tab：括号内 tab = 未勾选任务项
        List<TaskLine> lines = TaskCheckboxHelper.scan("- [\t] odd");
        assertEquals(1, lines.size());
        assertFalse(lines.get(0).checked);
    }

    // ── injectMarkers ────────────────────────────────────────────────────────

    @Test
    public void inject_zeroWidthTokenPerOrdinal() {
        MarkedSource marked = TaskCheckboxHelper.injectMarkers("- [ ] a\n- [x] b\n");
        assertEquals(2, marked.lines.size());
        assertEquals("- [ ] \u200Ba\n- [x] \u200B\u200Bb\n", marked.text);
    }

    @Test
    public void inject_noTasks_identity() {
        String src = "plain\n";
        MarkedSource marked = TaskCheckboxHelper.injectMarkers(src);
        assertSame(src, marked.text);
        assertTrue(marked.lines.isEmpty());
    }

    @Test
    public void inject_nullOrEmpty_handled() {
        MarkedSource marked = TaskCheckboxHelper.injectMarkers(null);
        assertEquals("", marked.text);
        assertTrue(marked.lines.isEmpty());
    }

    @Test
    public void inject_multiLinePositionsStable() {
        String src = "- [ ] a\npara\n- [x] b\n - [ ] c";
        MarkedSource marked = TaskCheckboxHelper.injectMarkers(src);
        assertEquals(3, marked.lines.size());
        // 每处标记 = 序号+1 个零宽字符，且出现在对应行的内容起点
        for (int i = 0; i < 3; i++) {
            // 注入位置按源坐标计算，前面任务行的 token 会整体右移后续行——解码须在
            // 标记实际所在处（渲染侧 spanStart 即如此），用恰好 i+1 长的 token 查找
            String token = repeat(TaskCheckboxHelper.MARKER, i + 1);
            int where = marked.text.indexOf(token);
            assertTrue("i=" + i + " 未找到 token", where >= 0);
            assertEquals(i, TaskCheckboxHelper.decodeMarkerIndex(marked.text, where));
        }
    }

    @Test
    public void inject_fencedCodeLineNotMarked() {
        MarkedSource marked = TaskCheckboxHelper.injectMarkers("```\n- [ ] fake\n```\n- [ ] real\n");
        assertEquals(1, marked.lines.size());
        assertFalse(marked.text.contains(TaskCheckboxHelper.MARKER + "fake"));
        assertTrue(marked.text.contains(TaskCheckboxHelper.MARKER + "real"));
    }

    // ── decodeMarkerIndex ────────────────────────────────────────────────────

    @Test
    public void decode_basic() {
        assertEquals(0, TaskCheckboxHelper.decodeMarkerIndex("\u200Bfoo", 0));
        assertEquals(1, TaskCheckboxHelper.decodeMarkerIndex("\u200B\u200Bbar", 0));
        assertEquals(-1, TaskCheckboxHelper.decodeMarkerIndex("foo", 0));
        assertEquals(-1, TaskCheckboxHelper.decodeMarkerIndex("", 0));
        assertEquals(-1, TaskCheckboxHelper.decodeMarkerIndex("foo", 5));
        assertEquals(-1, TaskCheckboxHelper.decodeMarkerIndex(null, 0));
        assertEquals(-1, TaskCheckboxHelper.decodeMarkerIndex("x", -1));
    }

    @Test
    public void decode_withContentAfter() {
        assertEquals(0, TaskCheckboxHelper.decodeMarkerIndex("\u200Babc\u200B", 0));
        // 只统计连续段：用户正文中的零宽不并入
        assertEquals(0, TaskCheckboxHelper.decodeMarkerIndex("\u200B\u200Btail", 1));
    }

    // ── flipTaskState ────────────────────────────────────────────────────────

    @Test
    public void flip_toggleRoundTrip() {
        String src = "- [ ] a";
        List<TaskLine> lines = TaskCheckboxHelper.scan(src);
        String on = TaskCheckboxHelper.flipTaskState(src, lines.get(0), true);
        assertEquals("- [x] a", on);
        List<TaskLine> lines2 = TaskCheckboxHelper.scan(on);
        assertEquals("- [ ] a", TaskCheckboxHelper.flipTaskState(on, lines2.get(0), false));
    }

    @Test
    public void flip_upperXCheckedWritesCanonicalX() {
        String src = "- [X] a";
        TaskLine l = TaskCheckboxHelper.scan(src).get(0);
        assertEquals("- [x] a", TaskCheckboxHelper.flipTaskState(src, l, true));
        assertEquals("- [ ] a", TaskCheckboxHelper.flipTaskState(src, l, false));
    }

    @Test
    public void flip_preservesEverythingElseIncludingCrlf() {
        String src = "- [ ] a\r\n    - [x] b\r\n";
        List<TaskLine> lines = TaskCheckboxHelper.scan(src);
        assertEquals("- [x] a\r\n    - [x] b\r\n",
                TaskCheckboxHelper.flipTaskState(src, lines.get(0), true));
        assertEquals("- [ ] a\r\n    - [ ] b\r\n",
                TaskCheckboxHelper.flipTaskState(src, lines.get(1), false));
    }

    @Test
    public void flip_defensiveOutOfRange() {
        String src = "- [ ] a";
        assertNull(TaskCheckboxHelper.flipTaskState(null,
                TaskCheckboxHelper.scan(src).get(0), true));
        TaskLine bad = new TaskLine(0, 999, 6, false);
        assertSame(src, TaskCheckboxHelper.flipTaskState(src, bad, true));
        assertSame(src, TaskCheckboxHelper.flipTaskState(src, null, true));
    }

    @Test
    public void flip_noopWhenStateAlreadyMatches() {
        String src = "- [x] a";
        TaskLine l = TaskCheckboxHelper.scan(src).get(0);
        assertSame(src, TaskCheckboxHelper.flipTaskState(src, l, true));
    }

    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }
}