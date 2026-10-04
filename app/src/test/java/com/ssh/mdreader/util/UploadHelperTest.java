package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * UploadHelper 纯函数单测：本地显示名解析（displayName/lastPathSegment 兜底链）与
 * 上传目标路径构建（目录规范化/拼接委托语义锁定）。
 *
 * <p>语义基准（与 DownloadHelper/NewFileHelper/BookmarkHelper 委托链对齐）：
 * 非空名称一律经 DownloadHelper.suggestFileName 取末级（含 {@code /} 的异常名只取末级
 * =防路径注入）；目标路径经 BookmarkHelper.normalizePath + NewFileHelper.joinPath
 * 拼接（根目录不双斜杠、目录去尾斜杠、名称 trim）。</p>
 */
public class UploadHelperTest {

    // ── resolveDisplayName(displayName, lastPathSegment) ───────────────

    @Test
    public void bothNull_usesFallback() {
        assertEquals("uploaded_file", UploadHelper.resolveDisplayName(null, null));
    }

    @Test
    public void bothBlank_usesFallback() {
        assertEquals("uploaded_file", UploadHelper.resolveDisplayName("", ""));
    }

    @Test
    public void displayNameBlank_withLastPathSegment_usesSegment() {
        assertEquals("photo.png", UploadHelper.resolveDisplayName("   ", "/tmp/photo.png"));
    }

    @Test
    public void displayNameMissing_fallsBackToSegmentLastName() {
        assertEquals("guide.md", UploadHelper.resolveDisplayName(null, "docs/guide.md"));
    }

    @Test
    public void segmentIsRootOnly_delegatesToSuggestChain() {
        // 输入非空 → 委托 DownloadHelper.suggestFileName（/ 提取后为空）→ 委托链末端兜底 remote_file。
        // 与 UploadHelper.DEFAULT_FILE_NAME 的语义区分：DEFAULT 仅用于「输入整体缺失」。
        assertEquals("remote_file", UploadHelper.resolveDisplayName(null, "/"));
    }

    @Test
    public void displayNameWins_overSegment() {
        assertEquals("photo.png", UploadHelper.resolveDisplayName("photo.png", "ignored.txt"));
    }

    @Test
    public void simpleName_preserved() {
        assertEquals("app.log", UploadHelper.resolveDisplayName("app.log", null));
    }

    @Test
    public void spacesInName_preserved() {
        assertEquals("my notes.txt", UploadHelper.resolveDisplayName("my notes.txt", null));
    }

    @Test
    public void chineseName_preserved() {
        assertEquals("翻译稿.md", UploadHelper.resolveDisplayName("翻译稿.md", null));
    }

    @Test
    public void nameWithSlash_onlyLastName_preventsPathInjection() {
        assertEquals("c.log", UploadHelper.resolveDisplayName("a/b/c.log", null));
    }

    @Test
    public void nameWithBackslash_normalizedToLastName() {
        assertEquals("cfg.json", UploadHelper.resolveDisplayName("C:\\temp\\cfg.json", null));
    }

    @Test
    public void nameTrimmedBeforeExtraction() {
        assertEquals("名", UploadHelper.resolveDisplayName("  名 ", null));
    }

    @Test
    public void nameIsSlash_delegatesToSuggestChain() {
        // 同上：非空输入委托 suggestFileName，其「提取后为空」末端兜底=remote_file。
        assertEquals("remote_file", UploadHelper.resolveDisplayName("/", null));
    }

    // ── resolveDisplayName(displayName) 单参便捷版 ──────────────────────

    @Test
    public void singleNull_usesFallback() {
        assertEquals("uploaded_file", UploadHelper.resolveDisplayName(null));
    }

    @Test
    public void singleBlank_usesFallback() {
        assertEquals("uploaded_file", UploadHelper.resolveDisplayName("  "));
    }

    @Test
    public void singleValid_passedThrough() {
        assertEquals("x.txt", UploadHelper.resolveDisplayName("x.txt"));
    }

    // ── buildTargetPath ────────────────────────────────────────────────

    @Test
    public void rootDir_noDoubleSlash() {
        assertEquals("/a.txt", UploadHelper.buildTargetPath("/", "a.txt"));
    }

    @Test
    public void normalDir_pathJoined() {
        assertEquals("/data/a.txt", UploadHelper.buildTargetPath("/data", "a.txt"));
    }

    @Test
    public void dirTrailingSlash_stripped() {
        assertEquals("/data/a.txt", UploadHelper.buildTargetPath("/data/", "a.txt"));
    }

    @Test
    public void dirDoubleSlashes_folded() {
        assertEquals("/data/logs/a.txt", UploadHelper.buildTargetPath("/data//logs", "a.txt"));
    }

    @Test
    public void nullDir_nameOnly() {
        assertEquals("a.txt", UploadHelper.buildTargetPath(null, "a.txt"));
    }

    @Test
    public void emptyDir_nameOnly() {
        assertEquals("a.txt", UploadHelper.buildTargetPath("", "a.txt"));
    }

    @Test
    public void nullDisplayName_usesFallbackName() {
        assertEquals("/data/uploaded_file", UploadHelper.buildTargetPath("/data", null));
    }

    @Test
    public void blankDisplayName_usesFallbackName() {
        assertEquals("/data/uploaded_file", UploadHelper.buildTargetPath("/data", "  "));
    }

    @Test
    public void displayNameWithSpaces_trimmed() {
        assertEquals("/data/nested.txt", UploadHelper.buildTargetPath("/data", " nested.txt "));
    }

    @Test
    public void dirSurroundingSpaces_normalized() {
        assertEquals("/data/x.txt", UploadHelper.buildTargetPath(" /data ", "x.txt"));
    }

    @Test
    public void displayNameWithSlash_onlyLastNameInTarget() {
        assertEquals("/data/b.txt", UploadHelper.buildTargetPath("/data", "a/b.txt"));
    }

    @Test
    public void displayNameBackslash_onlyLastNameInTarget() {
        assertEquals("/data/y.txt", UploadHelper.buildTargetPath("/data", "C:\\x\\y.txt"));
    }

    @Test
    public void deepDir_withChainedDelegates() {
        // 目录规范化（BookmarkHelper）+ 拼接（NewFileHelper）+ 名称取末级（DownloadHelper）链对齐。
        assertEquals("/var/log/nginx/access.log",
                UploadHelper.buildTargetPath("/var/log/nginx/", "archive/access.log"));
    }
}
