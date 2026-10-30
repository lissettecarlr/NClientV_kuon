package com.maxwai.nclientv3.utility;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.TagType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Database;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Custom (fork only) feature: tag suggestions for the search bar.
 * Matches the English name stored in the database and, for non ASCII input,
 * the Chinese translation (see {@link TagTranslator}).
 */
public final class TagSuggest {
    private static final int MAX_TRANSLATION_KEYS = 80;

    private TagSuggest() {
    }

    @NonNull
    public static List<Tag> search(@NonNull Context context, @NonNull String text, int limit) {
        String query = text.trim().toLowerCase(Locale.ROOT);
        List<Tag> result = new ArrayList<>();
        SQLiteDatabase db = Database.getDatabase();
        if (query.isEmpty() || db == null) return result;

        Set<Integer> seen = new HashSet<>();
        // 1. English name stored in the database: exact match, then prefix, then contains
        String sql = "SELECT * FROM " + Queries.TagTable.TABLE_NAME + " WHERE name LIKE ? AND type != ?" +
            " ORDER BY CASE WHEN name = ? THEN 0 WHEN name LIKE ? THEN 1 ELSE 2 END, count DESC LIMIT ?";
        try (Cursor c = db.rawQuery(sql, new String[]{
            "%" + escape(query) + "%", "" + TagType.UNKNOWN.getId(), query, escape(query) + "%", "" + limit})) {
            while (c.moveToNext()) {
                Tag tag = Queries.TagTable.cursorToTag(c);
                if (seen.add(tag.getId())) result.add(tag);
            }
        }

        // 2. Chinese translation
        if (hasNonAscii(query)) {
            List<String> keys = TagTranslator.searchKeys(context, query, MAX_TRANSLATION_KEYS);
            List<Tag> translated = loadByKeys(db, keys);
            for (Tag tag : translated)
                if (seen.add(tag.getId())) result.add(tag);
        }
        if (result.size() > limit) return new ArrayList<>(result.subList(0, limit));
        return result;
    }

    /**
     * @param keys "type:name", best matches first
     */
    @NonNull
    private static List<Tag> loadByKeys(@NonNull SQLiteDatabase db, @NonNull List<String> keys) {
        List<Tag> tags = new ArrayList<>();
        if (keys.isEmpty()) return tags;
        StringBuilder where = new StringBuilder();
        List<String> args = new ArrayList<>(keys.size() * 2);
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            int sep = key.indexOf(':');
            TagType type = TagType.typeByName(key.substring(0, sep));
            if (type == TagType.UNKNOWN) continue;
            if (where.length() > 0) where.append(" OR ");
            where.append("(name = ? COLLATE NOCASE AND type = ?)");
            args.add(key.substring(sep + 1));
            args.add("" + type.getId());
            rank.put(type.getId() + ":" + key.substring(sep + 1).toLowerCase(Locale.ROOT), i);
        }
        if (args.isEmpty()) return tags;
        try (Cursor c = db.rawQuery("SELECT * FROM " + Queries.TagTable.TABLE_NAME + " WHERE " + where, args.toArray(new String[0]))) {
            while (c.moveToNext()) tags.add(Queries.TagTable.cursorToTag(c));
        }
        tags.sort(Comparator
            .comparingInt((Tag t) -> rank.getOrDefault(t.getType().getId() + ":" + t.getName().toLowerCase(Locale.ROOT), Integer.MAX_VALUE))
            .thenComparing(Comparator.comparingInt(Tag::getCount).reversed()));
        return tags;
    }

    private static boolean hasNonAscii(@NonNull String s) {
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) > 127) return true;
        return false;
    }

    /**
     * LIKE wildcards are not supposed to be special for the user
     */
    private static String escape(@NonNull String s) {
        return s.replace("%", "").replace("_", "");
    }
}
