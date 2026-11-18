# BLCKCHAT / Remote SMS E2EE - Protocol and implementation plan

**Status 2026-11-18:** target design, NOT shipped E2EE. Legacy LAN APK is not a remote messenger. Never invite offsite testers into that build as if it works over SMS.

## Acceptance objective

Phone A (Admin) sends a genuinely encrypted text message via the carrier's SMS network to Phone B on a separate network. Phone B decrypts locally, verifies the sender's cryptographic identity, and replies. Neither Wi-Fi, GitHub accounts nor a permanently running Phone A socket is required for this SMS exchange. Phone C is the next test participant.

## Explicit boundaries

- SMS operators see sender/recipient phone numbers, timing and lengths. E2EE protects payload only.
- No cipher, hash, DH primitive or ratchet written in-house. Reviewed crypto implementation is a required provider dependency.
- Initial manual SMS send using system ACTION_SENDTO is preferable to requesting broad SMS permissions. Inbound encrypted SMS may initially be copied/pasted or shared into the app explicitly.
- iOS cannot generally intercept/send messages unattended through third-party apps. Cross-platform experience may prefer authenticated HTTPS after initial SMS-based invite.
- A public SMS invite locator is not the identity verification ceremony. A forwarded/intercepted SMS must not create silent trust.

## Client identity and verification

1. Each device creates an identity signing key and a cryptographic key agreement key using a vetted provider. Private keys stay on device.
2. The admin sends a public key bundle and room invite locator through an SMS encoded invitation. Never put a private key or a shared group key in the link.
3. Guest responds with a public key bundle and signed enrollment request.
4. Both clients display the same security fingerprint derived from the transcript and identity keys. Confirmation requires an out-of-band comparison (e.g., a trusted voice call) before the UI marks the peer verified.
5. A key replacement is visible and blocks automatic silent trust. Sessions are bound to verified device keys, not to a display name.
6. Key agreement derives direction-specific ephemeral/ratcheted message keys; authenticate room id, both identities, message id, content type and protocol version as AEAD associated data.
7. On member addition/removal, perform authenticated group key update before future group messages. Never give a newly invited client old history by default.

Do not describe this as stronger than Signal or audited E2EE until independent review, formal threat model and reproducible builds are complete.

## SMS transport envelope: implemented parser, NOT encryption

The strict ISO C11 `blcksms_frame` component only carries **already AEAD-sealed bytes**.

```
BC42|1|<32 lowercase hex message id>|<2-digit index>/<2-digit total>|<base64url ciphertext slice>
```

- Body contains GSM-compatible printable ASCII (no secret plaintext).
- Indices are 1-based; count 1..24; each nonlast slice exactly 96 chars.
- Each complete SMS frame is <=153 characters.
- Opaque ciphertext input currently <=1536 bytes. Maximum output is 24 SMS; actual costs may be high, so default user text limits should be far lower.
- Reassembly is ID-scoped, bounded and strict. Reorder and exact duplicate are accepted; conflicting duplicate, cross-ID splice, malformed segment and overflow are rejected.
- Collector lifetime, simultaneous active message limits, global dedup, drop policy, resend and timeout are intentionally caller-owned.
- Framing is **not** encryption/authentication; AEAD verification must happen after complete reassembly and BEFORE rendering.
- Expose no ciphertext as successful delivery until SMS sender has confirmed a send result. Carrier delivery and application read are separate states.

Potentially useful portability test: `make blckchat-sms-frame-test && ./blckchat-sms-frame-test`.

## Multi-transport behavior

Every logical message gets a cryptographically random stable message ID and a locally generated encrypted envelope. When connectivity is available, send the same sealed envelope over authenticated HTTPS/mobile data using a durable server-side queue. SMS is fallback when data is absent or explicitly selected. Duplicate arrival over both transports must not produce two visible messages. Never decrypt on GitHub or gateway.

## Android UI plan

- Compose page: destination known from invitation, no manual IP address.
- Establish trust page: six-word or equivalent safety verification, not a checkbox defaulting to verified.
- Message composer: clearly shows `SMS` or `Mobile data`, estimated segment count and whether a carrier fee may apply.
- When permission is denied: use system SMS composer, and show how to import encrypted responses without READ_SMS.
- Do not require Phone A and Phone B to share LAN.
- App must not request SEND_SMS/READ_SMS in guest flavor by default. Automatic SMS is an explicitly consented admin-only option with Play policy analysis.

## Implementation order

1. [DONE] Document remote SMS-only requirement and neutral public roles.
2. [DONE] Implement C11 bounded SMS ciphertext fragment codec and deterministic corruption/reorder tests.
3. [NEXT] Integrate audited E2EE provider, device identity keys and out-of-band verification flow.
4. [NEXT] Build Android SMS compose and ciphertext import UX; run encrypted Phone A ↔ Phone B over distant cellular networks.
5. Add Phone C with group key rekey, then mobile-data gateway and durable asynchronous delivery.
6. Security test and external audit; ship signed verified release.

**Design blockers:** Working crypto provider and identity verification are mandatory before any public claim of E2EE. The legacy LAN APK must remain clearly marked as LAN-only and not for offsite secure chat.
