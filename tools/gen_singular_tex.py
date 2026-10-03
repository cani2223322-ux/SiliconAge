# -*- coding: utf-8 -*-
"""Singular armour textures (Silicon Age, docs/plan-singular-armor.md) - every colour scheme from one table.

For each row of SCHEMES it writes
  items/armorSingular<Helmet|Chestplate|Leggings|Boots>_<key>.png       (32 x 32 icons)
  models/armor/singular_<key>_layer_<1|2>.png                            (128 x 64 worn layers)
  models/armor/singular_<key>_glow_<1|2>.png, _glow_<1|2>_<0..7>.png,    (glow: own colours, 8 animation frames,
              _gloww_<1|2>.png, _glowred_<1|2>.png                         white for a picked light colour, red when empty)
and once: blocks/fluids/singularmatter_still/_flow.png (+ .mcmeta), and a preview sheet of all schemes.

The Exo textures are only the silhouette and the plate shading: Exo's own details (glow strips, gold
studs, its chip) are wiped and the Singular ones painted instead - the singularity core (a glowing
ring round a dark centre) on the chest, orbit arcs, rings on helmet / shoulders / knees / ankles,
accent lines. Adding a scheme = a row in SCHEMES (and SingularScheme.java + lang), then run again.

Usage: python gen_singular_tex.py [project dir] [preview png]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else 'C:/Users/Aleksandr/Documents/MineMod/SiliconAge'
PREVIEW = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'singular_tex_preview.png')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')

# key, name, plate base (RGB), accent (RGB) - same order as SingularScheme.java (append only)
SCHEMES = [
    ('a', 'Violet Dark',     (34, 22, 48),    (190, 110, 255)),
    ('b', 'Event Horizon',   (18, 18, 22),    (120, 230, 255)),
    ('c', 'Crimson',         (40, 14, 20),    (255, 70, 90)),
    ('d', 'White Dwarf',     (150, 150, 165), (140, 200, 255)),
    ('e', 'Accretion Disk',  (26, 20, 16),    (255, 170, 50)),
    ('f', 'Dark Matter',     (14, 22, 16),    (90, 255, 120)),
    ('g', 'Quasar',          (16, 20, 52),    (255, 80, 220)),
    ('h', 'Void',            (12, 12, 12),    (235, 235, 235)),
    ('i', 'Supernova',       (44, 26, 10),    (255, 230, 120)),
    ('j', 'Nebula',          (14, 40, 46),    (255, 120, 190)),
    ('k', 'Neutron Star',    (52, 58, 66),    (60, 140, 255)),
]

PIECES = ['Helmet', 'Chestplate', 'Leggings', 'Boots']
FRAMES = 8
MATTER = (200, 90, 255)                 # singular matter's colour (ArmorGasSC.Gas.SINGULAR_MATTER)


# ---------------------------------------------------------------------------------------------- colour

def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k, add=0):
    return tuple(clamp(v * k + add) for v in c)


class Palette(object):
    def __init__(self, base, accent):
        self.base, self.acc = base, accent
        lum = (0.3 * base[0] + 0.59 * base[1] + 0.11 * base[2]) / 255.0
        self.light_suit = lum > 0.4                        # White Dwarf: a pale plate, dark lines
        self.dark = scale(base, 0.55)
        self.light = scale(base, 1.3, 30) if not self.light_suit else scale(base, 1.18, 22)
        self.out = scale(base, 0.25) if not self.light_suit else scale(base, 0.32)
        self.core = mix((4, 3, 8), accent, 0.06)
        self.acc_hi = mix(accent, (255, 255, 255), 0.55)
        self.acc_dim = mix(accent, base, 0.45)
        self.acc_sh = mix(accent, (0, 0, 0), 0.35)

    def plate(self, t):
        t = max(0.0, min(1.0, t))
        return mix(self.dark, self.base, t * 2) if t < 0.5 else mix(self.base, self.light, (t - 0.5) * 2)


# ---------------------------------------------------------------------------------------------- canvas

class Canvas(object):
    """An RGBA image being painted, with a glow mask (0 none, 1 accent, 2 bright accent)."""

    def __init__(self, img):
        self.img = img
        self.px = img.load()
        self.w, self.h = img.size
        self.glow = [[0] * self.h for _ in range(self.w)]

    def inside(self, x, y):
        return 0 <= x < self.w and 0 <= y < self.h

    def opaque(self, x, y):
        return self.inside(x, y) and self.px[x, y][3] > 0

    def put(self, x, y, c, glow=0, force=False):
        if not self.inside(x, y) or (not force and self.px[x, y][3] == 0):
            return
        self.px[x, y] = (c[0], c[1], c[2], 255)
        self.glow[x][y] = glow

    def rect(self, x0, y0, x1, y1, c, glow=0, force=False):
        for x in range(x0, x1):
            for y in range(y0, y1):
                self.put(x, y, c, glow, force)

    def hline(self, x0, x1, y, c, glow=0, step=1):
        for x in range(x0, x1, step):
            self.put(x, y, c, glow)

    def vline(self, x, y0, y1, c, glow=0, step=1):
        for y in range(y0, y1, step):
            self.put(x, y, c, glow)

    def disc(self, cx, cy, r, c, glow=0, box=None):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for y in range(int(cy - r - 1), int(cy + r + 2)):
                if box and not (box[0] <= x < box[2] and box[1] <= y < box[3]):
                    continue
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    self.put(x, y, c, glow)

    def ring(self, cx, cy, r0, r1, c, glow=1, hi=None, box=None):
        """Pixels with r0 <= distance <= r1; `hi` lights the upper-left arc."""
        for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
            for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
                if box and not (box[0] <= x < box[2] and box[1] <= y < box[3]):
                    continue
                dx, dy = x + 0.5 - cx, y + 0.5 - cy
                d = math.hypot(dx, dy)
                if r0 <= d <= r1:
                    if hi is not None and dx + dy < -0.6 * r1:
                        self.put(x, y, hi, 2)
                    else:
                        self.put(x, y, c, glow)

    def orbit(self, cx, cy, rx, ry, ang, c, glow=1, skip_r=0.0, dots=1, box=None, phase=0):
        """A tilted ellipse, every `dots`-th step lit, not inside skip_r of the centre."""
        seen = set()
        n = int(2 * math.pi * max(rx, ry) * 2.2)
        ca, sa = math.cos(ang), math.sin(ang)
        for i in range(n):
            a = 2 * math.pi * i / n
            ex, ey = rx * math.cos(a), ry * math.sin(a)
            x, y = int(math.floor(cx + ex * ca - ey * sa)), int(math.floor(cy + ex * sa + ey * ca))
            if (x, y) in seen:
                continue
            seen.add((x, y))
            if box and not (box[0] <= x < box[2] and box[1] <= y < box[3]):
                continue
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) < skip_r:
                continue
            if (len(seen) + phase) % dots == 0:
                self.put(x, y, c, glow)


# ---------------------------------------------------------------------------------------------- the Exo base, recoloured

def recolour(src, pal):
    """The Exo picture as plain Singular plating: outline / plate shading kept, Exo's own details wiped."""
    img = src.convert('RGBA')
    w, h = img.size
    sp = img.load()
    t = [[None] * h for _ in range(w)]
    kind = [[0] * h for _ in range(w)]                 # 0 none, 1 outline, 2 plate, 3 detail (filled in from plates)
    for x in range(w):
        for y in range(h):
            r, g, b, a = sp[x, y]
            if a == 0:
                continue
            sat = max(r, g, b) - min(r, g, b)
            lum = (r + g + b) / 3.0
            gold = r > 140 and g > 100 and b < 100
            if sat > 62 or gold or lum > 120:
                kind[x][y] = 3
            elif lum < 26:
                kind[x][y] = 1
                t[x][y] = lum / 26.0
            else:
                kind[x][y] = 2
                t[x][y] = (lum - 26) / 52.0
    out = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    op = out.load()
    for x in range(w):
        for y in range(h):
            k = kind[x][y]
            if k == 0:
                continue
            if k == 3:                                      # a detail: the plate round it
                vals = [t[x + dx][y + dy] for dx in (-2, -1, 0, 1, 2) for dy in (-2, -1, 0, 1, 2)
                        if 0 <= x + dx < w and 0 <= y + dy < h and kind[x + dx][y + dy] == 2]
                tt = sum(vals) / len(vals) if vals else 0.45
                op[x, y] = pal.plate(tt) + (255,)
            elif k == 1:
                op[x, y] = mix(pal.out, pal.dark, t[x][y] * 0.5) + (255,)
            else:
                op[x, y] = pal.plate(t[x][y]) + (255,)
    return Canvas(out)


