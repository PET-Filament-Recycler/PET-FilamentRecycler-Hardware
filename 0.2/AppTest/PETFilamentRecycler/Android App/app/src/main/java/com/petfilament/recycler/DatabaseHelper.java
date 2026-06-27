package com.petfilament.recycler;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "bluetooth_logs.db";
    private static final int DATABASE_VERSION = 2;
    private static final int MAX_LOG_ENTRIES = 1000;

    private static final String TABLE_LOGS = "bluetooth_logs";
    private static final String COLUMN_ID = "_id";
    private static final String COLUMN_TIMESTAMP = "timestamp";
    private static final String COLUMN_DIRECTION = "direction";
    private static final String COLUMN_MESSAGE = "message";
    private static final String COLUMN_DEVICE = "device";

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    private static volatile DatabaseHelper instance;

    public static DatabaseHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (DatabaseHelper.class) {
                if (instance == null) {
                    instance = new DatabaseHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE " + TABLE_LOGS + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        COLUMN_TIMESTAMP + " TEXT NOT NULL, " +
                        COLUMN_DIRECTION + " TEXT NOT NULL, " +
                        COLUMN_MESSAGE + " TEXT NOT NULL, " +
                        COLUMN_DEVICE + " TEXT NOT NULL DEFAULT '');"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL(
                    "ALTER TABLE " + TABLE_LOGS + " ADD COLUMN " + COLUMN_DEVICE + " TEXT NOT NULL DEFAULT '';"
            );
        }
    }

    public synchronized void insertLog(String direction, String message) {
        insertLog(direction, message, "");
    }

    public synchronized void insertLog(String direction, String message, String device) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date());
        values.put(COLUMN_TIMESTAMP, timestamp);
        values.put(COLUMN_DIRECTION, direction);
        values.put(COLUMN_MESSAGE, message);
        values.put(COLUMN_DEVICE, device != null ? device : "");
        db.insert(TABLE_LOGS, null, values);
        pruneOldLogs(db);
    }

    private void pruneOldLogs(SQLiteDatabase db) {
        int count = getLogCount(db);
        if (count <= MAX_LOG_ENTRIES) {
            return;
        }
        int excess = count - MAX_LOG_ENTRIES;
        db.execSQL(
                "DELETE FROM " + TABLE_LOGS + " WHERE " + COLUMN_ID + " IN (" +
                        "SELECT " + COLUMN_ID + " FROM " + TABLE_LOGS +
                        " ORDER BY " + COLUMN_ID + " ASC LIMIT " + excess + ")"
        );
    }

    public synchronized int getLogCount() {
        return getLogCount(getReadableDatabase());
    }

    private int getLogCount(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + TABLE_LOGS, null)) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    public synchronized ArrayList<LogEntry> getAllLogs() {
        ArrayList<LogEntry> logs = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor cursor = db.query(
                TABLE_LOGS,
                null,
                null,
                null,
                null,
                null,
                COLUMN_ID + " DESC"
        )) {
            while (cursor.moveToNext()) {
                logs.add(new LogEntry(
                        cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DIRECTION)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE)),
                        cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DEVICE))
                ));
            }
        }
        return logs;
    }

    public synchronized void clearAllLogs() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_LOGS, null, null);
    }

    public static class LogEntry {
        public final long id;
        public final String timestamp;
        public final String direction;
        public final String message;
        public final String device;

        public LogEntry(long id, String timestamp, String direction, String message, String device) {
            this.id = id;
            this.timestamp = timestamp;
            this.direction = direction;
            this.message = message;
            this.device = device != null ? device : "";
        }

        public boolean hasDevice() {
            return device != null && !device.isEmpty();
        }
    }
}