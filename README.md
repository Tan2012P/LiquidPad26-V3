# LiquidPad 26 V3

An original iPadOS 26-inspired Android home launcher for tablets. No Apple code or proprietary assets are included.

## Features
- Blue abstract gradient background with glass-style dock and widgets
- Multiple home-screen pages, swipe horizontally to change pages
- Search overlay with app launching
- Long-press app to pin/unpin to dock (stored in SharedPreferences)
- App icon launch press animation, home entrance animation
- Clock/date and battery widgets
- App library accessible from Search button

## Build in GitHub
1. Create a **new repository** or remove the existing LiquidPad26 files before uploading this project. Avoid merging it into old folders, because duplicate manifests can break builds.
2. Upload the **contents** of this ZIP to the repository root, including `.github/workflows/build.yml`.
3. Open **Actions** → **Build LiquidPad 26 APK** → **Run workflow**.
4. Open successful run → **Artifacts** → download **LiquidPad26-V3-debug-APK**.
5. Extract ZIP and install `app-debug.apk`.

If Android refuses to update an older LiquidPad installation, uninstall the old debug build first (the signing key differs between GitHub runners). This resets LiquidPad preferences but does not wipe tablet data.

Android controls system-wide animations, lock screen, notification shade and multitasking. This app does not replicate those OS features.
