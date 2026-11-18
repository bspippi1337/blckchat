# BLCKCHAT 42

Minimal C11 chat engine. Inspired by RESTMORE/RESTLESS, designed for a GitHub-backed multi-user transport.

**Status:** Alpha 0.1 foundation. Local append-only messaging works; GitHub login, transport, invites and encryption are **not implemented yet**. This is **not** a secure messenger.

## Build

```sh
make
make test
./blckchat init demo
./blckchat send demo alice "hello world"
./blckchat read demo
```

Requires a C11 compiler and Make. No third-party headers or libraries in the local engine.

## Design

- Small C11 protocol and storage layer with explicit error codes.
- One message per line, fields encoded as hex to prevent delimiter and newline injection.
- Local append-only journal with independent message records. No clock-based ordering guarantees.
- Future GitHub adapter will use **private conversation repositories**, device OAuth, authenticated commits and conditional synchronization.
- Invitations and participant removal must enforce GitHub collaborator permissions and rotate encryption keys when E2EE arrives.
- GitHub is not a real-time messaging service; transport will respect API rate limits and backoff.
- Never store access tokens or private keys in the repository.
- A public source-code repository is fine; a public **conversation repository** is not.

## Roadmap

1. Local C11 storage and deterministic tests (this commit).
2. Platform TLS/HTTP adapter, GitHub OAuth Device Flow and secure local token storage.
3. Chat repository provisioning, message upload/fetch, idempotent sync and retries.
4. Multi-party invitations and access management.
5. Audited crypto dependency, key verification, forward secrecy and group rekeying.
6. Native UI packages for Windows, Linux and Android.

License: MIT (see LICENSE).
