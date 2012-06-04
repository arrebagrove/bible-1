/*
 * vim: set sta sw=4 et:
 *
 * Copyright (C) 2012 Liu DongMiao <thom@piebridge.me>
 *
 * This program is free software. It comes without any warranty, to
 * the extent permitted by applicable law. You can redistribute it
 * and/or modify it under the terms of the Do What The Fuck You Want
 * To Public License, Version 2, as published by Sam Hocevar. See
 * http://sam.zoy.org/wtfpl/COPYING for more details.
 *
 */

package me.piebridge.bible;

import android.content.UriMatcher;
import android.content.ContentValues;
import android.content.ContentProvider;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import android.os.Environment;

public class Provider extends ContentProvider
{
    public static final String COLUMN_BOOK = "book";
    public static final String COLUMN_VERSE = "verse";
    public static final String COLUMN_HUMAN = "human";
    public static final String COLUMN_UNFORMATTED = "unformatted";
    public static final String COLUMN_PREVIOUS = "previous";
    public static final String COLUMN_NEXT = "next";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_OSIS = "osis";
    public static final String COLUMN_CHAPTERS = "chapters";
    public static final String TAG = "me.piebridge.bible";

    public static String PATH = null;
    public static String databaseVersion = "";
    public static boolean versionChanged = true;
    public static ArrayList<String> books = new ArrayList<String>();
    public static ArrayList<String> osiss = new ArrayList<String>();
    public static ArrayList<String> chapters = new ArrayList<String>();
    public static ArrayList<String> versions = new ArrayList<String>();

    private static SQLiteDatabase database = null;

    private static final String TABLE_VERSES = "verses left outer join books on (verses.book = books.osis)";
    private static final String[] COLUMNS_VERSE = {"id as _id", "book", "human", "verse * 1000 as verse", "unformatted"};

    private static final String TABLE_CHAPTERS = "chapters";
    private static final String[] COLUMNS_CHAPTER = {
        "reference_osis as osis",
        "reference_human as human",
        "content",
        "previous_reference_osis as previous",
        "next_reference_osis as next"};

    private static final String TABLE_BOOKS = "books";
    private static final String[] COLUMNS_BOOKS = {"number as _id", "osis", "human", "chapters"};

    public static final String AUTHORITY = "me.piebridge.bible.provider";
    public static final Uri CONTENT_URI_SEARCH = Uri.parse("content://" + AUTHORITY + "/search");
    public static final Uri CONTENT_URI_VERSE = Uri.parse("content://" + AUTHORITY + "/verse");
    public static final Uri CONTENT_URI_CHAPTER = Uri.parse("content://" + AUTHORITY + "/chapter");

    private static final int URI_SEARCH = 0;
    private static final int URI_VERSE = 1;
    private static final int URI_CHAPTER = 2;

    private static final UriMatcher uriMatcher = buildUriMatcher();
    private static UriMatcher buildUriMatcher() {
        UriMatcher matcher =  new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, "search/*", URI_SEARCH);
        matcher.addURI(AUTHORITY, "verse/#", URI_VERSE);
        matcher.addURI(AUTHORITY, "chapter/*", URI_CHAPTER);

        // TODO: support suggestion
        /*
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY, SEARCH_SUGGEST);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_QUERY + "/*", SEARCH_SUGGEST);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT, REFRESH_SHORTCUT);
           matcher.addURI(AUTHORITY, SearchManager.SUGGEST_URI_PATH_SHORTCUT + "/*", REFRESH_SHORTCUT);
           */

