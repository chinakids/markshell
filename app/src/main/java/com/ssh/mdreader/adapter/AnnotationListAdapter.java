package com.ssh.mdreader.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.ssh.mdreader.R;
import com.ssh.mdreader.model.AnnotationEntry;

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
    private OnItemClickListener  clickListener;
    private OnItemDeleteListener deleteListener;

    public void setOnItemClickListener(OnItemClickListener l)  { clickListener  = l; }
    public void setOnItemDeleteListener(OnItemDeleteListener l){ deleteListener = l; }

    public void setData(List<AnnotationEntry> data) {
        items.clear();
        items.addAll(data);
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

        // Original text — truncated label
        String orig = entry.originalText != null ? entry.originalText.trim() : "";
        holder.tvOriginal.setText(orig.isEmpty() ? "（原文未知）" : orig);

        // Annotation text
        holder.tvText.setText(entry.text);

        // Click → navigate to annotation in document
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onItemClick(entry);
        });

        // Long-press → delete
        holder.itemView.setOnLongClickListener(v -> {
            if (deleteListener != null) deleteListener.onItemDelete(entry);
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        final TextView tvOriginal;
        final TextView tvText;

        VH(@NonNull View itemView) {
            super(itemView);
            tvOriginal = itemView.findViewById(R.id.tv_annotation_original);
            tvText     = itemView.findViewById(R.id.tv_annotation_text);
        }
    }
}
