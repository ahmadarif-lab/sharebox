# Rilis Google Play — ShareBox

Paket `id.ahmadarif.sharebox`, versi **1.0.0 (code 2)**. AAB: `app/build/outputs/bundle/release/app-release.aab`
(`./gradlew :app:bundleRelease`, ditandatangani upload key `CN=ShareBox, O=ahmadarif-lab`,
SHA-256 `D8:BB:61:40:B4:C4:89:7E:E3:95:96:50:10:32:E7:B2:44:6B:4E:CB:4C:4A:9B:60:85:58:D9:65:37:14:80:77`).

Semua URL publik memakai `ahmadarif.id`:

| Untuk | URL |
|---|---|
| Kebijakan privasi | https://apps.ahmadarif.id/sharebox/privacy/ |
| Syarat & ketentuan | https://apps.ahmadarif.id/sharebox/terms/ |
| Situs | https://apps.ahmadarif.id/sharebox/ |
| Email kontak | ahmad.arif019@gmail.com |

## Aset grafis (`store/play/`)

Dibangun ulang dengan `python3 store/make_store_assets.py`. Latar dibuat Codex (`store/gen/`, prompt di
`p_*.txt`); screenshot app asli dari `store/screens/` dengan data demo `store/make_demo_files.py`.

| Slot Play | Berkas |
|---|---|
| App icon (512×512) | `icon_512.png` |
| Feature graphic (1024×500) | `feature_graphic_1024x500.png` |
| Phone screenshots (urut) | `phone_01_share.png` … `phone_05_transfers.png` |
| Opsional (README / promosi) | `hero_2000x1100.png` |

## Listing — en-US (default)

**App name** (≤30): `ShareBox: Wi-Fi File Transfer`

**Short description** (≤80):
`Send files phone to phone with a QR scan, or open your phone in any PC browser.`

**Full description** (≤4000):

```
ShareBox moves photos, videos, music, documents and apps over Wi-Fi. No internet, no account, no cable.

PHONE TO PHONE
• Tap Receive on one phone and it shows a QR code.
• Tap Send on the other, pick files and scan the code. The transfer starts right away.
• No shared Wi-Fi? Receive can start a local hotspot, and the QR lets the sender join it.

ANY PC OR BROWSER
• Turn on the Web server and open the address on a PC, laptop or tablet on the same network.
• Browse folders, download, upload with drag and drop, rename, delete, and download a folder as a zip.
• The page is protected by a password shown only in the app. Change it with one tap to sign out every
  browser.

A FILE MANAGER THAT TRAVELS
• Files tab with photo and video thumbnails, categories and search.
• Built-in viewer: pinch to zoom, swipe between photos, play videos.
• Send installed apps as APK files.
• Live transfer list with speed and progress; pause, resume or cancel.

PRIVATE BY DESIGN
• Files go straight between your devices. They never pass through our servers.
• No ads, no trackers, no analytics, no account. The app never contacts the internet.
• About 230 KB download.

Good to know
• The web server uses plain HTTP on your local network. Use it on networks you trust and turn it off
  when you are done.
• ShareBox asks for "All files access" so it can list, send and receive any file on your phone. Without
  it, ShareBox works inside its own folder.
```

## Listing — id-ID

**Nama** (≤30): `ShareBox: Kirim File Wi-Fi`

**Deskripsi singkat** (≤80):
`Kirim file antar HP cukup scan QR, atau buka HP-mu dari browser PC mana pun.`

**Deskripsi lengkap**:

