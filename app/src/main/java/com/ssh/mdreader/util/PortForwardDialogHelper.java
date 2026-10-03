package com.ssh.mdreader.util;

import android.content.Context;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.PortForwardRule;
import com.ssh.mdreader.model.SshConfig;

/**
 * 端口转发规则管理对话框（按服务器连接键隔离）的共享入口——原 SavedConnectionsActivity
 * 私有方法（迭代40），MainActivity 复用（走查命中连接列表两处长按菜单能力不一致：
 * MainActivity 无端口转发入口）；抽取为静态工具保证单一语义源。
 *
 * <p>职责：规则列表对话框（新建/选择）+ 新建/编辑输入对话框 + 条目编辑/删除动作。
 * 全部持久化委托 {@link PreferenceManager}，规则校验委托 {@link PortForwardHelper}
 * （单一语义源），规则「下次连接生效」语义与 SshManager.setPortForwardRules 一致。
 */
public final class PortForwardDialogHelper {

    private PortForwardDialogHelper() {
    }

    /** 规则管理入口：展示「新建规则 + 现有规则列表」（现有规则点选进入编辑/删除）。 */
    public static void showManager(Context context, PreferenceManager prefManager, SshConfig config) {
        String key = SshConnectionHelper.deriveConnectionKey(config);
        if (key == null) {
            UiUtils.showToast(context, "连接信息不完整");
            return;
        }
        java.util.List<PortForwardRule> rules = prefManager.getPortForwardRules(key);
        String[] items = new String[rules.size() + 1];
        int[] icons = new int[rules.size() + 1];
        items[0] = "＋ 新建规则";
        icons[0] = R.drawable.ic_add;
        for (int i = 0; i < rules.size(); i++) {
            items[i + 1] = PortForwardHelper.describe(rules.get(i));
            icons[i + 1] = 0;
        }
        DialogHelper.showListDialog(context, "端口转发（" + config.getDisplayName() + "）",
                items, icons, (d, which) -> {
                    if (which == 0) {
                        showEditDialog(context, prefManager, key, null);
                    } else {
                        showActionsDialog(context, prefManager, key, rules.get(which - 1));
                    }
                });
    }

    /** 新建（initial=null）或编辑（initial=已有规则）：输入后校验保存；规则于下次连接生效。 */
    private static void showEditDialog(Context context, PreferenceManager prefManager,
                                       String key, PortForwardRule initial) {
        DialogHelper.showPortForwardDialog(context,
                initial == null ? "新建端口转发规则" : "编辑端口转发规则",
                "保存", "取消", initial, rule -> {
                    String error = PortForwardHelper.validate(rule);
                    if (error != null) {
                        UiUtils.showToast(context, error);
                        return;
                    }
                    if (prefManager.savePortForwardRule(key, rule)) {
                        UiUtils.showToast(context, "规则已保存，重新连接后生效");
                    } else {
                        UiUtils.showToast(context, "规则保存失败");
                    }
                });
    }

    /** 单条规则的编辑/删除动作。 */
    private static void showActionsDialog(Context context, PreferenceManager prefManager,
                                          String key, PortForwardRule rule) {
        DialogHelper.showListDialog(context, PortForwardHelper.describe(rule),
                new String[]{"编辑", "删除"},
                new int[]{R.drawable.ic_edit, R.drawable.ic_delete},
                (d, which) -> {
                    if (which == 0) {
                        showEditDialog(context, prefManager, key, rule);
                    } else if (which == 1) {
                        if (prefManager.deletePortForwardRule(key, rule.getLocalPort())) {
                            UiUtils.showToast(context, "规则已删除");
                        } else {
                            UiUtils.showToast(context, "规则删除失败");
                        }
                    }
                });
    }
}
