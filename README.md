# ShareBox

Wi-Fi file transfer app for Android — like SHAREit, but tiny, ad-free, and built on the plain
Android framework (no AndroidX/Compose, one dependency: zxing for QR codes).

## Features

- **Browser access (PC / any phone)** — embedded HTTP server + full web file manager:
  browse, download, upload (drag & drop), rename, delete, new folder, zip download,
  image/video/audio/PDF preview, HTTP Range (video seeking works).
  Browser uploads land in **the folder you are viewing** (Root by default).
- **Phone-to-phone (Send / Receive)** — Share Me style, over its own protocol (see below),
  separate from the web server. **Receive** turns on a local-only hotspot and shows a QR;
  **Send** (pick files first, then Send) opens the camera, scans the QR, joins the hotspot
  and sends directly. On a shared Wi-Fi the QR carries the LAN address instead, and devices on
  the same network can also be picked from a list (UDP broadcast discovery, not physical proximity). Received files land in the `Inbox` folder.
- **Web server mode** — HTTP file manager for a PC/browser, started on its own; it does not
  accept phone-to-phone transfers and the Direct port stays closed while only the web server runs.
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

## Direct protocol (SBX1) — phone to phone

TCP port `47778`, only open while **Receive** is on. Framed: a 4-byte magic `SBX1`, JSON frames
(`writeUTF`) and raw file bytes.

```
C → S  MAGIC, {"id","name","key"?,"token"?}
S → C  {"ok":true,"token","name"} | {"ok":false,"error":"declined"|"timeout"}
C → S  {"name","size"} + <size bytes>        (repeated per file)
S → C  {"ok":true,"name":<saved name>}
C → S  {"end":true}
```

The receiver's QR is `sharebox://join?id=&n=&h=<host>&p=<port>&k=<session key>[&s=<ssid>&w=<pass>]`.
A sender that presents the session key (only visible on the receiver's screen) is accepted
without a prompt; anyone else (e.g. picked from the nearby list) needs an Approve tap on the
receiver. Files are written to `Inbox`.

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

- Port `2999` (same as SHAREit's WebShare), fixed and not configurable.
- Discovery uses UDP port `47777` (`{"app":"sharebox",...}` hello packets; announces the Direct port).
- No auth on the HTTP server — anyone on the same network can browse. Keep it on trusted networks.
- Android 15+ may stop the `dataSync` foreground service after ~6h; restart the server if needed.
