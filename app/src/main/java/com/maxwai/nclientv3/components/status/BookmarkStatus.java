package com.maxwai.nclientv3.components.status;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.settings.Database;

import java.util.Date;

/**
 * Custom (fork only) feature: the bookmarked gallery gets the "Reading" status (blue by default),
 * so it can be seen at a glance in the lists.
 * <p>
 * The color follows the bookmark: moving or deleting the bookmark takes the status away again,
 * but only if the gallery still is "Reading" (the user may have changed it since).
 */
public final class BookmarkStatus {
    private BookmarkStatus() {
    }

    /**
     * The status names are stored in the database in the language that was active when it was
     * created, so accept the usual ones.
     */
    private static String[] candidateNames(Context context) {
        return new String[]{context.getString(R.string.default_status_1), "Reading", "正在阅读"};
    }

    /**
     * @return the "Reading" status, created if the user deleted it
     */
    @NonNull
    private static Status getReadingStatus(@NonNull Context context) {
        for (String name : candidateNames(context)) {
            Status status = StatusManager.getByName(name);
            if (status != null) return status;
        }
        return StatusManager.add(context.getString(R.string.default_status_1), Color.BLUE);
    }

    private static boolean isReading(@NonNull Context context, @NonNull Status status) {
        for (String name : candidateNames(context))
            if (name.equals(status.name)) return true;
        return false;
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
        Queries.StatusMangaTable.remove(id);
        CompletedSync.refreshLists(id);
    }
}
