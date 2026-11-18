package com.blckswan.blckchat;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECFieldFp;
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
    private static final String SEEN="received_ids"; // legacy rolling replay cache
    private static final String LOCAL_IDENTITY="bound_local_identity";
    private static final String MARKER="BC42K|1|";
    private static final byte[] CONTEXT="BLCKCHAT42-SMS-E2EE-ALPHA1"
        .getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RNG=new SecureRandom();
    private final Context app;
    private final SharedPreferences store;
    private final SmsReplayStore replay;
    private final ECPublicKey publicKey;

    public SmsE2ee(Context ctx) throws Exception {
        if(Build.VERSION.SDK_INT<31)
            throw new IllegalStateException("SMS-kryptering krever Android 12 eller nyere");
        app=ctx.getApplicationContext();
        store=app.getSharedPreferences(PREF,Context.MODE_PRIVATE);
        replay=new SmsReplayStore(app);
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
        // A restored preferences file must not silently trust a replaced
        // or regenerated Android Keystore identity.
        String current=encode(publicKey.getEncoded());
        String previouslyBound=store.getString(LOCAL_IDENTITY,null);
        if(!current.equals(previouslyBound)) {
            SharedPreferences.Editor edit=store.edit()
                .putString(LOCAL_IDENTITY,current)
                .remove(PEER).remove(VERIFIED).remove(SEEN);
            if(!edit.commit())
                throw new IllegalStateException("Kunne ikke låse identiteten");
        }
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
    private static volatile ECParameterSpec expectedCurve;
    private static ECParameterSpec expectedP256() throws Exception {
        ECParameterSpec cached=expectedCurve;
        if(cached!=null)return cached;
        synchronized(SmsE2ee.class) {
            cached=expectedCurve;
            if(cached==null) {
                KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
                generator.initialize(new ECGenParameterSpec("secp256r1"));
                cached=((ECPublicKey)generator.generateKeyPair()
                    .getPublic()).getParams();
                expectedCurve=cached;
            }
        }
        return cached;
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
        ECParameterSpec wanted=expectedP256();
        ECParameterSpec got=ec.getParams();
        if(!(got.getCurve().getField() instanceof ECFieldFp) ||
           !got.getCurve().getField().equals(wanted.getCurve().getField()) ||
           !got.getCurve().getA().equals(wanted.getCurve().getA()) ||
           !got.getCurve().getB().equals(wanted.getCurve().getB()) ||
           !got.getGenerator().equals(wanted.getGenerator()) ||
           !got.getOrder().equals(wanted.getOrder()) ||
           got.getCofactor()!=wanted.getCofactor())
            throw new SecurityException("Nøkkelen bruker feil kurve");
        // Independently reject infinity, out-of-field and off-curve points
        // before handing an untrusted peer key to Android Keystore.
        java.security.spec.ECPoint point=ec.getW();
        if(point==null || java.security.spec.ECPoint.POINT_INFINITY.equals(point))
            throw new SecurityException("Ugyldig EC-punkt");
        BigInteger p=((ECFieldFp)got.getCurve().getField()).getP();
        BigInteger x=point.getAffineX(),y=point.getAffineY();
        if(x==null||y==null||x.signum()<0||y.signum()<0||
           x.compareTo(p)>=0||y.compareTo(p)>=0)
            throw new SecurityException("EC-punkt utenfor feltet");
        BigInteger left=y.multiply(y).mod(p);
        BigInteger right=x.multiply(x).mod(p).multiply(x)
            .add(got.getCurve().getA().multiply(x))
            .add(got.getCurve().getB()).mod(p);
        if(!left.equals(right))
            throw new SecurityException("EC-punktet ligger ikke på kurven");
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
    /**
     * Tests the ACTUAL Android Keystore ECDH provider against an ephemeral
     * software EC key, then confirms HKDF and AES-GCM interoperate.
     * No peer or SMS is touched. A pass is not proof of group security.
     */
    public void runDeviceSelfTest() throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"),RNG);
        KeyPair ephemeral=generator.generateKeyPair();
        byte[] mine=publicKey.getEncoded();
        byte[] other=ephemeral.getPublic().getEncoded();
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        java.security.PrivateKey persistent=(java.security.PrivateKey)ks.getKey(ALIAS,null);
        if(persistent==null)throw new SecurityException("Android Keystore-nøkkel mangler");
        KeyAgreement left=KeyAgreement.getInstance("ECDH","AndroidKeyStore");
        left.init(persistent);
        left.doPhase(ephemeral.getPublic(),true);
        byte[] a=left.generateSecret();
        KeyAgreement right=KeyAgreement.getInstance("ECDH");
        right.init(ephemeral.getPrivate());
        right.doPhase(publicKey,true);
        byte[] b=right.generateSecret();
        byte[] fromA=null,fromB=null;
        try {
            if(!MessageDigest.isEqual(a,b))
                throw new SecurityException("ECDH-selvtest mislyktes");
            fromA=SmsCryptoPrimitives.directionKey(a,mine,other,mine);
            fromB=SmsCryptoPrimitives.directionKey(b,other,mine,mine);
            if(!MessageDigest.isEqual(fromA,fromB))
                throw new SecurityException("HKDF-selvtest mislyktes");
            String id="00000000000000000000000000000042";
            byte[] ad=SmsCryptoPrimitives.associatedData(mine,other,id);
            byte[] nonce=new byte[12];RNG.nextBytes(nonce);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(fromA,"AES"),
                        new GCMParameterSpec(128,nonce));
            cipher.updateAAD(ad);
            byte[] content="BLCKCHAT42 self-test"
                .getBytes(StandardCharsets.UTF_8);
            byte[] sealed=cipher.doFinal(content);
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(fromB,"AES"),
                        new GCMParameterSpec(128,nonce));
            cipher.updateAAD(SmsCryptoPrimitives.associatedData(other,mine,id));
            byte[] opened=cipher.doFinal(sealed);
            if(!MessageDigest.isEqual(content,opened))
                throw new SecurityException("AES-GCM-selvtest mislyktes");
            Arrays.fill(opened,(byte)0);
            Arrays.fill(content,(byte)0);
        } finally {
            Arrays.fill(a,(byte)0);
            Arrays.fill(b,(byte)0);
            if(fromA!=null)Arrays.fill(fromA,(byte)0);
            if(fromB!=null)Arrays.fill(fromB,(byte)0);
        }
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
    private String peerFingerprint() throws Exception {
        byte[] hash=sha256(peerBytes());
        StringBuilder out=new StringBuilder(hash.length*2);
        for(byte b:hash) {
            out.append(Character.forDigit((b&255)>>>4,16))
               .append(Character.forDigit(b&15,16));
        }
        Arrays.fill(hash,(byte)0);
        return out.toString();
    }
    private boolean isSeen(String id) throws Exception {
        // Keep the old cache read-only during migration to permanent SQLite.
        String old=store.getString(SEEN,"");
        if((","+old+",").contains(","+id+","))return true;
        return replay.contains(peerFingerprint(),id);
    }
    private void markSeen(String id) throws Exception {
        // Unique constraint closes concurrent replay races. Never rotate
        // away an old authenticated ID just because >256 messages arrived.
        if(!replay.record(peerFingerprint(),id))
            throw new SecurityException("Meldingen er allerede lest");
    }
}
