# BLCKCHAT Android SMS gateway adapter

This is an **Android system adapter**, not a complete installable APK.

The C11 `blcksms` library creates an SMS invitation and a login-code body. `AdminSmsTransport.java` supplies two Android transport methods:

- `composeSms`: default, user-confirmed SMS through an installed SMS app, no SEND_SMS permission required.
- `sendAutomatically`: opt-in, administrator-only automatic sending using SmsManager; requires `android.permission.SEND_SMS` declared in the **admin flavor** manifest and a runtime grant.

Do **not** put SEND_SMS into the general guest APK. Google Play heavily restricts this permission for non-default SMS apps; administrator automatic SMS is primarily suitable for direct distribution, subject to local law and carrier limits.

### Workflow (planned, not implemented yet)

1. Admin enrolls an Android phone as SMS sender; gateway securely pairs the device to one room owner.
2. Guest opens public Pages install invitation, then reopens it.
3. Gateway checks invite locator and queues a new short-lived OTP SMS for the enrolled admin phone.
4. Admin phone securely fetches a job, sends SMS through SIM, then reports status with a unique job ID and retries safely.
5. Guest enters OTP in app, gateway rate-limits and atomically consumes OTP; guest session is room scoped.
6. Gateway revokes sessions when the administrator removes a participant.

### Operating conditions

- Admin phone must have a working SIM, messaging-capable mobile subscription, adequate credit, and signal.
- SMS can cost money, vary in delivery latency or be blocked by carrier.
- When phone is offline, queue invites on the gateway and show 'Waiting for administrator SMS sender'; do not pretend delivered.
- Android background execution and battery optimizations can delay automated sends. Prefer a foreground service with explicit user opt-in for an unattended gateway.
- SMS on iPhone cannot be sent unattended using MessageUI; iPhone clients are still supported as invite recipients.
- Never poll GitHub for SMS jobs or upload OTPs into a public repository.
- Avoid plaintext phone numbers, OTPs and message bodies in logs, analytics, crash reports or GitHub.
- On dual SIM, choose an explicit subscription ID in admin settings; never assume SIM slot zero.
- Add carrier sent/delivery callbacks before considering the automatic mode complete.
- Queues need idempotent job IDs, rate limits, SMS send quotas, TTL and cancellation controls.

### Security note

The first SMS contains a **non-authenticating public invite locator**. Install may take arbitrarily long. A *different* eight-digit OTP, issued after installation, is checked over HTTPS. OTPs are authenticated by gateway-side rate limiting, short expiry, hashed storage, redemption atomicity, and optional admin approval. They are **not** generated on the Android device. Do not use SMS alone as a high-assurance identity proof.
