package com.blckswan.blckchat;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Durable ciphertext-only fragment cache. The user explicitly pastes or
 * shares incoming SMS text; BLCKCHAT does not read the SMS inbox.
 * Cache carries no private keys or plaintext. Android backup is disabled.
 */
public final class SmsFrameInbox {
    private static final String PREFS="blckchat42_sms_fragment_cache_v1";
    private static final String STATE="pending_ciphertext_fragments";
    private final SharedPreferences store;
    private final SmsFrameAssembler assembler;

    public SmsFrameInbox(Context context) {
        store=context.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        assembler=new SmsFrameAssembler();
        assembler.restore(store.getString(STATE,""),System.currentTimeMillis());
    }

    private void persist() {
        if(!store.edit().putString(STATE,
            assembler.snapshot(System.currentTimeMillis())).commit())
            throw new IllegalStateException("Kunne ikke lagre de krypterte SMS-delene");
    }

    /** COMPLETE still requires the independent native/AEAD verification. */
    public synchronized SmsFrameAssembler.Result accept(String frames) {
        SmsFrameAssembler.Result result=assembler.accept(
            frames,System.currentTimeMillis());
        persist();
        return result;
    }

    /** Call only after successful authentication and replay recording. */
    public synchronized void acknowledged(String id) {
        assembler.discard(id);
        persist();
    }

    public synchronized void clear() {
        assembler.clear();
        persist();
    }

    public synchronized int pendingCount() {
        return assembler.pendingCount();
    }
}
