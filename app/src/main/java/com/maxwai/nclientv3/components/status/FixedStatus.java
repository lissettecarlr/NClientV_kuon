package com.maxwai.nclientv3.components.status;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.utility.LogUtility;

/**
 * Custom (fork only) feature: two statuses that are set by the app itself and so can't be
 * deleted, renamed or recolored by the user:
 * <ul>
 *     <li>{@link #DOWNLOADED} (green): set when a gallery is downloaded, see {@link CompletedSync}</li>
 *     <li>{@link #BOOKMARK} (blue): set on the book a bookmark points to, see {@link BookmarkStatus}</li>
 * </ul>
 * They replace the old "Completed" and "Reading" statuses. The database stores a fixed name (not
 * translated, so it does not depend on the language that was active when it was created); the
 * shown name is {@link #displayName(Context, String)}.
 */
public final class FixedStatus {
    public static final String DOWNLOADED = "Downloaded";
    public static final String BOOKMARK = "Bookmark";

    private static final int DOWNLOADED_COLOR = Color.GREEN;
    private static final int BOOKMARK_COLOR = Color.BLUE;

    /**
     * The old "Completed" status (the default one, in every language it was translated to)
     */
    private static final String[] LEGACY_DOWNLOADED = {
        "Completed", "已完成", "Виконано", "Komplett", "完了済", "Completado", "Concluído",
        "تم", "Tamamlandı", "Terminé", "Завершено", "Completati"
    };
    /**
     * The old "Reading" status
     */
    private static final String[] LEGACY_BOOKMARK = {
        "Reading", "正在阅读", "Читання", "Lesen", "読書中", "Leyendo", "Lendo", "閱讀中",
        "قيد القراءة", "Okunuyor", "En cours", "Читается", "Leggendo"
    };

    private FixedStatus() {
    }

    public static boolean isFixed(String name) {
        return DOWNLOADED.equals(name) || BOOKMARK.equals(name);
    }

    /**
     * @return the name to show to the user for a status name stored in the database
     */
    @NonNull
    public static String displayName(@NonNull Context context, @NonNull String name) {
        if (DOWNLOADED.equals(name)) return context.getString(R.string.status_downloaded);
        if (BOOKMARK.equals(name)) return context.getString(R.string.status_bookmark);
        return name;
    }

    /**
     * Make sure both statuses exist with their fixed color, and turn the old "Completed" and
     * "Reading" statuses (and the galleries which had them) into them.
     * Called every time the database is set (open, upgrade, import of a backup), so an old
     * backup can't bring the old statuses back.
     */
    public static void migrate(@NonNull SQLiteDatabase db) {
        try {
            ensure(db, DOWNLOADED, DOWNLOADED_COLOR);
            ensure(db, BOOKMARK, BOOKMARK_COLOR);
            rename(db, LEGACY_DOWNLOADED, DOWNLOADED);
            rename(db, LEGACY_BOOKMARK, BOOKMARK);
        } catch (RuntimeException e) {
            LogUtility.e("FixedStatus: unable to migrate the statuses", e);
        }
    }

    private static void ensure(SQLiteDatabase db, String name, int color) {
        ContentValues values = new ContentValues(2);
        values.put("name", name);
        values.put("color", new Status(color, name).color);
        // no REPLACE: it would delete the row first (and the galleries with it if foreign keys were on)
        db.insertWithOnConflict("Status", null, values, SQLiteDatabase.CONFLICT_IGNORE);
        values.remove("name");
        db.update("Status", values, "name=?", new String[]{name});
    }

    private static void rename(SQLiteDatabase db, String[] legacyNames, String name) {
        for (String legacy : legacyNames) {
            ContentValues values = new ContentValues(1);
            values.put("name", name);
            db.update("StatusManga", values, "name=?", new String[]{legacy});
            db.delete("Status", "name=?", new String[]{legacy});
        }
    }
}