# ---------------------------------------------------------------------------------------------- icons (32 x 32)

def core(c, pal, cx, cy, rr, box=None):
    """The singularity core: a dim halo, the glowing ring (lit upper-left), a dark centre."""
    c.disc(cx, cy, rr + 1.6, pal.dark, box=box)
    c.ring(cx, cy, rr + 0.7, rr + 1.6, pal.acc_dim, 1, box=box)
    c.ring(cx, cy, rr - 1.1, rr + 0.7, pal.acc, 1, hi=pal.acc_hi, box=box)
    c.disc(cx, cy, rr - 1.1, pal.core, box=box)


def mini_ring(c, pal, cx, cy, big=False):
    """A tiny ring for 32px icons: a plus of accent round a dark pixel (big: a 4x4 ring)."""
    if big:
        for (x, y) in ((1, 0), (2, 0), (0, 1), (3, 1), (0, 2), (3, 2), (1, 3), (2, 3)):
            c.put(cx + x, cy + y, pal.acc, 1)
        c.rect(cx + 1, cy + 1, cx + 3, cy + 3, pal.core)
        c.put(cx + 1, cy, pal.acc_hi, 2)
    else:
        for (x, y) in ((0, -1), (-1, 0), (1, 0), (0, 1)):
            c.put(cx + x, cy + y, pal.acc, 1)
        c.put(cx, cy, pal.core)


