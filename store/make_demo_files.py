#!/usr/bin/env python3
"""Buat file demo untuk screenshot Play Store: ilustrasi pemandangan (JPG), video pendek,
lagu, dan dokumen. Semua sintetis, jadi tidak ada file pribadi yang ikut terfoto.

  python3 store/make_demo_files.py          # hasil di store/demo/
"""
import math
import random
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent / "demo"
OUT.mkdir(exist_ok=True)

W, H = 1600, 1200

# nama: (langit atas, langit bawah, matahari, lapisan gunung dari jauh ke dekat)
SCENES = {
    "aurora": ((12, 18, 48), (36, 92, 110), (190, 255, 220), [(30, 60, 80), (20, 42, 60), (10, 24, 38)]),
    "beach": ((90, 170, 235), (250, 210, 160), (255, 245, 210), [(70, 150, 190), (40, 120, 170), (240, 205, 150)]),
    "canyon": ((250, 150, 90), (255, 210, 150), (255, 240, 200), [(200, 100, 70), (160, 70, 50), (110, 45, 35)]),
    "city-night": ((25, 20, 60), (90, 50, 120), (255, 220, 240), [(60, 40, 100), (40, 28, 75), (22, 16, 45)]),
    "forest": ((150, 210, 200), (230, 240, 210), (255, 250, 220), [(90, 150, 120), (50, 110, 80), (25, 70, 50)]),
    "mountain": ((80, 130, 200), (190, 215, 240), (255, 255, 240), [(120, 150, 190), (75, 100, 150), (40, 60, 100)]),
    "sunset": ((70, 40, 110), (250, 140, 110), (255, 220, 150), [(120, 60, 110), (80, 40, 90), (45, 22, 60)]),
    "lake": ((120, 190, 230), (220, 240, 250), (255, 255, 235), [(100, 160, 170), (60, 120, 140), (30, 90, 110)]),
}


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scene(name, sky_top, sky_bot, sun, layers, seed):
    rnd = random.Random(seed)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for y in range(H):
        d.line([(0, y), (W, y)], fill=lerp(sky_top, sky_bot, y / H))

    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    sx, sy = rnd.randint(380, 1220), rnd.randint(260, 460)
    ImageDraw.Draw(glow).ellipse((sx - 260, sy - 260, sx + 260, sy + 260), fill=sun + (90,))
    glow = glow.filter(ImageFilter.GaussianBlur(90))
    img.paste(glow, (0, 0), glow)
    d = ImageDraw.Draw(img)
    d.ellipse((sx - 95, sy - 95, sx + 95, sy + 95), fill=sun)

    if name in ("aurora", "city-night"):
        for _ in range(140):
            x, y = rnd.randint(0, W), rnd.randint(0, H // 2)
            r = rnd.choice((1, 1, 2))
            d.ellipse((x - r, y - r, x + r, y + r), fill=(255, 255, 255))

    for i, col in enumerate(layers):
        base = H * (0.52 + i * 0.13)
        amp = 150 - i * 30
        f1, f2 = rnd.uniform(1.2, 2.4), rnd.uniform(3.0, 5.5)
        p1, p2 = rnd.uniform(0, 6.28), rnd.uniform(0, 6.28)
        pts = [(0, H)]
        for x in range(0, W + 1, 8):
            t = x / W
            y = base - amp * (0.6 * math.sin(t * math.pi * f1 + p1) + 0.4 * math.sin(t * math.pi * f2 + p2))
            pts.append((x, y))
        pts.append((W, H))
        d.polygon(pts, fill=col)

    if name == "city-night":
        x = 0
        while x < W:
            bw, bh = rnd.randint(60, 140), rnd.randint(180, 480)
            d.rectangle((x, H - bh, x + bw, H), fill=(18, 14, 38))
            for wy in range(H - bh + 20, H - 20, 34):
                for wx in range(x + 12, x + bw - 16, 26):
                    if rnd.random() < 0.45:
                        d.rectangle((wx, wy, wx + 10, wy + 16), fill=(255, 214, 140))
            x += bw + rnd.randint(4, 18)
    return img


def main():
    for i, (name, (top, bot, sun, layers)) in enumerate(SCENES.items()):
        scene(name, top, bot, sun, layers, seed=i * 7 + 3).save(OUT / f"{name}.jpg", quality=88)

    # Video pendek: geser pelan di atas dua ilustrasi.
    for src, dst in (("beach", "harbor-walk.mp4"), ("mountain", "timelapse.mp4")):
        subprocess.run([
            "ffmpeg", "-y", "-loglevel", "error", "-loop", "1", "-i", str(OUT / f"{src}.jpg"),
            "-vf", "scale=1920:1440,crop=1280:720:'(in_w-out_w)*t/6':360,format=yuv420p",
            "-t", "6", "-r", "30", "-c:v", "libx264", "-crf", "24", str(OUT / dst),
        ], check=True)

    # Lagu: nada lembut 20 detik.
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-f", "lavfi",
        "-i", "sine=frequency=440:duration=20,volume=0.2", "-c:a", "libmp3lame", "-q:a", "6",
        str(OUT / "morning-walk.mp3"),
    ], check=True)

    # Dokumen satu halaman.
    page = Image.new("RGB", (1240, 1754), "white")
    d = ImageDraw.Draw(page)
    d.rectangle((100, 120, 1140, 200), fill=(51, 88, 232))
    for y in range(280, 1600, 46):
        d.rectangle((100, y, 100 + random.Random(y).randint(600, 1040), y + 18), fill=(220, 226, 240))
    page.save(OUT / "trip-itinerary.pdf")

    print("\n".join(sorted(p.name for p in OUT.iterdir())))


if __name__ == "__main__":
    main()
