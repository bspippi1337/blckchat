# iOS architecture

BLCKCHAT supports iOS as a first-class target.

The shared networking, protocol, chat, sync and room logic remains ISO C11; the iOS application links it as a static library using a small Objective-C/Swift FFI bridge. Native UIKit/SwiftUI is needed for system UI, SMS universal links, notifications, secure Keychain storage, and background execution. This platform glue is intentionally separate from the C11 core.

iOS cannot be distributed as an executable assembled solely from portable C11 without Apple SDK, signing, and native app packaging. Builds require Xcode/macOS or an authorized macOS CI runner. Background activity is OS-limited, so implement foreground catch-up and APNs notifications via the gateway. Do not embed administrator GitHub credentials in the app.

First target: iOS 16+ (proposed); validate supported devices before release.
