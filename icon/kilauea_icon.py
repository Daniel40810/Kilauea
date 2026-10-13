"""Programmsymbol Kīlauea, ganzer Vulkan: flacher Schild mit glühender Caldera und Gasfahne,
Mauna Loa dahinter, Spaltenreihe auf der Ostriftzone, Lava erreicht das Meer mit Dampf.
Goldkante nach dem Maß von heidelberg.ico (Rahmen 6..250, Kante 5 px #ECC064, Radius 27)."""
import math, random, sys, os
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

S = 1024
k = S / 256.0
OUT, IN, R, GW = 6 * k, 250 * k, 27 * k, 5 * k
GOLD = (236, 192, 100, 255)
SEA = 186 * k

def layer(): return Image.new("RGBA", (S, S), (0, 0, 0, 0))

def vgrad(h, stops):
    ys = np.linspace(0, 1, h); out = np.zeros((h, 3))
    for i in range(len(stops) - 1):
        t0, c0 = stops[i]; t1, c1 = stops[i + 1]
        m = (ys >= t0) & (ys <= t1); t = (ys[m] - t0) / max(1e-6, t1 - t0)
        out[m] = np.array(c0) * (1 - t[:, None]) + np.array(c1) * t[:, None]
    return out

img = np.zeros((S, S, 3))
img[:int(SEA)] = vgrad(int(SEA), [(0, (20, 26, 64)), (0.5, (66, 42, 88)), (0.82, (170, 76, 72)), (1, (236, 136, 74))])[:, None, :]
img[int(SEA):] = vgrad(S - int(SEA), [(0, (88, 58, 82)), (0.35, (34, 34, 58)), (1, (12, 16, 30))])[:, None, :]
base = Image.fromarray(img.clip(0, 255).astype(np.uint8)).convert("RGBA")

def glow(cx, cy, r, col, a):
    g = layer(); ImageDraw.Draw(g).ellipse([cx - r, cy - r, cx + r, cy + r], fill=col + (a,))
    return g.filter(ImageFilter.GaussianBlur(r * 0.45))

# Sterne
random.seed(3)
d = ImageDraw.Draw(base)
for _ in range(26):
    x, y = random.uniform(10, 246) * k, random.uniform(10, 80) * k
    r = random.uniform(0.5, 1.1) * k
    d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 245, 230, random.randint(120, 220)))

def profile(x0, x1, top, peak, width, power, ystep=1.0):
    pts = []
    for i in range(0, 257, 2):
        x = i
        t = (x - peak) / width
        y = top + (SEA / k - top) * min(1.0, abs(t) ** power) if abs(t) < 1 else SEA / k
        pts.append((x * k, y * k))
    return pts

# Mauna Loa: riesiger flacher Schild hinten links, bläulich im Dunst
ml = profile(0, 256, 96, 70, 175, 1.6)
d.polygon(ml + [(256 * k, SEA), (0, SEA)], fill=(58, 48, 86, 255))
d.line(ml[:48], fill=(128, 92, 120, 255), width=int(1.4 * k))
# Kīlauea: flacher Schild vorn, Gipfel bei x≈108
kil = []
for i in range(0, 257, 2):
    x = i
    t = (x - 108) / 150.0
    y = 140 + (SEA / k - 140) * min(1.0, abs(t) ** 1.35 * (1.35 if x > 108 else 1.1))
    kil.append((x * k, y * k))
d.polygon(kil + [(256 * k, SEA), (0, SEA)], fill=(26, 20, 28, 255))
# Caldera: Kerbe am Gipfel
d.polygon([(96 * k, 140.5 * k), (101 * k, 144.5 * k), (116 * k, 144.5 * k), (121 * k, 140.5 * k)], fill=(14, 10, 14, 255))

# Glut der Caldera
base = Image.alpha_composite(base, glow(108 * k, 141 * k, 34 * k, (255, 130, 60), 170))
base = Image.alpha_composite(base, glow(108 * k, 143 * k, 12 * k, (255, 190, 100), 230))
d = ImageDraw.Draw(base)
d.ellipse([(103) * k, 142.2 * k, 113 * k, 145 * k], fill=(255, 200, 110, 255))

