# BRAVIA Cleaner (Android 10) — source project

A small, TV-remote-friendly Kotlin app with a Shizuku-authorized shell. **Not a root app.** The app does not kill everything automatically and blocks destructive actions against core Sony/Android packages.

## Build

1. Install Android Studio (JDK 17), Android SDK Platform 35 and SDK Platform Tools.
2. Open this folder as an Android Studio project; allow Gradle to download Android Gradle Plugin 8.5.2, Kotlin 1.9.24 and Shizuku SDK 13.1.5. This source bundle intentionally does not include the Gradle wrapper binary; Android Studio can use its configured Gradle installation, or you can generate a wrapper with `gradle wrapper --gradle-version 8.7`.
3. Build > Build APK(s). APK: `app/build/outputs/apk/debug/app-debug.apk`.
4. TV and computer must be on the same network. Enable TV Developer Options and USB/network debugging; find its IP. On computer run `adb connect TV_IP:5555` (or use a USB connection), accept the TV's authorization prompt, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
5. Install the official Shizuku app on the TV (the exact TV version/launcher compatibility may vary). On Android 10 Shizuku generally needs its **ADB start** workflow, not Android 11+ wireless-debugging pairing. Follow the Shizuku app's displayed ADB startup instructions; these can change by release. The Shizuku service normally needs restarting after TV reboot. Open BRAVIA Cleaner and choose **Authorize Shizuku**.

## Features

- Total/free RAM and internal free storage via public Android APIs.
- App list (including system apps) with D-pad navigable Android buttons.
- Per-app **Open Android Settings** (use its Storage & cache screen for individual cache deletion).
- Force-stop selected non-protected packages via Shizuku shell: `am force-stop PACKAGE`.
- Uninstall selected **non-system** apps for user 0: `pm uninstall --user 0 PACKAGE`.
- Global cache trim request: `pm trim-caches 999G internal` (depends on firmware support; the requested free-space target is intentionally high).
- CPU snapshot via `top -b -n 1 -m 15`.

## Important restrictions

- No normal Android 10 APK can silently force-stop other apps or wipe every app's private cache.
- This app uses the **legacy Shizuku remote-process API** (`Shizuku.newProcess`); its availability and behavior vary with Shizuku releases. If the app displays `Command failed` or reports a Shizuku API error, the remote-process backend needs migration to a Shizuku UserService for that release. The app remains useful for RAM/storage monitoring and launching app settings without Shizuku.
- The cache-trim command is system-wide and opportunistic; it does **not** guarantee every app cache is cleared. It may report a nonzero exit code on some Sony firmware.
- CPU snapshots are only instantaneous and don't measure long-term battery/performance impact.
- Uninstall is deliberately disabled for preinstalled system packages, even if some are optional. Use Android Settings to disable those instead. No root and no system-partition modification.
- Target SDK 28 is chosen for Android 10 TV compatibility and unrestricted package enumeration; **not suitable for Play Store submission**. For distribution, update the target and package visibility implementation.
- Do not expose ADB over the internet; disable debugging after setup if no longer needed.

## Testing checklist

Test D-pad focus, home/back, settings navigation, RAM/storage display, Shizuku authorization, `top`, force-stop a throwaway app, reinstall an app, and cache-trim output. Then check HDMI, tuner, Wi-Fi, Bluetooth, casting, remote and Play Store.

## Known limitation

The project is supplied as source and has **not been compiled or tested on your exact Sony BRAVIA**. The Shizuku legacy process API is the main compatibility risk; check the on-screen command result before relying on it.
