# BLCKCHAT 42

**Website source:** [`site/`](site/) · **Planned Pages URL:** https://bspippi1337.github.io/blckchat/ · **Releases:** https://github.com/bspippi1337/blckchat/releases

The Pages deployment workflow is in `.github/workflows/pages.yml`. In GitHub **Settings → Pages → Build and deployment**, select **GitHub Actions**. Publication requires a successful workflow and may need repository Pages settings enabled.

The site is public-facing only: downloads, guides, project information. The private guest gateway is a separate C11 service; never store invite tokens, administrator credentials or private chat content in Pages.


A minimal GitHub-backed group messenger, written primarily in ISO C11.

## Product model

**Only the room administrator needs GitHub.** The administrator owns a *private* GitHub conversation repository. Guests do **not** need GitHub accounts. An administrator invites them by SMS using a short-lived, single-use URL token. Guests exchange that token for a scoped session over HTTPS, then chat in the same room.

**A gateway is necessary.** GitHub cannot safely accept anonymous guest messages directly to a private repository. A small C11 gateway authenticates guest sessions, stores the administrator's GitHub credential outside the repository, validates permissions and acts as the only GitHub API writer/reader. GitHub Actions and Pages are *not* a substitute for an always-available private gateway.

## Active Android build: REMOTE SMS Alpha 2

**Phone A (Admin) and Phone B can exchange encrypted SMS messages while in different locations.** Android 12+ is required for the Android Keystore P-256 ECDH implementation. An active SIM and standard SMS app are used; no common Wi-Fi, GitHub account or backend is needed for this two-phone trial.

