package com.ssh.mdreader.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * EditHistoryHelper 单测（能力发现 #23 编辑撤销/重做纯函数层）。
 *
 * <p>覆盖语义（与 markor TextViewUndoRedo.java 源码实证对齐）：
 * findDiff=最小差分（公共前缀/后缀剥离+纯裁剪/纯追加/相同特例）；
 * EditItem=差分最小化+zeroChange+equals 去重；
 * EditHistory=游标 undo/redo、新增截断未来、容量 trim、链合并（打字/删除同类合并、空格链、
 * 超窗口分开、undo 后新编辑不再合并且作废 redo）。</p>
 */
public class EditHistoryHelperTest {

    // ── findDiff ─────────────────────────────────────────────────────────────

    @Test
    public void findDiff_identical_returnsSameRegion() {
        int[] d = EditHistoryHelper.findDiff("abc", "abc");
        assertEquals(3, d[0]);
        assertEquals(3, d[1]);
        assertEquals(3, d[2]);
    }

    @Test
    public void findDiff_pureInsertion_appendsAtEnd() {
        // "ab" -> "abc"：纯追加（dest 是 source 前缀）
        int[] d = EditHistoryHelper.findDiff("ab", "abc");
        assertEquals(2, d[0]);
        assertEquals(2, d[1]);
        assertEquals(3, d[2]);
    }

    @Test
    public void findDiff_pureDeletion_cropsSuffix() {
        // "abc" -> "ab"：纯裁剪
        int[] d = EditHistoryHelper.findDiff("abc", "ab");
        assertEquals(2, d[0]);
        assertEquals(3, d[1]);
        assertEquals(2, d[2]);
    }

    @Test
    public void findDiff_middleSubstitution_keepsCommonOutside() {
        // "hello" -> "heXXo"：中段替换，公共前缀 he/公共后缀 o 剥除
        int[] d = EditHistoryHelper.findDiff("hello", "heXXo");
        assertEquals(2, d[0]);
        assertEquals(4, d[1]);
        assertEquals(4, d[2]);
    }

    @Test
    public void findDiff_prefixInsertion_keepsSuffix() {
        // "world" -> "hello world"：前缀插入
        int[] d = EditHistoryHelper.findDiff("world", "hello world");
        assertEquals(0, d[0]);
        assertEquals(0, d[1]);
        assertEquals(6, d[2]);
    }

    @Test
    public void findDiff_fullyDifferent_wholeRegion() {
        int[] d = EditHistoryHelper.findDiff("abc", "xyz");
        assertEquals(0, d[0]);
        assertEquals(3, d[1]);
        assertEquals(3, d[2]);
    }

    // ── EditItem 最小化 ─────────────────────────────────────────────────────

    @Test
    public void editItem_minimizesDiff_keepsOnlyChangedSegment() {
        // 全文本 "abcdef" -> "abcXef"：仅中段 'd' 替换为 'X'
        EditHistoryHelper.EditItem item =
                new EditHistoryHelper.EditItem(0, "abcdef", "abcXef", 3, 3);
        assertEquals(3, item.start);
        assertEquals("d", item.before);
        assertEquals("X", item.after);
    }

    @Test
    public void editItem_zeroChange_detected() {
        EditHistoryHelper.EditItem item =
                new EditHistoryHelper.EditItem(0, "ab", "ab", 1, 1);
        assertTrue(item.zeroChange());
    }

    @Test
    public void editItem_nullBeforeAfter_treatedAsEmpty() {
        EditHistoryHelper.EditItem item =
                new EditHistoryHelper.EditItem(0, null, "x", 0, 1);
        assertEquals("", item.before);
        assertEquals("x", item.after);
        assertFalse(item.zeroChange());
    }

    @Test
    public void editItem_equals_comparesOnlyDiffSelectionIgnored() {
        EditHistoryHelper.EditItem a =
                new EditHistoryHelper.EditItem(0, "ab", "axb", 1, 2);
        EditHistoryHelper.EditItem b =
                new EditHistoryHelper.EditItem(0, "ab", "axb", 9, 9);
        assertEquals(a, b);   // 选择位置不同仍视为同一变化（markor equals 语义）
    }

    // ── EditHistory 游标：undo/redo ────────────────────────────────────────

    @Test
    public void history_singleInsert_undoRestoresRedoReapplies() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(2, "", "X", 2, 3, 1000L);   // "hello" -> "heXllo"
        assertTrue(h.canUndo());
        assertFalse(h.canRedo());

