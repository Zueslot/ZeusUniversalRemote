# Android TV Remote protocol

`app/src/main/proto/polo.proto` and `remotemessage.proto` were obtained from
https://github.com/tronikos/androidtvremote2/tree/main/src/androidtvremote2
on 2026-10-28. Java namespace options were adjusted for Zeus; protocol fields are unchanged.
The pairing schema carries Google's 2009 copyright notice and originates in
AOSP google-tv-pairing-protocol. The remote schema credits louis49/androidtv-remote
and AOSP keycodes. Original attribution comments are retained.

The reference implementation is Apache-2.0; its license is distributed in
`app/src/main/assets/licenses/androidtvremote2.txt` inside the APK.
Shield pairing and remote negotiation were implemented using that reference:
https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/pairing.py
https://github.com/tronikos/androidtvremote2/blob/main/src/androidtvremote2/remote.py
