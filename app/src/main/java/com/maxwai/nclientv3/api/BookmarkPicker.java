package com.maxwai.nclientv3.api;

import android.app.Activity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.components.GenericGallery;

import java.util.List;

/**
 * Custom (fork only) feature: "pick mode" of the add bookmark button. The button does not guess
 * which gallery to bookmark: it asks the user to tap one. While picking, a tap on a gallery
 * bookmarks it instead of opening it. The hint stays until a gallery is picked or the user cancels
 * (cancel button, back key, or the add bookmark button again).
 */
public final class BookmarkPicker {
    @Nullable
    private Snackbar hint = null;

    public boolean isActive() {
        return hint != null;
    }

    /**
     * Start picking, or cancel if already picking. Does nothing if the list can not be bookmarked.
     */
    public void toggle(@NonNull View master, @Nullable InspectorV3 inspector) {
        if (isActive()) {
            cancel();
            return;
        }
        if (!BookmarkMarker.isBookmarkable(inspector)) return;
        Snackbar snackbar = Snackbar.make(master, R.string.bookmark_pick_hint, Snackbar.LENGTH_INDEFINITE)
            .setAction(R.string.cancel, v -> cancel());
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                // dismissed by something else: stop picking
                if (hint == transientBottomBar) hint = null;
            }
        });
        hint = snackbar;
        snackbar.show();
    }

    public void cancel() {
        Snackbar current = hint;
        hint = null;
        if (current != null) current.dismiss();
    }

    /**
     * A gallery was tapped.
     *
     * @return true if the tap was used to bookmark the gallery, false if the tap is a normal one
     */
    public boolean pick(@NonNull Activity activity, @Nullable InspectorV3 inspector,
                        @NonNull List<? extends GenericGallery> list, int position) {
        if (!isActive() || inspector == null) return false;
        BookmarkMarker.Book book = BookmarkMarker.bookAt(list, position);
        if (book == null) return false;
        cancel();
        BookmarkMarker.save(activity, inspector, book);
        return true;
    }
}
