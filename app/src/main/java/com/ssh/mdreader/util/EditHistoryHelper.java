package com.ssh.mdreader.util;

import java.util.LinkedList;

/**
 * 编辑撤销/重做纯函数层（能力发现 #23 编辑撤销/重做）。
 *
 * <p>对标 markor（gsantner/markor）TextViewUndoRedo.java（opoc 库 563 行实现源码实证）语义：
 * <ul>
 *   <li><b>EditHistory</b>=LinkedList + position 游标：position 指向「下一步恢复点」
 *       （undo 回退、redo 前进）；新增编辑截断未来（redo 分支作废）并限容量（trim 最旧）。</li>
 *   <li><b>EditItem</b>=start/before/after 最小化差分（findDiff：剥公共前缀+公共后缀；
 *       纯裁剪/纯追加特例），记录编辑前后文本差异段与选择位置。</li>
 *   <li><b>链合并</b>=连续同类单字符编辑在合并窗口（5s）内合并为一步——「打字流=一步撤销」；
 *       CHAR/SPACE/NL 三类，CHAR 后接 SPACE 合并、SPACE 后接 CHAR 分开。</li>
 * </ul>
 *
 * <p>本层零 Android 依赖（JDK 纯 Java），JVM 可测；时间戳由调用方注入（nowMillis），
 * 保证单测确定性。UI 接线（Editable.replace / TextWatcher / 按钮状态）在
 * MarkdownReaderActivity，经 {@link #undoStep()}/{@link #redoStep()} 取步后应用。
 */
public final class EditHistoryHelper {

    private EditHistoryHelper() {}

    /** 链合并时间窗口（ms）：窗口内相邻同类单字符编辑合并为一步（markor 同值 5000）。 */
    public static final long MERGE_WINDOW_MS = 5000;

    // ── 编辑差分项 ─────────────────────────────────────────────────────────────

    /**
     * 一次编辑操作的最小化差分：文本区间 [start, start+before.length()) 由 before 替换为 after；
     * selBefore/selAfter=编辑前后的光标位置（-1=无记录，应用时保持当前位置）。
     */
    public static final class EditItem {
        public final int start;
        public final String before;
        public final String after;
        public final int selBefore;
        public final int selAfter;

        /**
         * 构造并最小化差分（markor EditItem 语义）：findDiff 剥公共前缀/后缀后只存差异段。
         *
         * @param start      编辑发生位置（原始文本坐标系）
         * @param before     编辑前的整段文本（该次变化被删除的内容）
         * @param after      编辑后的整段文本（该次变化插入的内容）
         * @param selBefore  编辑前光标位置（-1=未知）
         * @param selAfter   编辑后光标位置（-1=未知）
         */
        public EditItem(int start, String before, String after, int selBefore, int selAfter) {
            String b = before == null ? "" : before;
            String a = after == null ? "" : after;
            int[] d = findDiff(b, a);
            this.start = start + d[0];
            this.before = b.substring(d[0], d[1]);
            this.after = a.substring(d[0], d[2]);
            this.selBefore = selBefore;
            this.selAfter = selAfter;
        }