[**Install the experimental SMS APK**](https://bspippi1337.github.io/blckchat/join.html)
· [**Simple Norwegian setup**](https://bspippi1337.github.io/blckchat/guide.html)
· [**GitHub Actions APK**](https://github.com/bspippi1337/blckchat/actions/workflows/android-test.yml)

The first SMS link carries only the administrator's **public** key in a URL fragment. The recipient installs the app, reopens the SMS link, then imports the prefilled public key and sends back their own public key. Both devices MUST compare a security code independently before activating encryption. Android's standard SMS app sends ciphertext, and recipients manually paste/share the encrypted SMS to BLCKCHAT for decryption. The C11 SMS frame layer handles packet framing.

**Not audited; NOT equivalent to Signal.** This experimental static ECDH scheme has no Double Ratchet, forward secrecy against long-term key compromise, group E2EE, background SMS reading, durable chat history or cloud data transport. Phone C and mobile-data delivery are later work. Do not use this test for sensitive communications. The older LAN APK is archived development history only.

[Remote cryptographic engineering plan](docs/REMOTE_SMS_E2EE.md) · [Security](SECURITY.md)

## Android LAN Alpha: real test messages

**Download for Phone A (Admin), Phone B and Phone C:** [Simple Android installation page](https://bspippi1337.github.io/blckchat/join.html). The [direct test APK](https://bspippi1337.github.io/blckchat/downloads/blckchat-lan-alpha.apk) is built, verified and published by the Pages workflow, while GitHub Actions also retains its own internal artifact.

This is **not the promised production GitHub-backed Alpha 1**. It is an internal, openly accessible testing build with real text messages over the same local Wi-Fi. The administrator's Android runs the TCP room server and sends one-use invitation links using the native SMS compose app. Guest Android screens only ask for a name and one tap to join. Messages are encrypted in transit via platform AES-GCM, but the host can read them; there is no audited E2EE, no persistent history, no remote cross-network delivery and no private GitHub synchronization yet.

**Never use this build for sensitive messages.** Android debug signing and an external APK install can trigger normal Android installation warnings. Do not disable Play Protect or ignore malware warnings.

[Plain-Norwegian onboarding guide](https://bspippi1337.github.io/blckchat/guide.html) · [Three-phone manual](docs/ANDROID_LAN_TEST.md) · [Security boundaries](SECURITY.md)

## Release policy: first APK must actually message

**No demo-only APK release.** An Android artifact must not be named or advertised as the first release unless it passes end-to-end integration tests with the actual HTTPS gateway, a private GitHub-backed room and two independently authenticated guests.

Required acceptance checks:
1. Admin configures GitHub credentials server-side and provisions a private channel.
2. Guest A and B enroll without GitHub accounts using distinct short-lived, one-time SMS credentials; no secrets leak into GitHub Pages.
3. A sends a text message, B receives it; B replies and A receives it on a separate device/process.
4. Messages persist through app restart and are sourced from GitHub-backed storage or a durable synchronized queue.
5. A revoked guest cannot read or post; expired or reused OTPs are rejected.
6. Bad network, 429/rate-limit, conflicting writes and offline/reconnection have defined recovery.
7. TLS verification is active, tokens are scoped and stored securely, no plaintext credentials in APK or git.
8. APK is signed, installable, and download link points to the actual produced binary.

Build jobs may generate internal test artifacts during development; public download labels must distinguish prefunctional artifacts and not claim a working messenger. An internet-reachable gateway deployment, operational admin-phone SMS sender, and admin GitHub authorization are prerequisites. A GitHub Pages-only deployment is insufficient.

## Administrator phone as SMS gateway

**Chosen design:** the administrator's **Android** phone sends invitations and login-code SMS using its own SIM. The C11 `blcksms` library formats messages and validates recipients; a tiny Android system adapter uses the platform's SMS facilities. Guests on Android and iOS receive SMS as normal and do not need a GitHub account.

- **Default:** open prefilled SMS in the phone's messaging app for administrator confirmation (no special SMS permission).
- **Optional admin-only mode:** send automatically after explicit admin opt-in and Android `SEND_SMS` permission. This permission is tightly restricted on Google Play; do not request it in the ordinary guest app.
- **Gateway interaction:** the authenticated HTTPS gateway holds short-lived SMS jobs and OTP verification state; the admin handset polls or receives push jobs, sends and acknowledges them. GitHub Pages is *not* the gateway.
- **Two-stage invitation:** first SMS carries an install/link locator that is NOT an authentication token. After installation, a new eight-digit OTP is created by the gateway and SMS-sent by the admin phone. The OTP expires in ten minutes. Token issuance, hashing, limits and one-time redemption belong server-side.
- **Offline behavior:** if the admin handset is offline, new invite/OTP SMS must be queued or held with clear status. Already enrolled users should keep chatting through the separate online gateway.
- **Costs:** third-party SMS-provider fees are avoided, but carrier SMS charges and limits may still apply.
- **iOS:** supported as a guest client; iOS does not provide an unattended SMS gateway through MessageUI.

The formatting module and Android adapter are present, with native C11 formatter tests. **This is not yet an operative SMS gateway or a working messenger APK.** Device registration, job transport, OTP validation, delivery acknowledgement and actual app packaging are still pending. See [Android adapter](android/README.md).

## State

**Alpha foundation and internal Android LAN app.** A signed debug Android APK now supports a real same-Wi-Fi test conversation and one-use SMS invitations. Local CLI journal, C11 GUI scene prototype, SMS formatter, deny-by-default role policy and a tested RAM-only three-person room engine are present. No deployed production GitHub gateway, automatic SMS queue, durable cloud messaging, iOS client or end-to-end encryption yet. Do not use for sensitive conversations. GitHub transport is eventual-consistency, not real-time. See [SECURITY.md](SECURITY.md).

## Build and local smoke test

```sh
make && make test
./blckchat init demo
./blckchat send demo alice "hello"
./blckchat read demo
```

The local journal is **plain text encoded as hexadecimal**, not encrypted. It uses no third-party headers. This format and CLI are prototypes, not the production protocol.

## Architecture

- `libbs_core`: buffers, parsers, error handling, logging.
- `libbs_chat`: local store, room/message models, stable message IDs, validation.
- `libbs_net`: HTTP client and HTTPS gateway interfaces with a small, audited TLS provider.
- `libbs_gh`: GitHub REST transport, conditional GETs, rate limiting, backoff.
- `libbs_invite`: single-use, short-lived invite credentials, guest enrollment.
- `libbs_auth`: scoped sessions, expiration, revocation, abuse controls.
- `libbs_ui`: minimal native chat client.

**No home-grown crypto primitives for security.** Cryptographically secure randomness, keyed hashes, authenticated encryption and TLS must use reviewed platform or external cryptographic implementations. Core/application logic stays in C11 with small explicit adapter headers. The planned protocol does *not* promise E2EE until group key lifecycle and device verification have been reviewed and tested.

## SMS invitation flow

1. Admin signs into GitHub and creates a private room via the gateway.
2. Admin enters guest phone number and chooses invite TTL, default **10 minutes**.
3. Gateway generates a non-authenticating room invitation locator with its own bounded lifetime. This locator does not grant access or reveal private chat data.
4. Admin Android phone sends an invitation SMS linking to `https://bspippi1337.github.io/blckchat/join.html#i=...`; the guest installs the app first. This is only a locator, not a bearer token.
5. After installation, guest reopens the link. Gateway queues a new short-lived eight-digit code to the admin phone for SMS transmission. The guest submits this OTP to gateway over authenticated TLS; the gateway rate-limits attempts and atomically marks the OTP redeemed.
6. Guest chooses a display name and gets a room-scoped session. Admin can kick/revoke guests. Unregistered guests do not need GitHub accounts.

The gateway requires private, protected credential/session storage and a durable atomic invite-redemption store. Prefer short-lived cookies with Secure, HttpOnly and SameSite where a browser frontend is used. Never log invite tokens or pass them into analytics. SMS messages may be forwarded, intercepted or SIM-swapped, so the token proves **possession**, not verified identity. Admin should be able to require approval after redemption. Use fixed maximum token lifetimes, rate limits and per-number SMS throttles.

## GitHub transport contract

- Private repository per room; append-only entries with unique IDs to avoid last-write-wins overwrite.
- Gateway serializes or retries GitHub writes on SHA conflicts, and sync uses ETag/conditional requests.
- Rate limit budget/backoff, no credential exposure to guests.
- The GitHub repository is archival transport; gateway has to keep ephemeral sessions, invites, and delivery queues outside GitHub.
- Guest messages are not visible until gateway writes and the clients resync; no instant delivery guarantee.
- Admin token uses least available permissions and must be revocable.

## Next milestones

- C11 local core and reproducible tests.
- C11 HTTPS gateway plus durable session/invite store, external TLS/crypto adapters.
- GitHub login and private room management.
- SMS integration, single-use invite redemption and guest UI.
- Reliable GitHub write queue, deduplication and synchronization.
- Native iOS shell (Swift/Objective-C bridge to C11 core), Keychain credential storage, URL-based invite redemption, and App Store-compatible HTTPS networking.
- Group confidentiality, device/key verification, revocation and third-party security review.

MIT licensed.
