package com.blckswan.blckchat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Portable JCA protocol bindings for BLCKCHAT SMS Alpha 2.
 * Does not implement a cipher or a hash: delegates standard
 * HMAC-SHA256 and SHA-256 to the platform provider.
 *
 * Static P-256 ECDH is deliberately NOT a ratchet or forward-secret
 * messenger. These methods are factored out for independent JVM
 * cross-device test vectors.
 */
public final class SmsCryptoPrimitives {
    private SmsCryptoPrimitives() {}

    private static final byte[] CONTEXT =
        "BLCKCHAT42-SMS-E2EE-ALPHA1".getBytes(StandardCharsets.US_ASCII);

    private static byte[] combine(byte[]... arrays) {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        for(byte[] a:arrays) {
            if(a==null)throw new IllegalArgumentException("Manglende nøkkelmateriale");
            out.write(a,0,a.length);
        }
        return out.toByteArray();
    }

    private static int byteOrder(byte[] a,byte[] b) {
        int end=Math.min(a.length,b.length);
        for(int i=0;i<end;i++) {
            int difference=(a[i]&255)-(b[i]&255);
            if(difference!=0)return difference;
        }
        return a.length-b.length;
    }

    private static byte[] ordered(byte[] a,byte[] b) {
        if(a==null||b==null||a.length<80||b.length<80||
           a.length>120||b.length>120||Arrays.equals(a,b))
            throw new IllegalArgumentException("Ugyldig nøkkelpar");
        return byteOrder(a,b)<0?combine(a,b):combine(b,a);
    }

    public static String safetyCode(byte[] localPublic,byte[] peerPublic)
        throws GeneralSecurityException {
        byte[] hash=MessageDigest.getInstance("SHA-256")
            .digest(combine(CONTEXT,ordered(localPublic,peerPublic)));
        StringBuilder value=new StringBuilder("42");
        for(int i=0;i<10;i++) {
            if(i%2==0)value.append(' ');
            int b=hash[i]&255;
            value.append(Character.forDigit(b>>>4,16))
                 .append(Character.forDigit(b&15,16));
        }
        return value.toString().toUpperCase(Locale.ROOT);
    }

    public static byte[] associatedData(
        byte[] localPublic,byte[] peerPublic,String messageId) {
        if(messageId==null||!messageId.matches("[0-9a-f]{32}"))
            throw new IllegalArgumentException("Ugyldig meldings-ID");
        return combine(CONTEXT,ordered(localPublic,peerPublic),
                       messageId.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * RFC 5869 single-block HKDF-SHA256 (extract + expand).
     * Sender identity is included in HKDF info to separate the
     * two message directions and reject reflected ciphertext.
     *
     * sharedSecret MUST be from platform-authenticated P-256 ECDH.
     */
    public static byte[] directionKey(byte[] sharedSecret,
        byte[] localPublic,byte[] peerPublic,byte[] senderPublic)
        throws GeneralSecurityException {
        if(sharedSecret==null||sharedSecret.length!=32 ||
           senderPublic==null||
           (!Arrays.equals(senderPublic,localPublic) &&
            !Arrays.equals(senderPublic,peerPublic)))
            throw new IllegalArgumentException("Ugyldig ECDH-identitet");
        byte[] salt=MessageDigest.getInstance("SHA-256")
            .digest(combine(CONTEXT,ordered(localPublic,peerPublic)));
        Mac hmac=Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(salt,"HmacSHA256"));
        byte[] prk=hmac.doFinal(sharedSecret);
        try {
            hmac.init(new SecretKeySpec(prk,"HmacSHA256"));
            return hmac.doFinal(combine(CONTEXT,senderPublic,new byte[]{1}));
        }finally {
            Arrays.fill(prk,(byte)0);
            Arrays.fill(salt,(byte)0);
        }
    }
}
