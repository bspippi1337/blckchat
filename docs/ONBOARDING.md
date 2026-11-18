# BLCKCHAT two-stage guest onboarding

## Goal

A novice accepts an SMS invite, installs the app, and joins a preconfigured room without needing GitHub or understanding repositories.

### Stage 1: Install

SMS leads to a public static onboarding landing page on GitHub Pages. Page offers verified Android/desktop release links or signed official iOS distribution when available. There is no API credential or chat secret on Pages.

### Stage 2: Join

Opening the same invitation again invokes an installed-app Universal Link (iOS) or Verified App Link (Android); desktop equivalent uses registered URL handler with explicit consent.

**Security constraint:** a ten-minute single-use authentication token must NOT be issued before a potentially long installation, or users will be locked out. Use a non-authenticating invitation locator (untrusted, non-secret) to identify the intended room; after app install, gateway issues a fresh short-lived 256-bit one-time credential by SMS to the invited number. A locator is never sufficient to authenticate, create a session, or retrieve chat data. It must not reveal private room details. The app presents 'Continue', awaits code validation, then the gateway atomically redeems the code and issues a scoped guest session. Number-based invite challenges must be rate-limited, have a bounded issuance lifetime and quotas, and require explicit admin approval when configured.

No auto-redeem from a public Pages host and no Github token in any guest app.

## Acceptance tests

- Install completed inside 10 minutes: guest can join.
- Install takes longer than 10 minutes: same SMS onboarding locator works, but fresh OTP is mandatory.
- Guest has app already: stage 1 skipped.
- Expired, reused or wrong-number OTP: rejected.
- Removing member revokes all sessions.
- Universal/App Link unsupported: simple fallback, no arbitrary URL protocol abuse.
- No private tokens in referrer, logs, analytics, git commits, GitHub Pages, crash reports or browser history.
- iOS installation button only shown for an actually usable, Apple-approved signed distribution, never a raw IPA.
- No claim that this flow is functional until gateway and app link verification are tested.

## Store distribution and payment

GitHub Pages and GitHub Releases remain the primary public distribution channel for Android and desktop. Google Play Console registration does not accept prepaid cards. Strex-supported Google Play consumer purchases must not be conflated with developer registration fees. iOS distribution requires an Apple-accepted signing and distribution route; simply linking an IPA does not install an iOS app. Do not advertise unverified prepaid card vendors as a solution. BankID is not presumed available.
