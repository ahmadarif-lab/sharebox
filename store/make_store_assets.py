#!/usr/bin/env python3
"""Susun aset Play Store: ikon 512, feature graphic 1024x500, screenshot promosi 1080x1920,
dan satu gambar hero landscape.

Bahan: store/gen/*.png (latar dari Codex, tanpa teks), store/screens/*.png (tangkapan app
sungguhan dengan data dari make_demo_files.py), docs/icon-master.webp. Semua teks dirender
di sini dengan Inter supaya ejaannya pasti benar.

  python3 store/make_store_assets.py        # hasil di store/play/
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
STORE = ROOT / "store"
GEN = STORE / "gen"
SHOTS = STORE / "screens"
OUT = STORE / "play"
FONTS = STORE / "fonts"
OUT.mkdir(exist_ok=True)

WHITE = (255, 255, 255)
SOFT = (226, 232, 255)
BEZEL = (17, 22, 38)


def font(weight, size):
    return ImageFont.truetype(str(FONTS / f"Inter-{weight}.ttf"), size)


def cover(im, w, h, fx=0.5, fy=0.5):
    s = max(w / im.width, h / im.height)
    im = im.resize((round(im.width * s), round(im.height * s)), Image.LANCZOS)
    x, y = int((im.width - w) * fx), int((im.height - h) * fy)
    return im.crop((x, y, x + w, y + h))


def rounded(im, radius):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), radius, fill=255)
    out = im.convert("RGBA")
    out.putalpha(m)
    return out


def drop_shadow(canvas, box, radius, blur=30, alpha=90, dy=18):
    x0, y0, x1, y1 = box
    pad = blur * 3
    s = Image.new("RGBA", (x1 - x0 + pad * 2, y1 - y0 + pad * 2), (0, 0, 0, 0))
    ImageDraw.Draw(s).rounded_rectangle((pad, pad, pad + x1 - x0, pad + y1 - y0), radius, fill=(8, 10, 40, alpha))
    s = s.filter(ImageFilter.GaussianBlur(blur))
    canvas.alpha_composite(s, (x0 - pad, y0 - pad + dy))


def phone(shot_path, height):
    """Tangkapan layar di dalam bingkai ponsel sederhana."""
    shot = Image.open(shot_path).convert("RGB")
    bez = max(10, height // 70)
    inner_h = height - bez * 2
    inner_w = round(shot.width * inner_h / shot.height)
    shot = shot.resize((inner_w, inner_h), Image.LANCZOS)
    r = round(inner_w * 0.11)
    frame = rounded(Image.new("RGB", (inner_w + bez * 2, height), BEZEL), r + bez)
    frame.alpha_composite(rounded(shot, r), (bez, bez))
    return frame


def browser(shot_path, width):
    """Tangkapan web UI di dalam jendela browser dengan tiga titik."""
    shot = Image.open(shot_path).convert("RGB")
    bar = round(width * 0.035)
    h = round(shot.height * width / shot.width)
    shot = shot.resize((width, h), Image.LANCZOS)
    win = Image.new("RGB", (width, h + bar), (236, 239, 247))
    d = ImageDraw.Draw(win)
    for i, c in enumerate(((255, 95, 87), (254, 188, 46), (40, 200, 64))):
        cx, cy, rr = bar * (0.8 + i * 0.7), bar / 2, bar * 0.2
        d.ellipse((cx - rr, cy - rr, cx + rr, cy + rr), fill=c)
    win.paste(shot, (0, bar))
    return rounded(win, round(width * 0.018))


def text_block(d, x, y, lines, max_w, anchor_center=False):
    """lines: [(teks, weight, size, warna, jarak_sesudah)] — font diperkecil kalau terlalu lebar."""
    for txt, weight, size, col, gap in lines:
        f = font(weight, size)
        while d.textlength(txt, font=f) > max_w and size > 20:
            size -= 2
            f = font(weight, size)
        if anchor_center:
            d.text((x, y), txt, font=f, fill=col, anchor="mt")
        else:
            d.text((x, y), txt, font=f, fill=col)
        y += size + gap
    return y


# ---------- ikon ----------

def icon():
    im = Image.open(ROOT / "docs" / "icon-master.webp").convert("RGBA").resize((512, 512), Image.LANCZOS)
    im.save(OUT / "icon_512.png", optimize=True)
    print("icon_512.png", im.size)


# ---------- feature graphic ----------

def feature_graphic():
    W, H = 1024, 500
    bg = cover(Image.open(GEN / "feature_bg.png").convert("RGB"), W, H).convert("RGBA")
    d = ImageDraw.Draw(bg)
    ic = Image.open(ROOT / "docs" / "icon-master.webp").convert("RGBA").resize((92, 92), Image.LANCZOS)
    x = 64
    drop_shadow(bg, (x, 108, x + 92, 200), 22, blur=12, alpha=70, dy=8)
    bg.alpha_composite(ic, (x, 108))
    text_block(d, x, 222, [
        ("ShareBox", "ExtraBold", 76, WHITE, 18),
        ("Share files over Wi-Fi", "Bold", 34, WHITE, 12),
        ("Phone to phone or any browser.", "Medium", 24, SOFT, 6),
        ("No internet, no cables.", "Medium", 24, SOFT, 0),
    ], 470)
    bg.convert("RGB").save(OUT / "feature_graphic_1024x500.png", optimize=True)
    print("feature_graphic_1024x500.png", bg.size)


# ---------- screenshot promosi ----------

SCREENS = [
    # nama keluaran, tangkapan, judul, subjudul
    ("01_share", "01_home.png", "Share files over Wi-Fi", "Fast and private. No internet needed."),
    ("02_receive", "06_receive.png", "Scan once, receive instantly", "Phone to phone with a single QR code"),
    ("03_web", None, "Open your phone on any PC", "A password-protected file manager in the browser"),
    ("04_files", "03_files.png", "All your files in one place", "Photos, videos, music and documents"),
    ("05_transfers", "04_transfers.png", "Every transfer, live", "See progress, pause, resume or cancel"),
]


def promo_screens():
    W, H = 1080, 1920
    src_bg = Image.open(GEN / "promo_bg.png").convert("RGB")
    for name, shot, title, sub in SCREENS:
        bg = cover(src_bg, W, H).convert("RGBA")
        d = ImageDraw.Draw(bg)
        y = text_block(d, W // 2, 118, [
            (title, "ExtraBold", 72, WHITE, 26),
            (sub, "Medium", 38, SOFT, 0),
        ], W - 120, anchor_center=True)
        top = y + 70
        if shot:
            ph = phone(SHOTS / shot, H - top + 260)        # sengaja terpotong di bawah
            px = (W - ph.width) // 2
            drop_shadow(bg, (px, top, px + ph.width, top + ph.height), 70)
            bg.alpha_composite(ph, (px, top))
        else:
            # Jendela browser dipangkas ke kolom file (panel QR di kanan tidak muat).
            web = Image.open(SHOTS / "web_files.png")
            crop = SHOTS / "web_files_crop.png"
            web.crop((0, 0, round(web.width * 0.75), web.height)).save(crop)
            win = browser(crop, 1500)                      # terpotong di kanan
            wx, wy = 60, top + 10
            drop_shadow(bg, (wx, wy, wx + win.width, wy + win.height), 24)
            bg.alpha_composite(win, (wx, wy))
            ph = phone(SHOTS / "02_web.png", 1060)
            px, py = W - ph.width - 50, H - 720            # baris password harus kelihatan
            drop_shadow(bg, (px, py, px + ph.width, py + ph.height), 70)
            bg.alpha_composite(ph, (px, py))
        bg.convert("RGB").save(OUT / f"phone_{name}.png", optimize=True)
        print(f"phone_{name}.png", bg.size)


# ---------- hero landscape (README / promosi) ----------

def hero():
    W, H = 2000, 1100
    bg = cover(Image.open(GEN / "feature_bg.png").convert("RGB"), W, H, 0.0, 0.5).convert("RGBA")
    d = ImageDraw.Draw(bg)
    text_block(d, 110, 80, [
        ("ShareBox", "ExtraBold", 120, WHITE, 26),
        ("Share files over Wi-Fi: fast, private, offline.", "Bold", 46, WHITE, 16),
        ("Phone to phone with a QR scan, or any PC in the browser.", "Medium", 36, SOFT, 0),
    ], 1000)
    win = browser(SHOTS / "web_files.png", 1060)
    wx, wy = 90, 380
    drop_shadow(bg, (wx, wy, wx + win.width, wy + win.height), 24)
    bg.alpha_composite(win, (wx, wy))
    for shot, x, y, h in (("06_receive.png", 1130, 110, 900), ("03_files.png", 1580, 250, 820)):
        ph = phone(SHOTS / shot, h)
        drop_shadow(bg, (x, y, x + ph.width, y + ph.height), 60)
        bg.alpha_composite(ph, (x, y))
    bg.convert("RGB").save(OUT / "hero_2000x1100.png", optimize=True)
    print("hero_2000x1100.png", bg.size)


if __name__ == "__main__":
    icon()
    feature_graphic()
    promo_screens()
    hero()
