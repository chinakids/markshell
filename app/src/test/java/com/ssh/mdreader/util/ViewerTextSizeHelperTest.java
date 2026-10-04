package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * ViewerTextSizeHelper 纯函数单测：clamp 边界 / 合法性 / 应用阈值（防抖动）。
 * 语义与两查看器原内联实现（min 8 / max 32 / delta > 0.5f）严格一致=抽取后单一语义源。
 */
public class ViewerTextSizeHelperTest {

    // ── 常量契约（与历史行为一致：查看器原硬编码 14f/8f/32f/0.5f）──────────

    @Test
    public void defaultTextSize_14() {
        assertEquals(14f, ViewerTextSizeHelper.DEFAULT_TEXT_SIZE, 0f);
    }

    @Test
    public void minTextSize_8() {
        assertEquals(8f, ViewerTextSizeHelper.MIN_TEXT_SIZE, 0f);
    }

    @Test
    public void maxTextSize_32() {
        assertEquals(32f, ViewerTextSizeHelper.MAX_TEXT_SIZE, 0f);
    }

    // ── clamp：边界夹取 ─────────────────────────────────────────────────────

    @Test
    public void clamp_middle_unchanged() {
        assertEquals(18f, ViewerTextSizeHelper.clamp(18f), 0f);
    }

    @Test
    public void clamp_belowMin_returnsMin() {
        assertEquals(8f, ViewerTextSizeHelper.clamp(5f), 0f);
    }

    @Test
    public void clamp_aboveMax_returnsMax() {
        assertEquals(32f, ViewerTextSizeHelper.clamp(40f), 0f);
    }

    @Test
    public void clamp_exactMin_unchanged() {
        assertEquals(8f, ViewerTextSizeHelper.clamp(8f), 0f);
    }

    @Test
    public void clamp_exactMax_unchanged() {
        assertEquals(32f, ViewerTextSizeHelper.clamp(32f), 0f);
    }

    @Test
    public void clamp_negative_returnsMin() {
        assertEquals(8f, ViewerTextSizeHelper.clamp(-3f), 0f);
    }

    @Test
    public void clamp_nan_returnsDefault() {
        assertEquals(14f, ViewerTextSizeHelper.clamp(Float.NaN), 0f);
    }

    @Test
    public void clamp_infinite_returnsMax() {
        assertEquals(32f, ViewerTextSizeHelper.clamp(Float.POSITIVE_INFINITY), 0f);
    }

    // ── isValid：合法区间（含端点） ────────────────────────────────────────

    @Test
    public void isValid_default_true() {
        assertTrue(ViewerTextSizeHelper.isValid(14f));
    }

    @Test
    public void isValid_belowMin_false() {
        assertFalse(ViewerTextSizeHelper.isValid(7.9f));
    }

    @Test
    public void isValid_aboveMax_false() {
        assertFalse(ViewerTextSizeHelper.isValid(32.1f));
    }

    @Test
    public void isValid_exactBounds_true() {
        assertTrue(ViewerTextSizeHelper.isValid(8f));
        assertTrue(ViewerTextSizeHelper.isValid(32f));
    }

    // ── shouldApply：>0.5f 严格阈值（防抖动，语义与原实现一致）──────────────

    @Test
    public void shouldApply_exactDelta_notApply() {
        assertFalse(ViewerTextSizeHelper.shouldApply(14f, 14.5f));
    }

    @Test
    public void shouldApply_smallAbove_apply() {
        assertTrue(ViewerTextSizeHelper.shouldApply(14f, 14.6f));
    }

    @Test
    public void shouldApply_zero_notApply() {
        assertFalse(ViewerTextSizeHelper.shouldApply(14f, 14f));
    }

    @Test
    public void shouldApply_shrink_apply() {
        assertTrue(ViewerTextSizeHelper.shouldApply(14f, 13f));
    }

    @Test
    public void shouldApply_largeChange_apply() {
        assertTrue(ViewerTextSizeHelper.shouldApply(20f, 26f));
    }
}
