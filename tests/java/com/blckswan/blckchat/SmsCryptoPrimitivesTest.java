package com.blckswan.blckchat;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class SmsCryptoPrimitivesTest {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
    }
    private static byte[] shared(KeyPair mine,KeyPair theirs)
        throws Exception {
        KeyAgreement agreement=KeyAgreement.getInstance("ECDH");
        agreement.init(mine.getPrivate());
        agreement.doPhase(theirs.getPublic(),true);
        return agreement.generateSecret();
    }
    private static byte[] seal(byte[] key,byte[] aad,byte[] plaintext)
        throws Exception {
        byte[] nonce=new byte[12];
        RANDOM.nextBytes(nonce);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),
                    new GCMParameterSpec(128,nonce));
        cipher.updateAAD(aad);
        byte[] enc=cipher.doFinal(plaintext);
        byte[] output=Arrays.copyOf(nonce,nonce.length+enc.length);
        System.arraycopy(enc,0,output,nonce.length,enc.length);
        return output;
    }
    private static byte[] open(byte[] key,byte[] aad,byte[] packet)
        throws Exception {
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),
                    new GCMParameterSpec(128,Arrays.copyOf(packet,12)));
        cipher.updateAAD(aad);
        return cipher.doFinal(packet,12,packet.length-12);
    }
    private static void failsDecrypt(byte[] key,byte[] aad,byte[] sealed,
                                     String label) throws Exception {
        boolean failed=false;
        try{open(key,aad,sealed);}
        catch(AEADBadTagException ex){failed=true;}
        check(failed,label);
    }
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"),RANDOM);
        KeyPair phoneA=generator.generateKeyPair();
        KeyPair phoneB=generator.generateKeyPair();
        KeyPair intruder=generator.generateKeyPair();
        byte[] ap=phoneA.getPublic().getEncoded();
        byte[] bp=phoneB.getPublic().getEncoded();
        byte[] ip=intruder.getPublic().getEncoded();
        byte[] sa=shared(phoneA,phoneB),sb=shared(phoneB,phoneA);
        check(Arrays.equals(sa,sb),"ECDH across two devices");
        check(SmsCryptoPrimitives.safetyCode(ap,bp)
            .equals(SmsCryptoPrimitives.safetyCode(bp,ap)),
            "identical safety code in both directions");
        check(!SmsCryptoPrimitives.safetyCode(ap,bp)
            .equals(SmsCryptoPrimitives.safetyCode(ap,ip)),
            "changing a peer forces another verification code");
        byte[] aToB=SmsCryptoPrimitives.directionKey(sa,ap,bp,ap);
        byte[] bReceivesA=SmsCryptoPrimitives.directionKey(sb,bp,ap,ap);
        byte[] bToA=SmsCryptoPrimitives.directionKey(sb,bp,ap,bp);
        check(aToB.length==32&&Arrays.equals(aToB,bReceivesA),
            "same directional key for sender and recipient");
        check(!Arrays.equals(aToB,bToA),"different keys for opposite senders");

        String id="0123456789abcdef0123456789abcdef";
        byte[] senderAad=SmsCryptoPrimitives.associatedData(ap,bp,id);
        byte[] receiverAad=SmsCryptoPrimitives.associatedData(bp,ap,id);
        check(Arrays.equals(senderAad,receiverAad),"same authenticated transcript");
        byte[] plaintext="Kryptert melding over avstand"
            .getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext=seal(aToB,senderAad,plaintext);
        check(Arrays.equals(open(bReceivesA,receiverAad,ciphertext),plaintext),
            "Phone B decrypts Phone A's text");
        failsDecrypt(bToA,receiverAad,ciphertext,"reflected message rejected");
        byte[] changed=Arrays.copyOf(ciphertext,ciphertext.length);
        changed[changed.length-1]^=1;
        failsDecrypt(bReceivesA,receiverAad,changed,"tampered ciphertext rejected");
        failsDecrypt(bReceivesA,
            SmsCryptoPrimitives.associatedData(bp,ap,
                "fedcba9876543210fedcba9876543210"),
            ciphertext,"altered message identifier rejected");
        boolean invalid=false;
        try{SmsCryptoPrimitives.associatedData(ap,bp,"bad");}
        catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"bad message ID rejected");
        invalid=false;
        try{SmsCryptoPrimitives.directionKey(sa,ap,bp,ip);}
        catch(IllegalArgumentException expected){invalid=true;}
        check(invalid,"external sender not permitted");
        Arrays.fill(sa,(byte)0);Arrays.fill(sb,(byte)0);
        Arrays.fill(aToB,(byte)0);Arrays.fill(bToA,(byte)0);
        Arrays.fill(bReceivesA,(byte)0);
        System.out.println("PASS E2EE primitive vectors: P-256 ECDH, HKDF, safety code, AES-GCM and tamper/reflection");
    }
}
