# BLCKCHAT 42 / SECURITY.md

## Security objectives

BLCKCHAT does not claim to be safer than Signal today. The initial source
is a prototype, **not** an end-to-end encrypted messenger. A good security
claim must be based on tests, audits, threat modeling and a published
protocol, not allegations about competitors.

- No intended backdoors, escrow keys, administrator decryption keys or
  hidden lawful-access interfaces.
- Avoid custom cryptographic algorithms. Use audited platform implementations
  for CSPRNG, TLS, AEAD, signatures, KDF and secure key exchange.
- Reproducible release builds with signed provenance and source-to-binary
  verification, with published independent audit reports before making
  comparative security claims.
- Device key verification, protection from silent participant addition and
  group rekey on membership changes are required before E2EE is advertised.

## Trust boundaries

| Boundary | Trust limitation |
| --- | --- |
| Public GitHub Pages | Documentation and public downloads ONLY. Never holds secrets. |
| Admin Android device | SMS delivery and admin workflow. Not a general-purpose key escrow. |
| Gateway | Auth, rate limits, durable queue and GitHub writes. Must not have chat decryption keys in a future E2EE design. |
| Private GitHub repository | Archival/transport layer, never a trusted confidentiality boundary. |
| Client | Owns device secrets and renders verified conversation membership. |

Private GitHub repos are not equivalent to E2EE. Even a perfectly secure
gateway cannot prevent a compromised client from leaking plaintext.

## Code in the repository, as of first room-engine test

- `blckauthz`: deny-by-default role policy. NOT an identity provider.
- `blckroom`: RAM-only, finite-capacity internal domain model.
  It exercises invitation state, 8-digit OTP issue/redeem, one-time consumption,
  room-scoped bearer sessions, message author attribution from validated
  sessions and immediate access denial after guest revocation.
- `blcksecure`: compact ISO C11 header with OpenSSL provider implementation
  for CSPRNG, HMAC-SHA256, constant-time comparison and memory zeroing.
- `blcksms`: SMS text formatting. Admin Android platform transport is separate.

**The room engine currently stores message plaintext in RAM. It DOES NOT
provide E2EE or remotely accessible chat, durable storage, GitHub transport,
automatic OTP delivery, mobile sessions, or a production-ready API.**

## Experimental carrier-SMS E2EE client (not audited)

Android SMS Alpha 2 now contains a manually operated two-device messaging path:

- Android 12+ Keystore-generated P-256 ECDH identity; private key cannot be
  exported through the app, and the app fails closed if the provider is absent.
- Peer public keys exchanged as SMS records. Neither identity is trusted
  until both users independently compare and confirm the identical safety
  code. Silent public-key replacement is blocked.
- P-256 ECDH secret -> HKDF-SHA256 -> sender-direction AES-256-GCM key.
  Each ciphertext uses a fresh random 96-bit nonce. The associated data
  authenticates both public identities and a random 128-bit message ID.
- Strict C11 SMS ciphertext framing and reassembly; duplicates rejected
  with locally persisted message identifiers.
- Carrier SMS sending through Android's system compose UI; incoming
  ciphertext manually pasted or explicitly shared. No automatic SMS
  reading or sending permission is requested in the guest APK.
- No LAN or internet connection is necessary for the SMS path.

**Additional local storage defenses:** The SMS outbox stores only
ciphertext segments and a Keystore-HMAC binding to both the intended
E.164 phone number and the verified contact key fingerprint. Neither
recipient number nor raw contact key is written into the draft cache.
A different number or verified peer identity blocks retries and manual
send confirmation. The local conversation journal uses a **distinct
Android Keystore AES-GCM encryption key**, fresh IV and record-specific
associated data per message. SQLite never stores message plaintext, but
screen capture, a compromised running app or an unlocked phone remain
possible exposure paths. Journal retention is capped at 300 records and
can be deleted by the user. Old history is removed when the peer is
forgotten. These are implementation controls, not an external audit.

**Limitations:** This is static-pair ECDH, NOT the Signal protocol, Double
Ratchet, MLS or forward-secret messaging. Compromise of a device's
long-lived ECDH key exposes older captured SMS ciphertext for that peer.
There is no device-independent backup recovery, multi-party E2EE,
post-compromise security, audited key verification UX, traffic analysis
protection, guaranteed SMS delivery or cross-platform iOS client.
A shared phone number is not by itself authentication. SMS operators
see metadata. Android OS compromise defeats local confidentiality.

An Android CI build only verifies compilation and packages an APK;
it does not prove safe operation on physical phones. Until real Phone A
and Phone B tests plus independent security review, the release is
**experimental, not suitable for sensitive use**.

## State-machine controls

- Invite locator is an unprivileged reference valid for up to seven days;
  it cannot grant a guest session without fresh SMS OTP validation.
- OTP TTL ten minutes, 8 digits, rejection-sampled CSPRNG generation,
  max three issues per invitation, one-minute resend cooldown, max five
  bad guesses per active OTP. Gateway must separately throttle global,
  per-IP, per-phone, per-device, per-room and per-admin issuance.
- Guest session TTL 24 hours. Expired sessions denied. Refresh and device
  rebinding require a separate secure implementation.
- Guests only see messages **after** their enrollment by default; past
  messages are not automatically exposed to newly invited devices.
- Removing a participant immediately revokes subsequent server access.
  Previously received messages cannot be remotely erased or unshared.
- All gateway requests and message fields require a validated device
  session. A display name supplied by a client is not proof of identity.

## Open engineering gates before APK release

1. Durable atomic transactions, credential encryption at rest,
   replay-resistant idempotent message identifiers and crash recovery.
2. TLS 1.3 endpoint with authenticated routing and no query-string secrets.
3. GitHub private repository provisioning, synchronized durable message
   writes and pull, conflict recovery and API quota management.
4. Admin-phone SMS delivery with real provider callbacks/acknowledgements.
5. Android guest/admin app packaging, deep-link confirmation and device tests.
6. Independent security assessment and abuse testing.
7. E2EE protocol design and implementation, preferably established
   multi-device/group standards rather than homegrown cryptography.

## Verification

```sh
make test
# To independently exercise the room tests with memory/undefined sanitizers
cc -std=c11 -g -O1 -fsanitize=address,undefined -fno-omit-frame-pointer \
  -Wall -Wextra -Werror -pedantic -Iinclude \
  tests/room.c src/blckroom.c src/blcksecure_openssl.c src/blcksms.c \
  $(pkg-config --cflags --libs openssl) -o /tmp/blckchat-room-sanitized
/tmp/blckchat-room-sanitized
```

No public APK should be described as a working messenger until actual
Phone A (Admin)–Phone B–Phone C end-to-end message delivery has been verified on devices.