# Gasfahne: steigt auf und biegt mit dem Passat nach links (Westen)
random.seed(11)
pl = layer(); pd = ImageDraw.Draw(pl)
for i in range(60):
    t = i / 59.0
    cx = (108 - 62 * t ** 1.5 + random.uniform(-3, 3)) * k
    cy = (138 - 86 * t ** 0.8 + random.uniform(-3, 3)) * k
    r = (6 + 20 * t) * k
    hot = max(0.0, 1 - t * 2.2)
    col = (int(220 + 35 * hot), int(170 + 20 * hot), int(175 - 60 * hot))
    pd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col + (int(110 * (1 - t) ** 0.6),))
base = Image.alpha_composite(base, pl.filter(ImageFilter.GaussianBlur(5 * k)))

# Spaltenreihe auf der Ostriftzone: glühende Striche die Flanke hinab
fz = layer(); fd = ImageDraw.Draw(fz)
for i in range(7):
    x = 132 + i * 13
    t = (x - 108) / 150.0
    y = 140 + (SEA / k - 140) * min(1.0, abs(t) ** 1.35 * 1.35)
    fd.line([((x - 4) * k, (y + 0.6) * k), ((x + 4) * k, (y - 0.2) * k)], fill=(255, 150, 60, 255), width=int(2.2 * k))
base = Image.alpha_composite(base, fz.filter(ImageFilter.GaussianBlur(4 * k)))
base = Image.alpha_composite(base, fz)

# Lavastrom von der letzten Spalte zum Meer und Dampf am Eintritt
lv = layer(); ld = ImageDraw.Draw(lv)
path = [(205 * k, 176 * k), (210 * k, 180 * k), (214 * k, 184 * k), (217 * k, SEA)]
ld.line(path, fill=(255, 120, 40, 255), width=int(3 * k), joint="curve")
base = Image.alpha_composite(base, lv.filter(ImageFilter.GaussianBlur(5 * k)))
base = Image.alpha_composite(base, lv)
st = layer(); sd = ImageDraw.Draw(st)
for i in range(22):
    t = i / 21.0
    cx, cy = (217 - 18 * t) * k, (SEA / k - 2 - 30 * t) * k
    r = (4 + 9 * t) * k
    sd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(235, 215, 225, int(120 * (1 - t))))
base = Image.alpha_composite(base, st.filter(ImageFilter.GaussianBlur(3 * k)))
base = Image.alpha_composite(base, glow(217 * k, SEA, 10 * k, (255, 120, 60), 170))

# Meer: Spiegelstreifen der Glut wie die Lichtlinien in heidelberg.ico
d = ImageDraw.Draw(base)
d.rectangle([6 * k, SEA - 0.8 * k, 250 * k, SEA + 0.8 * k], fill=(240, 170, 120, 255))
for x0, y, x1, a in [(92, 196, 124, 170), (98, 206, 120, 140), (86, 216, 128, 110), (100, 228, 116, 80), (204, 196, 228, 150), (208, 208, 224, 110)]:
    d.line([(x0 * k, y * k), (x1 * k, y * k)], fill=(255, 160, 96, a), width=int(2.2 * k))

mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([OUT, OUT, IN - 1, IN - 1], radius=R, fill=255)
icon = layer(); icon.paste(base, (0, 0), mask)
ImageDraw.Draw(icon).rounded_rectangle([OUT, OUT, IN - 1, IN - 1], radius=R, outline=GOLD, width=int(GW))

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
big = icon.resize((256, 256), Image.LANCZOS)
big.save(os.path.join(out, "kilauea.png"))
big.save(os.path.join(out, "kilauea.ico"), sizes=[(s, s) for s in (16, 24, 32, 48, 64, 128, 256)])
for s in (16, 24, 32, 48, 64, 128, 256):
    icon.resize((s, s), Image.LANCZOS).save(os.path.join(out, "kilauea_%d.png" % s))
prev = Image.new("RGBA", (256 + 128 + 64 + 48 + 32 + 16 + 70, 256), (40, 40, 44, 255)); x = 0
for s in (256, 128, 64, 48, 32, 16):
    prev.alpha_composite(icon.resize((s, s), Image.LANCZOS), (x, 0)); x += s + 10
prev.save(os.path.join(out, "preview.png"))
print("ok")
