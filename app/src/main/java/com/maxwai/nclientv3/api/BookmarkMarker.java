package com.maxwai.nclientv3.api;

import android.app.Activity;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.api.enums.ApiRequestType;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Global;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Custom (fork only) feature: a bookmark points to one gallery of a list, not to a page.
 * <p>
 * Long pressing a gallery marks it. The bookmark remembers this gallery and the two galleries
 * after it in the list (the older ones, so a gallery taken down by the site does not lose the
 * bookmark). Everything above the bookmarked gallery is new since the bookmark was set.
 * <p>
 * {@link BookmarkSeeker} finds the page, {@link #locate} finds the position on that page.
 */
public final class BookmarkMarker {
    /**
     * Galleries remembered after the bookmarked one
     */
    private static final int FOLLOWERS = 2;
    private static final Pattern PAGE_PARAM = Pattern.compile("([?&]page=)\\d+");

    private BookmarkMarker() {
    }

    /**
     * The bookmarked gallery and the ones after it
     */
    public static final class Book {
        /**
         * [bookmarked gallery, next one, the one after], 0 when there is none
         */
        public final int[] ids;
        public final String title;

        Book(int[] ids, String title) {
            this.ids = ids;
            this.title = title == null ? "" : title;
        }
    }

    public static final class Landing {
        /**
         * Position in the list where the bookmark is
         */
        public final int position;
        /**
         * false when the bookmarked gallery itself is gone and the position was guessed
         */
        public final boolean exact;

        Landing(int position, boolean exact) {
            this.position = position;
            this.exact = exact;
        }
    }

    /**
     * @return the gallery at <code>position</code> with its followers, null if there is no gallery
     */
    @Nullable
    public static Book bookAt(@NonNull List<? extends GenericGallery> list, int position) {
        if (position < 0 || position >= list.size()) return null;
        GenericGallery gallery = list.get(position);
        if (gallery == null) return null;
        int[] ids = new int[1 + FOLLOWERS];
        ids[0] = gallery.getId();
        for (int i = 1; i <= FOLLOWERS; i++) {
            int index = position + i;
            GenericGallery next = index < list.size() ? list.get(index) : null;
            ids[i] = next == null ? 0 : next.getId();
        }
        return new Book(ids, gallery.getTitle());
    }

    public static boolean isBookmarkable(@Nullable InspectorV3 inspector) {
        if (inspector == null) return false;
        ApiRequestType type = inspector.getRequestType();
        return type == ApiRequestType.BYALL || type == ApiRequestType.BYSEARCH
            || type == ApiRequestType.BYTAG || type == ApiRequestType.FAVORITE;
    }

    /**
     * One bookmark per list: the url of the bookmark is the one of the first page, so marking
     * another gallery of the same list moves the bookmark.
     */
    @NonNull
    public static String listUrl(@NonNull String url) {
        return PAGE_PARAM.matcher(url).replaceFirst("$1" + 1);
    }

    public static void save(@NonNull Activity activity, @NonNull InspectorV3 inspector, @NonNull Book book) {
        // With infinite scroll the list holds several pages, so the page of the gallery is unknown.
        // It is only a hint: BookmarkSeeker searches in both directions
        int page = Global.isInfiniteScrollMain() ? 1 : inspector.getPage();
        Queries.BookmarkTable.addBookmark(inspector, listUrl(inspector.getUrl()), page, book);
        Toast.makeText(activity, activity.getString(R.string.bookmark_saved, book.title), Toast.LENGTH_SHORT).show();
    }

    /**
     * Long press on a gallery: mark it as bookmark or do the usual action (<code>toggleTitles</code>)
     */
    public static void showMenu(@NonNull Activity activity, @Nullable InspectorV3 inspector,
                                @NonNull List<? extends GenericGallery> list, int position,
                                @NonNull Runnable toggleTitles) {
        Book book = isBookmarkable(inspector) ? bookAt(list, position) : null;
        if (book == null) {
            toggleTitles.run();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
            .setTitle(book.title)
            .setItems(new CharSequence[]{
                    activity.getString(R.string.bookmark_mark_gallery),
                    activity.getString(R.string.bookmark_toggle_titles)},
                (dialog, which) -> {
                    if (which == 0) save(activity, inspector, book);
                    else toggleTitles.run();
                })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }

    /**
     * Where is the bookmark in <code>galleries</code> (one loaded page)?
     * <ol>
     *     <li>the bookmarked gallery</li>
     *     <li>else the first follower still there (it took the place of the missing gallery)</li>
     *     <li>else, only if the list is ordered by id, the first gallery older than the bookmark</li>
     * </ol>
     *
     * @param ids     {@link Book#ids}
     * @param ordered true if the ids decrease along the list (sorted by recent)
     * @return null if the bookmark is not in this list
     */
    @Nullable
    public static Landing locate(@Nullable List<GenericGallery> galleries, @Nullable int[] ids, boolean ordered) {
        if (galleries == null || ids == null || ids.length == 0) return null;
        for (int k = 0; k < ids.length; k++) {
            if (ids[k] <= 0) continue;
            for (int i = 0; i < galleries.size(); i++) {
                if (galleries.get(i).getId() == ids[k]) return new Landing(i, k == 0);
            }
        }
        if (ordered && ids[0] > 0) {
            for (int i = 0; i < galleries.size(); i++) {
                if (galleries.get(i).getId() < ids[0]) return new Landing(i, false);
            }
        }
        return null;
    }

    /**
     * Safe to call from any thread
     */
    public static void announce(@NonNull Activity activity, @Nullable String title, @NonNull Landing landing) {
        activity.runOnUiThread(() -> {
            if (activity.isFinishing()) return;
            String text = landing.exact
                ? activity.getString(R.string.bookmark_located, title == null ? "" : title)
                : activity.getString(R.string.bookmark_located_missing);
            Toast.makeText(activity, text, Toast.LENGTH_LONG).show();
        });
    }
}
