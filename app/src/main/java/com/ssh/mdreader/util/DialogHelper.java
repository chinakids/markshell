package com.ssh.mdreader.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.PortForwardRule;

public class DialogHelper {

    public interface OnPositiveListener {
        void onPositive(Dialog dialog);
    }

    public interface OnNegativeListener {
        void onNegative(Dialog dialog);
    }

    /** 中立按钮回调（连接失败「编辑配置」等：介于取消与主操作之间）。 */
    public interface OnNeutralListener {
        void onNeutral(Dialog dialog);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(Dialog dialog, int which);
    }

    /** 列表条目长按回调（书签/历史管理用：长按=删除该条；回调方负责刷新对话框数据）。 */
    public interface OnItemLongClickListener {
        void onItemLongClick(Dialog dialog, int which);
    }

    /** 列表对话框底部操作按钮回调（书签/历史管理用：底部=清空）。 */
    public interface OnFooterListener {
        void onFooter(Dialog dialog);
    }

    private static boolean canShow(Context context) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            return !activity.isFinishing() && !activity.isDestroyed();
        }
        return false;
    }

    public static void showMessageDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull OnPositiveListener listener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_brand, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) view.findViewById(R.id.dialog_message)).setText(message);

        TextView btnPositive = view.findViewById(R.id.dialog_btn_positive);
        btnPositive.setText(positiveText);
        btnPositive.setOnClickListener(v -> {
            listener.onPositive(dialog);
            dialog.dismiss();
        });

        dialog.setCancelable(true);
        dialog.show();
    }

    public static void showConfirmDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull String negativeText,
                                         @NonNull OnPositiveListener positiveListener,
                                         @NonNull OnNegativeListener negativeListener) {
        showConfirmDialog(context, title, message, positiveText, negativeText,
                false, false, positiveListener, negativeListener);
    }

    public static void showDangerConfirmDialog(@NonNull Context context,
                                                @NonNull String title,
                                                @NonNull String message,
                                                @NonNull String positiveText,
                                                @NonNull String negativeText,
                                                @NonNull OnPositiveListener positiveListener,
                                                @NonNull OnNegativeListener negativeListener) {
        showConfirmDialog(context, title, message, positiveText, negativeText,
                true, false, positiveListener, negativeListener);
    }

    /**
     * 主机指纹变更确认框（危险样式）：展示已记录/服务器当前两组指纹（4 字符分组换行），
     * 「信任」= 以新指纹落盘并执行 {@code onTrusted}；「取消」= 仅执行 {@code onRejected}。
     */
    public static void showHostKeyChangedDialog(@NonNull Context context,
                                                @NonNull HostKeyStore store,
                                                @NonNull String host,
                                                int port,
                                                @NonNull String expectedFingerprint,
                                                @NonNull String actualFingerprint,
                                                @NonNull Runnable onTrusted,
                                                @NonNull Runnable onRejected) {
        if (!canShow(context)) return;
        showDangerConfirmDialog(context,
                context.getString(R.string.host_key_changed_title),
                context.getString(R.string.host_key_changed_message,
                        host, port,
                        HostKeyHelper.formatGrouped(expectedFingerprint, 4),
                        HostKeyHelper.formatGrouped(actualFingerprint, 4)),
                context.getString(R.string.host_key_changed_trust),
                context.getString(R.string.host_key_changed_cancel),
                d -> {
                    store.saveFingerprint(host, port, actualFingerprint);
                    onTrusted.run();
                },
                d -> onRejected.run());
    }

    public static void showConfirmDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull String negativeText,
                                         @NonNull String neutralText,
                                         @NonNull OnPositiveListener positiveListener,
                                         @NonNull OnNegativeListener negativeListener,
                                         @NonNull OnNeutralListener neutralListener) {
        showConfirmDialog(context, title, message, positiveText, negativeText,
                false, false, positiveListener, negativeListener, neutralText, neutralListener);
    }

    public static void showConfirmDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull String negativeText,
                                         boolean danger,
                                         boolean cancelable,
                                         @NonNull OnPositiveListener positiveListener,
                                         @NonNull OnNegativeListener negativeListener) {
        showConfirmDialog(context, title, message, positiveText, negativeText,
                danger, cancelable, positiveListener, negativeListener, null, null);
    }

    public static void showConfirmDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull String negativeText,
                                         boolean danger,
                                         boolean cancelable,
                                         @NonNull OnPositiveListener positiveListener,
                                         @NonNull OnNegativeListener negativeListener,
                                         @Nullable String neutralText,
                                         @Nullable OnNeutralListener neutralListener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_brand, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) view.findViewById(R.id.dialog_message)).setText(message);

        TextView btnNeutral = view.findViewById(R.id.dialog_btn_neutral);
        if (neutralText != null && neutralListener != null) {
            btnNeutral.setVisibility(View.VISIBLE);
            btnNeutral.setText(neutralText);
            btnNeutral.setOnClickListener(v -> {
                neutralListener.onNeutral(dialog);
                dialog.dismiss();
            });
        }

        TextView btnNegative = view.findViewById(R.id.dialog_btn_negative);
        btnNegative.setVisibility(View.VISIBLE);
        btnNegative.setText(negativeText);
        btnNegative.setOnClickListener(v -> {
            negativeListener.onNegative(dialog);
            dialog.dismiss();
        });

        TextView btnPositive = view.findViewById(R.id.dialog_btn_positive);
        btnPositive.setText(positiveText);
        if (danger) {
            btnPositive.setBackgroundResource(R.drawable.bg_dialog_btn_danger);
            btnPositive.setTextColor(Color.WHITE);
        }
        btnPositive.setOnClickListener(v -> {
            positiveListener.onPositive(dialog);
            dialog.dismiss();
        });

        dialog.setCancelable(cancelable);
        dialog.show();
    }

    public static void showListDialog(@NonNull Context context,
                                      @NonNull String title,
                                      @NonNull String[] items,
                                      @DrawableRes int[] icons,
                                      @NonNull OnItemSelectedListener listener) {
        showListDialog(context, title, items, icons, listener, null, null, null);
    }

    /**
     * 列表对话框（可选长按删除与底部操作按钮，书签/历史管理用）。
     * 旧签名保持不变并委托本重载（长按/footer 均 null=零行为变更）。
     */
    public static void showListDialog(@NonNull Context context,
                                      @NonNull String title,
                                      @NonNull String[] items,
                                      @DrawableRes int[] icons,
                                      @NonNull OnItemSelectedListener listener,
                                      @Nullable OnItemLongClickListener longClickListener,
                                      @Nullable String footerLabel,
                                      @Nullable OnFooterListener footerListener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_list, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_list_title)).setText(title);

        TextView footer = view.findViewById(R.id.dialog_list_footer);
        if (footerLabel != null && footerListener != null) {
            footer.setText(footerLabel);
            footer.setVisibility(View.VISIBLE);
            footer.setOnClickListener(v -> {
                footerListener.onFooter(dialog);
            });
        } else {
            footer.setVisibility(View.GONE);
        }

        RecyclerView recycler = view.findViewById(R.id.dialog_list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(new ListDialogAdapter(context, items, icons, (which) -> {
            listener.onItemSelected(dialog, which);
            dialog.dismiss();
        }, longClickListener == null ? null : (which) ->
                longClickListener.onItemLongClick(dialog, which)));

        dialog.setCancelable(true);
        dialog.show();
    }

    public interface OnInputListener {
        /** Called when the user confirms input. @param input trimmed text (may be empty). */
        void onInput(String input);
    }

    /**
     * Brand-styled input dialog with a multi-line EditText.
     * 旧签名保持不变，委托给带 {@code inputType}/{@code initialText} 的新重载（默认无特殊输入法、无预填）。
     */
    public static void showInputDialog(@NonNull Context context,
                                       @NonNull String title,
                                       @NonNull String hint,
                                       @NonNull String positiveText,
                                       @NonNull String negativeText,
                                       @NonNull OnInputListener inputListener) {
        showInputDialog(context, title, hint, positiveText, negativeText, 0, null, inputListener);
    }

    /**
     * 带输入法类型与预填文本的输入对话框。
     *
     * @param inputType   非 0 时替换 EditText 输入法类型（如 {@code InputType.TYPE_CLASS_NUMBER}）
     *                    并改为单行紧凑布局；0 表示保持布局默认（多行文本）。
     * @param initialText 非 null 时预填文本并全选，便于整体替换。
     */
    public static void showInputDialog(@NonNull Context context,
                                       @NonNull String title,
                                       @NonNull String hint,
                                       @NonNull String positiveText,
                                       @NonNull String negativeText,
                                       int inputType,
                                       String initialText,
                                       @NonNull OnInputListener inputListener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_input_title)).setText(title);

        EditText et = view.findViewById(R.id.dialog_input_et);
        et.setHint(hint);

        if (inputType != 0) {
            et.setInputType(inputType);
            et.setMinLines(1);
            et.setMaxLines(1);
            et.setGravity(Gravity.CENTER_VERTICAL);
        }
        if (initialText != null && !initialText.isEmpty()) {
            et.setText(initialText);
            et.setSelection(0, et.length());
        }

        TextView btnNeg = view.findViewById(R.id.dialog_input_btn_negative);
        btnNeg.setText(negativeText);
        btnNeg.setOnClickListener(v -> {
            dismissWithKeyboard(dialog, et);
        });

        TextView btnPos = view.findViewById(R.id.dialog_input_btn_positive);
        btnPos.setText(positiveText);
        btnPos.setOnClickListener(v -> {
            String input = et.getText() != null ? et.getText().toString().trim() : "";
            dismissWithKeyboard(dialog, et);
            inputListener.onInput(input);
        });

        dialog.setCancelable(true);
        dialog.show();

        // Auto-show keyboard
        et.postDelayed(() -> {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    private static void dismissWithKeyboard(Dialog dialog, EditText et) {
        InputMethodManager imm = (InputMethodManager)
                et.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        dialog.dismiss();
    }

    // ── 端口转发规则输入对话框 ──────────────────────────────────────────────

    public interface OnPortForwardListener {
        /** 用户确认时回调（字段已收集，合法性由调用方经 PortForwardHelper.validate 校验）。 */
        void onResult(PortForwardRule rule);
    }

    /**
     * 端口转发规则输入对话框（名称/本地端口/远端主机/远端端口/绑定地址 五字段，
     * 布局 dialog_port_forward；旧规则经 {@code initial} 预填=编辑语义）。
     * 校验由调用方负责（toast 错误并保持对话框不变——本方法只负责收集与展示）。
     */
    public static void showPortForwardDialog(@NonNull Context context,
                                             @NonNull String title,
                                             @NonNull String positiveText,
                                             @NonNull String negativeText,
                                             final PortForwardRule initial,
                                             @NonNull OnPortForwardListener listener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_port_forward, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_pf_title)).setText(title);

        EditText etName = view.findViewById(R.id.dialog_pf_name);
        EditText etLocalPort = view.findViewById(R.id.dialog_pf_local_port);
        EditText etRemoteHost = view.findViewById(R.id.dialog_pf_remote_host);
        EditText etRemotePort = view.findViewById(R.id.dialog_pf_remote_port);
        EditText etBind = view.findViewById(R.id.dialog_pf_bind);

        if (initial != null) {
            if (initial.getName() != null) etName.setText(initial.getName());
            etLocalPort.setText(String.valueOf(initial.getLocalPort()));
            etRemoteHost.setText(initial.getRemoteHost() == null ? "" : initial.getRemoteHost());
            etRemotePort.setText(String.valueOf(initial.getRemotePort()));
            if (initial.getBindAddress() != null) etBind.setText(initial.getBindAddress());
        }

        TextView btnNeg = view.findViewById(R.id.dialog_pf_btn_negative);
        btnNeg.setText(negativeText);
        btnNeg.setOnClickListener(v -> dismissWithKeyboard(dialog, etLocalPort));

        TextView btnPos = view.findViewById(R.id.dialog_pf_btn_positive);
        btnPos.setText(positiveText);
        btnPos.setOnClickListener(v -> {
            PortForwardRule rule = new PortForwardRule();
            rule.setName(etName.getText() != null ? etName.getText().toString() : "");
            rule.setLocalPort(parsePort(etLocalPort.getText()));
            rule.setRemoteHost(etRemoteHost.getText() != null ? etRemoteHost.getText().toString() : "");
            rule.setRemotePort(parsePort(etRemotePort.getText()));
            rule.setBindAddress(etBind.getText() != null ? etBind.getText().toString() : "");
            rule.setName(PortForwardHelper.normalizeName(rule.getName()));
            rule.setRemoteHost(rule.getRemoteHost().trim());
            rule.setBindAddress(PortForwardHelper.normalizeBindAddress(rule.getBindAddress()));
            dismissWithKeyboard(dialog, etLocalPort);
            listener.onResult(rule);
        });

        dialog.setCancelable(true);
        dialog.show();

        etLocalPort.postDelayed(() -> {
            etLocalPort.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(etLocalPort, InputMethodManager.SHOW_IMPLICIT);
        }, 100);
    }

    /** 输入框文本→端口：空/非法返回 0（由校验层识别并提示）。 */
    private static int parsePort(CharSequence text) {
        String s = text == null ? "" : text.toString().trim();
        if (s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** 对话框宽度设为屏幕 88%、居中；util 包内共享（以 DirectoryPickerDialog 复用）。 */
    static void applyDialogSize(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = (int) (getScreenWidth(dialog.getContext()) * 0.88);
            lp.gravity = Gravity.CENTER;
            window.setAttributes(lp);
        }
    }

    private static int getScreenWidth(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (wm != null) {
            android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
            wm.getDefaultDisplay().getMetrics(dm);
            return dm.widthPixels;
        }
        return 800;
    }

    private interface OnItemClick {
        void onClick(int which);
    }

    private static class ListDialogAdapter extends RecyclerView.Adapter<ListDialogAdapter.VH> {

        private final LayoutInflater inflater;
        private final String[] items;
        @DrawableRes
        private final int[] icons;
        private final OnItemClick clickListener;
        @Nullable
        private final OnItemClick longClickListener;

        ListDialogAdapter(Context context, String[] items, @DrawableRes int[] icons, OnItemClick clickListener) {
            this(context, items, icons, clickListener, null);
        }

        ListDialogAdapter(Context context, String[] items, @DrawableRes int[] icons,
                          OnItemClick clickListener, @Nullable OnItemClick longClickListener) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.icons = icons;
            this.clickListener = clickListener;
            this.longClickListener = longClickListener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(inflater.inflate(R.layout.dialog_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.text.setText(items[position]);
            if (icons != null && position < icons.length && icons[position] != 0) {
                holder.icon.setImageResource(icons[position]);
                holder.icon.setVisibility(View.VISIBLE);
            } else {
                holder.icon.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> clickListener.onClick(position));
            holder.itemView.setOnLongClickListener(longClickListener == null ? null :
                    v -> {
                        longClickListener.onClick(position);
                        return true;
                    });
        }

        @Override
        public int getItemCount() {
            return items.length;
        }

        static class VH extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView text;

            VH(@NonNull View itemView) {
                super(itemView);
                icon = itemView.findViewById(R.id.list_item_icon);
                text = itemView.findViewById(R.id.list_item_text);
            }
        }
    }
}
