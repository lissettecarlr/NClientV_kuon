package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Custom (fork only) feature: hide galleries of a fixed set of themes (gay / boys-only content).
 * Used by {@link MinPagesFilter}, which already post-processes every list response.
 * <p>
 * Matching is done on the raw {@code tag_ids} of the list items, so it also works for tags
 * which aren't in the local tag database yet.
 */
final class BlockedTagFilter {
    /**
     * nhentai tag id -> name (type "tag")
     */
    private static final Map<Integer, String> BLOCKED = new HashMap<>();

    static {
        BLOCKED.put(23895, "yaoi");
        BLOCKED.put(21712, "males only");
    }

    private BlockedTagFilter() {
    }

    static boolean isEnabled(@Nullable Context context) {
        return context != null && context.getSharedPreferences("Settings", 0)
            .getBoolean(context.getString(R.string.preference_key_hide_gay), true);
    }

    /**
     * @return the tag ids to hide for this request, empty if disabled. A tag which is explicitly
     * part of the search url (e.g. the user searches {@code tag:"yaoi"}) is never hidden.
     */
    @NonNull
    static Set<Integer> blockedIds(@Nullable Context context, @NonNull String url) {
        Set<Integer> ids = new HashSet<>();
        if (!isEnabled(context)) return ids;
        String decoded;
        try {
            decoded = URLDecoder.decode(url, StandardCharsets.UTF_8.name()).toLowerCase();
        } catch (Exception e) {
            decoded = url.toLowerCase();
        }
        for (Map.Entry<Integer, String> e : BLOCKED.entrySet())
            if (!decoded.contains("tag:\"" + e.getValue() + "\"")) ids.add(e.getKey());
        return ids;
    }

    static boolean isBlocked(@NonNull JSONObject item, @NonNull Set<Integer> blocked) {
        if (blocked.isEmpty()) return false;
        JSONArray tagIds = item.optJSONArray("tag_ids");
        if (tagIds == null) return false;
        for (int i = 0; i < tagIds.length(); i++)
            if (blocked.contains(tagIds.optInt(i))) return true;
        return false;
    }
}
