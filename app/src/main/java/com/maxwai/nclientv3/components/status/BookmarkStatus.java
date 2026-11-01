package com.maxwai.nclientv3.components.status;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;

import java.util.Date;

/**
 * Custom (fork only) feature: the bookmarked gallery gets the fixed "Bookmark" status (blue),
 * so it can be seen at a glance in the lists.
 * <p>
 * The color follows the bookmark: moving or deleting the bookmark takes the status away again
 * ("Downloaded" if the gallery is downloaded), but only if the gallery still is "Bookmark"
 * (the user may have changed it since).
 */
public final class BookmarkStatus {
    private BookmarkStatus() {
    }

    /**
     * @return the fixed "Bookmark" status (see {@link FixedStatus}), created if missing
     */
    @NonNull
    private static Status getReadingStatus(@NonNull Context context) {
        Status status = StatusManager.getByName(FixedStatus.BOOKMARK);
        return status != null ? status : StatusManager.add(FixedStatus.BOOKMARK, Color.BLUE);
    }

    private static boolean isReading(@NonNull Context context, @NonNull Status status) {
        return FixedStatus.BOOKMARK.equals(status.name);
    }

    /**
     * Mark the gallery as "Reading", whatever status it had. Only the id is known (an entry of
     * the list), see {@link CompletedSync#markCompleted(Context, int)}.
     */
    public static void mark(@NonNull Context context, int id) {
        SQLiteDatabase db = Database.getDatabase();
        if (db == null || id < 1) return;
        Status status = getReadingStatus(context);
        Queries.StatusTable.insert(status);
        ContentValues values = new ContentValues(3);
        values.put("gallery", id);
        values.put("name", status.name);
        values.put("time", new Date().getTime());
        db.insertWithOnConflict(Queries.StatusMangaTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        CompletedSync.refreshLists(id);
    }

    /**
     * The bookmark does not point to the gallery any more: remove "Reading", unless another
     * bookmark still points to it or the user chose another status.
     * Call it after the bookmark was moved or deleted.
     */
    public static void clear(@NonNull Context context, int id) {
        if (Database.getDatabase() == null || id < 1) return;
        if (Queries.BookmarkTable.isAnchor(id)) return;
        if (!isReading(context, Queries.StatusMangaTable.getStatus(id))) return;
        // a gallery that is downloaded goes back to "Downloaded" instead of losing its status
        if (Global.hasStoragePermission(context) && Global.findGalleryFolder(context, id) != null) {
            CompletedSync.markCompleted(context, id);
        } else {
            Queries.StatusMangaTable.remove(id);
        }
        CompletedSync.refreshLists(id);
    }
}