```
ShareBox memindahkan foto, video, musik, dokumen, dan aplikasi lewat Wi-Fi. Tanpa internet, tanpa akun, tanpa kabel.

DARI HP KE HP
• Ketuk Receive di satu HP, lalu muncul kode QR.
• Ketuk Send di HP lain, pilih file, dan pindai kodenya. Pengiriman langsung dimulai.
• Tidak ada Wi-Fi bersama? Receive bisa menyalakan hotspot lokal, dan QR-nya membuat pengirim langsung
  tersambung.

DARI PC ATAU BROWSER MANA PUN
• Nyalakan Web server, lalu buka alamatnya di PC, laptop, atau tablet di jaringan yang sama.
• Jelajahi folder, unduh, unggah dengan seret dan lepas, ganti nama, hapus, dan unduh folder sebagai zip.
• Halamannya dilindungi kata sandi yang hanya tampil di app. Ganti dengan satu ketukan untuk
  mengeluarkan semua browser.

PENGELOLA FILE SEKALIGUS
• Tab Files dengan thumbnail foto dan video, kategori, dan pencarian.
• Penampil bawaan: cubit untuk zoom, geser antar-foto, putar video.
• Kirim aplikasi terpasang sebagai file APK.
• Daftar transfer langsung dengan kecepatan dan progres; jeda, lanjutkan, atau batalkan.

PRIVASI SEJAK AWAL
• File berpindah langsung antar perangkatmu, tidak lewat server kami.
• Tanpa iklan, pelacak, analitik, atau akun. Aplikasi tidak pernah menghubungi internet.
• Ukuran unduhan sekitar 230 KB.

Perlu diketahui
• Server web memakai HTTP biasa di jaringan lokal. Gunakan di jaringan yang kamu percaya dan matikan
  setelah selesai.
• ShareBox meminta "Akses semua file" agar bisa menampilkan, mengirim, dan menerima file apa pun di HP.
  Tanpa izin itu, ShareBox bekerja di dalam foldernya sendiri.
```

**Kategori:** Tools. **Tag:** File transfer, File manager.

## Data safety

- Apakah app mengumpulkan atau membagikan data pengguna? **Tidak.**
  Alasan: tidak ada server, SDK, atau analitik; app tidak menghubungi internet. File hanya berpindah
  antar perangkat milik pengguna di jaringan lokal, atas perintah pengguna sendiri.
- Data dienkripsi saat transit: tidak berlaku (tidak ada data yang dikumpulkan).
- Pengguna bisa meminta penghapusan data: tidak berlaku.

## App content (lainnya)

| Bagian | Jawaban |
|---|---|
| Privacy policy | https://apps.ahmadarif.id/sharebox/privacy/ |
| Ads | Tidak ada iklan |
| App access | Semua fitur bisa dipakai tanpa login atau akses khusus. Kata sandi server web dibuat dan ditampilkan di app sendiri |
| Content rating | Kuesioner IARC: kategori Utility/Productivity, jawab "Tidak" untuk semua konten sensitif → Everyone / 3+ |
| Target audience | 13+ (bukan untuk anak) |
| News app | Tidak |
| Government app | Tidak |
| Financial features | Tidak ada |
| Health | Tidak |

## Deklarasi izin (Policy → App content)

### All files access (`MANAGE_EXTERNAL_STORAGE`)

Kategori inti: **File manager**.

> ShareBox is a file manager and Wi-Fi file transfer app. Its core features are browsing, renaming,
> deleting and sending any file in shared storage, receiving files into any folder chosen by the user,
> and serving the phone's storage to a PC browser through its built-in web file manager. Users pick
> arbitrary files and folders (documents, archives, APKs, media in any folder), which the Storage Access
> Framework and MediaStore cannot cover as a whole-device file browser. Without the permission the app
> falls back to its own app-specific folder.

Video demo (YouTube unlisted, ±30 detik): buka Files → jelajahi folder Download/Documents → ganti nama
→ kirim ke HP lain → buka web server di PC dan unduh file dari folder acak.

### Install unknown apps (`REQUEST_INSTALL_PACKAGES`)

Kategori: **File sharing, transfer or management**.

> Users can send and receive APK files between their own devices. After an APK arrives, ShareBox lets
> the user open it in the system package installer. The app never downloads or installs APKs on its own.

### Foreground service (`FOREGROUND_SERVICE_DATA_SYNC`)

> The foreground service keeps a user-started transfer or local server running while the screen is
> off: phone-to-phone receiving and the local web server. It starts only when the user taps Receive or
> Web server, shows a persistent notification with a Stop action, and ends when the user stops it.
> Interruption would cut large file transfers.

Video: nyalakan Receive → kunci layar → kirim file besar dari HP lain → notifikasi tetap tampil dan
transfer selesai.

## Langkah di Play Console

1. **Buat app** (manual, API tidak bisa): All apps → Create app → nama `ShareBox: Wi-Fi File Transfer`,
   bahasa default English (United States), App, Free, centang deklarasi.
2. Setelah app ada, sisanya bisa lewat MCP `play-console`: listing EN/ID, ikon, feature graphic,
   screenshot, dan unggah AAB ke track internal → production.
3. Isi manual di Console: App content (tabel di atas), Data safety, deklarasi izin + video, harga &
   negara.
4. Akun developer personal baru wajib **closed testing 12 tester selama 14 hari** sebelum production.
   Cek status akun di Console; Haid Tracker/Archili bisa jadi acuan.
