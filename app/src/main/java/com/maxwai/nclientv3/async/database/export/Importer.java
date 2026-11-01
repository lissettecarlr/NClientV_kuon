package com.maxwai.nclientv3.async.database.export;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.JsonReader;

import androidx.annotation.NonNull;

import com.maxwai.nclientv3.components.status.FixedStatus;
import com.maxwai.nclientv3.components.status.StatusManager;
import com.maxwai.nclientv3.settings.Database;
import com.maxwai.nclientv3.utility.LogUtility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

class Importer {
    private static void importSharedPreferences(Context context, String sharedName, InputStream stream) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(stream));
        if (sharedName.contains("/")) {
            String[] names = sharedName.split("/");
            sharedName = names[names.length - 1];
        }
        SharedPreferences.Editor editor = context.getSharedPreferences(sharedName, 0).edit();
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            reader.beginObject();
            Exporter.SharedType type = Exporter.SharedType.valueOf(reader.nextName());
            switch (type) {
                case STRING:
                    editor.putString(name, reader.nextString());
                    break;
                case INT:
                    editor.putInt(name, reader.nextInt());
                    break;
                case FLOAT:
                    editor.putFloat(name, (float) reader.nextDouble());
                    break;
                case LONG:
                    editor.putLong(name, reader.nextLong());
                    break;
                case BOOLEAN:
                    editor.putBoolean(name, reader.nextBoolean());
                    break;
                case STRING_SET:
                    Set<String> strings = new HashSet<>();
                    reader.beginArray();
                    while (reader.hasNext())
                        strings.add(reader.nextString());
                    reader.endArray();
                    editor.putStringSet(name, strings);
                    break;
            }
            reader.endObject();
        }
        editor.apply();
    }

    private static void importDB(InputStream stream) throws IOException {
        SQLiteDatabase db = Database.getDatabase();
        if (db == null)
            throw new IOException("Can't import Database, don't have database connection yet");
        db.beginTransaction();
        try {// [FORK] the transaction was never ended when the file was bad: rolled back now
            importTables(db, stream);
            FixedStatus.migrate(db);// [FORK] an old backup has the old "Completed"/"Reading" statuses
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        StatusManager.reload();// [FORK] the statuses in memory are the ones from before the import
    }

    private static void importTables(SQLiteDatabase db, InputStream stream) throws IOException {
        JsonReader reader = new JsonReader(new InputStreamReader(stream));
        reader.beginObject();
        while (reader.hasNext()) {
            String tableName = reader.nextName();
            db.delete(tableName, null, null);
            reader.beginArray();
            while (reader.hasNext()) {
                reader.beginObject();
                ContentValues values = new ContentValues();
                while (reader.hasNext()) {
                    String fieldName = reader.nextName();
                    switch (reader.peek()) {
                        case NULL:
                            values.putNull(fieldName);
                            reader.nextNull();
                            break;
                        case NUMBER:
                            //there are no doubles in the DB
                            values.put(fieldName, reader.nextLong());
                            break;
                        case STRING:
                            values.put(fieldName, reader.nextString());
                            break;
                    }
                }
                db.insertWithOnConflict(tableName, null, values, SQLiteDatabase.CONFLICT_REPLACE);
                reader.endObject();
            }
            reader.endArray();


        }

        reader.endObject();
    }

    public static void importData(@NonNull Context context, Uri selectedFile) throws IOException {
        // [FORK] copy the file and open it as a ZipFile first: a truncated backup (export interrupted) has no
        // central directory, so it fails here with a ZipException before anything of the database is touched
        File copy = File.createTempFile("import", ".zip", context.getCacheDir());
        try {
            try (InputStream in = context.getContentResolver().openInputStream(selectedFile);
                 OutputStream out = new FileOutputStream(copy)) {
                if (in == null) throw new IOException("Can't open " + selectedFile);
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            try (ZipFile zip = new ZipFile(copy)) {
                if (zip.getEntry(Exporter.DB_ZIP_FILE) == null)
                    throw new ZipException("Not a backup: no " + Exporter.DB_ZIP_FILE);
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    LogUtility.d("Importing: " + name);
                    try (InputStream inputStream = zip.getInputStream(entry)) {
                        if (Exporter.DB_ZIP_FILE.equals(name)) {
                            importDB(inputStream);
                        } else {
                            String shared = name.substring(0, name.length() - 5);
                            importSharedPreferences(context, shared, inputStream);
                        }
                    }
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            copy.delete();
        }
    }
}
