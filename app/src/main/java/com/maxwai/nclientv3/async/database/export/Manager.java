package com.maxwai.nclientv3.async.database.export;

import android.net.Uri;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.R;
import com.maxwai.nclientv3.SettingsActivity;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.EOFException;
import java.io.IOException;
import java.util.zip.ZipException;

public class Manager extends Thread {
    @NonNull
    private final Uri file;
    @NonNull
    private final SettingsActivity context;
    private final boolean export;
    private final Runnable end;

    public Manager(@NonNull Uri file, @NonNull SettingsActivity context, boolean export, Runnable end) {
        this.file = file;
        this.context = context;
        this.export = export;
        this.end = end;
    }


    @Override
    public void run() {
        // [FORK] the export takes ~15 s on a big tag table and gave no sign of life: leaving the app killed it
        if (export)
            context.runOnUiThread(() -> Toast.makeText(context, R.string.export_in_progress, Toast.LENGTH_LONG).show());
        try {
            if (export) Exporter.exportData(context, file);
            else Importer.importData(context, file);
            context.runOnUiThread(end);
        } catch (IOException | RuntimeException e) {// [FORK] RuntimeException: a bad file must not crash the app
            LogUtility.e(e, e);
            // [FORK] a truncated zip (export interrupted) gets a clear message
            boolean damaged = !export && (e instanceof ZipException || e instanceof EOFException);
            context.runOnUiThread(() -> Toast.makeText(context, damaged ? R.string.import_backup_damaged : R.string.failed,
                damaged ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
        }
    }
}
