package com.maxwai.nclientv3.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.regex.Pattern;

import okhttp3.Request;
import okhttp3.Response;

/**
 * Custom (fork only) feature: a bookmark remembers the id of the first gallery of the bookmarked
 * page (the "anchor"). New galleries push it to later pages, so when the bookmark is opened we look
 * for the page where the anchor is now and jump there. The list itself is not touched: the user
 * can then browse backwards (towards the newer galleries) with the normal page switcher.
 * <p>
 * Only lists sorted by recent are supported (ids decrease monotonically with the position).
 * <p>
 * The server rate limits (HTTP 429), so the search is an interpolation search: the id span of a
 * page tells how many pages away the anchor is, which usually takes 3-4 requests.
 */
public final class BookmarkSeeker {
    private static final Pattern PAGE_PARAM = Pattern.compile("([?&]page=)\\d+");
    /**
     * Safety net
     */
    private static final int MAX_REQUESTS = 14;
    private static final long PACE_MS = 300;

    private BookmarkSeeker() {
    }

    /**
     * @return id of the first gallery currently displayed by the inspector, 0 if none
     */
    public static int firstGalleryId(@NonNull InspectorV3 inspector) {
        List<GenericGallery> galleries = inspector.getGalleries();
        if (galleries == null || galleries.isEmpty()) return 0;
        return galleries.get(0).getId();
    }

    /**
     * Only lists sorted by recent have decreasing ids
     */
    public static boolean isSeekable(@NonNull ApiRequestType type, @NonNull String url) {
        if (type == ApiRequestType.FAVORITE) return false;
        if (type != ApiRequestType.BYALL && type != ApiRequestType.BYSEARCH && type != ApiRequestType.BYTAG)
            return false;
        return PAGE_PARAM.matcher(url).find() && !url.contains("sort=");
    }

    /**
     * Blocking, never call it from the UI thread.
     * <p>
     * A page "contains" the anchor when its smallest id is &lt;= anchor &lt;= its biggest id. Pages
     * whose smallest id is bigger than the anchor are before it ("bad"), the others are "good".
     * The result is the first good page.
     *
     * @param url       url of the bookmark (contains the page)
     * @param startPage page of the bookmark, the anchor can only have moved to a later page
     * @param anchor    id of the gallery to look for
     * @return the page that contains the anchor (or the first older gallery), startPage on failure
     */
    public static int seek(@NonNull Context context, @NonNull String url, int startPage, int anchor) {
        int[] requests = {0};
        int bad = 0;// last known page entirely before the anchor
        int good = Integer.MAX_VALUE;// first known page at or after the anchor
        int total = Integer.MAX_VALUE;
        int cur = Math.max(1, startPage);
        int found = -1;
        while (found < 0) {
            Probe p = probe(context, url, cur, requests);
            if (p == null) return good != Integer.MAX_VALUE ? good : startPage;
            if (p.totalPages > 0) total = p.totalPages;
            if (p.empty && cur > total) {// the list shrunk
                good = Math.min(good, cur);
            } else if (p.minId <= anchor) {
                if (p.maxId >= anchor) {
                    found = cur;
                    break;
                }
                good = Math.min(good, cur);
            } else {
                bad = Math.max(bad, cur);
                if (cur >= total) {// the anchor is older than everything we have
                    found = cur;
                    break;
                }
            }
            int low = bad + 1;
            int high = good == Integer.MAX_VALUE ? total : good - 1;
            if (low > high) {
                found = good != Integer.MAX_VALUE ? good : cur;
                break;
            }
            // How many pages away is the anchor, according to the id span of this page
            int next;
            if (p.empty) {
                next = (low + high) / 2;
            } else {
                long span = Math.max(1, (long) p.maxId - p.minId + 1);
                if (p.minId > anchor) next = (int) Math.min(high, cur + Math.max(1, ((long) p.minId - anchor) / span));
                else next = (int) Math.max(low, cur - Math.max(1, ((long) anchor - p.maxId) / span));
            }
            cur = Math.max(low, Math.min(high, next));
        }
        LogUtility.d("BookmarkSeeker: anchor " + anchor + " found on page " + found + " (" + requests[0] + " requests)");
        if (found > 1) MinPagesFilter.markJump(url, found);
        return found;
    }

    @Nullable
    private static Probe probe(@NonNull Context context, @NonNull String url, int page, int[] requests) {
        if (requests[0]++ >= MAX_REQUESTS) return null;
        String pageUrl = PAGE_PARAM.matcher(url).replaceFirst("$1" + page);
        // 429: wait and try again
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Thread.sleep(attempt == 0 ? PACE_MS : 1500L * attempt);
            } catch (InterruptedException e) {
                return null;
            }
            boolean[] rateLimited = {false};
            Probe result = fetchProbe(context, pageUrl, rateLimited);
            if (result != null || !rateLimited[0]) return result;
        }
        return null;
    }

    @Nullable
    private static Probe fetchProbe(@NonNull Context context, @NonNull String pageUrl, boolean[] rateLimited) {
        try (Response response = Global.getClient(context).newCall(new Request.Builder().url(pageUrl).build()).execute()) {
            if (response.code() == 429) rateLimited[0] = true;
            if (!response.isSuccessful() || response.body() == null) return null;
            JSONObject json = new JSONObject(response.body().string());
            JSONArray result = json.optJSONArray("result");
            if (result == null) return null;
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (int i = 0; i < result.length(); i++) {
                int id = result.getJSONObject(i).optInt("id", -1);
                if (id < 0) continue;
                min = Math.min(min, id);
                max = Math.max(max, id);
            }
            return new Probe(result.length() == 0 || max < 0, min, max, json.optInt("num_pages", 0));
        } catch (Exception e) {
            LogUtility.e("BookmarkSeeker: unable to fetch " + pageUrl, e);
            return null;
        }
    }

    private static final class Probe {
        final boolean empty;
        final int minId, maxId;
        final int totalPages;

        Probe(boolean empty, int minId, int maxId, int totalPages) {
            this.empty = empty;
            this.minId = empty ? Integer.MIN_VALUE : minId;
            this.maxId = empty ? Integer.MIN_VALUE : maxId;
            this.totalPages = totalPages;
        }
    }
}
