package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Tag;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Custom (fork only) feature: hide galleries with fewer pages than a configurable minimum.
 * <p>
 * Kept in its own file so that merging upstream only has to deal with a few hook lines in
 * {@link InspectorV3}.
 * <p>
 * After filtering, a server page can be left (almost) empty. To keep every displayed page full,
 * the next server pages are fetched and filtered the same way until the page is filled.
 * Since a displayed page can then consume several server pages (and only part of the last one),
 * we remember where each displayed page ended: (server page, offset in the filtered list).
 */
final class MinPagesFilter {
    /**
     * Max additional requests done to fill one displayed page
     */
    private static final int MAX_EXTRA_REQUESTS = 6;
    private static final int MAX_CURSORS = 500;
    private static final Pattern PAGE_PARAM = Pattern.compile("([?&]page=)\\d+");
    /**
     * key: pageless url + "|" + displayed page; value: {server page, offset} where that page starts
     */
    private static final Map<String, int[]> CURSORS = new HashMap<>();
    private static int pageSize = 0;

    private MinPagesFilter() {
    }

    static int getMinPages(@Nullable Context context) {
        if (context == null) return 0;
        return context.getSharedPreferences("Settings", 0)
            .getInt(context.getString(R.string.preference_key_min_pages), 10);
    }

    static boolean isEnabled(@Nullable Context context, @NonNull ApiRequestType type) {
        // Own favorites are never hidden
        return type != ApiRequestType.FAVORITE && (getMinPages(context) > 0 || BlockedTagFilter.isEnabled(context));
    }

    /**
     * @param first     already downloaded response of {@code url}
     * @param exactTags if not null, galleries must contain all of these tags (exact match setting)
     */
    @NonNull
    static Result load(@NonNull Context context, @NonNull String url, int page, @NonNull JSONObject first, int minPages, @Nullable Set<Tag> exactTags) throws JSONException {
        Set<Integer> blocked = BlockedTagFilter.blockedIds(context, url);// [FORK]
        Matcher matcher = PAGE_PARAM.matcher(url);
        if (!matcher.find()) {
            // Unknown url format, only filter, no filling
            List<SimpleGallery> list = parsePage(context, first, minPages, exactTags, blocked);
            return new Result(list, first.optInt("num_pages", Math.max(1, page)));
        }
        String base = matcher.replaceFirst("$1#");

        int target;
        int[] start;
        int maxExtra = MAX_EXTRA_REQUESTS;
        synchronized (CURSORS) {
            pageSize = Math.max(pageSize, first.getJSONArray("result").length());
            target = pageSize;
            if (page <= 1) removeCursors(base);
            start = CURSORS.get(base + "|" + page);
            // [FORK] pages before a bookmark jump are plain server pages: filling them from the next
            // server pages would repeat what the user just saw on the page after
            int[] jump = CURSORS.get(base + "|jump");
            if (start == null && jump != null && page < jump[0]) maxExtra = 0;
        }
        int serverPage = start != null ? start[0] : page;
        int offset = start != null ? start[1] : 0;
        int totalPages = first.optInt("num_pages", Math.max(1, page));

        List<SimpleGallery> out = new ArrayList<>(target);
        JSONObject current = serverPage == page ? first : fetch(context, url, serverPage);
        int nextPage = serverPage, nextOffset = offset;
        int extra = 0;
        while (current != null) {
            totalPages = current.optInt("num_pages", totalPages);
            List<SimpleGallery> kept = current == first
                ? parsePage(context, current, minPages, exactTags, blocked)
                : parsePageSafe(context, current, minPages, exactTags, blocked);
            int i = offset;
            while (i < kept.size() && out.size() < target) out.add(kept.get(i++));
            if (i < kept.size()) {//this server page is only partially used
                nextPage = serverPage;
                nextOffset = i;
                break;
            }
            nextPage = serverPage + 1;
            nextOffset = 0;
            if (out.size() >= target || nextPage > totalPages || extra >= maxExtra) break;
            extra++;
            offset = 0;
            serverPage = nextPage;
            current = fetch(context, url, serverPage);//null on failure -> retried next time
        }

        boolean exhausted = nextPage > totalPages;
        synchronized (CURSORS) {
            if (!exhausted) {
                if (CURSORS.size() > MAX_CURSORS) CURSORS.clear();
                CURSORS.put(base + "|" + (page + 1), new int[]{nextPage, nextOffset});
            }
        }
        LogUtility.d("MinPagesFilter: page " + page + " -> " + out.size() + " galleries, next=" + nextPage + "/" + nextOffset + " of " + totalPages);
        // When everything was consumed, this is really the last displayed page
        return new Result(out, exhausted ? page : totalPages);
    }

