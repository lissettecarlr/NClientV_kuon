package com.maxwai.nclientv3.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.utility.TagTranslator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Custom (fork only) feature: tag suggestions shown while typing in the search screen.
 */
public class TagSuggestAdapter extends RecyclerView.Adapter<TagSuggestAdapter.ViewHolder> {
    private final List<Tag> tags = new ArrayList<>();
    private final OnTagPicked listener;

    public TagSuggestAdapter(@NonNull OnTagPicked listener) {
        this.listener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setTags(@NonNull List<Tag> newTags) {
        tags.clear();
        tags.addAll(newTags);
        notifyDataSetChanged();
    }

    private static int typeName(TagType type) {
        if (type == TagType.PARODY) return R.string.parodies;
        if (type == TagType.CHARACTER) return R.string.characters;
        if (type == TagType.ARTIST) return R.string.artists;
        if (type == TagType.GROUP) return R.string.groups;
        if (type == TagType.LANGUAGE) return R.string.languages;
        if (type == TagType.CATEGORY) return R.string.categories;
        return R.string.tags;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.entry_tag_suggest, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Context context = holder.itemView.getContext();
        Tag tag = tags.get(position);
        holder.title.setText(TagTranslator.display(context, tag));
        holder.subtitle.setText(String.format(Locale.US, "%s · %d", context.getString(typeName(tag.getType())), tag.getCount()));
        holder.itemView.setOnClickListener(v -> listener.onTagPicked(tag));
    }

    @Override
    public int getItemCount() {
        return tags.size();
    }

    public interface OnTagPicked {
        void onTagPicked(@NonNull Tag tag);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView subtitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
        }
    }
}
