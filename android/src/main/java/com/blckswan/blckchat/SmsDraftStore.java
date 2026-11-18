package com.blckswan.blckchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;

/**
 * One unresolved SMS message, stored as ciphertext only. Each of its
 * individual segments needs a user-confirmed send through the external
 * system SMS composer. Never infer carrier delivery from returning to app.
 *
 * A non-exportable Android Keystore HMAC key binds the ciphertext to the
 * verified contact identity AND to the intended E.164 recipient.
 * Neither the phone number nor the contact public key is persisted here.
 * Legacy drafts without this binding are discarded rather than delivered
 * to an arbitrary contact.
 */
public final class SmsDraftStore {
    private static final String PREF="blckchat42_sms_unsent_v1";
    private static final String WIRE="last_encrypted_wire";
    private static final String CREATED="draft_created";
    private static final String NEXT_PART="next_confirmed_part_index";
    private static final String RECIPIENT_MAC="recipient_binding_hmac_v1";
    private static final String ALIAS="blckchat42_sms_draft_binding_hmac_v1";
    private static final long EXPIRES_MS=7L*24L*60L*60L*1000L;
    private static final int MAX_TEXT=3500;
    private final SharedPreferences prefs;

    public SmsDraftStore(Context context) {
        prefs=context.getApplicationContext()
            .getSharedPreferences(PREF,Context.MODE_PRIVATE);
    }

    private static void validDestination(String phone,String peer) {
        if(phone==null || !phone.matches("\\+[1-9][0-9]{7,14}") ||
           peer==null || !peer.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Ugyldig kontakt eller mottakernummer");
    }

    private static SecretKey bindingKey() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey key=(SecretKey)store.getKey(ALIAS,null);
        if(key!=null)return key;
        synchronized(SmsDraftStore.class) {
            store.load(null);
            key=(SecretKey)store.getKey(ALIAS,null);
            if(key!=null)return key;
            KeyGenerator gen=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_HMAC_SHA256,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_SIGN).setDigests(
                    KeyProperties.DIGEST_SHA256).build());
            return gen.generateKey();
        }
    }

    private static byte[] destinationMac(String phone,String peer) {
        validDestination(phone,peer);
        try {
            Mac hmac=Mac.getInstance("HmacSHA256");
            hmac.init(bindingKey());
            byte[] input=("BC42-DRAFT-RECIPIENT-V1|"+phone+"|"+peer)
                .getBytes(StandardCharsets.US_ASCII);
            return hmac.doFinal(input);
        }catch(Exception e){
            throw new SecurityException("Kunne ikke bekrefte mottakerbindingen",e);
        }
    }

    private static String base64(byte[] data) {
        return Base64.encodeToString(data,
            Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }

    public synchronized String pending() {
        String wire=prefs.getString(WIRE,null);
        long created=prefs.getLong(CREATED,0L);
        long now=System.currentTimeMillis();
        if(wire==null)return null;
        String binding=prefs.getString(RECIPIENT_MAC,null);
        if(wire.isEmpty()||wire.length()>MAX_TEXT||created<=0||
           created>now||now-created>EXPIRES_MS ||
           binding==null || !binding.matches("[A-Za-z0-9_-]{43}")) {
            // Old drafts were not recipient-bound; fail closed on migration.
            clear();
            return null;
        }
        return wire;
    }

    public synchronized void remember(String wire,String phone,
                                      String verifiedPeerHash) {
        validDestination(phone,verifiedPeerHash);
        if(wire==null||wire.length()>MAX_TEXT||wire.isEmpty())
            throw new IllegalArgumentException("Ugyldig kryptert SMS-utkast");
        SmsFrameAssembler assembly=new SmsFrameAssembler();
        SmsFrameAssembler.Result result=assembly.accept(
            wire,System.currentTimeMillis());
        if(result.state!=SmsFrameAssembler.State.COMPLETE)
            throw new IllegalArgumentException("Ufullstendig kryptert utkast");
        String mac=base64(destinationMac(phone,verifiedPeerHash));
        String previous=pending();
        if(previous!=null) {
            if(previous.equals(wire)) {
                verifyRecipient(phone,verifiedPeerHash);
                return;
            }
            throw new IllegalStateException(
                "Det finnes allerede en uavklart kryptert SMS. "+
                "Fullfør den eller forkast utkastet før du lager en ny.");
        }
        if(!prefs.edit().putString(WIRE,wire)
            .putString(RECIPIENT_MAC,mac)
            .putInt(NEXT_PART,0)
            .putLong(CREATED,System.currentTimeMillis()).commit())
            throw new IllegalStateException("Kunne ikke lagre kryptert SMS-utkast");
    }

    /** Must be called on EVERY send/retry/confirmation, not just creation. */
    public synchronized void verifyRecipient(String phone,
                                             String verifiedPeerHash) {
        String wire=pending();
        if(wire==null)
            throw new IllegalStateException("Ingen kryptert SMS er lagret");
        String expected=prefs.getString(RECIPIENT_MAC,null);
        byte[] supplied=destinationMac(phone,verifiedPeerHash);
        byte[] recorded;
        try {
            recorded=Base64.decode(expected,
                Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
        }catch(IllegalArgumentException bad){
            throw new SecurityException("Ugyldig mottakerbinding",bad);
        }
        if(!MessageDigest.isEqual(supplied,recorded))
            throw new SecurityException(
                "Mottaker eller sikkerhetsidentitet er endret. "+
                "Denne krypterte SMS-en kan ikke åpnes for et annet nummer.");
    }

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
        return wire.split("\\n",-1)[currentPartIndex()];
    }

    /** User confirms only the current SMS part, never carrier delivery. */
    public synchronized boolean confirmCurrentPart(
        String phone,String verifiedPeerHash) {
        verifyRecipient(phone,verifiedPeerHash);
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
        if(!prefs.edit().remove(WIRE).remove(CREATED)
            .remove(NEXT_PART).remove(RECIPIENT_MAC).commit())
            throw new IllegalStateException("Kunne ikke slette SMS-utkastet");
    }
}
