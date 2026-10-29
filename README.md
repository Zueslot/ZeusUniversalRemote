# Zeus Universal Remote v0.2.0

This is an Android Samsung/Shield remote prototype, not a complete universal remote.

## What's new
- Shield pairing now uses a stable unique client name and certificate CN per phone installation. To migrate existing phones, update each phone and use Pair Shield once on each; legacy credentials are replaced only when explicitly starting pairing. Two-phone coexistence requires device validation. Each phone may scan the existing companion QR for app launching; do not reset companion access between phones, as that revokes the shared QR token.
- Assigning an installed Shield app to a favorite immediately displays its actual name on the remote, Apps favorites and layout picker. Names are cached per Shield address for offline/restart display; the four slot IDs and positions remain stable. The assignment picker numbers slots so duplicate app names can still be distinguished.
- The glass remote now has four favorites: SmartTube, Nuvio, PLAYFy TV and Reezen. Streamio remains in the installed-app catalog if installed, but is retired from favorite slots. Menu, Home and Back use matching glass pills with a simple left arrow for Back. App tiles retain their taller glass cards, colored symbols and labels. Existing version-2 layouts realign once to the centered D-pad, navigation row and evenly spaced favorites; new edits save as version 3.
- Samsung S95B secure WebSocket authorization on port 8002 with token persistence
- Samsung volume, mute, power and Source menu key requests
- Explicit opt-in for the TV's self-signed TLS certificate, restricted to a private IPv4 address
- Improved dark UI contrast
- Shield TCP reachability diagnostics retained
- Shield Remote v2 PIN pairing (6467), authenticated TLS control (6466), D-pad and playback controls added; the owner confirmed the local-test build works on the phone/Shield
- Favorite tile names: SmartTube, Nuvio, Streamio, PLAYFy TV and Reezen. The owner confirmed that the companion QR appears on Shield and scanning it populates the phone's installed-app list. Favorite assignment and launching still need device validation.

## Known limitations
- Samsung TV must be on and approve the remote authorization prompt. Some firmware may reject certain keys (especially power-on).
- HDMI 1 / Shield runs a timed menu sequence (about 7 seconds in faster mode, 32 seconds in slower mode). Start with the Source menu closed and do not use another remote during selection. Manual Source/navigation controls remain available after completion or cancellation.
- Power is a single-tap button: a fresh Samsung status of on selects standby for both; off or unavailable status selects wake for both. Unknown status uses idempotent wake requests, never a blind TV toggle. Wake sends Samsung Wake-on-LAN and Shield Wake; standby sends Samsung's working power key only after fresh REST status reports on, then Shield Sleep. The owner confirmed both-device standby, wake and automatic HDMI 1 selection after enabling Anynet+; the single-tap behavior still needs device validation.
- Shield keyboard and double-tap Home/recent apps are not implemented. App listing/launching requires the separate Shield companion, QR pairing and background-launch permission on Shield.
- Samsung commands, the HDMI shortcut and Shield pairing/control have been tested by the owner. Features beyond these still require implementation and device validation; a successful build does not verify device behavior.

## Install
Build and install the APK, connect Samsung, and press Source. Use the Samsung navigation buttons to highlight HDMI 1 (possibly named Shield) and press OK. If Source does nothing, press TV Home and navigate to Connected Devices. Report which route worked, the displayed device name, and the exact button sequence. Check volume and mute still work.

Samsung fallback controls navigate the TV; the main D-pad controls Shield. A queued key does not confirm execution.

### Power setup

Connect and authorize Samsung while it is on so the app can learn its wake MAC address. Settings → Power & Wake also allows entering the MAC or reading it from the TV. Enable Samsung **Power On with Mobile** and **Anynet+ (HDMI-CEC)**, and enable the desired TV wake/sleep options in Shield **Display & Sound → Power control**. These device settings must be configured on the devices themselves.

Samsung reconnects automatically after a lost connection, with retry delays capped at 30 seconds; authorization rejection stops retries. Power wake requests a fresh connection and sends magic packets to the TV address and available broadcast addresses on UDP ports 7 and 9. If CEC does not select the Shield input, enable the optional HDMI 1 menu fallback in Power & Wake; this displays the existing source-navigation sequence. Standby skips the TV power toggle if its state cannot be confirmed as on. Shield CEC settings and physical testing remain necessary.

