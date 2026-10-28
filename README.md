# Zeus Universal Remote — Android prototype 0.1

Targets: Samsung S95B (Wi-Fi), NVIDIA Shield Pro (Ethernet), HDMI 1.

## What works
- Native Android app with dark Compose UI.
- Editable, locally persisted device IP addresses.
- Real TCP connectivity tests for Samsung port 8002 and Shield ports 6467/6466.
- Remote and five app tiles shown as non-operational placeholders, clearly labeled.

## Not yet implemented
- Samsung TLS trust, authorization token pairing, and remote commands.
- NVIDIA Android TV Remote v2 encrypted pairing/control.
- Reliable power-on, HDMI switching, app launching, and recent-apps command.

## Build
Open this directory in Android Studio (JDK 17, Android SDK 35). Sync Gradle and run on an Android phone connected to your home Wi-Fi. Android Studio can build an APK using Build > Build Bundle(s) / APK(s) > Build APK(s).

This environment does not have the Android SDK/Gradle available, so an APK has **not** been compiled or verified here. Do not install APKs from unknown sources.

## Network troubleshooting
Phone must be on the home LAN, not guest Wi-Fi or a VPN that blocks LAN access. TCP success means only that the port is reachable, not that remote pairing works. Some Shield firmware versions may expose different behavior on the listed ports.
