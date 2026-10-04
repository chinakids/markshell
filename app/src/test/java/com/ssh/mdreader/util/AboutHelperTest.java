package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * AboutHelper 纯函数单测：版本行格式化（markor MoreInfoFragment「Version v%s (%d)」同型）
 * 与防御归一（null/空白 versionName 回退「?」；versionCode 透传）。
 * 走查（能力发现循环第四十二轮）：action_about 死字符串 + versionName/versionCode 无处展示。
 */
public class AboutHelperTest {

    @Test
    public void versionLine_normal() {
        assertEquals("v1.0.0 (1)", AboutHelper.versionLine("1.0.0", 1));
    }

    @Test
    public void versionLine_withTrim() {
        assertEquals("v1.0.0 (42)", AboutHelper.versionLine(" 1.0.0 ", 42));
    }

    @Test
    public void versionLine_nullVersionName_fallsBackQuestionMark() {
        assertEquals("v? (1)", AboutHelper.versionLine(null, 1));
    }

    @Test
    public void versionLine_emptyVersionName_fallsBackQuestionMark() {
        assertEquals("v? (1)", AboutHelper.versionLine("", 1));
    }

    @Test
    public void versionLine_blankVersionName_fallsBackQuestionMark() {
        assertEquals("v? (3)", AboutHelper.versionLine("   ", 3));
    }

    @Test
    public void versionLine_zeroCode_passthrough() {
        assertEquals("v1.0.0 (0)", AboutHelper.versionLine("1.0.0", 0));
    }
}