## HDMI 1 shortcut validation
The sequence `Source → Left ×10 → Right ×3 → OK`, with two-second delays, was tested from both TV and HDMI 1 through a desktop WebSocket connection. The owner confirmed HDMI 1 selection in both cases. Repeated Left presses reach the Connected Devices sidebar; three Right presses reach HDMI 1 through SmartThings and TV.

The Android shortcut uses the same key sequence, with progress, cancellation, duplicate-run prevention and connection checks. Other TV key buttons are ignored while it runs to avoid interleaving commands. It depends on this TV's menu layout and does not read back the active input. Firmware or menu changes may require recalibration.

The owner confirmed the original 32-second Android shortcut worked on the phone. Faster mode keeps the two-second initial wait and Source-menu opening delay, uses 100 ms between directions, waits one second before OK, and waits 500 ms afterward (6.7 seconds of planned delays). A desktop WebSocket test completed in 6.9 seconds, and the owner confirmed it selected HDMI 1. The key sequence and ten Left presses are unchanged. Faster mode is enabled by default; its setting is saved and cannot change during a run. Turn it off to restore the tested two-second spacing.

The owner confirmed the updated approximately seven-second Android shortcut worked. If it misses inputs, turn off faster selection and retry with the menu closed.

## Shield pairing and navigation test

### RSA TLS compatibility fix