def icon_helmet(c, pal):
    for x in range(32):                                 # a smooth dome instead of Exo's studs
        c.px[x, 2] = (0, 0, 0, 0)
        if c.px[x, 3][3]:
            c.px[x, 3] = pal.out + (255,)
    c.rect(5, 10, 27, 15, pal.out)                     # the visor: a dark slit with an accent scan line
    c.hline(6, 26, 12, pal.acc, 1)
    c.hline(6, 26, 11, pal.acc_sh)
    c.put(15, 12, pal.acc_hi, 2)
    c.put(16, 12, pal.acc_hi, 2)
    mini_ring(c, pal, 14, 4, big=True)                 # the "third eye" ring on the brow
    c.hline(6, 13, 7, pal.acc_dim, 1, step=2)           # orbit traces round the dome
    c.hline(20, 27, 7, pal.acc_dim, 1, step=2)
    c.put(4, 16, pal.acc, 1)
    c.put(27, 16, pal.acc, 1)
    c.hline(10, 22, 18, pal.acc_sh)


def icon_chest(c, pal):
    for y in range(3):                                  # no Exo horns
        for x in range(32):
            c.px[x, y] = (0, 0, 0, 0)
    for x in range(32):                                 # nor the dangling studs
        c.px[x, 21] = (0, 0, 0, 0)
    c.rect(4, 5, 28, 19, pal.plate(0.45))
    c.hline(4, 28, 5, pal.plate(0.75))
    core(c, pal, 16.0, 12.0, 3.4, box=(4, 4, 28, 20))
    c.orbit(16.0, 12.0, 10.2, 2.6, -0.32, pal.acc_hi, 2, skip_r=5.2, dots=2, box=(3, 4, 29, 20))
    mini_ring(c, pal, 5, 7)                            # shoulder rings
    mini_ring(c, pal, 26, 7)
    c.vline(9, 16, 20, pal.acc_dim, 1)
    c.vline(22, 16, 20, pal.acc_dim, 1)
    c.hline(12, 20, 19, pal.out)
    c.put(15, 20, pal.acc, 1)
    c.put(16, 20, pal.acc, 1)


