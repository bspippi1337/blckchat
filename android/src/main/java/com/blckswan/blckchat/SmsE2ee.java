package com.blckswan.blckchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * BC42 SMS Alpha 2. Device-held P-256 ECDH identity and AES-256-GCM.
 * This is a SIMPLE PAIRED-DEVICE prototype, NOT a ratcheting protocol:
 * no forward secrecy after long-term key compromise, no group E2EE,
 * no automatic SMS reception, and no audit. Both users MUST compare
 * the same safety code by an independent, trusted call before exchange.
 *
 * Android 12+ only. Fail closed if AndroidKeyStore ECDH is unavailable.
 */
public final class SmsE2ee {
    private static final String ALIAS="blckchat42_sms_ecdh_p256_v1";
    private static final String PREF="blckchat42_sms_peers_v1";
    private static final String PEER="verified_peer_public";
    private static final String VERIFIED="safety_verified";
    private static final String SEEN="received_ids";
    private static final String MARKER="BC42K|1|";
    private static final byte[] CONTEXT="BLCKCHAT42-SMS-E2EE-ALPHA1"
        .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RNG=new SecureRandom();
    private final Context app;
    private final SharedPreferences store;
    private final ECPublicKey publicKey;

    public SmsE2ee(Context ctx) throws Exception {
        if(Build.VERSION.SDK_INT<31)
            throw new IllegalStateException("SMS-kryptering krever Android 12 eller nyere");
        app=ctx.getApplicationContext();
        store=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(!ks.containsAlias(ALIAS)) {
            KeyPairGenerator gen=KeyPairGenerator.getInstance("EC","AndroidKeyStore");
            KeyGenParameterSpec spec=new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(false)
                .build();
            gen.initialize(spec);
            gen.generateKeyPair();
        }
        java.security.cert.Certificate cert=ks.getCertificate(ALIAS);
        if(cert==null || !(cert.getPublicKey() instanceof ECPublicKey))
            throw new IllegalStateException("Android Keystore mangler EC-identitet");
        publicKey=(ECPublicKey)cert.getPublicKey();
    }

