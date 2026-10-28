# Zeus Universal Remote v0.2.0

This is an Android connectivity and Samsung control prototype, not a complete universal remote.

## What's new
- Samsung S95B secure WebSocket authorization on port 8002 with token persistence
- Samsung volume, mute, power and HDMI 1 key requests
- Explicit opt-in for the TV's self-signed TLS certificate, restricted to a private IPv4 address
- Improved dark UI contrast
- Shield TCP reachability diagnostics retained

## Known limitations
- Samsung TV must be on and approve the remote authorization prompt. Some firmware may reject certain keys (especially direct HDMI and power-on).
- Power-on from standby is not implemented; Wake-on-LAN/CEC would need additional work.
- NVIDIA Shield Remote v2 TLS pairing/control is **not implemented**. Shield navigation and app launch buttons are intentionally disabled.
- This source has not been compiled or tested against the physical devices here. Run GitHub Actions to verify compilation.

## Install
Upload the contents of this ZIP into the GitHub repository preserving directory structure, then run the existing GitHub Actions `Build Android APK` workflow.
