"""Turn the branding artwork into Android launcher, monochrome, in-app and notification assets.

Usage (from the repository root):

    python3 tools/make_icons.py [preview-dir]

Reads branding/app-icon.jpg (colored app icon) and branding/logo-white.png (white logo on transparency)
and writes the resources under app/src/main/res. Needs Pillow and NumPy.
"""
import sys, os
import numpy as np
from PIL import Image, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app', 'src', 'main', 'res')
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, 'build', 'icon-preview')
os.makedirs(OUT, exist_ok=True)
DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}

src = np.asarray(Image.open(os.path.join(ROOT, 'branding', 'app-icon.jpg')).convert('RGB')).astype(np.float64)
H, W, _ = src.shape
CX, CY = 1023.5, 1024.0          # knot centre
KNOT_R = 740.0                   # knot radius in source px
TARGET_R_DP = 32.0               # knot radius on the 108dp adaptive canvas (safe zone is 33dp)

# ---------------------------------------------------------------- background gradient fit (vertical)
rows = np.arange(250, 1780, 10)
samples = np.array([np.concatenate([src[y, 150:250], src[y, 1800:1890]]).mean(0) for y in rows])
coef = [np.polyfit(rows, samples[:, c], 1) for c in range(3)]
def bg(y):
    return np.stack([np.polyval(coef[c], y) for c in range(3)], -1)

side = KNOT_R / TARGET_R_DP * 108.0         # source px covered by the 108dp canvas
top, bottom = CY - side / 2, CY + side / 2
start = np.clip(bg(np.array([top]))[0], 0, 255).round().astype(int)
end = np.clip(bg(np.array([bottom]))[0], 0, 255).round().astype(int)
hexs = lambda c: '#%02X%02X%02X' % tuple(c)
print('gradient', hexs(start), '->', hexs(end), 'side', round(side))

# ---------------------------------------------------------------- un-composite the knot from the background
yy, xx = np.mgrid[0:H, 0:W].astype(np.float64)
B = bg(yy)
diff = src - B
T = 110.0
alpha = np.clip(np.abs(diff).max(-1) / T, 0, 1)
dist = np.sqrt((xx - CX) ** 2 + (yy - CY) ** 2)
alpha[dist > KNOT_R + 45] = 0                       # drop the frame / corners
fade = np.clip((KNOT_R + 45 - dist) / 30, 0, 1)     # soft edge for the outer shadow
alpha *= fade
alpha[alpha < 0.035] = 0                            # JPEG noise in flat areas
safe = np.where(alpha > 0, alpha, 1)
F = np.clip(B + diff / safe[..., None], 0, 255)
fg = np.dstack([F, alpha * 255]).round().astype(np.uint8)
fg_img = Image.fromarray(fg, 'RGBA')

def canvas_crop(img, cx, cy, side, fill=(0, 0, 0, 0)):
    s = int(round(side))
    c = Image.new('RGBA', (s, s), fill)
    c.paste(img, (int(round(s / 2 - cx)), int(round(s / 2 - cy))), img)
    return c

fg_canvas = canvas_crop(fg_img, CX, CY, side)

# ---------------------------------------------------------------- monochrome / in-app mask from the white logo
w = np.asarray(Image.open(os.path.join(ROOT, 'branding', 'logo-white.png'))).astype(np.float64)
lum = w[..., :3].mean(-1)
wa = w[..., 3] / 255.0
mask = np.clip((lum - 40) / (215 - 40), 0, 1) * wa       # white strands opaque, black outlines + background clear
wy, wx = np.nonzero(mask > 0.5)
wcx, wcy = (wx.min() + wx.max()) / 2, (wy.min() + wy.max()) / 2
wr = np.sqrt((wx - wcx) ** 2 + (wy - wcy) ** 2).max()
print('white logo centre', wcx, wcy, 'radius', round(wr, 1))
white = np.dstack([np.full(mask.shape + (3,), 255.0), mask * 255]).round().astype(np.uint8)
white_img = Image.fromarray(white, 'RGBA')

def mono_canvas(img, r, cx, cy, target_r_dp=TARGET_R_DP):
    side_w = r / target_r_dp * 108.0
    return canvas_crop(img, cx, cy, side_w)

mono = mono_canvas(white_img, wr, wcx, wcy)

# Widen the gaps between strands for tiny renderings (notification icon), so the weave stays readable.
def widened(px_gap):
    m = Image.fromarray((mask * 255).astype(np.uint8), 'L')
    m = m.filter(ImageFilter.MinFilter(px_gap))   # erode white strands -> wider gaps
    arr = np.asarray(m).astype(np.float64)
    return Image.fromarray(np.dstack([np.full(arr.shape + (3,), 255.0), arr]).round().astype(np.uint8), 'RGBA')

