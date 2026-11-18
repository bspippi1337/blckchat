# BLCKCHAT Android LAN Alpha / Test on mobile A (Pippi)

**Internal engineering build**, not the first public production messenger.
It has real encrypted local Wi-Fi messaging and an SMS-composer invite,
but is **not GitHub-backed**, **not end-to-end encrypted**, has no durable
history, and hasn't passed a third-party audit. Do not use for sensitive data.

## Install

1. Open [GitHub Actions](https://github.com/bspippi1337/blckchat/actions/workflows/android-test.yml).
2. Open the most recent **successful** `Android LAN Messenger internal APK` run.
3. Under **Artifacts**, download `blckchat-internal-lan-debug-apk` (ZIP).
   GitHub may require you to sign in.
4. Unzip the file, tap the `android-debug.apk` file on the phone.
5. Allow installation from the app/browser you used to open the APK,
   then install. Do not switch off Android's scanning or protection.
6. Launch **BLCKCHAT 42**.

**A failed or incomplete GitHub Actions run means no verified APK.**
Never install an APK that someone sent from an unknown source.

## Actual phone A test

1. Choose display name `Pippi`.
2. Tap **OPEN A ROOM · ADMIN**.
3. Confirm that status changes to `Connected to 127.0.0.1 · Pippi`.
4. Write `Pippi test 42` and tap the upward send arrow.
5. The same message should reappear in the conversation as
   **PIPPI: Pippi test 42**. This tests the actual local encrypted
   socket round-trip, not a hard-coded demo bubble.

The host is only active while the app process is alive. Keep phone A
unlocked and plugged in for the initial group test.

## Invite Trond and Thomas (same Wi-Fi)

1. Connect all phones to the *same private Wi-Fi*, or use Pippi's hotspot.
2. On mobile A enter Trond's E.164 number (e.g. `+47...`).
3. Tap **CREATE INVITE & OPEN SMS**. Review and send the SMS yourself.
4. Repeat for Thomas. Each SMS contains a different ten-minute
   one-use invitation.
5. Once they have the test APK installed, Trond and Thomas open their
   SMS link again, select **Åpne BLCKCHAT**, enter their name and
   tap **JOIN THIS ROOM**.
6. Each participant writes a message; all participants should receive it.

If an invitation expires during installation, Pippi sends a fresh one.
The public installation page does **not** authenticate guests.

**Current limitations:**
- Not reachable over mobile data or different networks: no public
  gateway, VPN or NAT traversal.
- No message durability or offline delivery if phone A exits.
- SMS is sent through the system compose UI and requires Pippi to tap Send.
- The host sees the plaintext; AES-GCM protects transport only. It is
  not a replacement for Signal.
- No automated tests on physical devices yet.
- If Android says an update has an incompatible signature, uninstall
  the previous debug test app before installing a newly signed build.

## Build from source

```sh
gradle :android:assembleDebug
# APK: android/build/outputs/apk/debug/android-debug.apk
```

The Android app links the in-repository C11 BLCKUI library by Android
NDK/CMake. Network I/O and AES-GCM use Android platform APIs as temporary
adapters. The long-term protocol and gateway architecture stay in C11.

See [SECURITY.md](../SECURITY.md) for release gates and audit policy.