If an earlier APK failed with `RSA routines:OPENSSL_internal:internal error`, install the updated build, tap **Forget Shield**, then **Pair Shield**. Existing Keystore keys cannot gain new authorizations in place. The new identity permits the raw RSA operation used internally by Conscrypt for TLS PSS signatures and the prehashed signing path. Private keys remain non-exportable; PIN authentication and peer certificate pinning are unchanged. Old incompatible keys now produce a clear replacement message rather than retrying the broken TLS operation. This matches the failure described in [Conscrypt issue 718](https://github.com/google/conscrypt/issues/718) and its [PSS explanation](https://github.com/google/conscrypt/issues/1201); confirmation on this phone and Shield is still required.

1. Install the new APK. Keep Shield on, on the same private network, with Android TV Remote Service enabled. Select HDMI 1 to see its pairing prompt.
2. Enter the Shield's private IPv4 address in the existing Shield IP field, then tap **Pair Shield**.
3. Enter all six characters shown by Shield (hexadecimal: 0–9 and A–F) and tap **Confirm pairing**. The request expires after two minutes; restart pairing if needed.
4. Wait for **Connected and authenticated**, then test Up/Down/Left/Right, OK, Back and Home. Test playback buttons inside an app that supports them.
5. Tap Disconnect, then Connect Shield: it should reconnect without another code. Repeat after closing and reopening the app.
6. Briefly disconnect Shield's network and restore it. Zeus continues retrying with backoff capped at 30 seconds while its controller is alive. Returning to the foreground refreshes a stale session. Explicit Disconnect, Forget, PIN pairing or a changed/unverified certificate stops automatic recovery. Cancelling or changing the Shield IP closes its active sockets.

Power always restores a fresh authenticated Shield session and retries explicit Wake/Sleep up to three times, waiting for the remote protocol's power status. A failed confirmation is shown on the remote. Android may suspend or kill the phone app, so this is recovery on use rather than a promise of a permanent background connection. Shield must remain powered and reachable on the network. Long-standby reliability still needs an owner test after installation.
7. Test an incorrect code, cancellation, and **Forget Shield** followed by pairing again. Forget removes this phone's per-IP identity and peer pin; it does not manage other clients on Shield.
8. Verify Samsung volume, mute and HDMI 1 still work. Report the exact Shield status message for failures; do not share keys or credential files.

The phone's per-device RSA private key stays in Android Keystore. Successful code verification and the Shield's secret acknowledgement are required before its certificate fingerprint is pinned. Control connections reject changed certificates. The initial pairing connection accepts a self-signed RSA peer only at the configured private IPv4 endpoint; the on-screen code binds the certificate keys. It does not mark an open TCP socket as authenticated control. No pairing PIN is stored or logged. Peer pins are stored in app-private, non-backup files; app backup is disabled because Keystore identities do not transfer to a new phone. If Shield changes IP, configure the new address and pair it again.

Implementation is separated into `ShieldPairingClient`, `ShieldRemoteClient`, `ShieldCredentials`, a bounded protobuf transport, `ShieldController` and `ShieldControls`. Socket operations, credential creation and persistence run off the UI thread. The screen owns the connection lifetime and closes it when disposed. Recreating the activity automatically restores existing Shield pairing; Samsung reconnects to its configured private IP, using its saved token when available; approve authorization on the TV if requested. Menu shows both connection states and provides Reconnect devices for manual recovery. New devices still require pairing in Settings. Android TV protocol sources and license attribution are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Source organization

Physical Volume Up/Down keys now control Samsung while the Remote screen is focused and Samsung is authorized. The on-screen volume rail is removed from Remote and the layout picker. Connect Samsung in Menu → Settings, return to Remote, then test Volume Up/Down and hold/release. Repeats use a minimum 120 ms spacing; key releases do not send extra commands. Outside Remote, while editing, when Samsung is unavailable, or when Zeus is not focused, the keys retain normal phone behavior. A held press stays with its initial target until released. Hardware behavior still needs confirmation on the phone/TV.

The remote interface uses rounded controls with dark and light appearances. The main Remote screen has Shield navigation, Home, Back, TV power and five favorites. ZEUS and ONE ROOM. ONE REMOTE. are restored at the top left. Mute, the device tile/selector, Setup, HDMI 1, Source, the Shield playback rail and Play/Pause are removed from the main remote and layout picker. Samsung fallback controls and the tested HDMI macro remain in Settings.

Haptic clicks are enabled by default for remote controls, app actions and navigation tabs. Settings has a saved Haptic feedback switch. Feedback respects Android's system haptics setting and does not run for disabled controls.

### Remote layout editor

Remote now uses the full available screen, without the former header, fixed canvas height, side padding or bottom tab strip. System status/gesture areas are still protected. Open the draggable **Menu** handle for Apps, Settings, theme and **Edit layout**. Drag controls to move them, then tap a control to open its Size and Horizontal/Vertical position sliders. The same dialog has a picker for covered controls. Drag Menu elsewhere if it covers a control. ZEUS/tagline, Power, D-pad, Home, Back and each of the five favorite tiles can be positioned separately. Older layouts retain all other positions; a top-left Power button moves to the top right to make room for the restored branding. Retired control IDs are kept only for saved-layout compatibility and are never rendered. Multi-button groups resize together and retain their native minimum touch size; smaller standalone controls retain a 48dp minimum target.

**Menu → Save layout** applies the layout and stores it with the existing device preferences. **Menu → Cancel changes** (or Android Back) restores the saved layout. **Menu → Reset to default** previews the default positions/sizes; save to keep the reset. Draft edits survive activity recreation, but only Save writes them to permanent settings. Positions use fractions of the actual remaining screen width and height so resized controls stay on screen across display sizes and can reach all four usable edges. Overlapping controls are allowed: the picker lets you select covered items, and the movable Menu keeps reset/save actions accessible. Tab switching is disabled while editing, and every control callback is guarded so editing cannot send TV/Shield commands or launch apps.

Validation: unit tests cover saved-layout round trips, corrupt/partial settings, bounds and minimum touch sizes across screen widths, drag clamping and separation of saved state from drafts. On the phone, verify drag, resize, Save/reopen, Cancel, Reset/Save and that the TV does not react to editing gestures. Existing device pairing and favorite assignments are stored separately and are unchanged.

Apps now supports a searchable installed-app catalog, app launching and assignment of the five favorite slots. This requires [Zeus Shield Companion](shield-companion/README.md) on Shield: start it, scan its TV QR from phone Apps, enable app-launch permission on Shield and refresh. Apps without launcher activities are excluded. A disconnected companion shows an honest setup/error state instead of a fabricated installed-app list. Companion hardware behavior still needs validation.

Device controllers stay above the tabs, so switching tabs does not disconnect pairing/control. Existing device settings and HDMI timings are preserved. `app/` is the phone app, `shield-companion/` is the separate TV app, and `shared/` contains QR protocol validation and encrypted credential storage. App-catalog client/state code lives in `shield/apps/`; its UI is `shield/ui/ShieldAppsScreen.kt`.

For side-by-side USB testing when an installed APK uses a different signing key, build with
`gradle :app:assembleDebug -PlocalTestInstall=true`. This produces **Zeus Local Test**
(`com.zeus.remote.localtest`) with separate settings and pairing identities; the normal Zeus app is preserved.
Without this flag, the normal application ID and label are unchanged.

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

Device IP fields retain only the first line of pasted text. Existing multiline IP values are repaired on startup so pasted connection-error paragraphs cannot prevent authorization. This does not clear device tokens, Shield credentials or saved layouts.
