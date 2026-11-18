package com.blckswan.blckchat;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Local, opt-out, ciphertext-only message journal. No message plaintext,
 * phone number, identity key or cryptographic session key is written to
 * SQLite. A separate non-exportable Android Keystore AES-GCM key encrypts
 * every entry, with domain separation and a random record identifier in AAD.
 *
 * Note: this only provides AT-REST protection while the app sandbox and
 * Keystore are intact. It cannot protect text shown on an unlocked screen.
 */
public final class SmsLocalHistory extends SQLiteOpenHelper {
    private static final String DATABASE="blckchat42_local_history_v1.db";
    private static final String KEY="blckchat42_local_history_aesgcm_v1";
    private static final byte[] DOMAIN="BC42-LOCAL-JOURNAL-V1"
        .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RNG=new SecureRandom();
    private static final int VERSION=1;
    private static final int MAX_RECORDS=300;
    private static final int MAX_PLAINTEXT_BYTES=2048;

    public static final class Entry {
        public final long timestamp;
        public final String direction;
        public final String messageId;
        public final String text;
        private Entry(long timestamp,String direction,String messageId,String text) {
            this.timestamp=timestamp;
            this.direction=direction;
            this.messageId=messageId;
            this.text=text;
        }
    }

    public SmsLocalHistory(Context app) {
        super(app.getApplicationContext(),DATABASE,null,VERSION);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE journal (rid TEXT PRIMARY KEY NOT NULL, " +
            "nonce BLOB NOT NULL, sealed BLOB NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int next) {
        throw new IllegalStateException("Historikken skal ikke automatisk dekrypteres");
    }

    private static SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        SecretKey existing=(SecretKey)ks.getKey(KEY,null);
        if(existing!=null)return existing;
        synchronized(SmsLocalHistory.class) {
            ks.load(null);
            existing=(SecretKey)ks.getKey(KEY,null);
            if(existing!=null)return existing;
            KeyGenerator gen=KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            gen.init(new KeyGenParameterSpec.Builder(KEY,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
            return gen.generateKey();
        }
    }

    private static boolean hex(String input,int digits) {
        return input!=null && input.matches("[0-9a-f]{"+digits+"}");
    }

    private static String randomId() {
        byte[] random=new byte[16];
        RNG.nextBytes(random);
        StringBuilder id=new StringBuilder(32);
        for(byte b:random) {
            id.append(Character.forDigit((b&255)>>>4,16))
              .append(Character.forDigit(b&15,16));
        }
        Arrays.fill(random,(byte)0);
        return id.toString();
    }

    private static void aad(Cipher cipher,String recordId) {
        cipher.updateAAD(DOMAIN);
        cipher.updateAAD(recordId.getBytes(StandardCharsets.US_ASCII));
    }

    private static byte[] serialize(String direction,String peer,String messageId,
                                    String text) throws Exception {
        byte[] bytes=text.getBytes(StandardCharsets.UTF_8);
        if(bytes.length<1||bytes.length>MAX_PLAINTEXT_BYTES)
            throw new IllegalArgumentException("Meldingen er for lang");
        ByteArrayOutputStream b=new ByteArrayOutputStream(bytes.length+128);
        DataOutputStream out=new DataOutputStream(b);
        out.writeByte(1);
        out.writeLong(System.currentTimeMillis());
        out.writeUTF(direction);
        out.writeUTF(peer);
        out.writeUTF(messageId);
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
        Arrays.fill(bytes,(byte)0);
        return b.toByteArray();
    }

    private static Entry parse(byte[] record,String selectedPeer) throws Exception {
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(record));
        if(in.readUnsignedByte()!=1)
            throw new SecurityException("Ukjent kryptert historikkformat");
        long timestamp=in.readLong();
        String direction=in.readUTF();
        String peer=in.readUTF();
        String messageId=in.readUTF();
        int length=in.readInt();
        if(timestamp<0||!hex(peer,64)||!hex(messageId,32)||
           !(direction.equals("MOTTATT")||direction.equals("UTKAST"))||
           length<1||length>MAX_PLAINTEXT_BYTES||in.available()!=length)
            throw new SecurityException("Ugyldig historikkpost");
        byte[] plain=new byte[length];
        in.readFully(plain);
        String text=new String(plain,StandardCharsets.UTF_8);
        if(!Arrays.equals(text.getBytes(StandardCharsets.UTF_8),plain))
            throw new SecurityException("Ugyldig UTF-8 i historikk");
        Arrays.fill(plain,(byte)0);
        if(!peer.equals(selectedPeer))return null;
        return new Entry(timestamp,direction,messageId,text);
    }

    public synchronized void add(String direction,String peerHash,
                                 String messageId,String text) throws Exception {
        if(!hex(peerHash,64)||!hex(messageId,32)||
           !(direction.equals("MOTTATT")||direction.equals("UTKAST")))
            throw new IllegalArgumentException("Ugyldig historikkidentitet");
        byte[] payload=serialize(direction,peerHash,messageId,text);
        String rid=randomId();
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,key());
        aad(cipher,rid);
        byte[] nonce=cipher.getIV();
        if(nonce==null||nonce.length!=12)
            throw new SecurityException("Ugyldig Keystore AES-GCM-nonce");
        byte[] sealed;
        try {
            sealed=cipher.doFinal(payload);
        }finally {
            Arrays.fill(payload,(byte)0);
        }
        SQLiteDatabase db=getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues values=new ContentValues();
            values.put("rid",rid);
            values.put("nonce",nonce);
            values.put("sealed",sealed);
            db.insertOrThrow("journal",null,values);
            db.execSQL("DELETE FROM journal WHERE rowid NOT IN " +
                "(SELECT rowid FROM journal ORDER BY rowid DESC LIMIT "+MAX_RECORDS+")");
            db.setTransactionSuccessful();
        }finally {
            db.endTransaction();
            Arrays.fill(sealed,(byte)0);
        }
    }

    public synchronized List<Entry> latest(String verifiedPeerHash,int max)
        throws Exception {
        if(!hex(verifiedPeerHash,64)||max<1||max>50)
            throw new IllegalArgumentException("Ugyldig historikkforespørsel");
        List<Entry> result=new ArrayList<>();
        SQLiteDatabase db=getReadableDatabase();
        try(Cursor cursor=db.rawQuery(
            "SELECT rid,nonce,sealed FROM journal ORDER BY rowid DESC LIMIT 300",null)) {
            while(cursor.moveToNext() && result.size()<max) {
                String rid=cursor.getString(0);
                byte[] nonce=cursor.getBlob(1),sealed=cursor.getBlob(2);
                if(!hex(rid,32)||nonce==null||nonce.length!=12||
                   sealed==null||sealed.length<16||sealed.length>4096)
                    throw new SecurityException("Kryptert journal er endret");
                Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,nonce));
                aad(cipher,rid);
                byte[] plain=cipher.doFinal(sealed);
                try {
                    Entry item=parse(plain,verifiedPeerHash);
                    if(item!=null)result.add(item);
                }finally {
                    Arrays.fill(plain,(byte)0);
                }
            }
        }
        return result;
    }

    public synchronized void clear() {
        getWritableDatabase().delete("journal",null,null);
    }
}
