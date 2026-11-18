# BLCKCHAT 42 | Brand identity

## The illusion

Official identity: **BLCKCHAT 42**. The first **C** in CHAT is still a C.
A fine, translucent fedora suggestion rests in its letter space. At first
glance the viewer reads BLCKCHAT; the second reading suggests BLACK HAT.
The ratio is deliberately **85% C / 15% fedora**.

No animated disappearance of C. No floating real-world hat. No neon pulse,
excessive embossing, or beveling on the C. The visual remains a static
typographic mark.

## Art source and surfaces

The source artwork was developed from the approved static design dated
2026-11-18. Its lightweight WebP derivative is
`site/assets/blckchat-42-official.webp`, 1000×318 px, WebP. The exact
same bytes are committed as
`android/src/main/res/drawable-nodpi/blckchat_wordmark.webp` and displayed
on the first Android SMS screen. This derivative is optimized for display;
the original concept was a higher-resolution generated image.

The small icon is deliberately a *vector adaptation*, because the entire
wordmark would be unreadable at launcher and favicon sizes:
`site/icon.svg` and `android/src/main/res/drawable/blck_logo.xml`.
These use the same white C, restrained blue-gray hat accent, dark ground
and minimal turquoise stroke.

## Safe usage

- Keep the entire wordmark readable, including 42.
- Preserve the implied hat at 15% visual weight.
- Do not replace the C with a pictorial fedora.
- Do not animate the mark or change its width between frames.
- Keep user names/phone numbers off public marketing surfaces.
- Keep SMS security statements accurate: design changes are not a
  security review and do not certify end-to-end communication.

## QA

The site test checks that the original motion mark is absent and the
web and Android artwork are byte-identical. Android instrumented tests
verify the WebP decodes to the intended dimensions and the launcher
vector is present. CI builds the Android APK with bundled assets.
