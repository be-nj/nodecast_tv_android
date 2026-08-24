# NodeCast (native)

A native Kotlin app for Google TV / Android TV that works like a Chromecast:
the TV itself has (almost) no controls — you pair your phone by scanning a QR
code and control everything from a web remote in your phone's browser. No app
install on the phone needed.

## How it works

```
┌──────────── TV ────────────┐         ┌──────── Phone ────────┐
│ ExoPlayer (HLS/IPTV)       │  HTTP   │ Browser               │
│ Embedded web server :8765  │ ◄────── │  remote web app       │
│  • serves the remote UI    │   WS    │  (served by the TV)   │
│  • WebSocket control       │ ◄─────► │  play/pause/volume/…  │
│ QR code with pairing token │         │  channel list (M3U)   │
└────────────────────────────┘         └───────────────────────┘
```

1. The TV app shows a QR code (URL + 128-bit pairing token) and a 4-digit
   fallback code for manual pairing (rate-limited against guessing).
2. Scanning the code opens the remote in the phone browser; it connects over
   WebSocket on the local network.
3. From the phone you load an M3U playlist (fetched and parsed on the TV,
   avoiding CORS), pick channels, play/pause, switch channels, stop, and
   control the real TV volume.
4. The TV shows only the stream plus a minimal auto-hiding overlay (channel
   name, LIVE badge, state). Per the TV app quality guidelines the physical
   remote still works: D-pad center and play/pause keys toggle playback, and
   a Media3 `MediaSession` enables Google Assistant voice control.

## Building

Requires JDK 17+ and the Android SDK (platform 35).

```
cd native
gradle assembleDebug        # or ./gradlew if you generate a wrapper
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`. Install on the TV via
`adb install` (see the root README for TV sideloading instructions).

## Notes

- minSdk 26 (Android 8) — covers all Google TV and recent Android TV devices.
- The pairing code/token persist across restarts, so a paired phone reconnects
  automatically (the token is stored in the browser's localStorage).
- Streams and the remote UI use plain HTTP on the LAN
  (`usesCleartextTraffic`), as IPTV sources are commonly HTTP.
