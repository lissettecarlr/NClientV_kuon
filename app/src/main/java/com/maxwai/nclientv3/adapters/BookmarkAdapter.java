package com.maxwai.nclientv3.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.maxwai.nclientv3.BookmarkActivity;
import com.maxwai.nclientv3.MainActivity;
import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.api.BookmarkSeeker;
import com.maxwai.nclientv3.api.InspectorV3;
import com.maxwai.nclientv3.async.database.Queries;
import com.maxwai.nclientv3.components.classes.Bookmark;
import com.maxwai.nclientv3.utility.IntentUtility;

import java.util.List;

public class BookmarkAdapter extends RecyclerView.Adapter<BookmarkAdapter.ViewHolder> {
    private static final int LAYOUT = R.layout.bookmark_layout;

    private final List<Bookmark> bookmarks;
    private final BookmarkActivity bookmarkActivity;
    private boolean seeking = false;// [FORK]

    public BookmarkAdapter(BookmarkActivity bookmarkActivity) {
        this.bookmarkActivity = bookmarkActivity;
        this.bookmarks = Queries.BookmarkTable.getBookmarks();
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(LAYOUT, parent, false));
    }


    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int pos) {
        final int position = holder.getBindingAdapterPosition();
        Bookmark bookmark = bookmarks.get(position);

        holder.queryText.setText(bookmark.toString());
        holder.pageLabel.setText(bookmarkActivity.getString(R.string.bookmark_page_format, bookmark.page));

        holder.rootLayout.setOnClickListener(v -> loadBookmark(bookmark));
        // [FORK] the small X was hard to hit: long press the bookmark to delete it
        holder.rootLayout.setOnLongClickListener(v -> {
            new MaterialAlertDialogBuilder(bookmarkActivity)
                .setTitle(bookmark.toString())
                .setItems(new CharSequence[]{bookmarkActivity.getString(R.string.delete)},
                    (dialog, which) -> removeBookmarkAtPosition(bookmarks.indexOf(bookmark)))
                .setNegativeButton(R.string.cancel, null)
                .show();
            return true;
        });
    }

    /**
     * Start an {@link MainActivity} with <code>bookmark</code> as query and page
     *
     * @param bookmark bookmark to load
     */
    private void loadBookmark(Bookmark bookmark) {
        // [FORK] the list moved since the bookmark was saved: look for the page of the anchor gallery
        if (bookmark.canSeek()) {
            if (seeking) return;
            seeking = true;
            Toast.makeText(bookmarkActivity, R.string.bookmark_seeking, Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                int page = BookmarkSeeker.seek(bookmarkActivity, bookmark.url, bookmark.page, bookmark.anchor);
                bookmarkActivity.runOnUiThread(() -> {
                    seeking = false;
                    if (!bookmarkActivity.isFinishing()) openBookmark(bookmark, page);
                });
            }).start();
            return;
        }
        openBookmark(bookmark, bookmark.page);
    }

    private void openBookmark(Bookmark bookmark, int page) {
        Intent i = new Intent(bookmarkActivity, MainActivity.class);
        i.putExtra(bookmarkActivity.getPackageName() + ".BYBOOKMARK", true);
        InspectorV3 inspector = bookmark.createInspector(bookmarkActivity, null);
        if (page != bookmark.page) inspector.setPage(page);// [FORK]
        i.putExtra(bookmarkActivity.getPackageName() + ".INSPECTOR", inspector);
        if (bookmark.canSeek()) i.putExtra(bookmarkActivity.getPackageName() + ".ANCHOR", bookmark.anchor);// [FORK]
        IntentUtility.startAnotherActivity(bookmarkActivity, i);
    }

    /**
     * remove bookmark from the adapter at <code>position</code>
     *
     * @param position index to delete
     */
    private void removeBookmarkAtPosition(int position) {
        if (position >= bookmarks.size()) return;
        Bookmark bookmark = bookmarks.get(position);
        bookmark.deleteBookmark();
        bookmarks.remove(bookmark);
        bookmarkActivity.runOnUiThread(() -> notifyItemRemoved(position));
    }

    @Override
    public int getItemCount() {
        return bookmarks.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final AppCompatImageButton deleteButton;
        final TextView queryText;
        final TextView pageLabel;
        final ConstraintLayout rootLayout;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            deleteButton = itemView.findViewById(R.id.remove_button);
            deleteButton.setVisibility(View.GONE);// [FORK] replaced by long press, see onBindViewHolder
            pageLabel = itemView.findViewById(R.id.page);
            queryText = itemView.findViewById(R.id.title);
            rootLayout = itemView.findViewById(R.id.master_layout);
        }
    }

}
