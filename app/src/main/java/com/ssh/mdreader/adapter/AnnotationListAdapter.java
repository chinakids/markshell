package com.ssh.mdreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.AnnotationEntry;
import com.ssh.mdreader.util.AnnotationHelper;

import java.util.ArrayList;
import java.util.List;

public class AnnotationListAdapter
        extends RecyclerView.Adapter<AnnotationListAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(AnnotationEntry entry);
    }

    public interface OnItemDeleteListener {
        void onItemDelete(AnnotationEntry entry);
    }

    private final List<AnnotationEntry> items = new ArrayList<>();
    /** 与 {@link #items} 同序的定位标注状态（{@link AnnotationHelper.AnnotationStatus}）。 */
    private final List<AnnotationHelper.AnnotationStatus> statuses = new ArrayList<>();
    private OnItemClickListener  clickListener;
    private OnItemDeleteListener deleteListener;

    public void setOnItemClickListener(OnItemClickListener l)  { clickListener  = l; }
    public void setOnItemDeleteListener(OnItemDeleteListener l){ deleteListener = l; }

    public void setData(List<AnnotationEntry> data) {
        setData(data, null);
    }

    /**
     * 设置数据与逐条定位状态（与 {@code data} 同序；statuses 为空/长度不符时按全部
     * 可定位处理——旧调用方零行为差异）。
     */
    public void setData(List<AnnotationEntry> data,
                        List<AnnotationHelper.AnnotationStatus> statuses) {
        items.clear();
        items.addAll(data);
        this.statuses.clear();
        for (int i = 0; i < data.size(); i++) {
            AnnotationHelper.AnnotationStatus s = (statuses != null && i < statuses.size())
                    ? statuses.get(i) : AnnotationHelper.AnnotationStatus.OK;
            this.statuses.add(s);
        }
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_annotation_list, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AnnotationEntry entry = items.get(position);
        boolean failed = position < statuses.size()
                && statuses.get(position) == AnnotationHelper.AnnotationStatus.FAILED;

        // Original text — truncated label (matches the unrendered source text)
        String orig = entry.originalText != null ? entry.originalText.trim() : "";
        holder.tvOriginal.setText(orig.isEmpty() ? "（原文未知）" : orig);

        // Annotation text
        holder.tvText.setText(entry.text);

        // 失效条目标注（编辑保存后未找到原文）：角标「未找到原文」提示
        holder.tvStatus.setVisibility(failed ? View.VISIBLE : View.GONE);

        // Click → navigate to annotation in document
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(entry);
        });

        // Per-row delete icon → confirm-and-delete (same path as long-press)
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) deleteListener.onItemDelete(entry);
        });

        // Long-press → delete (legacy convenience)
        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) deleteListener.onItemDelete(entry);
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView  tvOriginal;
        final TextView  tvText;
        final TextView  tvStatus;
        final ImageView btnDelete;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOriginal = itemView.findViewById(R.id.tv_annotation_original);
            tvText     = itemView.findViewById(R.id.tv_annotation_text);
            tvStatus   = itemView.findViewById(R.id.tv_annotation_status);
            btnDelete  = itemView.findViewById(R.id.btn_annotation_delete);
        }
    }
}
