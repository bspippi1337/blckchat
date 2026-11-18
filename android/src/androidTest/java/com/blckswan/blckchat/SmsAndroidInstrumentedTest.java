package com.blckswan.blckchat;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Emulator/device smoke coverage. No SMS is sent or any phone contacted. */
@RunWith(AndroidJUnit4.class)
public final class SmsAndroidInstrumentedTest {
    @Test public void approvedBrandArtworkDecodesOnAndroid() {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.graphics.Bitmap mark=android.graphics.BitmapFactory.decodeResource(
            app.getResources(),R.drawable.blckchat_wordmark);
        assertNotNull("Approved bundled WebP must decode on device",mark);
        try {
            assertEquals("Official wordmark width",1000,mark.getWidth());
            assertEquals("Official wordmark height",318,mark.getHeight());
        }finally {
            mark.recycle();
        }
        assertNotNull("C/fedora launcher mark",
            app.getDrawable(R.drawable.blck_logo));
    }

    @Test public void realAndroidKeystoreEcdhWorks() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsE2ee e2ee=new SmsE2ee(context);
        e2ee.runDeviceSelfTest();
        assertTrue(e2ee.myPairingCode().startsWith("BC42K|1|"));
    }

    @Test public void nativeC11SmsCodecRoundTripAndTamperReject() {
        byte[] sealed=new byte[613];
        for(int i=0;i<sealed.length;i++)sealed[i]=(byte)(i*17);
        String id="abcdef0123456789abcdef0123456789";
        String packed=NativeSmsCodec.pack(sealed,id);
        assertNotNull(packed);
        assertTrue(packed.contains("\n"));
        byte[] restored=NativeSmsCodec.unpack(packed);
        assertArrayEquals(sealed,restored);
        assertNull(NativeSmsCodec.unpack(packed.replaceFirst("BC42", "BC99")));
    }

    @Test public void multipartCiphertextSurvivesRestart() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsFrameInbox inbox=new SmsFrameInbox(context);
        inbox.clear();
        byte[] sealed=new byte[203];
        Arrays.fill(sealed,(byte)42);
        String messageId="1234567890abcdef1234567890abcdef";
        String[] lines=NativeSmsCodec.pack(sealed,messageId).split("\\n");
        assertTrue(lines.length>1);
        SmsFrameAssembler.Result result=inbox.accept(lines[lines.length-1]);
        assertEquals(SmsFrameAssembler.State.PENDING,result.state);
        SmsFrameInbox reopened=new SmsFrameInbox(context);
        for(int i=lines.length-2;i>=0;i--) {
            result=reopened.accept(lines[i]);
        }
        assertEquals(SmsFrameAssembler.State.COMPLETE,result.state);
        assertArrayEquals(sealed,NativeSmsCodec.unpack(result.completeWire));
        reopened.acknowledged(result.id);
        assertEquals(0,new SmsFrameInbox(context).pendingCount());
    }

    @Test public void replayRegistryPersistsAndRejectsDuplicates() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsReplayStore first=new SmsReplayStore(context);
        String peer="a".repeat(64);
        String id="0123456789abcdef0123456789abcdef";
        // A fresh emulator run has a new DB; duplicate returns false.
        first.record(peer,id);
        assertTrue(first.contains(peer,id));
        assertFalse(first.record(peer,id));
        first.close();
        SmsReplayStore reopened=new SmsReplayStore(context);
        assertTrue(reopened.contains(peer,id));
        reopened.close();
    }

    @Test public void invalidPeerCurvesAndTamperedPointsAreRejected() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsE2ee sms=new SmsE2ee(context);
        java.security.KeyPairGenerator gen=java.security.KeyPairGenerator.getInstance("EC");

        gen.initialize(new java.security.spec.ECGenParameterSpec("secp384r1"));
        byte[] wrongCurve=gen.generateKeyPair().getPublic().getEncoded();
        boolean rejected=false;
        try {
            sms.importPairingCode("BC42K|1|"+SmsE2ee.encode(wrongCurve));
        }catch(Exception expected) {rejected=true;}
        assertTrue("Wrong elliptic curve must be rejected",rejected);

        gen.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        byte[] altered=gen.generateKeyPair().getPublic().getEncoded();
        altered[altered.length-1]^=1;
        rejected=false;
        try {
            sms.importPairingCode("BC42K|1|"+SmsE2ee.encode(altered));
        }catch(Exception expected) {rejected=true;}
        assertTrue("Tampered P-256 point must be rejected",rejected);
    }

    @Test public void encryptedSmsDraftBindsDestinationAndSurvivesRestart() {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsDraftStore store=new SmsDraftStore(context);
        store.clear();
        String phoneA="+4799999901",phoneB="+4799999902";
        String peerA="a".repeat(64),peerB="b".repeat(64);
        String id="00112233445566778899aabbccddeeff";
        String secondId="ffeeddccbbaa99887766554433221100";
        byte[] sealed=new byte[240];
        Arrays.fill(sealed,(byte)0x5a);
        String first=NativeSmsCodec.pack(sealed,id);
        String other=NativeSmsCodec.pack(sealed,secondId);
        assertNotNull(first);
        assertNotNull(other);
        int expected=first.split("\\n").length;
        assertTrue("Must exercise multiple SMS segments",expected>1);
        store.remember(first,phoneA,peerA);
        assertEquals(first,new SmsDraftStore(context).pending());
        store.remember(first,phoneA,peerA);
        assertEquals(0,store.currentPartIndex());
        assertEquals(expected,store.partCount());
        String[] parts=first.split("\\n");
        assertEquals(parts[0],store.currentPart());
        boolean rejected=false;
        try{store.remember(other,phoneA,peerA);}
        catch(IllegalStateException exception){rejected=true;}
        assertTrue("Cannot overwrite unconfirmed ciphertext",rejected);
        rejected=false;
        try{store.verifyRecipient(phoneB,peerA);}
        catch(SecurityException exception){rejected=true;}
        assertTrue("Reject phone number replacement",rejected);
        rejected=false;
        try{store.verifyRecipient(phoneA,peerB);}
        catch(SecurityException exception){rejected=true;}
        assertTrue("Reject verified contact key replacement",rejected);
        rejected=false;
        try{store.confirmCurrentPart(phoneB,peerA);}
        catch(SecurityException exception){rejected=true;}
        assertTrue("Cannot confirm for wrong phone",rejected);
        assertEquals(0,store.currentPartIndex());
        for(int i=0;i<expected;i++) {
            SmsDraftStore reopened=new SmsDraftStore(context);
            reopened.verifyRecipient(phoneA,peerA);
            assertEquals(i,reopened.currentPartIndex());
            assertEquals(parts[i],reopened.currentPart());
            boolean finished=reopened.confirmCurrentPart(phoneA,peerA);
            if(i+1<expected) {
                assertFalse("Cannot finish before final SMS frame",finished);
                assertEquals(i+1,new SmsDraftStore(context).currentPartIndex());
            }else {
                assertTrue("Final confirmation clears draft",finished);
            }
        }
        assertNull(new SmsDraftStore(context).pending());
        assertEquals(0,new SmsDraftStore(context).partCount());
    }

    @Test public void encryptedLocalHistoryKeepsNoPlaintextAndRejectsTampering()
        throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsLocalHistory journal=new SmsLocalHistory(context);
        journal.clear();
        String peer="a".repeat(64);
        String otherPeer="b".repeat(64);
        String id="aabbccddeeff00112233445566778899";
        String secret="Test SMS 42: confidential journal entry";
        journal.add("MOTTATT",peer,id,secret);
        SmsLocalHistory restored=new SmsLocalHistory(context);
        java.util.List<SmsLocalHistory.Entry> entries=restored.latest(peer,12);
        assertEquals(1,entries.size());
        assertEquals(secret,entries.get(0).text);
        assertEquals(id,entries.get(0).messageId);
        assertEquals("MOTTATT",entries.get(0).direction);
        assertTrue(restored.latest(otherPeer,12).isEmpty());

        android.database.sqlite.SQLiteDatabase db=restored.getWritableDatabase();
        String rid;
        byte[] sealed;
        try(android.database.Cursor cursor=db.rawQuery(
            "SELECT rid,nonce,sealed FROM journal LIMIT 1",null)) {
            assertTrue(cursor.moveToFirst());
            rid=cursor.getString(0);
            assertEquals(12,cursor.getBlob(1).length);
            sealed=cursor.getBlob(2);
        }
        // The disk representation contains only AES-GCM ciphertext.
        assertFalse(new String(sealed,java.nio.charset.StandardCharsets.ISO_8859_1)
            .contains(secret));
        sealed[sealed.length-1]^=1;
        android.content.ContentValues values=new android.content.ContentValues();
        values.put("sealed",sealed);
        assertEquals(1,db.update("journal",values,"rid=?",new String[]{rid}));
        boolean rejected=false;
        try{restored.latest(peer,12);}
        catch(javax.crypto.AEADBadTagException expected){rejected=true;}
        assertTrue("Modified AES-GCM journal data must fail closed",rejected);
        restored.clear();
        journal.close();
        restored.close();
    }

    @Test public void fullAndroidPhoneAAndSimulatedPhoneBExchange()
        throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SmsE2ee phoneA=new SmsE2ee(context);
        phoneA.forgetPeer();
        try {
            // Phone B runs in the test only, with a distinct ephemeral key.
            java.security.KeyPairGenerator keys=
                java.security.KeyPairGenerator.getInstance("EC");
            keys.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            java.security.KeyPair phoneB=keys.generateKeyPair();
            byte[] publicB=phoneB.getPublic().getEncoded();
            String pairing=phoneA.myPairingCode();
            assertTrue(pairing.startsWith("BC42K|1|"));
            byte[] publicA=android.util.Base64.decode(
                pairing.substring("BC42K|1|".length()),
                android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|
                android.util.Base64.NO_PADDING);
            java.security.PublicKey keyA=java.security.KeyFactory
                .getInstance("EC").generatePublic(
                    new java.security.spec.X509EncodedKeySpec(publicA));
            phoneA.importPairingCode("BC42K|1|"+SmsE2ee.encode(publicB));
            assertEquals(
                SmsCryptoPrimitives.safetyCode(publicB,publicA),
                phoneA.safetyCode());
            phoneA.setVerified(true);

            javax.crypto.KeyAgreement agreement=
                javax.crypto.KeyAgreement.getInstance("ECDH");
            agreement.init(phoneB.getPrivate());
            agreement.doPhase(keyA,true);
            byte[] secret=agreement.generateSecret();

            // A -> B through the actual app encryption and C11 SMS frames.
            String outbound=phoneA.encrypt("Phone A -> Phone B over SMS");
            String outgoingId=outbound.substring(7,39);
            byte[] encrypted=NativeSmsCodec.unpack(outbound);
            assertNotNull(encrypted);
            byte[] fromA=SmsCryptoPrimitives.directionKey(secret,
                publicB,publicA,publicA);
            javax.crypto.Cipher open=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            open.init(javax.crypto.Cipher.DECRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(fromA,"AES"),
                new javax.crypto.spec.GCMParameterSpec(128,
                    Arrays.copyOfRange(encrypted,0,12)));
            open.updateAAD(SmsCryptoPrimitives.associatedData(
                publicB,publicA,outgoingId));
            String bReads=new String(
                open.doFinal(encrypted,12,encrypted.length-12),
                java.nio.charset.StandardCharsets.UTF_8);
            assertEquals("Phone A -> Phone B over SMS",bReads);

            // B -> A encrypted using independent JCA implementation,
            // then reassembled by C11 and decrypted by the real Android app.
            byte[] fromB=SmsCryptoPrimitives.directionKey(secret,
                publicB,publicA,publicB);
            String id=java.util.UUID.randomUUID().toString()
                .replace("-","").toLowerCase(java.util.Locale.ROOT);
            byte[] nonce=new byte[12];
            new java.security.SecureRandom().nextBytes(nonce);
            javax.crypto.Cipher seal=javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            seal.init(javax.crypto.Cipher.ENCRYPT_MODE,
                new javax.crypto.spec.SecretKeySpec(fromB,"AES"),
                new javax.crypto.spec.GCMParameterSpec(128,nonce));
            seal.updateAAD(SmsCryptoPrimitives.associatedData(
                publicB,publicA,id));
            byte[] text="Phone B -> Phone A: verified ciphertext"
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] body=seal.doFinal(text);
            byte[] sealed=new byte[nonce.length+body.length];
            System.arraycopy(nonce,0,sealed,0,nonce.length);
            System.arraycopy(body,0,sealed,nonce.length,body.length);
            String wire=NativeSmsCodec.pack(sealed,id);
            assertNotNull(wire);
            assertEquals("Phone B -> Phone A: verified ciphertext",
                phoneA.decrypt(wire));
            boolean replayDenied=false;
            try{phoneA.decrypt(wire);}
            catch(SecurityException expected){replayDenied=true;}
            assertTrue("A correctly authenticated SMS cannot be replayed",replayDenied);
            Arrays.fill(secret,(byte)0);
            Arrays.fill(fromA,(byte)0);
            Arrays.fill(fromB,(byte)0);
        }finally {
            phoneA.forgetPeer();
        }
    }

    @Test public void appDoesNotRequestSmsOrInternetPermissions() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        PackageInfo info=context.getPackageManager().getPackageInfo(
            context.getPackageName(),PackageManager.GET_PERMISSIONS);
        String[] permissions=info.requestedPermissions;
        if(permissions==null)return;
        for(String permission:permissions) {
            assertFalse(permission,permission.equals("android.permission.INTERNET") ||
                permission.equals("android.permission.SEND_SMS") ||
                permission.equals("android.permission.READ_SMS") ||
                permission.equals("android.permission.RECEIVE_SMS"));
        }
    }
}
