# Product

<!-- impeccable:product-schema 1 -->

## Platform

adaptive

## Users

- **Primary:** one technically comfortable owner running the app on their own Android phone, moving files between that phone and a PC or another phone on the same Wi-Fi (or the phone's own hotspot). The job is an occasional bulk transfer — photos, videos, APKs, single large files — that must not touch the internet, an account, or a cable.
- **Secondary:** a PC user with nothing installed who needs only a browser tab; and trusted friends, family, or colleagues who sideload the APK to send to, or receive from, the primary device.

## Product Purpose

Turn an Android phone into a local file hub: an embedded HTTP server with a browser file manager on one side, app-to-app nearby sending on the other. Success is a multi-GB transfer that starts within seconds of opening the app, needs no setup on the other device, and keeps running with the screen off.

## Positioning

No cloud, no account, no tracking, no ads, and a deliberately tiny binary (~190 KB). The mechanism is the position: the phone *is* the server, every other device is a browser or a second install, and the transport is the local network only. A cloud transfer product cannot truthfully claim offline operation; a bloated local-transfer app cannot truthfully claim an ad-free tiny APK.

## Operating Context

- Both devices share a Wi-Fi network, or the visiting device joins the phone's local-only hotspot when no shared network exists.
- The intended primary flow is the PC: open the shown URL (or scan the QR) and use the web file manager — browse, download, upload, rename, delete, zip, preview.
- Phone-to-phone reuses the same HTTP endpoint for receiving: the sender PUTs files into the receiver's `Inbox`.
- Transfers are long-running and expected to survive backgrounding: the server runs in a foreground service with a Wi-Fi lock and a notification carrying the current URL.
- Verification is done by using the running app: the server is reachable from the build machine over `adb forward`, and the web UI is driven in a real browser at desktop and phone sizes, light and dark.

## Capabilities and Constraints

**Confirmed functionality:** embedded HTTP server (default port 2999) with endpoints for directory listing, category search, ranged download (kernel sendfile), folder zip, raw upload, mkdir/rename/delete, live transfer progress, pause/resume/cancel, QR generation, device info, and nearby peers; vanilla web UI in `assets/web/` (no build step, no CDN) with file browser, search, categories, upload with drag & drop, preview with navigation and zoom, transfer panel, dark mode, mobile layout; UDP-broadcast peer discovery (port 47777) with HTTP transfer into a peer's `Inbox`; local-only hotspot mode; foreground service (`dataSync`) with Wi-Fi lock; QR + URL display; MediaStore-based categories with a filesystem scan as a complement.

**Technical constraints and facts:**

- Kotlin on the plain Android framework — no AndroidX, no Compose; one runtime dependency (zxing core, QR encoding only); minSdk 26, targetSdk 36; release build with R8 and resource shrinking.
- The web UI cannot open a local folder on the PC or on the visiting phone (browser sandbox). Any "open folder" affordance can only apply to the phone that hosts the server.
- The HTTP server has no authentication: anyone on the same network can browse and write. Trusted-network assumption.
- Wi-Fi Direct is not implemented; discovery depends on a shared network or the hotspot.

**Stated but not started — the biggest open risk:**

- Distribution intent is a **public release / Play Store**, which the current storage model conflicts with. The shipped build requests All Files Access (`MANAGE_EXTERNAL_STORAGE`), a permission Google Play restricts to narrow app categories; a public release needs a Storage Access Framework or MediaStore-based model (or app-folder-only mode) first. No Play-specific work has begun.
- No release signing identity, privacy policy, data-safety declaration, or store listing assets exist yet.
- No PIN/authentication for the HTTP server.

## Brand Commitments

- **Name:** ShareBox.
- **Icon:** the owner's own artwork, kept in `assets/icon-sharebox-v1.jpg`, `icon-sharebox-v2.png`, `icon-sharebox-v3.png`; v3 (transparent) is the one in use as launcher icon, favicon, and sidebar mark.
- **Voice:** English for all UI and documentation copy; Indonesian is used only in conversation.
- **Palette anchor:** the icon's blue→violet identity is carried into the app and the web UI.
- **Binding stance, not a current state:** no ads, no trackers, no analytics, no third-party SDKs beyond zxing.

## Evidence on Hand

- A working app and web UI, verified by use rather than by assertion: server reachable over `adb forward`, web UI driven in a real browser (desktop 1440 and phone 412, light and dark), transfers, preview, selection, and file operations all exercised.
- `README.md` — features, build command, web API table, known limits.
- Release artifact at `app/build/outputs/apk/release/app-release.apk`; self-signed keystore in `keystore/`.
- **Absences future work must not fabricate:** no automated tests, no CI, no analytics or usage data, no testimonials, no press, no store listing.

## Product Principles

1. **Local by default.** Nothing leaves the LAN; the phone serves rather than being a client of someone else's service.
2. **One tap plus a URL.** If a flow needs an account, a cable, or an install on the other side, it is the wrong flow.
3. **The binary stays tiny.** Every dependency and asset is weighed against a ~190 KB budget.
4. **Long transfers are the normal case.** Resilient, resumable, pausable, and observable in real numbers (speed, ETA).
5. **Honest affordances.** Never show a control that cannot work without saying why (e.g. "open folder" on the web).

## Accessibility & Inclusion

No product-specific accessibility requirement was established by the user. The polish pass set WCAG AA text contrast (4.5:1 body, 3:1 large) as the working floor, with visible keyboard focus, themed browser surfaces (selection, caret, scrollbars), and ≥38 px touch targets on phone layouts.
