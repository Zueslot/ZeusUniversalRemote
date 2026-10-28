# Zeus Universal Remote v0.2.0

This is an Android connectivity and Samsung control prototype, not a complete universal remote.

## What's new
- Samsung S95B secure WebSocket authorization on port 8002 with token persistence
- Samsung volume, mute, power and Source menu key requests
- Explicit opt-in for the TV's self-signed TLS certificate, restricted to a private IPv4 address
- Improved dark UI contrast
- Shield TCP reachability diagnostics retained

## Known limitations
- Samsung TV must be on and approve the remote authorization prompt. Some firmware may reject certain keys (especially power-on).
- HDMI 1 / Shield runs a timed menu sequence (about 7 seconds in faster mode, 32 seconds in slower mode). Start with the Source menu closed and do not use another remote during selection. Manual Source/navigation controls remain available after completion or cancellation.
- Power-on from standby is not implemented; Wake-on-LAN/CEC would need additional work.
- NVIDIA Shield Remote v2 TLS pairing/control is **not implemented**. Shield navigation and app launch buttons are intentionally disabled.
- This source has not been compiled or tested against the physical devices here. Run GitHub Actions to verify compilation.

## Install
Build and install the APK, connect Samsung, and press Source. Use the Samsung navigation buttons to highlight HDMI 1 (possibly named Shield) and press OK. If Source does nothing, press TV Home and navigate to Connected Devices. Report which route worked, the displayed device name, and the exact button sequence. Check volume and mute still work.

These controls navigate the Samsung TV; they are not Shield Remote v2 controls. A queued key does not confirm execution. HDMI-CEC coordination remains unimplemented.

## HDMI 1 shortcut validation
The sequence `Source → Left ×10 → Right ×3 → OK`, with two-second delays, was tested from both TV and HDMI 1 through a desktop WebSocket connection. The owner confirmed HDMI 1 selection in both cases. Repeated Left presses reach the Connected Devices sidebar; three Right presses reach HDMI 1 through SmartThings and TV.

The Android shortcut uses the same key sequence, with progress, cancellation, duplicate-run prevention and connection checks. Other TV key buttons are ignored while it runs to avoid interleaving commands. It depends on this TV's menu layout and does not read back the active input. Firmware or menu changes may require recalibration.

The owner confirmed the original 32-second Android shortcut worked on the phone. Faster mode keeps the two-second initial wait and Source-menu opening delay, uses 100 ms between directions, waits one second before OK, and waits 500 ms afterward (6.7 seconds of planned delays). A desktop WebSocket test completed in 6.9 seconds, and the owner confirmed it selected HDMI 1. The key sequence and ten Left presses are unchanged. Faster mode is enabled by default; its setting is saved and cannot change during a run. Turn it off to restore the tested two-second spacing.

The updated Android timing still needs APK build and phone validation: run it once from TV and once from HDMI 1 with the menu initially closed, verify the Shield picture, test cancellation, and check volume/mute afterward. If it misses inputs, turn off faster selection and retry with the menu closed.

Upload the contents of this ZIP into the GitHub repository preserving directory structure, then run the existing GitHub Actions `Build Android APK` workflow.
