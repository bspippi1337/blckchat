package com.blckswan.blckchat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * One pending encrypted SMS draft, intentionally without recipient number,
 * private key or plaintext. ACTION_SENDTO does not guarantee that a user
 * actually pressed Send, so this draft persists until explicit confirmation.
 *
 * Retry reuses the SAME ciphertext and message ID. The receiver's replay
 * store prevents it being displayed twice if the first attempt arrived.
 */
public final class SmsDraftStore {
    private static final String PREF="blckchat42_sms_unsent_v1";
    private static final String WIRE="last_encrypted_wire";
    private static final String CREATED="draft_created";
    private static final long EXPIRES_MS=7L*24L*60L*60L*1000L;
    private static final int MAX_TEXT=3500;

    private final SharedPreferences prefs;

    public SmsDraftStore(Context context) {
        prefs=context.getApplicationContext().getSharedPreferences(
            PREF,Context.MODE_PRIVATE);
    }

    public synchronized String pending() {
        String wire=prefs.getString(WIRE,null);
        long created=prefs.getLong(CREATED,0L);
        long now=System.currentTimeMillis();
        if(wire==null)return null;
        if(wire.isEmpty()||wire.length()>MAX_TEXT||created<=0||
           created>now||now-created>EXPIRES_MS) {
            clear();
            return null;
        }
        return wire;
    }

    public synchronized void remember(String wire) {
        if(wire==null||wire.length()>MAX_TEXT||wire.isEmpty())
            throw new IllegalArgumentException("Ugyldig kryptert SMS-utkast");
        SmsFrameAssembler assembly=new SmsFrameAssembler();
        SmsFrameAssembler.Result result=assembly.accept(
            wire,System.currentTimeMillis());
        if(result.state!=SmsFrameAssembler.State.COMPLETE)
            throw new IllegalArgumentException("Ufullstendig kryptert utkast");
        String previous=pending();
        if(previous!=null) {
            if(previous.equals(wire))return;
            throw new IllegalStateException(
                "Du har allerede en kryptert SMS som ikke er bekreftet sendt. "+
                "Åpne den på nytt eller slett utkastet før du lager en ny.");
        }
        if(!prefs.edit().putString(WIRE,wire)
           .putLong(CREATED,System.currentTimeMillis()).commit())
            throw new IllegalStateException("Kunne ikke lagre kryptert SMS-utkast");
    }

    public synchronized void clear() {
        if(!prefs.edit().remove(WIRE).remove(CREATED).commit())
            throw new IllegalStateException("Kunne ikke slette SMS-utkastet");
    }
}
