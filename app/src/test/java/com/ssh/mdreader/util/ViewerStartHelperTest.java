package com.ssh.mdreader.util;

import static org.junit.Assert.assertEquals;

import com.ssh.mdreader.util.ViewerStartHelper.StartMode;

import org.junit.Test;

/**
 * {@link ViewerStartHelper} 决策契约单测。
 *
 * <p>四象限锁死：
 * <ul>
 *   <li>旋转恢复态恒 SESSION_RESTORE（同会话权威，迭代40 口径；设置开启/关闭均不可覆盖会话位置）；</li>
 *   <li>全新打开只有「设置开=尾部、设置关=既有默认」两分支。</li>
 * </ul>
 */
public class ViewerStartHelperTest {

    @Test
    public void rotationRestoreWithSettingOn_isSessionRestore() {
        assertEquals(StartMode.SESSION_RESTORE,
                ViewerStartHelper.resolveStartMode(true, true));
    }

    @Test
    public void rotationRestoreWithSettingOff_isSessionRestore() {
        assertEquals(StartMode.SESSION_RESTORE,
                ViewerStartHelper.resolveStartMode(true, false));
    }

    @Test
    public void freshOpenWithSettingOn_isStartAtBottom() {
        assertEquals(StartMode.START_AT_BOTTOM,
                ViewerStartHelper.resolveStartMode(false, true));
    }

    @Test
    public void freshOpenWithSettingOff_isDefault() {
        assertEquals(StartMode.DEFAULT,
                ViewerStartHelper.resolveStartMode(false, false));
    }
}
