# Zeus Shield Companion

Install this separate APK on NVIDIA Shield, not on the phone. It supplies the real launchable-app catalog that Android TV Remote v2 does not expose. No ADB is used by the running apps, and no cloud service is involved.

## Setup

1. Build `:shield-companion:assembleDebug`. The APK is `build/outputs/apk/debug/shield-companion-debug.apk` in this module; GitHub Actions publishes it as a separate artifact.
2. Sideload it onto Shield using your existing installation method, or install through an already-authorized Shield ADB connection. Open **Zeus Shield Companion** in Shield's apps list.
3. Select **Start companion**. Keep Shield and phone on the same private network. A QR code appears on the TV.
4. On the phone, open Zeus **Apps → Scan Shield companion QR**, allow camera access, and scan the TV. Make sure the Shield IP in phone Settings matches the address shown by the companion (or leave it blank for initial setup).
5. The phone shows all installed apps with TV or ordinary launcher activities, including sideloaded apps. Hidden services/system packages without launch activities are excluded. Refresh after installing or uninstalling apps.
6. On Shield, select **Enable app launching**, then grant **Display over other apps** to Zeus Shield Companion in system settings. This is Android's permission exception for starting apps from a background companion. Zeus does not draw overlays. If the shortcut is unsupported, navigate to Shield Settings → Apps → Special app access → Display over other apps manually.
7. Refresh on the phone. Tap **Launch**, then confirm the app opens on the TV. Playback/app focus can vary by app and firmware; a successful API response means a launch was requested, not proof it appeared.
8. Use **Set favorite** next to an installed app to map it to one of SmartTube, Nuvio, Streamio, PLAYFy TV, or Reezen. Exact matching app names are initially suggested automatically. Spelling differences (e.g. Stremio or Reezn) can be mapped explicitly. Favorites work from Remote and Apps while the companion is reachable and launching is allowed.

## Access and lifecycle

- The service listens only on the private IPv4 interface selected by Shield, at TLS port 8765. Network changes may require stopping/starting it and rescanning the updated QR code.
- The QR code grants app-list and app-launch access. It includes the certificate's SHA-256 fingerprint and a random 256-bit bearer token. Do not publish it. The phone verifies that exact certificate on every request; redirects/proxies are disabled. The QR is scanned locally without an external scanning service.
- Private TLS keys stay in Android Keystore. Access tokens are AES-GCM encrypted using a device-bound Keystore key and stored in non-backup app files. The server requires authentication for both catalog and launch requests and only launches installed launcher packages.
- The foreground service stays available when you leave the companion screen. A notification indicates it is running. It does not automatically start at boot: open it and select Start after a reboot. Shield standby or network changes can interrupt access.
- **Stop companion** stops listening. **Reset phone access** stops the service and replaces the shared token, revoking previously scanned phone access after restart. Rescan the new QR to reconnect. Phone **Forget companion** removes only that phone's saved credentials; use Reset on the TV to revoke access.
- QR pairing is separate from Android TV Remote v2 PIN pairing. Removing companion access does not remove navigation pairing.

## Validation

The companion was installed and opened on the owner's Shield Pro. The owner confirmed the TV QR code appeared and scanning it on the phone displayed the installed-app list. Background launching, favorite assignment, access reset and reconnection still require device validation.

Both APKs must compile; unit tests cover endpoint validation, QR credential parsing, package identifiers, bounded HTTP parsing, duplicate headers, chunked-body rejection, incomplete messages and oversized input. Device tests still required: scan QR, compare the catalog with Shield's apps screen, launch from the foreground and background, refresh after uninstalling an app, assign each favorite, reset access and verify the old token is rejected. Confirm that Samsung/Shield navigation and both HDMI timing modes in Settings remain unaffected.
