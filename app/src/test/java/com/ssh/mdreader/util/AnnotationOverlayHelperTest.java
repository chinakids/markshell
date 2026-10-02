package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** AnnotationOverlayHelper 纯函数单测：弹窗定位算法（迭代26 从 MarkdownReaderActivity 抽取，行为锁定）。 */
public class AnnotationOverlayHelperTest {

    @Test
    public void popupCenteredAboveTouch() {
        // 常规：水平居中，位于触摸点上方 offset，未越界
        AnnotationOverlayHelper.PopupPosition p =
                AnnotationOverlayHelper.computePopupPosition(400, 100, 1080, 2000, 48, 36, 800);
        assertEquals(340, p.x);
        assertEquals(664, p.y);
    }

    @Test
    public void popupFlipsBelowWhenNearTop() {
        // y = touchY - popupH - offset = -76 < margin 48 → 翻到下方 touchY + offset = 96
        AnnotationOverlayHelper.PopupPosition p =
                AnnotationOverlayHelper.computePopupPosition(400, 100, 1080, 2000, 48, 36, 60);
        assertEquals(340, p.x);
        assertEquals(96, p.y);
    }

    @Test
    public void popupClampedAtScreenBottom() {
        // y=1990-136=1854，1854+100 > 2000-48 → 钳到 2000-100-48=1852
        AnnotationOverlayHelper.PopupPosition p =
                AnnotationOverlayHelper.computePopupPosition(400, 100, 1080, 2000, 48, 36, 1990);
        assertEquals(340, p.x);
        assertEquals(1852, p.y);
    }

    @Test
    public void popupAtZeroTouchY() {
        // touchY=0 → 翻到下方 0+36=36（未越界）
        AnnotationOverlayHelper.PopupPosition p =
                AnnotationOverlayHelper.computePopupPosition(400, 100, 1080, 2000, 48, 36, 0);
        assertEquals(340, p.x);
        assertEquals(36, p.y);
    }

    @Test
    public void popupWiderThanScreenKeepsCenteredX() {
        // 原算法不钳 x（水平居中可能为负），行为锁定
        AnnotationOverlayHelper.PopupPosition p =
                AnnotationOverlayHelper.computePopupPosition(1200, 100, 1080, 2000, 48, 36, 800);
        assertEquals(-60, p.x);
        assertEquals(664, p.y);
    }
}
