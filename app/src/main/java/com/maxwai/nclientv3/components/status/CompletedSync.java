package com.maxwai.nclientv3.components.status;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.Gallery;
import com.maxwai.nclientv3.api.components.GenericGallery;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.async.downloader.DownloadGalleryV2;
import com.maxwai.nclientv3.async.downloader.DownloadQueue;
import com.maxwai.nclientv3.async.downloader.GalleryDownloaderV2;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.settings.Global;
import com.maxwai.nclientv3.utility.LogUtility;

import java.util.Date;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Custom (fork only) feature: keep the "Completed" status and the downloads in sync.
 * <ul>
 *     <li>a gallery fully downloaded gets the "Completed" status</li>
 *     <li>setting the "Completed" status on a gallery starts its download (if not already downloaded)</li>
 *     <li>"download all galleries in this page" marks all of them as "Completed"</li>
 * </ul>
 * Kept in its own file so that merging upstream only has to deal with a few hook lines.
 */
public final class CompletedSync {
    private CompletedSync() {
    }

    /**
     * The status names are stored in the database in the language that was active when it was
     * created, so accept the usual ones.
     */
    private static String[] candidateNames(Context context) {
        return new String[]{context.getString(R.string.default_status_2), "Completed", "已完成"};
    }

    /**
     * @return the "Completed" status, created if the user deleted it
     */
    @NonNull
    public static Status getCompletedStatus(@NonNull Context context) {
        for (String name : candidateNames(context)) {
            Status status = StatusManager.getByName(name);
            if (status != null) return status;
        }
        return StatusManager.add(context.getString(R.string.default_status_2), Color.GREEN);
    }

    public static boolean isCompleted(@NonNull Context context, @Nullable String statusName) {
        if (statusName == null) return false;
        for (String name : candidateNames(context))
            if (name.equals(statusName)) return true;
        return false;
    }

    /**
     * Mark a gallery for which we have the complete data (needed to be shown in the status viewer)
     */
    public static void markCompleted(@NonNull Context context, @NonNull GenericGallery gallery) {
        try {
            if (gallery.getGalleryData() == null) markCompleted(context, gallery.getId());
            else Queries.StatusMangaTable.insert(gallery, getCompletedStatus(context));
            refreshLists(gallery.getId());
        } catch (Exception e) {
            LogUtility.e("CompletedSync: unable to mark " + gallery.getId(), e);
        }
    }

    /**
     * Lists cache the status color of every entry: forget it for {@code id} and redraw, so a status
     * changed in the background (download finished) is visible without reloading the list
     */
    private static final Map<RecyclerView.Adapter<?>, SparseIntArray> LISTS = new WeakHashMap<>();

    /**
     * Register a list adapter and its status color cache, see {@link #refreshLists(int)}
     */
    public static void bind(@NonNull RecyclerView.Adapter<?> adapter, @NonNull SparseIntArray statusColors) {
        synchronized (LISTS) {
            LISTS.put(adapter, statusColors);
        }
    }

    static void refreshLists(int id) {
        new Handler(Looper.getMainLooper()).post(() -> {
            synchronized (LISTS) {
                for (Map.Entry<RecyclerView.Adapter<?>, SparseIntArray> e : LISTS.entrySet()) {
                    e.getValue().delete(id);
                    e.getKey().notifyDataSetChanged();
                }
            }
        });
    }

    /**
     * Mark a gallery knowing only its id (e.g. an entry of the main list).
     * The gallery data is stored later, when the download finishes and calls
     * {@link #markCompleted(Context, GenericGallery)}.
     */
    public static void markCompleted(@NonNull Context context, int id) {
        SQLiteDatabase db = Database.getDatabase();
        if (db == null || id < 1) return;
        Status status = getCompletedStatus(context);
        Queries.StatusTable.insert(status);
        ContentValues values = new ContentValues(3);
        values.put("gallery", id);
        values.put("name", status.name);
        values.put("time", new Date().getTime());
        db.insertWithOnConflict(Queries.StatusMangaTable.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /**
     * Called after the user chose a status for a gallery: "Completed" starts the download,
     * unless the gallery is already downloaded or being downloaded.
     */
    public static void onStatusChosen(@NonNull Context context, @NonNull GenericGallery gallery, @Nullable String statusName) {
        if (!isCompleted(context, statusName)) return;
        if (!(gallery instanceof Gallery) || !gallery.isValid()) return;
        if (!Global.hasStoragePermission(context)) return;
        int id = gallery.getId();
        if (Global.findGalleryFolder(context, id) != null) return;
        for (GalleryDownloaderV2 downloader : DownloadQueue.getDownloaders())
            if (downloader.getId() == id) return;
        DownloadGalleryV2.downloadRange(context, (Gallery) gallery, 0, gallery.getPageCount() - 1);
    }
}