    /**
     * [FORK] A bookmark opens the list directly on {@code page}: forget what we knew about this list
     * and remember that the pages before it must not be filled.
     *
     * @param url any url of the list (contains the page param)
     */
    static void markJump(@NonNull String url, int page) {
        Matcher matcher = PAGE_PARAM.matcher(url);
        if (!matcher.find()) return;
        String base = matcher.replaceFirst("$1#");
        synchronized (CURSORS) {
            removeCursors(base);
            CURSORS.put(base + "|jump", new int[]{page});
        }
    }

    private static void removeCursors(String base) {
        Iterator<String> it = CURSORS.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(base + "|")) it.remove();
        }
    }

    @Nullable
    private static JSONObject fetch(@NonNull Context context, @NonNull String url, int serverPage) {
        String pageUrl = PAGE_PARAM.matcher(url).replaceFirst("$1" + serverPage);
        try {
            // [FORK] the server answers 429 to bursts: wait a bit and try again
            for (int attempt = 0; attempt < 3; attempt++) {
                try (Response response = Global.getClient(context).newCall(new Request.Builder().url(pageUrl).build()).execute()) {
                    if (response.code() == 429 && attempt < 2) {
                        Thread.sleep(1500L * (attempt + 1));
                        continue;
                    }
                    if (!response.isSuccessful() || response.body() == null) return null;
                    JSONObject json = new JSONObject(response.body().string());
                    return json.has("result") ? json : null;
                }
            }
            return null;
        } catch (Exception e) {
            LogUtility.e("MinPagesFilter: unable to fetch " + pageUrl, e);
            return null;
        }
    }

    @NonNull
    private static List<SimpleGallery> parsePageSafe(Context context, JSONObject json, int minPages, @Nullable Set<Tag> exactTags, Set<Integer> blocked) {
        try {
            return parsePage(context, json, minPages, exactTags, blocked);
        } catch (JSONException e) {
            LogUtility.e("MinPagesFilter: invalid page", e);
            return new ArrayList<>();
        }
    }

    @NonNull
    private static List<SimpleGallery> parsePage(Context context, JSONObject json, int minPages, @Nullable Set<Tag> exactTags, Set<Integer> blocked) throws JSONException {
        JSONArray results = json.getJSONArray("result");
        List<SimpleGallery> list = new ArrayList<>(results.length());
        for (int i = 0; i < results.length(); i++) {
            JSONObject item = results.getJSONObject(i);
            // Missing field -> keep the gallery
            if (item.optInt("num_pages", Integer.MAX_VALUE) < minPages) continue;
            if (BlockedTagFilter.isBlocked(item, blocked)) {// [FORK]
                LogUtility.d("MinPagesFilter: hidden blocked gallery " + item.optInt("id"));
                continue;
            }
            SimpleGallery gallery = SimpleGallery.fromV2ListItem(context, item);
            if (exactTags != null && !gallery.hasTags(exactTags)) continue;
            list.add(gallery);
        }
        return list;
    }

    static final class Result {
        final List<SimpleGallery> galleries;
        final int pageCount;

        Result(List<SimpleGallery> galleries, int pageCount) {
            this.galleries = galleries;
            this.pageCount = pageCount;
        }
    }
}
