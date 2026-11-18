# BLCKCHAT Identity / The Moving A

**Official wordmark:** BLCKCHAT (A appears only in CHAT).

## The A is the message

The missing A in BLCK is deliberate. It can briefly enter
BLCK, turning it into BLACK, before returning to CHAT.
The animation shows that connection changes the shape of
the system without changing its identity.

A stands for:

- **Access:** Someone is invited, not forced to own a GitHub account.
- **Agency:** Every participant controls whether to join and what to share.
- **Attention:** Communication is about people, not the underlying transport.

**Brand line:** *The A moves where connection happens.*

The mark is not a progress indicator unless the UI has real progress
to report. Movement must never imply a message was delivered unless
the gateway acknowledged actual delivery.

## Motion sequence: 5.6 seconds

| Time | Motion |
| --- | --- |
| 0–0.84s | Stable BLCK/CHAT wordmark |
| 0.84–1.96s | A leaves CHAT and travels to the gap in BLCK |
| 1.96–3.02s | Brief hold: BLACK / CH_T |
| 3.02–4.20s | A returns to CHAT |
| 4.20–5.60s | Stable BLCK/CHAT |

Site implementation: CSS-only animation in `site/style.css`.
The final spelling is always BLCKCHAT. Decorative copies of the
A must be marked inaccessible to screen readers and the full brand
must have an accessible label.

## Use in the native GUI

The C11 BLCKUI scene engine should expose the mark as a reusable
component with a renderer-independent state machine. Platform
adapters draw text/paths and respect system accessibility settings.
When "Reduce Motion" is enabled, show the static BLCKCHAT mark.

Visual parameters: deep-ink surfaces, desaturated sea-glass accent,
precise easing, no aggressive glow, no strobing, no bounce effects.
Responsiveness should be subtle and purposeful.

Animation is never allowed to delay sending a message or accepting
an invitation. The interface remains usable while it plays.