    public static String encode(byte[] b) {
        return Base64.encodeToString(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static byte[] decode(String b) {
        if(b==null || !b.matches("[A-Za-z0-9_-]{100,160}"))
            throw new IllegalArgumentException("Ugyldig offentlig nøkkel");
        return Base64.decode(b,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);
    }
    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }
    private static byte[] join(byte[]... arrays) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] a:arrays)out.write(a,0,a.length);
        return out.toByteArray();
    }
    private static int order(byte[] a,byte[] b) {
        for(int i=0;i<Math.min(a.length,b.length);i++){
            int d=(a[i]&255)-(b[i]&255);
            if(d!=0)return d;
        }
        return a.length-b.length;
    }
    private static byte[] orderedKeys(byte[] a,byte[] b) {
        return order(a,b)<0?join(a,b):join(b,a);
    }
    private static PublicKey parsePublic(byte[] encoded) throws Exception {
        if(encoded.length<80 || encoded.length>120)
            throw new SecurityException("Uventet nøkkellengde");
        PublicKey key=KeyFactory.getInstance("EC")
            .generatePublic(new X509EncodedKeySpec(encoded));
        if(!(key instanceof ECPublicKey))
            throw new SecurityException("Feil nøkkeltype");
        ECPublicKey ec=(ECPublicKey)key;
        // Only our supported named NIST P-256 curve, not arbitrary EC params.
        KeyPairGenerator check=KeyPairGenerator.getInstance("EC");
        check.initialize(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec wanted=((ECPublicKey)check.generateKeyPair().getPublic()).getParams();
        ECParameterSpec got=ec.getParams();
        if(got.getCurve().getField().getFieldSize()!=256 ||
           !got.getCurve().getA().equals(wanted.getCurve().getA()) ||
           !got.getCurve().getB().equals(wanted.getCurve().getB()) ||
           !got.getGenerator().equals(wanted.getGenerator()) ||
           !got.getOrder().equals(wanted.getOrder()) ||
           got.getCofactor()!=wanted.getCofactor())
           throw new SecurityException("Nøkkelen bruker feil kurve");
        return key;
    }
    private byte[] peerBytes() throws Exception {
        String value=store.getString(PEER,null);
        if(value==null)throw new IllegalStateException("Koble til mottaker først");
        byte[] bytes=decode(value);
        parsePublic(bytes);
        return bytes;
    }
    public String myPairingCode() {
        return MARKER+encode(publicKey.getEncoded());
    }
    public void importPairingCode(String raw) throws Exception {
        if(raw==null)throw new IllegalArgumentException("Tom nøkkelkode");
        String text=raw.trim();
        // Only an exact pairing record. No links, hidden HTML or free text.
        if(!text.startsWith(MARKER) || text.indexOf('\n')>=0 ||
           text.indexOf('\r')>=0)
            throw new IllegalArgumentException("Lim inn hele BC42K-koden");
        byte[] candidate=decode(text.substring(MARKER.length()));
        parsePublic(candidate);
        if(Arrays.equals(candidate,publicKey.getEncoded()))
            throw new SecurityException("Du kan ikke pare enheten med seg selv");
        String existing=store.getString(PEER,null);
        if(existing!=null && !MessageDigest.isEqual(candidate,decode(existing)))
            throw new SecurityException("En annen mottaker er allerede paret. Nullstill paringen manuelt.");
        if(existing==null) {
            if(!store.edit().putString(PEER,encode(candidate))
                 .putBoolean(VERIFIED,false).remove(SEEN).commit())
                throw new IllegalStateException("Kunne ikke lagre parkoblingen");
        }
    }
    public boolean hasPeer() {
        return store.contains(PEER);
    }
    public boolean verified() {
        return hasPeer() && store.getBoolean(VERIFIED,false);
    }
    public String safetyCode() throws Exception {
        return SmsCryptoPrimitives.safetyCode(
            publicKey.getEncoded(),peerBytes());
    }
    public void setVerified(boolean checked) throws Exception {
        if(!hasPeer())throw new IllegalStateException("Ingen mottaker");
        if(!store.edit().putBoolean(VERIFIED,checked).commit())
            throw new IllegalStateException("Kunne ikke lagre bekreftelsen");
    }
    public void forgetPeer() throws Exception {
        if(!store.edit().remove(PEER).remove(VERIFIED).remove(SEEN).commit())
            throw new IllegalStateException("Kunne ikke nullstille kontakten");
    }
    private byte[] deriveKey(byte[] senderPublic) throws Exception {
        if(!verified())throw new SecurityException("Sammenlign sikkerhetskoden først");
        byte[] peer=peerBytes();
        java.security.KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.PrivateKey privateKey=(java.security.PrivateKey)ks.getKey(ALIAS,null);
        if(privateKey==null)throw new SecurityException("Lokal nøkkel mangler");
        KeyAgreement agreement=KeyAgreement.getInstance("ECDH","AndroidKeyStore");
        agreement.init(privateKey);
        agreement.doPhase(parsePublic(peer),true);
        byte[] shared=agreement.generateSecret();
        try {
            return SmsCryptoPrimitives.directionKey(shared,
                publicKey.getEncoded(),peer,senderPublic);
        }finally{
            Arrays.fill(shared,(byte)0);
        }
    }
    private byte[] aad(String id) throws Exception {
        return SmsCryptoPrimitives.associatedData(
            publicKey.getEncoded(),peerBytes(),id);
    }
    private static String randomId() {
        byte[] b=new byte[16];
        RNG.nextBytes(b);
        StringBuilder s=new StringBuilder(32);
        for(byte v:b){
            s.append(Character.forDigit((v&255)>>>4,16));
            s.append(Character.forDigit(v&15,16));
        }
        Arrays.fill(b,(byte)0);
        return s.toString();
    }
    public String encrypt(String message) throws Exception {
        if(!verified())throw new SecurityException("Mottakerens identitet er ikke verifisert");
        byte[] plain=message.getBytes(StandardCharsets.UTF_8);
        if(plain.length==0 || plain.length>650 ||
           message.indexOf('\u0000')>=0)
            throw new IllegalArgumentException("Bruk mellom 1 og 650 UTF-8-byte");
        String id=randomId();
        byte[] key=deriveKey(publicKey.getEncoded()),nonce=new byte[12];
        try {
            RNG.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),
                new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(id));
            byte[] sealed=join(nonce,cipher.doFinal(plain));
            String sms=NativeSmsCodec.pack(sealed,id);
            Arrays.fill(sealed,(byte)0);
            if(sms==null)throw new IllegalStateException("Klarte ikke pakke SMS");
            return sms;
        } finally {
            Arrays.fill(key,(byte)0);
            Arrays.fill(plain,(byte)0);
        }
    }
    public String decrypt(String frameText) throws Exception {
        if(!verified())throw new SecurityException("Bekreft sikkerhetskoden først");
        if(frameText==null || frameText.length()>3500)
            throw new IllegalArgumentException("Ugyldig SMS-lengde");
        String normalized=frameText.trim();
        String first=normalized.split("\\n",2)[0].trim();
        if(!first.matches("BC42\\|1\\|[0-9a-f]{32}\\|[0-9]{2}/[0-9]{2}\\|[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Dette er ikke en BLCKCHAT-kryptert SMS");
        String id=first.substring(7,39);
        if(isSeen(id))throw new SecurityException("Meldingen er allerede lest");
        byte[] ciphertext=NativeSmsCodec.unpack(normalized);
        if(ciphertext==null || ciphertext.length<28)
            throw new SecurityException("Meldingen er ufullstendig eller skadet");
        byte[] nonce=Arrays.copyOfRange(ciphertext,0,12);
        byte[] key=deriveKey(peerBytes());
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),
                new GCMParameterSpec(128,nonce));
            cipher.updateAAD(aad(id));
            byte[] plain=cipher.doFinal(ciphertext,12,ciphertext.length-12);
            String message=new String(plain,StandardCharsets.UTF_8);
            if(!Arrays.equals(message.getBytes(StandardCharsets.UTF_8),plain))
                throw new SecurityException("Ugyldig UTF-8");
            Arrays.fill(plain,(byte)0);
            markSeen(id);
            return message;
        } finally {
            Arrays.fill(key,(byte)0);
            Arrays.fill(ciphertext,(byte)0);
        }
    }
    private boolean isSeen(String id) {
        String history=store.getString(SEEN,"");
        return (","+history+",").contains(","+id+",");
    }
    private void markSeen(String id) {
        List<String> history=new ArrayList<>();
        String existing=store.getString(SEEN,"");
        if(!existing.isEmpty())Collections.addAll(history,existing.split(","));
        history.add(id);
        if(history.size()>256)
            history=new ArrayList<>(history.subList(history.size()-256,history.size()));
        if(!store.edit().putString(SEEN,android.text.TextUtils.join(",",history)).commit())
            throw new IllegalStateException("Kunne ikke lagre replay-beskyttelse");
    }
}
