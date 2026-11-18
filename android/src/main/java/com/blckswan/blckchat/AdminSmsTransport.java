package com.blckswan.blckchat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.telephony.SmsManager;

/**
 * Thin Android-only adapter. All policy, invite formatting and queue state live
 * in C11 / authenticated gateway. This class never generates OTPs or stores
 * GitHub credentials. It should only be called by an explicitly enrolled admin.
 *
 * The safe compose mode works without SEND_SMS and requires human confirmation.
 * Automatic mode is only for separately consented admin sideload builds.
 */
public final class AdminSmsTransport {
    private AdminSmsTransport() {}

    public static boolean canSendSms(Context ctx) {
        return ctx.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_TELEPHONY_MESSAGING);
    }

    public static void composeSms(Context ctx, String recipient, String text) {
        if (!canSendSms(ctx)) {
            throw new IllegalStateException("Device cannot send SMS");
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("smsto:" + Uri.encode(recipient)));
        intent.putExtra("sms_body", text);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /**
     * Enqueues transmission; NOT proof of carrier delivery. The caller must
     * implement sent/delivery PendingIntents, audit and idempotent job ACK.
     * Do not activate without explicit admin opt-in and runtime SEND_SMS grant.
     */
    public static void sendAutomatically(
            Context ctx, String recipient, String text,
            int subscriptionId, boolean adminEnabled) {
        if (!adminEnabled) {
            throw new SecurityException("Admin SMS gateway not enabled");
        }
        if (!canSendSms(ctx)) {
            throw new IllegalStateException("Device cannot send SMS");
        }
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("SEND_SMS not granted");
        }
        final SmsManager manager;
        if (Build.VERSION.SDK_INT >= 31) {
            SmsManager base = ctx.getSystemService(SmsManager.class);
            if (base == null) throw new IllegalStateException("SMS service missing");
            manager = base.createForSubscriptionId(subscriptionId);
        } else {
            manager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId);
        }
        manager.sendTextMessage(recipient, null, text, null, null);
    }
}