        StringBuilder sb = new StringBuilder("heXllo");
        EditHistoryHelper.EditItem u = h.undoStep();
        applyUndo(sb, u);
        assertEquals("hello", sb.toString());          // 插入被撤销
        assertFalse(h.canUndo());
        assertTrue(h.canRedo());

        EditHistoryHelper.EditItem r = h.redoStep();
        applyRedo(sb, r);
        assertEquals("heXllo", sb.toString());         // 重做恢复
        assertFalse(h.canRedo());
    }

    @Test
    public void history_undoPastStart_returnsNull() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        assertNull(h.undoStep());          // 空历史无可撤销
        assertFalse(h.canUndo());
    }

    @Test
    public void history_redoPastEnd_returnsNull() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(0, "", "a", 0, 1, 1000L);
        h.undoStep();
        EditHistoryHelper.EditItem r1 = h.redoStep();
        assertEquals("a", r1.after);
        assertNull(h.redoStep());          // 重做到尽头后无可重做
        assertFalse(h.canRedo());
    }

    @Test
    public void history_newEditAfterUndo_truncatesFuture() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(0, "", "a", 0, 1, 1000L);
        // b 在合并窗口外记录：独立一步（窗口内会与 a 合并成打字链）
        h.record(1, "", "b", 1, 2, 1000L + EditHistoryHelper.MERGE_WINDOW_MS + 1);
        assertEquals(2, h.size());
        h.undoStep();                      // 撤销 'b' → "a"
        assertTrue(h.canRedo());
        // undo 之后打新字符 'Z'：redo（b）作废
        h.record(1, "", "Z", 1, 2, 3000L); // "a" -> "aZ"
        assertFalse(h.canRedo());

        StringBuilder sb = new StringBuilder("aZ");
        EditHistoryHelper.EditItem u1 = h.undoStep();   // 撤销 'Z'
        applyUndo(sb, u1);
        assertEquals("a", sb.toString());
        EditHistoryHelper.EditItem u2 = h.undoStep();   // 撤销 'a'
        applyUndo(sb, u2);
        assertEquals("", sb.toString());
        assertNull(h.undoStep());
    }

    @Test
    public void history_clearResetsEverything() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(0, "", "a", 0, 1, 1000L);
        h.clear();
        assertFalse(h.canUndo());
        assertFalse(h.canRedo());
        assertEquals(0, h.size());
    }

    @Test
    public void history_maxSize_trimsOldestAndKeepsCursorValid() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(3);
        // 用非单字符「粘贴」操作记录，避免打字链合并成一步
        h.record(0, "", "ab", 0, 2, 1000L);
        h.record(2, "", "cd", 2, 4, 1001L);
        h.record(4, "", "ef", 4, 6, 1002L);
        h.record(6, "", "gh", 6, 8, 1003L);
        assertEquals(3, h.size());
        assertTrue(h.canUndo());
        // 最旧一步 'ab' 被 trim：从后往前撤销只能得到 gh/ef/cd
        EditHistoryHelper.EditItem u1 = h.undoStep();
        assertEquals("gh", u1.after);
        EditHistoryHelper.EditItem u2 = h.undoStep();
        assertEquals("ef", u2.after);
        EditHistoryHelper.EditItem u3 = h.undoStep();
        assertEquals("cd", u3.after);
        assertNull(h.undoStep());
    }

    // ── 链合并：打字/删除合并为一步 ────────────────────────────────────────

    @Test
    public void chain_typingLetters_mergesIntoOneUndoStep() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        long t = 1000L;
        for (int i = 0; i < 5; i++) {   // 连续插入 a b c d e（窗口内同步进）
            h.record(i, "", "abcde".substring(i, i + 1), i, i + 1, t + i);
        }
        // 5 次打字合并为一步：undo 一次清空全部
        assertFalse(h.canRedo());
        EditHistoryHelper.EditItem u = h.undoStep();
        assertEquals("abcde", u.after);
        assertEquals("", u.before);
        assertFalse(h.canUndo());       // 合并后仅剩一步
    }

    @Test
    public void chain_typingWithSpaces_spaceContinuesChainThenCharBreaks() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        // 序列 a b 空格 c：a+b 合并；空格并入（CHAR→SPACE）；'c' 在空格后=新链
        h.record(0, "", "a", 0, 1, 1000L);
        h.record(1, "", "b", 1, 2, 1001L);
        h.record(2, "", " ", 2, 3, 1002L);
        h.record(3, "", "c", 3, 4, 1003L);
        EditHistoryHelper.EditItem u1 = h.undoStep();
        assertEquals("c", u1.after);     // 'c' 独立一步
        EditHistoryHelper.EditItem u2 = h.undoStep();
        assertEquals("ab ", u2.after);   // a+b+空格合并步
        assertFalse(h.canUndo());
    }

    @Test
    public void chain_backspaceLetters_mergesIntoOneUndoStep() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        long t = 1000L;
        // 从 "hello"（索引 0..4）尾部开始连续 Backspace：o l l e h
        int[] starts = {4, 3, 2, 1, 0};
        String[] dels = {"o", "l", "l", "e", "h"};
        for (int i = 0; i < starts.length; i++) {
            int s = starts[i];
            h.record(s, dels[i], "", s, s, t + i);
        }
        EditHistoryHelper.EditItem u = h.undoStep();
        assertFalse(h.canUndo());
        // 一次撤销恢复全部被删字符（合并 before=hello，按原文本顺序拼接）
        assertEquals("hello", applyUndoTo("", u));
    }

    @Test
    public void chain_deleteLetterThenSpace_merges() {
        // 文本 "a e"：删 'e'（CHAR）后删空格（SPACE）：CHAR+SPACE 允许合并
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(2, "e", "", 2, 2, 1000L);   // "a e" -> "a "
        h.record(1, " ", "", 1, 1, 1001L);   // "a "  -> "a"
        EditHistoryHelper.EditItem u = h.undoStep();
        assertEquals("a e", applyUndoTo("a", u));   // 合并步一次恢复 " e"
        assertFalse(h.canUndo());
    }

    @Test
    public void chain_afterMergeWindow_doesNotMerge() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(0, "", "a", 0, 1, 1000L);
        h.record(1, "", "b", 1, 2, 1000L + EditHistoryHelper.MERGE_WINDOW_MS + 1);
        // 超窗口：两步独立
        assertTrue(h.canUndo());
        EditHistoryHelper.EditItem u1 = h.undoStep();
        assertEquals("b", u1.after);
        EditHistoryHelper.EditItem u2 = h.undoStep();
        assertEquals("a", u2.after);
    }

    @Test
    public void chain_zeroChangeIgnored() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(2, "ab", "ab", 2, 2, 1000L);   // 零变更
        assertFalse(h.canUndo());
    }

    @Test
    public void chain_duplicateNotificationIgnored() {
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(0, "", "x", 0, 1, 1000L);
        h.record(0, "", "x", 0, 1, 1001L);     // 同一变化二次通知：去重
        assertEquals(1, h.size());
    }

    @Test
    public void chain_undoThenRepeatedChange_notMergedIntoLast() {
        // undo 后（position<size）新记录不与旧末位合并（prev=null 语义）
        EditHistoryHelper.EditHistory h = new EditHistoryHelper.EditHistory(100);
        h.record(2, "", "a", 2, 3, 1000L);
        h.undoStep();
        h.record(2, "", "b", 2, 3, 1001L);
        assertFalse(h.canRedo());
        EditHistoryHelper.EditItem u = h.undoStep();
        assertEquals("b", u.after);
    }

    // ── 模拟应用（与接线层 replace 语义一致）───────────────────────────────

    /** 模拟 undo 应用：把 [start, start+after.length) 替换为 before（坐标按界 clamp）。 */
    private static void applyUndo(StringBuilder sb, EditHistoryHelper.EditItem item) {
        int s = Math.min(item.start, sb.length());
        int e = Math.min(s + item.after.length(), sb.length());
        sb.replace(s, e, item.before);
    }

    /** 模拟 redo 应用：把 [start, start+before.length) 替换为 after（坐标按界 clamp）。 */
    private static void applyRedo(StringBuilder sb, EditHistoryHelper.EditItem item) {
        int s = Math.min(item.start, sb.length());
        int e = Math.min(s + item.before.length(), sb.length());
        sb.replace(s, e, item.after);
    }

    /** 应用 undo 并返回结果串（测试断言用）。 */
    private static String applyUndoTo(String content, EditHistoryHelper.EditItem item) {
        StringBuilder sb = new StringBuilder(content);
        applyUndo(sb, item);
        return sb.toString();
    }
}
