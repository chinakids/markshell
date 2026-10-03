package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * {@link AnnotationOccurrenceIndex} 单测：一次扫描索引的定位语义——与
 * {@link AnnotationHelper#findNthOccurrence} 逐字符对拍（含重叠/相邻/失败链/随机 fuzz），
 * 及边界（空/null needle、越界序号、文本绑定）。专项 B3。
 */
public class AnnotationOccurrenceIndexTest {

    /** "aaa" 出现于 0/8/16，"bbb"@4，"ccc"@12。 */
    private static final String TEXT = "aaa bbb aaa ccc aaa";

    // ── 基础定位 ─────────────────────────────────────────────────────────────

    @Test
    public void singleNeedleNthStartsMatchReference() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa"));
        assertEquals(0, loc.occurrenceStart("aaa", 0));
        assertEquals(8, loc.occurrenceStart("aaa", 1));
        assertEquals(16, loc.occurrenceStart("aaa", 2));
        assertEquals(-1, loc.occurrenceStart("aaa", 3)); // 超出实际出现次数
        assertEquals(Arrays.asList(0, 8, 16), loc.occurrences("aaa"));
    }

    @Test
    public void multipleNeedlesResolvedInOneScan() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa", "bbb", "ccc", "zzz"));
        assertEquals(0, loc.occurrenceStart("aaa", 0));
        assertEquals(4, loc.occurrenceStart("bbb", 0));
        assertEquals(12, loc.occurrenceStart("ccc", 0));
        assertEquals(-1, loc.occurrenceStart("zzz", 0)); // 永不出现
        assertEquals(Arrays.asList(4), loc.occurrences("bbb"));
        assertTrue(loc.occurrences("zzz").isEmpty());
    }

    @Test
    public void singleCharNeedleEveryPosition() {
        // 单字符 needle：每个位置都是出现；非重叠语义下相邻允许 → 全部保留
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("aaa", Arrays.asList("a"));
        assertEquals(Arrays.asList(0, 1, 2), loc.occurrences("a"));
        assertEquals(2, loc.occurrenceStart("a", 2));
    }

    // ── 非重叠语义（与 indexOf 链一致） ───────────────────────────────────────

    @Test
    public void overlappingMatchesUseNonOverlappingSemantics() {
        // findNthOccurrence("aaaaa","aa",·) = 0,2（起点1/3 与前者重叠 → 跳过）
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("aaaaa", Arrays.asList("aa"));
        assertEquals(0, loc.occurrenceStart("aa", 0));
        assertEquals(2, loc.occurrenceStart("aa", 1));
        assertEquals(-1, loc.occurrenceStart("aa", 2));
        assertEquals(Arrays.asList(0, 2), loc.occurrences("aa"));
    }

    @Test
    public void adjacentMatchesKept() {
        // "abab"：indexOf 链 = 0 → 2（相邻非重叠保留）
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("abab", Arrays.asList("ab"));
        assertEquals(Arrays.asList(0, 2), loc.occurrences("ab"));
        assertEquals(2, loc.occurrenceStart("ab", 1));
    }

    @Test
    public void prefixOverlappingNeedlesResolvedIndependently() {
        // 同时索引 "a","aa","abc" 于 "aaabc"：各 needle 结果独立，非重叠只约束自身
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("aaabc", Arrays.asList("a", "aa", "abc"));
        assertEquals(Arrays.asList(0, 1, 2), loc.occurrences("a"));   // 相邻允许
        assertEquals(Arrays.asList(0), loc.occurrences("aa"));
        assertEquals(Arrays.asList(2), loc.occurrences("abc"));
    }

    // ── 失败链接（AC 多模式核心路径） ─────────────────────────────────────────

    @Test
    public void failureLinkChainReportsAllEndingHere() {
        // text="abc"，needles "bc","c"：位置 2 同时命中 "bc"（起点1）与 "c"（起点2）
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("abc", Arrays.asList("bc", "c"));
        assertEquals(1, loc.occurrenceStart("bc", 0));
        assertEquals(2, loc.occurrenceStart("c", 0));
        assertEquals(-1, loc.occurrenceStart("bc", 1));
    }

    @Test
    public void failureLinkTransitionsAcrossChars() {
        // text="abcbc"，needles "bc","c","cb"：验证跨字符 fail 跳转后继续匹配
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("abcbc", Arrays.asList("bc", "c", "cb"));
        assertEquals(Arrays.asList(1, 3), loc.occurrences("bc"));
        assertEquals(Arrays.asList(2, 4), loc.occurrences("c"));
        assertEquals(Arrays.asList(2), loc.occurrences("cb")); // 起点2 的 "cb"（3 处是 "bc" 重叠）
    }

    // ── 边界 ─────────────────────────────────────────────────────────────────

    @Test
    public void emptyNullUnknownNeedleAllMinusOne() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa"));
        assertEquals(-1, loc.occurrenceStart("", 0));
        assertEquals(-1, loc.occurrenceStart(null, 0));
        assertEquals(-1, loc.occurrenceStart("zzz", 0));
        assertTrue(loc.occurrences(null).isEmpty());
        assertTrue(loc.occurrences("").isEmpty());
    }

    @Test
    public void negativeIndexReturnsMinusOne() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa"));
        assertEquals(-1, loc.occurrenceStart("aaa", -1));
    }

    @Test
    public void emptyNeedleCollectionYieldsEmptyLocator() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, new ArrayList<>());
        assertEquals(-1, loc.occurrenceStart("aaa", 0));
        assertTrue(loc.occurrences("aaa").isEmpty());
        assertEquals(TEXT.length(), loc.textLength());
    }

    @Test
    public void duplicateNeedlesIndexedOnce() {
        // 同一原文多条批注 → 索引一份，结果共享（不得重复计数）
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa", "aaa", "aaa"));
        assertEquals(3, loc.occurrences("aaa").size());
        assertEquals(16, loc.occurrenceStart("aaa", 2));
    }

    @Test
    public void needleLongerThanTextNeverMatches() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("abc", Arrays.asList("abcdef"));
        assertEquals(-1, loc.occurrenceStart("abcdef", 0));
        assertTrue(loc.occurrences("abcdef").isEmpty());
    }

    @Test
    public void indexBoundToItsText() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("aaa", Arrays.asList("aaa"));
        // 换文本须重建：对旧索引查询另一文本内容 → 以其构建时文本为准
        assertEquals(0, loc.occurrenceStart("aaa", 0));
        AnnotationOccurrenceIndex.Locator other =
                AnnotationOccurrenceIndex.build(TEXT, Arrays.asList("aaa"));
        assertEquals(8, other.occurrenceStart("aaa", 1));
    }

    @Test
    public void chineseCjkNeedles() {
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build("今天天气很好，今天也很忙。",
                        Arrays.asList("今天", "天气"));
        assertEquals(0, loc.occurrenceStart("今天", 0));
        assertEquals(7, loc.occurrenceStart("今天", 1));
        assertEquals(-1, loc.occurrenceStart("今天", 2));
        assertEquals(2, loc.occurrenceStart("天气", 0));
    }

    // ── 对拍（与 findNthOccurrence 逐字符一致） ───────────────────────────────

    @Test
    public void fuzzParityWithFindNthOccurrence() {
        Random rnd = new Random(20261003L);
        String alphabet = "abcde ";
        for (int round = 0; round < 300; round++) {
            int len = 10 + rnd.nextInt(70);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) {
                sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            }
            String text = sb.toString();

            List<String> needles = new ArrayList<>();
            int needleCount = 1 + rnd.nextInt(8);
            for (int k = 0; k < needleCount; k++) {
                if (rnd.nextBoolean()) {
                    // 文本的真实子串（含前缀/后缀/整段）
                    int a = rnd.nextInt(text.length());
                    int b = a + rnd.nextInt(text.length() - a + 1);
                    needles.add(text.substring(a, b));
                } else {
                    // 随机串：很可能不出现（含空串）
                    int nLen = rnd.nextInt(5);
                    StringBuilder n = new StringBuilder(nLen);
                    for (int i = 0; i < nLen; i++) {
                        n.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
                    }
                    needles.add(n.toString());
                }
            }

            AnnotationOccurrenceIndex.Locator loc =
                    AnnotationOccurrenceIndex.build(text, needles);
            for (String needle : needles) {
                for (int n = 0; n < 6; n++) {
                    assertEquals(
                            "对拍失败 round=" + round + " text=" + text
                                    + " needle=[" + needle + "] n=" + n
                                    + " findNthOccurrence="
                                    + AnnotationHelper.findNthOccurrence(text, needle, n)
                                    + " occurrenceStart=" + loc.occurrenceStart(needle, n),
                            AnnotationHelper.findNthOccurrence(text, needle, n),
                            loc.occurrenceStart(needle, n));
                }
            }
        }
    }

    @Test
    public void parityOnLongRepetitiveText() {
        // 3000 字符重复文本 + 互嵌 needles（"ab"/"aba"/"b"），对拍前若干序
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1500; i++) sb.append("ab");
        String text = sb.toString();
        List<String> needles = Arrays.asList("ab", "aba", "b");
        AnnotationOccurrenceIndex.Locator loc =
                AnnotationOccurrenceIndex.build(text, needles);
        for (String needle : needles) {
            for (int n = 0; n < 8; n++) {
                assertEquals(AnnotationHelper.findNthOccurrence(text, needle, n),
                        loc.occurrenceStart(needle, n));
            }
        }
    }
}
