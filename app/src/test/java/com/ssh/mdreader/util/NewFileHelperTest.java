package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import com.ssh.mdreader.R;

import org.junit.Test;

/**
 * NewFileHelper 纯函数单测：名称校验（空/./../斜杠/合法名）与路径拼接（根目录/尾斜杠/trim）。
 *
 * <p>按「返回资源 ID」方案：{@link NewFileHelper#validateName} 合法返回 0、非法返回对应
 * {@code R.string.*} 资源 ID（本类保持无 Context 的 JVM 纯函数可测性，断言资源 ID 常量而非中文字面量）。
 */
public class NewFileHelperTest {

    // ── validateName ──────────────────────────────────────────────────────────

    @Test
    public void validateName_nullIsError() {
        assertEquals(R.string.error_newfile_name_empty, NewFileHelper.validateName(null));
    }

    @Test
    public void validateName_emptyIsError() {
        assertEquals(R.string.error_newfile_name_empty, NewFileHelper.validateName(""));
    }

    @Test
    public void validateName_blankIsError() {
        assertEquals(R.string.error_newfile_name_empty, NewFileHelper.validateName("   "));
    }

    @Test
    public void validateName_dotIsError() {
        assertEquals(R.string.error_newfile_name_dotdot, NewFileHelper.validateName("."));
    }

    @Test
    public void validateName_dotDotIsError() {
        assertEquals(R.string.error_newfile_name_dotdot, NewFileHelper.validateName(".."));
    }

    @Test
    public void validateName_containsSlashIsError() {
        assertEquals(R.string.error_newfile_name_slash, NewFileHelper.validateName("a/b.txt"));
    }

    @Test
    public void validateName_leadingSlashIsError() {
        assertEquals(R.string.error_newfile_name_slash, NewFileHelper.validateName("/note.md"));
    }

    @Test
    public void validateName_trailingSlashIsError() {
        assertEquals(R.string.error_newfile_name_slash, NewFileHelper.validateName("folder/"));
    }

    @Test
    public void validateName_plainFileOk() {
        assertEquals(0, NewFileHelper.validateName("笔记.md"));
    }

    @Test
    public void validateName_spacesInsideOk() {
        assertEquals(0, NewFileHelper.validateName("my notes.md"));
    }

    @Test
    public void validateName_trimmedSurroundingWhitespaceOk() {
        assertEquals(0, NewFileHelper.validateName("  报告.md  "));
    }

    @Test
    public void validateName_hiddenDotFileOk() {
        // 隐藏文件（点开头）允许创建——与「显示隐藏文件」语义一致
        assertEquals(0, NewFileHelper.validateName(".env"));
    }

    @Test
    public void validateName_chineseOk() {
        assertEquals(0, NewFileHelper.validateName("周报 2026-10"));
    }

    // ── joinPath ──────────────────────────────────────────────────────────────

    @Test
    public void joinPath_normalDir() {
        assertEquals("/data/docs/笔记.md", NewFileHelper.joinPath("/data/docs", "笔记.md"));
    }

    @Test
    public void joinPath_rootDir() {
        assertEquals("/note.md", NewFileHelper.joinPath("/", "note.md"));
    }

    @Test
    public void joinPath_trailingSlashDir() {
        assertEquals("/data/docs/note.md", NewFileHelper.joinPath("/data/docs/", "note.md"));
    }

    @Test
    public void joinPath_nullDirTreatedAsCurrent() {
        assertEquals("note.md", NewFileHelper.joinPath(null, "note.md"));
    }

    @Test
    public void joinPath_emptyDirTreatedAsCurrent() {
        assertEquals("note.md", NewFileHelper.joinPath("", "note.md"));
    }

    @Test
    public void joinPath_trimsName() {
        assertEquals("/data/notes.md", NewFileHelper.joinPath("/data", "  notes.md  "));
    }
}
