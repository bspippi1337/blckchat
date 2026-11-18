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
