# HISTORICAL LAN-ONLY TEST: SUPERSEDED FOR REMOTE USERS

**Do not send this LAN-only APK to offsite guests as a functioning remote messenger.** The current priority is E2EE over carrier SMS first, then mobile data. This file documents a past internal LAN build only.

# BLCKCHAT Android LAN Alpha / Test on mobile A (Phone A (Admin))

**Internal engineering build**, not the first public production messenger.
It has real encrypted local Wi-Fi messaging and an SMS-composer invite,
but is **not GitHub-backed**, **not end-to-end encrypted**, has no durable
history, and hasn't passed a third-party audit. Do not use for sensitive data.

## Install without a GitHub account

**Phone B and Phone C:** Open the SMS invite link Phone A (Admin) sends. The two-step
Norwegian guide at https://bspippi1337.github.io/blckchat/join.html
offers the official test APK at:

https://bspippi1337.github.io/blckchat/downloads/blckchat-lan-alpha.apk

1. Tap **Last ned Android-appen** and open the downloaded APK.
2. If Android asks permission to install from this browser, allow it
   for this one installation, then turn that permission off afterwards.
3. Do not disable Play Protect. Stop if there is a malware warning.
4. Open the SAME invitation SMS link again and tap **Åpne BLCKCHAT**.
5. Enter your name (Phone B/Phone C) and tap **BLI MED I SAMTALEN**.

No GitHub, BankID or server fields required. Both phones must be on the
same Wi-Fi/hotspot as Phone A (Admin). If installation exceeds the invite's
ten-minute lifetime, ask Phone A (Admin) for a new invite. The public Pages link
is a **test APK, not a production secure messenger**.

**Phone A (Admin):** Install from the same official website, launch BLCKCHAT,
use your name and tap **ÅPNE ROM SOM ADMIN**.

The GitHub Pages deployment builds the APK, checks its signature,
and publishes SHA-256 information in
`/blckchat/downloads/SHA256SUMS.txt`. A failed deployment must not
be assumed to have updated the public binary. The separate Actions
artifact is a developer fallback:

https://github.com/bspippi1337/blckchat/actions/workflows/android-test.yml

## Actual phone A test

1. Choose display name `Phone A (Admin)`.
2. Tap **OPEN A ROOM · ADMIN**.
3. Confirm the local server starts and the app reports a connected Phone A (Admin) session.
4. Write `Phone A (Admin) test 42` and tap the upward send arrow.
5. The same message should reappear in the conversation as
   **PIPPI: Phone A (Admin) test 42**. This tests the actual local encrypted
   socket round-trip, not a hard-coded demo bubble.

The host is only active while the app process is alive. Keep phone A
unlocked and plugged in for the initial group test.

## Invite Phone B and Phone C (same Wi-Fi)

1. Connect all phones to the *same private Wi-Fi*, or use Phone A (Admin)'s hotspot.
2. On mobile A enter Phone B's E.164 number (e.g. `+47...`).
3. Tap **CREATE INVITE & OPEN SMS**. Review and send the SMS yourself.
4. Repeat for Phone C. Each SMS contains a different ten-minute
   one-use invitation.
5. Once they have the test APK installed, Phone B and Phone C open their
   SMS link again, select **Åpne BLCKCHAT**, enter their name and
   tap **JOIN THIS ROOM**.
6. Each participant writes a message; all participants should receive it.

If an invitation expires during installation, Phone A (Admin) sends a fresh one.
The public installation page does **not** authenticate guests.

**Current limitations:**
- Not reachable over mobile data or different networks: no public
  gateway, VPN or NAT traversal.
- No message durability or offline delivery if phone A exits.
- SMS is sent through the system compose UI and requires Phone A (Admin) to tap Send.
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
