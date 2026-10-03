package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * NewFileHelper 纯函数单测：名称校验（空/./../斜杠/合法名）与路径拼接（根目录/尾斜杠/trim）。
 */
public class NewFileHelperTest {

    // ── validateName ──────────────────────────────────────────────────────────

    @Test
    public void validateName_nullIsError() {
        assertNotNull(NewFileHelper.validateName(null));
    }

    @Test
    public void validateName_emptyIsError() {
        assertNotNull(NewFileHelper.validateName(""));
    }

    @Test
    public void validateName_blankIsError() {
        assertNotNull(NewFileHelper.validateName("   "));
    }

    @Test
    public void validateName_dotIsError() {
        assertNotNull(NewFileHelper.validateName("."));
    }

    @Test
    public void validateName_dotDotIsError() {
        assertNotNull(NewFileHelper.validateName(".."));
    }

    @Test
    public void validateName_containsSlashIsError() {
        assertNotNull(NewFileHelper.validateName("a/b.txt"));
    }

    @Test
    public void validateName_leadingSlashIsError() {
        assertNotNull(NewFileHelper.validateName("/note.md"));
    }

    @Test
    public void validateName_trailingSlashIsError() {
        assertNotNull(NewFileHelper.validateName("folder/"));
    }

    @Test
    public void validateName_plainFileOk() {
        assertNull(NewFileHelper.validateName("笔记.md"));
    }

    @Test
    public void validateName_spacesInsideOk() {
        assertNull(NewFileHelper.validateName("my notes.md"));
    }

    @Test
    public void validateName_trimmedSurroundingWhitespaceOk() {
        assertNull(NewFileHelper.validateName("  报告.md  "));
    }

    @Test
    public void validateName_hiddenDotFileOk() {
        // 隐藏文件（点开头）允许创建——与「显示隐藏文件」语义一致
        assertNull(NewFileHelper.validateName(".env"));
    }

    @Test
    public void validateName_chineseOk() {
        assertNull(NewFileHelper.validateName("周报 2026-10"));
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
