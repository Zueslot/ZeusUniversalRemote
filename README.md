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
- The Source button sends `KEY_SOURCE` to open the TV's input/source interface if supported. Select HDMI 1 manually there; automatic HDMI 1 switching is not implemented.
- Power-on from standby is not implemented; Wake-on-LAN/CEC would need additional work.
- NVIDIA Shield Remote v2 TLS pairing/control is **not implemented**. Shield navigation and app launch buttons are intentionally disabled.
- This source has not been compiled or tested against the physical devices here. Run GitHub Actions to verify compilation.

## Install
Build and install the APK, connect Samsung, and press Source. Use the Samsung navigation buttons to highlight HDMI 1 (possibly named Shield) and press OK. If Source does nothing, press TV Home and navigate to Connected Devices. Report which route worked, the displayed device name, and the exact button sequence. Check volume and mute still work.

These controls navigate the Samsung TV; they are not Shield Remote v2 controls. A queued key does not confirm execution. Automatic input selection and HDMI-CEC coordination remain unimplemented pending device testing.

Upload the contents of this ZIP into the GitHub repository preserving directory structure, then run the existing GitHub Actions `Build Android APK` workflow.
