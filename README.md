# Zeus Universal Remote v0.2.0

This is an Android Samsung/Shield remote prototype, not a complete universal remote.

## What's new
- Samsung S95B secure WebSocket authorization on port 8002 with token persistence
- Samsung volume, mute, power and Source menu key requests
- Explicit opt-in for the TV's self-signed TLS certificate, restricted to a private IPv4 address
- Improved dark UI contrast
- Shield TCP reachability diagnostics retained
- Shield Remote v2 PIN pairing (6467), authenticated TLS control (6466), D-pad and playback controls added; awaiting physical Shield validation

## Known limitations
- Samsung TV must be on and approve the remote authorization prompt. Some firmware may reject certain keys (especially power-on).
- HDMI 1 / Shield runs a timed menu sequence (about 7 seconds in faster mode, 32 seconds in slower mode). Start with the Source menu closed and do not use another remote during selection. Manual Source/navigation controls remain available after completion or cancellation.
- Power-on from standby is not implemented; Wake-on-LAN/CEC would need additional work.
- Shield keyboard, double-tap Home/recent apps, app launching and coordinated power/CEC are not implemented. Favorite app tiles remain disabled.
- Samsung commands and the HDMI shortcut have been tested by the owner. Shield pairing/control still needs physical-device testing; a successful build does not verify device behavior.

## Install
Build and install the APK, connect Samsung, and press Source. Use the Samsung navigation buttons to highlight HDMI 1 (possibly named Shield) and press OK. If Source does nothing, press TV Home and navigate to Connected Devices. Report which route worked, the displayed device name, and the exact button sequence. Check volume and mute still work.

These controls navigate the Samsung TV; they are not Shield Remote v2 controls. A queued key does not confirm execution. HDMI-CEC coordination remains unimplemented.

## HDMI 1 shortcut validation
The sequence `Source → Left ×10 → Right ×3 → OK`, with two-second delays, was tested from both TV and HDMI 1 through a desktop WebSocket connection. The owner confirmed HDMI 1 selection in both cases. Repeated Left presses reach the Connected Devices sidebar; three Right presses reach HDMI 1 through SmartThings and TV.

The Android shortcut uses the same key sequence, with progress, cancellation, duplicate-run prevention and connection checks. Other TV key buttons are ignored while it runs to avoid interleaving commands. It depends on this TV's menu layout and does not read back the active input. Firmware or menu changes may require recalibration.

The owner confirmed the original 32-second Android shortcut worked on the phone. Faster mode keeps the two-second initial wait and Source-menu opening delay, uses 100 ms between directions, waits one second before OK, and waits 500 ms afterward (6.7 seconds of planned delays). A desktop WebSocket test completed in 6.9 seconds, and the owner confirmed it selected HDMI 1. The key sequence and ten Left presses are unchanged. Faster mode is enabled by default; its setting is saved and cannot change during a run. Turn it off to restore the tested two-second spacing.

The owner confirmed the updated approximately seven-second Android shortcut worked. If it misses inputs, turn off faster selection and retry with the menu closed.

## Shield pairing and navigation test

1. Install the new APK. Keep Shield on, on the same private network, with Android TV Remote Service enabled. Select HDMI 1 to see its pairing prompt.
2. Enter the Shield's private IPv4 address in the existing Shield IP field, then tap **Pair Shield**.
3. Enter all six characters shown by Shield (hexadecimal: 0–9 and A–F) and tap **Confirm pairing**. The request expires after two minutes; restart pairing if needed.
4. Wait for **Connected and authenticated**, then test Up/Down/Left/Right, OK, Back and Home. Test playback buttons inside an app that supports them.
5. Tap Disconnect, then Connect Shield: it should reconnect without another code. Repeat after closing and reopening the app.
6. Briefly disconnect Shield's network and restore it. Zeus makes up to five reconnect attempts with backoff. Tap Connect if retries run out. Cancelling or changing the Shield IP closes its active sockets.
7. Test an incorrect code, cancellation, and **Forget Shield** followed by pairing again. Forget removes this phone's per-IP identity and peer pin; it does not manage other clients on Shield.
8. Verify Samsung volume, mute and HDMI 1 still work. Report the exact Shield status message for failures; do not share keys or credential files.

The phone's per-device RSA private key stays in Android Keystore. Successful code verification and the Shield's secret acknowledgement are required before its certificate fingerprint is pinned. Control connections reject changed certificates. The initial pairing connection accepts a self-signed RSA peer only at the configured private IPv4 endpoint; the on-screen code binds the certificate keys. It does not mark an open TCP socket as authenticated control. No pairing PIN is stored or logged. Peer pins are stored in app-private, non-backup files; app backup is disabled because Keystore identities do not transfer to a new phone. If Shield changes IP, configure the new address and pair it again.

Implementation is separated into `ShieldPairingClient`, `ShieldRemoteClient`, `ShieldCredentials`, a bounded protobuf transport, `ShieldController` and `ShieldControls`. Socket operations, credential creation and persistence run off the UI thread. The screen owns the connection lifetime and closes it when disposed. Recreating the activity requires tapping Connect Shield, using saved credentials. Android TV protocol sources and license attribution are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Source organization

Android source lives under `app/src/main/java/com/zeus/remote/`:

```text
MainActivity.kt                 Activity entry point
data/
  DeviceRepository.kt           Existing device settings and Samsung token preferences
diagnostics/
  ConnectivityDiagnostics.kt    Background TCP reachability checks
samsung/
  SamsungRemoteClient.kt        WebSocket authorization, TLS and remote keys
  SamsungController.kt          Connection state and HDMI selection/cancellation
  HdmiInputSequence.kt          Hardware-tested menu sequence and timing
  ui/SamsungControls.kt         Samsung controls and HDMI shortcut
shield/
  ShieldController.kt           Pairing/control state and reconnect coordination
  ShieldPairingClient.kt        PIN pairing protocol
  ShieldRemoteClient.kt         Authenticated control protocol
  ShieldCredentials.kt          Keystore identity and peer certificate pin
  ShieldProtocol.kt             Framing, PIN proof and address validation
  ShieldTransport.kt            TLS socket I/O
  ui/ShieldControls.kt           Shield pairing and navigation UI
ui/
  RemoteScreen.kt               Shared screen composition and feature lifetime
  DeviceSettings.kt             Device setup fields and actions
```

Tests mirror the `samsung/` and `shield/` feature folders under `app/src/test/java/com/zeus/remote/`.
Protobuf schemas stay in `app/src/main/proto/`, Android resources in `app/src/main/res/`,
and bundled licenses in `app/src/main/assets/licenses/`.
The Android application ID, manifest entry point, preference file/keys and Shield credential aliases are unchanged.
Both HDMI timing modes retain the same verified command sequence and delays.

Upload the contents of this ZIP into the GitHub repository preserving directory structure, then run the existing GitHub Actions `Build Android APK` workflow.
