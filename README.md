# BLCKCHAT 42

**Website source:** [`site/`](site/) · **Planned Pages URL:** https://bspippi1337.github.io/blckchat/ · **Releases:** https://github.com/bspippi1337/blckchat/releases

The Pages deployment workflow is in `.github/workflows/pages.yml`. In GitHub **Settings → Pages → Build and deployment**, select **GitHub Actions**. Publication requires a successful workflow and may need repository Pages settings enabled.

The site is public-facing only: downloads, guides, project information. The private guest gateway is a separate C11 service; never store invite tokens, administrator credentials or private chat content in Pages.


A minimal GitHub-backed group messenger, written primarily in ISO C11.

## Product model

**Only the room administrator needs GitHub.** The administrator owns a *private* GitHub conversation repository. Guests do **not** need GitHub accounts. An administrator invites them by SMS using a short-lived, single-use URL token. Guests exchange that token for a scoped session over HTTPS, then chat in the same room.

**A gateway is necessary.** GitHub cannot safely accept anonymous guest messages directly to a private repository. A small C11 gateway authenticates guest sessions, stores the administrator's GitHub credential outside the repository, validates permissions and acts as the only GitHub API writer/reader. GitHub Actions and Pages are *not* a substitute for an always-available private gateway.

## State

**Alpha 0.1: foundation only.** Local CLI journal, build and tests. No working GitHub gateway, SMS sending, invites, guest sessions or encryption yet. Do not use it for sensitive conversations. GitHub transport is eventual-consistency, not real-time.

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
3. Gateway produces a **CSPRNG-generated opaque 256-bit token**. The token is never committed to GitHub. The gateway stores only a keyed digest and metadata (room, expiry, single-use, invite creator).
4. SMS provider sends `https://chat.example/join#token=...` or an equivalent secure deep link. The fragment is handled client-side, never included in an HTTP referrer/request URL automatically.
5. Guest opens link, submits token to gateway over HTTPS, and receives a limited session. Redeeming the token atomically marks it used.
6. Guest selects a display name and can join the same chat room. Admin can kick/revoke guest sessions.

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
