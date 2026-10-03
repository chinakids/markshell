package com.ssh.mdreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.util.TocHelper;
import com.ssh.mdreader.util.TocHelper.TocEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * 大纲（TOC）列表项适配器：按 {@link TocEntry#indent}（level-1）缩进展示标题文本；
 * 空标题显示占位「（无标题）」；点击以条目位置=标题扫描序回调（跳转由 Activity 消费）。
 */
public class TocAdapter extends RecyclerView.Adapter<TocAdapter.VH> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    private final List<TocEntry> items = new ArrayList<>();
    private OnItemClickListener clickListener;

    public void setOnItemClickListener(OnItemClickListener l) { clickListener = l; }

    public void setData(List<TocEntry> data) {
        items.clear();
        items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_toc, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        TocEntry entry = items.get(position);
        String text = entry.heading.text;
        holder.tvTitle.setText(text.isEmpty() ? "（无标题）" : text);
        // 缩进 = 级别-1（每级 14dp），器内一级（indent=0）用 14dp 与批注列表对齐
        float d = holder.tvTitle.getResources().getDisplayMetrics().density;
        int px = (int) ((entry.indent + 1) * 14 * d);
        holder.tvTitle.setPadding(px, 0, 0, 0);
        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                clickListener.onItemClick(pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvTitle;

        VH(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_toc_title);
        }
    }
}
