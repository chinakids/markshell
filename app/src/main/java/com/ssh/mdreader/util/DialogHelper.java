package com.ssh.mdreader.util;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;

public class DialogHelper {

    public interface OnPositiveListener {
        void onPositive(Dialog dialog);
    }

    public interface OnNegativeListener {
        void onNegative(Dialog dialog);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(Dialog dialog, int which);
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

    public static void showConfirmDialog(@NonNull Context context,
                                         @NonNull String title,
                                         @NonNull String message,
                                         @NonNull String positiveText,
                                         @NonNull String negativeText,
                                         boolean danger,
                                         boolean cancelable,
                                         @NonNull OnPositiveListener positiveListener,
                                         @NonNull OnNegativeListener negativeListener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_brand, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_title)).setText(title);
        ((TextView) view.findViewById(R.id.dialog_message)).setText(message);

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
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_list, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_list_title)).setText(title);

        RecyclerView recycler = view.findViewById(R.id.dialog_list_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(context));
        recycler.setAdapter(new ListDialogAdapter(context, items, icons, (which) -> {
            listener.onItemSelected(dialog, which);
            dialog.dismiss();
        }));

        dialog.setCancelable(true);
        dialog.show();
    }

    public interface OnInputListener {
        /** Called when the user confirms input. @param input trimmed text (may be empty). */
        void onInput(String input);
    }

    /**
     * Brand-styled input dialog with a multi-line EditText.
     */
    public static void showInputDialog(@NonNull Context context,
                                       @NonNull String title,
                                       @NonNull String hint,
                                       @NonNull String positiveText,
                                       @NonNull String negativeText,
                                       @NonNull OnInputListener inputListener) {
        if (!canShow(context)) return;

        Dialog dialog = new Dialog(context, R.style.BrandDialog);
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_input, null);
        dialog.setContentView(view);
        applyDialogSize(dialog);

        ((TextView) view.findViewById(R.id.dialog_input_title)).setText(title);

        EditText et = view.findViewById(R.id.dialog_input_et);
        et.setHint(hint);

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

    private static void applyDialogSize(Dialog dialog) {
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

        ListDialogAdapter(Context context, String[] items, @DrawableRes int[] icons, OnItemClick clickListener) {
            this.inflater = LayoutInflater.from(context);
            this.items = items;
            this.icons = icons;
            this.clickListener = clickListener;
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