        return matcher;
    }

    public static void closeDatabase() {
        if (database != null) {
            Log.d(TAG, "close database \"" + database.getPath() + "\"");
            database.close();
            database = null;
            databaseVersion = "";
        }
    }

    public void setBooks() {
        books.clear();
        osiss.clear();
        chapters.clear();
        Cursor cursor = database.query(TABLE_BOOKS, COLUMNS_BOOKS, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String osis = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_OSIS));
                String human = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_HUMAN));
                String count = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CHAPTERS));
                osiss.add(osis);
                books.add(human);
                chapters.add(count);
            }
        } finally {
            cursor.close();
        }

    }

    private static boolean setDatabasePath() {
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + ".piebridge" + File.separator;
            Log.d(TAG, "set database path: " + PATH);
            return true;
        }
        return false;
    }

    public boolean setVersion(String version) {
        if (database != null) {
            if (databaseVersion.equals(version)) {
                return true;
            }
            Log.d(TAG, "close database \"" + database.getPath() + "\"");
            database.close();
        }

        if (!setDatabasePath()) {
            return false;
        }
        versionChanged = true;
        String path = PATH + version + ".sqlite3";
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            databaseVersion = version;
            Log.d(TAG, "open database \"" + path + "\"");
            database = SQLiteDatabase.openDatabase(file.getAbsolutePath(), null,
                    SQLiteDatabase.OPEN_READONLY | SQLiteDatabase.NO_LOCALIZED_COLLATORS);
            setBooks();
            return true;
        } else {
            databaseVersion = "";
            database = null;
            return false;
        }
    }

    private Cursor queryVerse(String query) {
        if (database == null) {
            return null;
        }

        Cursor cursor = database.query(
                TABLE_VERSES,
                COLUMNS_VERSE,
                "unformatted like ?",
                new String[] { "%" + query + "%" },
                null,
                null,
                null
                );

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getVerse(String id) {
        if (database == null) {
            return null;
        }

        Cursor cursor = database.query(
                TABLE_VERSES,
                COLUMNS_VERSE,
                "_id = ?",
                new String[] {id},
                null,
                null,
                null
                );

        if (cursor == null) {
            return null;
        } else if (!cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    private Cursor getChapter(String osis) {
        if (database == null) {
            return null;
        }

        Cursor cursor = null;

        if (!osis.equals("null")) {
            cursor = database.query(
                TABLE_CHAPTERS,
                COLUMNS_CHAPTER,
                "reference_osis = ?",
                new String[] {osis},
                null,
                null,
                null,
                "1");
        } else {
            cursor = database.query(
                TABLE_CHAPTERS,
                COLUMNS_CHAPTER,
                null,
                null,
                null,
                null,
                null,
                "1");
        }

        if (cursor == null) {
            return null;
        } else if (cursor.getCount() != 1 || !cursor.moveToFirst()) {
            cursor.close();
            return null;
        }

        return cursor;
    }

    public static boolean setVersions() {
        versions.clear();
        if (!setDatabasePath()) {
            return false;
        }
        File path = new File(PATH);
        if (path.exists() && path.isDirectory()) {
            String[] names = path.list();
            for (String name: names) {
                File file = new File(path, name);
                if (name.endsWith("sqlite3") && file.exists() && file.isFile()) {
                    if (name.equals("niv.sqlite3")) {
                        versions.add(0, name.replace(".sqlite3", ""));
                    } else {
                        versions.add(name.replace(".sqlite3", ""));
                    }
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCreate() {
        setVersions();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query uri: " + uri);
        String version = uri.getFragment();
        if (version != null && !setVersion(version)) {
            return null;
        }
        Log.d(TAG, "query database \"" + database.getPath() + "\"");
        if (databaseVersion.equals("")) {
            return null;
        }
        switch (uriMatcher.match(uri)) {
            case URI_SEARCH:
                String query = uri.getLastPathSegment();
                return queryVerse(query);
            case URI_VERSE:
                String id = uri.getLastPathSegment();
                return getVerse(id);
            case URI_CHAPTER:
                String osis = uri.getLastPathSegment();
                return getChapter(osis);
            default:
                throw new IllegalArgumentException("Unknown Uri: " + uri);
        }
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    public static int[] getChapterVerse(String string) {
        Integer value = Integer.parseInt(string);
        Integer chapter = value / 1000;
        Integer verse = value - chapter * 1000;
        return new int[] {chapter, verse};
    }

    public static boolean isCJK(char c) {
        int v = (int) c;

        if(v >= 0x3400 && v <= 0x4db5) return true;
        if(v >= 0x4e00 && v <= 0x9fa5) return true;

        return false;
    }

    public static boolean isCJKVersion(String string) {
        if (string.equals("rcuvss") || string.equals("cunpss") || string.equals("ccb")) {
            return true;
        }
        return false;
    }

}
