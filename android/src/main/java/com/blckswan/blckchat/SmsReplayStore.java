package com.blckswan.blckchat;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Authenticated-message replay registry. Only message IDs and peer
 * public-key fingerprints are stored. No SMS content or private keys.
 *
 * Unique SQLite index makes concurrent duplicate arrival fail closed;
 * the old 256-ID rolling SharedPreferences cache was insufficient.
 * This DB lives in the app sandbox and is never exported or backed up.
 */
public final class SmsReplayStore extends SQLiteOpenHelper {
    private static final int VERSION=1;
    private static final int MAX_MESSAGES=100000;
    private static final String DB="blckchat42_sms_replay.db";

    public SmsReplayStore(Context context) {
        super(context.getApplicationContext(),DB,null,VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE seen (peer TEXT NOT NULL, " +
            "message_id TEXT NOT NULL, received_at INTEGER NOT NULL, " +
            "PRIMARY KEY(peer,message_id))");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int old,int next) {
        throw new IllegalStateException("Replay-databasen kan ikke nullstilles automatisk");
    }

    private static void valid(String peer,String id) {
        if(peer==null||!peer.matches("[0-9a-f]{64}")||
           id==null||!id.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Ugyldig replay-identitet");
    }

    public synchronized boolean contains(String peer,String id) {
        valid(peer,id);
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor cursor=db.rawQuery(
            "SELECT 1 FROM seen WHERE peer=? AND message_id=? LIMIT 1",
            new String[]{peer,id})) {
            return cursor.moveToFirst();
        }
    }

    /**
     * Returns false if the ID was previously consumed.
     * Must be called only AFTER AES-GCM verification succeeded.
     */
    public synchronized boolean record(String peer,String id) {
        valid(peer,id);
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            try(Cursor already=db.rawQuery(
                "SELECT 1 FROM seen WHERE peer=? AND message_id=? LIMIT 1",
                new String[]{peer,id})) {
                if(already.moveToFirst()) {
                    db.setTransactionSuccessful();
                    return false;
                }
            }
            try(Cursor count=db.rawQuery("SELECT COUNT(*) FROM seen",null)) {
                if(!count.moveToFirst()||count.getLong(0)>=MAX_MESSAGES)
                    throw new IllegalStateException(
                        "Replay-registret er fullt. Ingen meldinger godtas.");
            }
            ContentValues values=new ContentValues();
            values.put("peer",peer);
            values.put("message_id",id);
            values.put("received_at",System.currentTimeMillis());
            long row=db.insertOrThrow("seen",null,values);
            db.setTransactionSuccessful();
            return row>=0;
        }catch(SQLiteConstraintException duplicate) {
            return false;
        }finally {
            db.endTransaction();
        }
    }
}