# ---------------------------------------------------------------- write resources
def save(img, path, size):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.resize((size, size), Image.LANCZOS).save(path, optimize=True)

for d, f in DENS.items():
    n = int(round(108 * f))
    save(fg_canvas, f'{RES}/mipmap-{d}/ic_launcher_foreground.png', n)
    save(mono, f'{RES}/mipmap-{d}/ic_launcher_monochrome.png', n)
    # Notification: 24dp canvas, artwork 22dp wide
    nn = int(round(24 * f))
    art = widened(9 if f <= 1.5 else 7)
    side_n = (wr * 2) / 22.0 * 24.0
    save(canvas_crop(art, wcx, wcy, side_n), f'{RES}/drawable-{d}/ic_notification.png', nn)

# In-app logo mark: tight square, 512px
side_l = wr * 2 * 1.02
save(canvas_crop(white_img, wcx, wcy, side_l), f'{RES}/drawable-nodpi/logo_mark.png', 512)

with open(f'{RES}/values/ic_launcher_colors.xml', 'w') as fh:
    fh.write(f'''<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Sampled from the app icon artwork's background -->
    <color name="ic_launcher_bg_top">{hexs(start)}</color>
    <color name="ic_launcher_bg_bottom">{hexs(end)}</color>
</resources>
''')

# ---------------------------------------------------------------- previews: what launchers will show
def bg_img(n):
    g = np.linspace(0, 1, n)[:, None]
    arr = start[None, None, :] * (1 - g[..., None]) + end[None, None, :] * g[..., None]
    arr = np.broadcast_to(arr, (n, n, 3))
    return Image.fromarray(arr.round().astype(np.uint8), 'RGB').convert('RGBA')

def launcher_preview(n, shape):
    full = int(round(n * 108 / 72))
    comp = bg_img(full)
    comp.alpha_composite(fg_canvas.resize((full, full), Image.LANCZOS))
    off = (full - n) // 2
    comp = comp.crop((off, off, off + n, off + n))
    m = Image.new('L', (n * 4, n * 4), 0)
    from PIL import ImageDraw
    dr = ImageDraw.Draw(m)
    if shape == 'circle':
        dr.ellipse((0, 0, n * 4 - 1, n * 4 - 1), fill=255)
    else:
        dr.rounded_rectangle((0, 0, n * 4 - 1, n * 4 - 1), radius=n * 4 * 0.28, fill=255)
    m = m.resize((n, n), Image.LANCZOS)
    out = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    out.paste(comp, (0, 0), m)
    return out

sheet = Image.new('RGBA', (1080, 420), (236, 242, 250, 255))
x = 20
for shape, n in [('circle', 192), ('squircle', 192), ('circle', 96), ('squircle', 48)]:
    p = launcher_preview(n, shape)
    sheet.alpha_composite(p, (x, 30)); x += n + 30
# themed (monochrome) on a tinted circle
mono_prev = Image.new('RGBA', (288, 288), (0, 0, 0, 0))
th = Image.new('RGBA', (192, 192), (0, 0, 0, 0))
from PIL import ImageDraw
ImageDraw.Draw(th).ellipse((0, 0, 191, 191), fill=(201, 222, 255, 255))
mm = mono.resize((288, 288), Image.LANCZOS)
tint = Image.new('RGBA', mm.size, (14, 52, 120, 255)); tint.putalpha(mm.getchannel('A'))
th.alpha_composite(tint.crop((48, 48, 240, 240)))
sheet.alpha_composite(th, (x, 30))
# dark background row
dark = Image.new('RGBA', (1080, 160), (12, 18, 30, 255))
x = 20
for shape, n in [('circle', 120), ('squircle', 120)]:
    dark.alpha_composite(launcher_preview(n, shape), (x, 20)); x += n + 30
nt = Image.open(f'{RES}/drawable-xxxhdpi/ic_notification.png')
dark.alpha_composite(nt, (x, 32)); x += 130
dark.alpha_composite(Image.open(f'{RES}/drawable-mdpi/ic_notification.png').resize((72, 72), Image.NEAREST), (x, 32)); x += 100
dark.alpha_composite(Image.open(f'{RES}/drawable-mdpi/ic_notification.png'), (x, 56))
sheet.alpha_composite(dark, (0, 250))
sheet.save(f'{OUT}/icon_preview.png')
print('done')