        /** 是否零变更（前后文本一致；如自动更正插入又被撤销）。 */
        public boolean zeroChange() {
            return before.equals(after);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof EditItem)) return false;
            EditItem other = (EditItem) o;
            return start == other.start && before.equals(other.before) && after.equals(other.after);
        }

        @Override
        public int hashCode() {
            return (start * 31 + before.hashCode()) * 31 + after.hashCode();
        }
    }

    // ── 差分定位（markor TextViewUtils.findDiff 语义）─────────────────────────

    /**
     * 求 before→after 的最小单段差异：返回 {a, b, c}，使 before[a:b] 替换为 after[a:c] 后
     * 两者的公共部分保留在差分外（公共前缀+公共后缀剥离；纯追加/纯裁剪/完全相同特例）。
     * 结果语义与 markor {@code TextViewUtils.findDiff(before, after, 0, 0)} 一致。
     */
    public static int[] findDiff(String dest, String source) {
        int dl = dest.length();
        int sl = source.length();
        int minLength = Math.min(dl, sl);

        int start = 0;
        while (start < minLength && dest.charAt(start) == source.charAt(start)) start++;

        if (sl == dl && start == sl) {            // 完全相同
            return new int[]{dl, dl, sl};
        } else if (sl < dl && start == sl) {      // 纯裁剪（source 是 dest 前缀，删除尾部）
            return new int[]{start, dl, sl};
        } else if (dl < sl && start == dl) {      // 纯追加（dest 是 source 前缀，插入尾部）
            return new int[]{dl, dl, sl};
        }

        int end = 0;
        int maxEnd = minLength - start;
        while (end < maxEnd
                && source.charAt(sl - end - 1) == dest.charAt(dl - end - 1)) {
            end++;
        }
        return new int[]{start, dl - end, sl - end};
    }

    // ── 编辑历史（游标 + 截断 + 链合并）───────────────────────────────────────

    /** 字符类型（链合并判定用）：普通字符 / 空格 / 换行。 */
    private static final int CHAR = 0, SPACE = 1, NL = 2;

    private static int typeOf(char c) {
        if (c == '\n') return NL;
        if (c == ' ') return SPACE;
        return CHAR;
    }

    /**
     * 编辑历史栈（markor EditHistory 语义，零 Android 依赖）。
     * 线程模型：与编辑器同线程（主线程），非线程安全——调用方保证串行。
     */
    public static final class EditHistory {

        private final LinkedList<EditItem> history = new LinkedList<>();
        /** 游标：指向「下一步恢复/重做」位置；undo 回退、redo 前进、新增截断未来。 */
        private int position = 0;
        /** 最大历史步数（&lt;0=不限，仅受内存约束）。 */
        private final int maxHistorySize;
        /** 链合并状态：当前是否处于连续同类编辑链中。 */
        private boolean inChain = false;
        /** 上一次记录时间（ms，调用方注入的时钟）。 */
        private long lastTime = 0;

        public EditHistory(int maxHistorySize) {
            this.maxHistorySize = maxHistorySize;
        }

        public boolean canUndo() {
            return position > 0;
        }

        public boolean canRedo() {
            return position < history.size();
        }

        public int size() {
            return history.size();
        }

        /** 清空历史与合并状态（重开编辑会话/加载草稿时调用）。 */
        public void clear() {
            history.clear();
            position = 0;
            inChain = false;
            lastTime = 0;
        }

        /**
         * 记录一次编辑（markor afterTextChanged 语义：最小化+去重+链合并+截断未来+trim）。
         *
         * @param start    编辑位置（before 变换前的起始）
         * @param before   编辑前该区间内容
         * @param after    编辑后该区间内容
         * @param selBefore 编辑前光标（-1=未知）
         * @param selAfter  编辑后光标（-1=未知）
         * @param nowMs    当前时间（调用方注入，单测确定性）。
         */
        public void record(int start, String before, String after,
                           int selBefore, int selAfter, long nowMs) {
            EditItem cur = new EditItem(start, before, after, selBefore, selAfter);
            if (cur.zeroChange()) return;                 // 零变更忽略（自动更正类）
            EditItem prev = (position == history.size() && !history.isEmpty())
                    ? history.getLast() : null;           // 仅「无未来待重做」时才有合并对象
            if (cur.equals(prev)) return;                 // 同一变化被两次通知：去重

            long delta = nowMs - lastTime;
            if (delta < MERGE_WINDOW_MS && prev != null && !prev.zeroChange()) {
                if (tryMergeInsert(prev, cur) || tryMergeDelete(prev, cur)) {
                    lastTime = nowMs;
                    return;
                }
            }

            // 非合并：截断未来（undo 后新编辑令 redo 分支作废）+ 追加 + 限容量
            inChain = false;
            while (history.size() > position) history.removeLast();
            history.add(cur);
            position++;
            trimHistory();
            lastTime = nowMs;
        }

        /** 连续单字符插入合并（markor insChain 语义）：同类链内且位置紧邻。 */
        private boolean tryMergeInsert(EditItem prev, EditItem cur) {
            int pbl = prev.before.length();
            int pal = prev.after.length();
            boolean insChain = pbl == 0 && (inChain || pal == 1);
            boolean singleIns = cur.before.length() == 0 && cur.after.length() == 1;
            boolean insFollows = cur.start == prev.start + pal;
            if (!(singleIns && insChain && insFollows)) return false;

            int chainType = typeOf(prev.after.charAt(pal - 1));
            int insType = typeOf(cur.after.charAt(0));
            if (chainType == insType || (chainType == CHAR && insType == SPACE)) {
                inChain = chainType == insType;           // CHAR 后接 SPACE 结束链
                replaceLast(new EditItem(Math.min(prev.start, cur.start), "",
                        prev.after + cur.after, prev.selBefore, cur.selAfter));
                return true;
            }
            return false;
        }

        /** 连续单字符删除合并（markor delChain 语义）：同类链内且位置紧邻（向前延伸）。 */
        private boolean tryMergeDelete(EditItem prev, EditItem cur) {
            int pal = prev.after.length();
            int pbl = prev.before.length();
            boolean delChain = pal == 0 && (inChain || pbl == 1);
            boolean singleDel = cur.after.length() == 0 && cur.before.length() == 1;
            boolean delFollows = cur.start == prev.start - cur.before.length();
            if (!(singleDel && delChain && delFollows)) return false;

            int chainType = typeOf(prev.before.charAt(0));
            int delType = typeOf(cur.before.charAt(0));
            if (chainType == delType || (chainType == CHAR && delType == SPACE)) {
                inChain = chainType == delType;
                replaceLast(new EditItem(Math.min(prev.start, cur.start),
                        cur.before + prev.before, "", prev.selBefore, cur.selAfter));
                return true;
            }
            return false;
        }

        /** 以合并项替换末位（markor add 合并语义：回退到合并起始、不增加步数）。 */
        private void replaceLast(EditItem merged) {
            history.removeLast();
            history.add(merged);
            trimHistory();
        }

        /** 超出容量移除最旧步（position 同步回退，防游标越界）。 */
        private void trimHistory() {
            if (maxHistorySize < 0) return;
            while (history.size() > maxHistorySize) {
                history.removeFirst();
                position--;
            }
            if (position < 0) position = 0;
        }

        /**
         * 取下一步撤销项并回退游标（应用方负责按 EditItem 还原文本）。
         * 无可撤销返回 null。
         */
        public EditItem undoStep() {
            if (position == 0) return null;
            position--;
            return history.get(position);
        }

        /**
         * 取下一步重做项并前进游标（应用方负责按 EditItem 重放文本）。
         * 无可重做返回 null。
         */
        public EditItem redoStep() {
            if (position >= history.size()) return null;
            EditItem item = history.get(position);
            position++;
            return item;
        }
    }
}