def icon_legs(c, pal):
    c.hline(6, 27, 5, pal.acc, 1)                      # the belt line and its buckle ring
    c.hline(6, 27, 4, pal.acc_sh)
    mini_ring(c, pal, 16, 5)
    c.rect(6, 7, 9, 11, pal.plate(0.5))                # wipe the Exo strips
    c.rect(22, 7, 27, 11, pal.plate(0.5))
    mini_ring(c, pal, 8, 13)                           # knee rings
    mini_ring(c, pal, 24, 13)
    c.vline(6, 15, 20, pal.acc_dim, 1)
    c.vline(26, 15, 20, pal.acc_dim, 1)
    c.vline(11, 15, 20, pal.acc_sh)
    c.vline(21, 15, 20, pal.acc_sh)


def icon_boots(c, pal):
    mini_ring(c, pal, 7, 13)                           # ankle rings
    mini_ring(c, pal, 24, 13)
    for x0, x1 in ((5, 15), (17, 27)):                 # the gravity sole: a lit line over a dark one
        c.hline(x0, x1, 19, pal.acc, 1)
        c.hline(x0, x1, 20, pal.out)
        c.put((x0 + x1) // 2, 19, pal.acc_hi, 2)
    c.hline(5, 10, 16, pal.acc_dim, 1, step=2)
    c.hline(22, 27, 16, pal.acc_dim, 1, step=2)


ICON_PAINT = {'Helmet': icon_helmet, 'Chestplate': icon_chest, 'Leggings': icon_legs, 'Boots': icon_boots}


# ---------------------------------------------------------------------------------------------- worn layers (128 x 64, 2x)

def faces(u, v, w, h, d):
    """Pixel rectangles (x, y, w, h) of a model box's six faces (vanilla net), at 2x."""
    k = 2
    return {'top': ((u + d) * k, v * k, w * k, d * k), 'bottom': ((u + d + w) * k, v * k, w * k, d * k),
            'right': (u * k, (v + d) * k, d * k, h * k), 'front': ((u + d) * k, (v + d) * k, w * k, h * k),
            'left': ((u + d + w) * k, (v + d) * k, d * k, h * k), 'back': ((u + d + w + d) * k, (v + d) * k, w * k, h * k)}


def layer1(c, pal):
    # helmet: front visor, a ring on the brow, orbit arcs on the sides, a ring on top
    c.rect(17, 21, 31, 26, pal.out)
    c.hline(18, 30, 23, pal.acc, 1)
    c.hline(18, 30, 24, pal.acc_sh)
    c.hline(22, 26, 23, pal.acc_hi, 2)
    c.ring(24, 18.5, 1.2, 2.4, pal.acc, 1, hi=pal.acc_hi)
    c.disc(24, 18.5, 1.2, pal.core)
    for x0 in (0, 32):                                 # the two sides
        c.orbit(x0 + 8, 24, 6.6, 3.0, 0.35, pal.acc_dim, 1, dots=2, box=(x0, 16, x0 + 16, 32))
        c.disc(x0 + 8, 24, 1.3, pal.acc_sh)
    c.ring(24, 8, 3.2, 4.6, pal.acc_dim, 1)            # top
    c.disc(24, 8, 3.2, pal.core)
    c.vline(55, 18, 31, pal.acc, 1)                    # back
    c.vline(56, 18, 31, pal.acc_sh)
    c.ring(56, 21, 1.3, 2.5, pal.acc, 1)
    c.disc(56, 21, 1.3, pal.core)
    # body front: the singularity core, its orbit, two conduits down to the belt
    core(c, pal, 48.0, 49.0, 4.0, box=(40, 40, 56, 64))
    c.orbit(48.0, 49.0, 7.4, 2.4, -0.38, pal.acc_hi, 2, skip_r=5.8, dots=2, box=(40, 40, 56, 64))
    c.vline(43, 56, 63, pal.acc, 1)
    c.vline(52, 56, 63, pal.acc, 1)
    c.hline(40, 56, 63, pal.out)
    c.hline(40, 56, 40, pal.plate(0.8))
    # body back: a hollow ring, the spine line
    c.ring(72, 47, 2.6, 4.2, pal.acc_dim, 1, hi=pal.acc)
    c.disc(72, 47, 2.6, pal.core)
    c.vline(71, 52, 63, pal.acc, 1)
    c.vline(72, 52, 63, pal.acc_sh)
    # body sides and top
    for x in (35, 60):
        c.vline(x, 43, 61, pal.acc_dim, 1, step=2)
    c.hline(42, 54, 36, pal.acc_dim, 1)
    # arms: a bracer band, a stripe on the outer face, a ring on the shoulder top
    for x0 in (80, 88, 96, 104):
        c.hline(x0, x0 + 8, 49, pal.acc, 1)
        c.hline(x0, x0 + 8, 50, pal.acc_sh)
        c.hline(x0, x0 + 8, 41, pal.plate(0.7))
    c.vline(83, 52, 62, pal.acc_dim, 1)
    c.vline(100, 52, 62, pal.acc_dim, 1)
    c.ring(92, 36, 1.4, 2.8, pal.acc, 1, hi=pal.acc_hi)
    c.disc(92, 36, 1.4, pal.core)
    # boots (the legs' lower part): an ankle ring in front, the gravity sole
    for x0 in (0, 8, 16, 24):
        c.hline(x0, x0 + 8, 61, pal.acc, 1)
        c.hline(x0, x0 + 8, 62, pal.out)
    c.ring(12, 56, 1.2, 2.5, pal.acc, 1, hi=pal.acc_hi)
    c.disc(12, 56, 1.2, pal.core)
    # the add-on parts (ModelArmorGlowSC: Exo's boxes) - orbs, rings, fins, radiator
    for f, (x, y, w, h) in faces(32, 0, 3, 3, 3).items():              # shoulder orbs: little singularities
        c.rect(x, y, x + w, y + h, pal.out, force=True)
        c.ring(x + w / 2.0, y + h / 2.0, 1.2, 2.6, pal.acc, 1, hi=pal.acc_hi)
        c.disc(x + w / 2.0, y + h / 2.0, 1.2, pal.core)
    for f, (x, y, w, h) in faces(32, 8, 6, 1, 6).items():              # the anti-grav rings
        if f in ('top', 'bottom'):
            c.rect(x, y, x + w, y + h, pal.dark, force=True)
            c.ring(x + w / 2.0, y + h / 2.0, 3.6, 5.6, pal.acc, 1, hi=pal.acc_hi)
            c.disc(x + w / 2.0, y + h / 2.0, 3.6, pal.core)
        else:
            c.rect(x, y, x + w, y + h, pal.acc, 1, force=True)
            c.hline(x, x + w, y, pal.acc_hi, 2)
    for f, (x, y, w, h) in faces(44, 0, 1, 2, 6).items():              # helmet fins
        c.rect(x, y, x + w, y + h, pal.plate(0.5), force=True)
        c.hline(x, x + w, y, pal.acc, 1)
    for f, (x, y, w, h) in faces(56, 16, 3, 6, 1).items():             # the back radiator
        for j in range(h):
            c.hline(x, x + w, y + j, pal.plate(0.65) if j % 2 == 0 else pal.out)
        c.hline(x, x + w, y, pal.acc, 1)


def layer2(c, pal):
    # knee plates (the leggings' add-on boxes) with a ring on the front
    for f, (x, y, w, h) in faces(0, 0, 4, 3, 1).items():
        c.rect(x, y, x + w, y + h, pal.plate(0.5), force=True)
        c.hline(x, x + w, y, pal.plate(0.85))
        if f == 'front':
            c.ring(x + w / 2.0, y + h / 2.0, 1.0, 2.4, pal.acc, 1, hi=pal.acc_hi)
            c.disc(x + w / 2.0, y + h / 2.0, 1.0, pal.core)
    # legs: wipe the Exo strips, knee rings front and back, a dotted line down the outer sides
    for x0 in (0, 8, 16, 24):
        c.rect(x0 + 1, 41, x0 + 7, 56, pal.plate(0.45))
        c.hline(x0, x0 + 8, 41, pal.plate(0.7))
    for cx in (12, 28):
        c.ring(cx, 46, 1.3, 2.6, pal.acc, 1, hi=pal.acc_hi)
        c.disc(cx, 46, 1.3, pal.core)
    for x in (3, 19):
        c.vline(x, 50, 63, pal.acc_dim, 1, step=2)
    c.vline(11, 51, 63, pal.acc_sh)
    c.vline(12, 51, 63, pal.acc, 1)
    # the waist: an accent belt line round it, a buckle ring in front
    for x0, x1 in ((32, 40), (40, 56), (56, 64), (64, 80)):
        c.rect(x0, 56, x1, 64, pal.plate(0.45))
        c.hline(x0, x1, 59, pal.acc, 1)
        c.hline(x0, x1, 60, pal.acc_sh)
        c.hline(x0, x1, 63, pal.out)
    c.ring(48, 59.5, 1.2, 2.6, pal.acc, 1, hi=pal.acc_hi)
    c.disc(48, 59.5, 1.2, pal.core)
    c.ring(72, 59.5, 1.2, 2.6, pal.acc_dim, 1)
    c.disc(72, 59.5, 1.2, pal.core)


# ---------------------------------------------------------------------------------------------- glow layers

def wave(x, y, f, n):
    """Light running outwards: radially from the chest core on the body front (layer 1), down the rest."""
    if n == 1 and 40 <= x < 56 and 40 <= y < 64:
        ph = math.hypot(x + 0.5 - 48, y + 0.5 - 49) / 8.0
    else:
        ph = (y % 32) / 16.0
    return 0.70 + 0.30 * (0.5 + 0.5 * math.cos(2 * math.pi * (ph - f / float(FRAMES))))


def write_glow(c, base, n, out_dir):
    w, h = c.w, c.h
    bp = c.px
    glow, red, white = [Image.new('RGBA', (w, h), (0, 0, 0, 0)) for _ in range(3)]
    frames = [Image.new('RGBA', (w, h), (0, 0, 0, 0)) for _ in range(FRAMES)]
    gp, rp, wp = glow.load(), red.load(), white.load()
    fp = [f.load() for f in frames]
    for x in range(w):
        for y in range(h):
            m = c.glow[x][y]
            if not m or bp[x, y][3] == 0:
                continue
            r, g, b, a = bp[x, y]
            gp[x, y] = (r, g, b, 255)
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            rp[x, y] = (255, clamp(40 + 150 * max(0.0, lum - 0.5)), clamp(30 + 140 * max(0.0, lum - 0.5)), 255)
            v = clamp(lum * 255 * 1.3 + 70)
            wp[x, y] = (v, v, v, 255)
            for f in range(FRAMES):
                k = wave(x, y, f, n) * (1.08 if m == 2 else 1.0)
                fp[f][x, y] = (clamp(r * k), clamp(g * k), clamp(b * k), 255)
    glow.save(os.path.join(out_dir, '%s_glow_%d.png' % (base, n)))
    red.save(os.path.join(out_dir, '%s_glowred_%d.png' % (base, n)))
    white.save(os.path.join(out_dir, '%s_gloww_%d.png' % (base, n)))
    for f in range(FRAMES):
        frames[f].save(os.path.join(out_dir, '%s_glow_%d_%d.png' % (base, n, f)))
    return frames[0]


# ---------------------------------------------------------------------------------------------- singular matter (fluid)

def fluid_textures():
    """16 x 256 (16 frames): a dark violet swirl with drifting sparks, still and flowing."""
    out_dir = os.path.join(TEX, 'blocks', 'fluids')
    deep, mid, hi = (26, 8, 44), MATTER, (255, 230, 255)
    for kind in ('still', 'flow'):
        img = Image.new('RGBA', (16, 256), (0, 0, 0, 255))
        px = img.load()
        for f in range(16):
            for x in range(16):
                for y in range(16):
                    if kind == 'still':
                        dx, dy = x - 7.5, y - 7.5
                        r, a = math.hypot(dx, dy), math.atan2(dy, dx)
                        s = math.sin(a * 2 + r * 0.9 - f * 2 * math.pi / 16) * 0.5 + 0.5
                    else:
                        s = math.sin((y + f) * 2 * math.pi / 16 + math.sin(x * 0.8) * 1.2) * 0.5 + 0.5
                    c = mix(deep, mid, s * 0.75)
                    if ((x * 7 + y * 13 + f * 5) % 53) == 0:
                        c = hi
                    px[x, f * 16 + y] = c + (230,)
        img.save(os.path.join(out_dir, 'singularmatter_%s.png' % kind))
        with open(os.path.join(out_dir, 'singularmatter_%s.png.mcmeta' % kind), 'w') as fh:
            fh.write('{"animation": {"frametime": %d}}' % (2 if kind == 'still' else 1))


# ---------------------------------------------------------------------------------------------- main

def main():
    items, armor = os.path.join(TEX, 'items'), os.path.join(TEX, 'models', 'armor')
    src_icons = {p: Image.open(os.path.join(items, 'armorExo%s.png' % p)).convert('RGBA') for p in PIECES}
    src_layers = {n: Image.open(os.path.join(armor, 'exo_layer_%d.png' % n)).convert('RGBA') for n in (1, 2)}
    rows = []
    written = 0
    for key, name, base, accent in SCHEMES:
        pal = Palette(base, accent)
        icons = []
        for p in PIECES:
            c = recolour(src_icons[p], pal)
            ICON_PAINT[p](c, pal)
            c.img.save(os.path.join(items, 'armorSingular%s_%s.png' % (p, key)))
            icons.append(c.img)
            written += 1
        layers, glows = [], []
        for n, painter in ((1, layer1), (2, layer2)):
            c = recolour(src_layers[n], pal)
            painter(c, pal)
            tex = 'singular_%s' % key
            c.img.save(os.path.join(armor, '%s_layer_%d.png' % (tex, n)))
            glows.append(write_glow(c, tex, n, armor))
            layers.append(c.img)
            written += 1 + 3 + FRAMES
        rows.append((key, name, icons, layers, glows))
    fluid_textures()
    written += 4
    preview(rows)
    print('ok: %d schemes, %d files, preview %s' % (len(SCHEMES), written, PREVIEW))


def preview(rows):
    """One row per scheme: the four icons x4, both layers x2, layer 1 with its glow frame on top."""
    from PIL import ImageDraw
    cell_h = 136
    w = 4 * 132 + 3 * 264 + 40
    sheet = Image.new('RGBA', (w, cell_h * len(rows) + 10), (58, 58, 62, 255))
    d = ImageDraw.Draw(sheet)
    for i, (key, name, icons, layers, glows) in enumerate(rows):
        y = 5 + i * cell_h
        d.text((6, y), '%s  %s' % (key.upper(), name), fill=(230, 230, 230, 255))
        for j, ic in enumerate(icons):
            big = ic.resize((128, 128), Image.NEAREST)
            sheet.paste(big, (6 + j * 132, y + 8), big)
        x0 = 6 + 4 * 132 + 10
        for j, l in enumerate(layers):
            big = l.resize((256, 128), Image.NEAREST)
            sheet.paste(big, (x0 + j * 264, y + 8), big)
        dark = Image.new('RGBA', layers[0].size, (0, 0, 0, 255))
        lp, gp, dp = layers[0].load(), glows[0].load(), dark.load()
        for x in range(dark.size[0]):
            for y2 in range(dark.size[1]):
                if lp[x, y2][3]:
                    r, g, b, a = lp[x, y2]
                    dp[x, y2] = (r // 3, g // 3, b // 3, 255)
                if gp[x, y2][3]:
                    dp[x, y2] = gp[x, y2]
        big = dark.resize((256, 128), Image.NEAREST)
        sheet.paste(big, (x0 + 2 * 264, y + 8))
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
