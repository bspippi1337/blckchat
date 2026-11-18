# BLCKUI / Visual system 42

**One identity. Native-feeling interaction. Entirely separate from the chat protocol.**

## Architecture

- `include/blckui.h`: stable C11 toolkit API, no vendor dependencies.
- `src/blckui.c`: dark/light design tokens, scene command buffer, compact / desktop adaptive layout.
- `examples/gui_preview.c`: standalone scene composition and SVG renderer demonstration.
- Future backend contract: `blckui_platform` provides window, input, text shaping, GPU/canvas and accessibility.
- OS-specific adapters are allowed but the layout, state machine, color system and widgets must remain within our own C11 toolkit.

## Design requirements

Brand: deep ink surfaces, sea-glass accent, subdued contrast, generous breathing room. Avoid neon-cyberpunk overload, gradients and cheap shadow stacks. Chat content first.

Design tokens: 8pt scale, 14-16px rounded bubbles, typography hierarchy, 44px+ touch targets, clear interactive states, reduced-motion support, high-contrast accessibility mode. Responsive at 320px through wide desktop.

Components to implement: rooms/sidebar, participant avatars, verified invite status, message bubble with wrapping and delivery state, composer with multiline support, attachments, sheets/dialogs, toast, notifications, search and keyboard navigation.

Required production work: accurate UTF-8 Unicode grapheme wrapping, font fallback, right-to-left handling, dynamic type, focus management, screen reader semantics, hit testing, layout measurements and tests. The SVG exporter is a **reference preview**, not a production rendering engine; the current sample does not wrap long messages.

### Preview

```sh
make preview
# Open blckchat-preview.svg in a browser.
```

iOS: native platform adapter for Metal/CoreGraphics/UIKit bridge. Android: NDK backend plus platform IME/keyboard and accessibility bridge. Desktop: Win32 + Direct2D, macOS Cocoa/Metal, Linux Wayland/X11 backend as appropriate.

Do not call the client native/accessible until these adapters are actually implemented and tested.
