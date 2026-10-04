# ShareBox

Wi-Fi file transfer app for Android — like SHAREit, but tiny, ad-free, and built on the plain
Android framework (no AndroidX/Compose, one dependency: zxing for QR codes).

## Features

- **Browser access (PC / any phone)** — embedded HTTP server + full web file manager:
  browse, download, upload (drag & drop), rename, delete, new folder, zip download,
  image/video/audio/PDF preview, HTTP Range (video seeking works).
  Browser uploads land in **the folder you are viewing** (Root by default).
- **Phone-to-phone** — nearby discovery over UDP broadcast; pick a device, tap files, done.
  Files sent from another phone land in the `Inbox` folder (Inbox is for phone-to-phone
  receives only, not for browser uploads).
- **Hotspot mode** — no shared Wi-Fi? Start a local-only hotspot and share the URL/QR.
- **QR code** — scan to open the web UI instantly.
- **Files tab** — category view (Images, Videos, Audio, Documents, Apps, Downloads),
  folder browsing, multi-select send/delete, share installed APKs.
- **Transfers tab** — live progress for uploads/downloads/sends.
- **Foreground service** — server keeps running with a notification; Wi-Fi lock keeps
  transfers alive with the screen off.
- **Storage modes** — app folder (no permissions) or full `/sdcard` with "All files access".

## Build

```sh
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

Release builds are minified + resource-shrunk. Signing uses the local self-signed key
(`keystore/sharebox.keystore`, password in `gradle.properties` — change it for real use).

## Web API

| Endpoint | Description |
|---|---|
| `GET /` | Web file manager UI |
| `GET /api/list?path=` | JSON directory listing |
| `GET /api/find?cat=&q=&limit=` | Search / category scan (images, videos, music, documents, archives, others, recent) |
| `GET /api/download?path=[&inline=1]` | Stream file (Range supported, kernel sendfile) |
| `GET /api/zip?path=` | Stream a folder as zip |
| `PUT /api/upload?path=&name=` | Raw-body upload (streamed) |
| `POST /api/mkdir` / `rename` / `delete` | JSON file management |
| `POST /api/pause` / `resume` / `cancel` | Control a running transfer by id (`{"id":123}`) |
| `GET /api/progress` | Live transfers (name, %, speed, ETA, folder) — powers the web progress panel |
| `GET /api/qr?text=&size=` | PNG QR code (used by the web sidebar) |
| `GET /api/peers` | Nearby ShareBox devices (UDP discovery) |
| `GET /api/info` | Device / storage info |
| `GET /api/browse` | Shortcut folders |

## Notes

- Default port `2999` (same as SHAREit's WebShare, configurable in Settings).
- Discovery uses UDP port `47777` (`{"app":"sharebox",...}` hello packets).
- No auth on the HTTP server — anyone on the same network can browse. Keep it on trusted networks.
- Android 15+ may stop the `dataSync` foreground service after ~6h; restart the server if needed.
