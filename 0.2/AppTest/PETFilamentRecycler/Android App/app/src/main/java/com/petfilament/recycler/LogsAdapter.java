package com.petfilament.recycler;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.ViewHolder> {

    private final ArrayList<DatabaseHelper.LogEntry> logs = new ArrayList<>();

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DatabaseHelper.LogEntry log = logs.get(position);
        Context context = holder.itemView.getContext();

        boolean isIncoming = DatabaseHelper.DIRECTION_IN.equalsIgnoreCase(log.direction);
        String directionLabel = isIncoming
                ? context.getString(R.string.log_direction_in)
                : context.getString(R.string.log_direction_out);

        if (log.hasDevice()) {
            holder.textViewPrimary.setText(
                    context.getString(
                            R.string.log_header_with_device,
                            log.timestamp,
                            directionLabel,
                            log.device
                    )
            );
        } else {
            holder.textViewPrimary.setText(
                    context.getString(R.string.log_header, log.timestamp, directionLabel)
            );
        }

        holder.textViewPrimary.setTextColor(ContextCompat.getColor(
                context,
                isIncoming ? R.color.primary : R.color.secondary
        ));
        holder.textViewSecondary.setText(log.message);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void replaceAll(List<DatabaseHelper.LogEntry> newLogs) {
        logs.clear();
        if (newLogs != null) {
            logs.addAll(newLogs);
        }
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView textViewPrimary;
        final TextView textViewSecondary;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewPrimary = itemView.findViewById(R.id.text_log_header);
            textViewSecondary = itemView.findViewById(R.id.text_log_message);
        }
    }
}