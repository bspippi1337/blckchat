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
    private static final String NEXT_PART="next_confirmed_part_index";
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
           .putInt(NEXT_PART,0)
           .putLong(CREATED,System.currentTimeMillis()).commit())
            throw new IllegalStateException("Kunne ikke lagre kryptert SMS-utkast");
    }

    /** The exact SMS part to offer to the system composer next. */
    public synchronized int currentPartIndex() {
        String wire=pending();
        if(wire==null)return -1;
        int index=prefs.getInt(NEXT_PART,0);
        int total=wire.split("\\n",-1).length;
        if(index<0||index>=total)
            throw new IllegalStateException("Ugyldig SMS-utkastindeks");
        return index;
    }

    public synchronized int partCount() {
        String wire=pending();
        return wire==null?0:wire.split("\\n",-1).length;
    }

    public synchronized String currentPart() {
        String wire=pending();
        if(wire==null)return null;
        int index=currentPartIndex();
        return wire.split("\\n",-1)[index];
    }

    /**
     * User confirms that the current part was SENT by the external SMS app.
     * This is a user assertion, not a carrier delivery receipt.
     * Returns true if that was the final part, otherwise advances persistently.
     */
    public synchronized boolean confirmCurrentPart() {
        String wire=pending();
        if(wire==null)
            throw new IllegalStateException("Ingen SMS-del å bekrefte");
        int total=partCount();
        int current=currentPartIndex();
        if(current+1>=total) {
            clear();
            return true;
        }
        if(!prefs.edit().putInt(NEXT_PART,current+1).commit())
            throw new IllegalStateException("Kunne ikke lagre SMS-fremdrift");
        return false;
    }

    public synchronized void clear() {
        if(!prefs.edit().remove(WIRE).remove(CREATED).remove(NEXT_PART).commit())
            throw new IllegalStateException("Kunne ikke slette SMS-utkastet");
    }
}
