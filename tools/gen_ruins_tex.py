# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 3, package X: the ruins and their «noise» (Silicon Age, docs/plan-substrate-tech-f3.md §8.7).

Writes into src/main/resources/assets/siliconage/textures:
  blocks/ruin_ruinMetal    - «Руинный металл»: the Lithographers' dark steel plate in the machines' bevelled frame, two
                             plates with rivets, a violet-blue oxide film blooming on it, rust running down from the rivets,
                             a crack and a dent (32 x 32, as the mod's machine casings);
  blocks/ruin_burntPanel   - «Выгоревшая панель»: a casing frame black with soot round a 3 x 3 grid of cells burnt out from
                             one hot spot, the grid's lines melted through; two cells keep a dim blue;
  blocks/ruin_tankHusk     - «Остов бака»: a tank's side - a rounded shell, two riveted hoops, its teal paint peeled to steel
                             and rust, a dent, rust drips;
  blocks/ruin_machineHusk  - «Остов машины»: a darkened casing with its front torn out - a black hole, a bent grate, copper
                             wires hanging, soot round it;
  items/loot_naniteDust    - «Нанитовая пыль»: a small heap of dark grey powder with green glints (32 x 32, outlined);
  entity/nanite            - the swarm's particle: a soft green-white dot (drawn additive, camera-facing);
  entity/sentryDrone       - the feral sentry drone's skin (64 x 32, RenderSentryDroneSC's boxes): rust-orange hull with
                             chipped paint and a hazard stripe, the lens' housing and glass, the cutter's barrel, arms, rotors;
  entity/mimic             - the mimic machine's parts (64 x 64, RenderMimicSC's boxes): hydraulic legs, the violet eyes, the
                             dark maw, steel teeth;
and a preview sheet next to this script (ruins_tex_preview.png).

Usage: python tools/gen_ruins_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'ruins_tex_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the Lithographers' steel: blue-grey, darker than the mod's own casings (machineCasingLV's bevel, its light from the top left)
STEEL = (86, 90, 104)
STEEL_HI = (134, 140, 156)
STEEL_MID = (104, 109, 124)
STEEL_LO = (60, 62, 74)
STEEL_DK = (34, 35, 43)
# the planet's oxide film (substrate_oxide_*): violet drifting to blue
OXIDE_V = (118, 78, 160)
OXIDE_B = (70, 104, 180)
RUST = (142, 86, 50)
RUST_D = (98, 58, 36)
RUST_L = (178, 116, 70)
SOOT = (26, 25, 28)
SOOT_L = (48, 46, 50)
CELL = (34, 52, 96)
CELL_L = (66, 104, 172)
GRID = (150, 156, 168)
PAINT = (64, 106, 104)            # the tanks' faded teal
PAINT_L = (88, 132, 126)
COPPER = (196, 112, 58)
COPPER_D = (128, 70, 36)
HOLE = (12, 12, 15)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def px(im, x, y, c, a=255):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def get(im, x, y):
    """The pixel's colour (a coordinate past the edge reads the edge's)."""
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[:3]


def noise_grid(seed, cells=4, size=S):
    """A smooth size x size value noise (-1..1) that tiles: a cells x cells grid of random values, smoothstep-bilinear."""
    r = random.Random(seed)
    g = [[r.uniform(-1, 1) for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    step = size / float(cells)
    for y in range(size):
        for x in range(size):
            fx, fy = x / step, y / step
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
            b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
            out[y][x] = a + (b - a) * ty
    return out


def grain(im, seed, k=0.06, x0=0, y0=0, x1=S - 1, y1=S - 1):
    """Per-pixel grain over a rectangle."""
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, scale(get(im, x, y), 1 + r.uniform(-k, k)))


def bevel(im, base, hi, mid, lo, dk, width=3):
    """The mod's casing frame: an outer line, a lit top-left bevel, a shaded bottom-right one, `width` wide."""
    for y in range(S):
        for x in range(S):
            if x == 0 or y == 0:
                c = mid
            elif x == S - 1 or y == S - 1:
                c = dk
            elif x < width and y < S - x or y < width and x < S - y:
                c = hi if min(x, y) == 1 else mix(hi, base, 0.4)
            elif x >= S - width or y >= S - width:
                c = lo if max(x, y) == S - 2 else mix(lo, base, 0.3)
            else:
                continue
            px(im, x, y, c)


def rivet(im, x, y):
    px(im, x, y, STEEL_HI)
    px(im, x + 1, y, STEEL_MID)
    px(im, x, y + 1, STEEL_LO)
    px(im, x + 1, y + 1, STEEL_DK)


def oxide_film(im, seed, amount, x0=3, y0=3, x1=S - 4, y1=S - 4):
    """The oxide's sheen: where the noise is high the steel takes a violet-to-blue film."""
    n = noise_grid(seed, 4)
    m = noise_grid(seed + 9, 8)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = n[y][x] - (1 - amount)
            if v > 0:
                film = mix(OXIDE_V, OXIDE_B, (m[y][x] + 1) / 2)
                px(im, x, y, mix(get(im, x, y), film, min(0.55, v * 1.4)))


def streak(im, x, y, length, seed, col=RUST, dark=RUST_D):
    """Rust running down from (x, y), thinning out."""
    r = random.Random(seed)
    for k in range(length):
        t = 1 - k / float(length)
        px(im, x, y + k, mix(get(im, x, y + k), col if k < 2 else dark, 0.75 * t + 0.15))
        if r.random() < 0.35 * t:
            px(im, x + r.choice((-1, 1)), y + k, mix(get(im, x + 1, y + k), dark, 0.4 * t))


def crack(im, pts):
    """A crack along the points: a dark line with a light lip under it."""
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        n = max(abs(bx - ax), abs(by - ay))
        for i in range(n + 1):
            x = ax + round((bx - ax) * i / float(max(1, n)))
            y = ay + round((by - ay) * i / float(max(1, n)))
            px(im, x, y, STEEL_DK)
            px(im, x, y + 1, mix(get(im, x, y + 1), STEEL_HI, 0.35))


# ---------------------------------------------------------------- blocks

def ruin_metal():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(41, 4)
    for y in range(S):
        for x in range(S):
            px(im, x, y, scale(STEEL, 1 + n[y][x] * 0.08))
    bevel(im, STEEL, STEEL_HI, STEEL_MID, STEEL_LO, STEEL_DK)
    # the seam between two plates (as the casing's), its lip
    for x in range(3, S - 3):
        px(im, x, 15, STEEL_LO)
        px(im, x, 16, STEEL_DK)
        px(im, x, 17, mix(STEEL, STEEL_HI, 0.3))
    oxide_film(im, 1414, 0.55)
    grain(im, 7, 0.05, 1, 1, S - 2, S - 2)
    # a dent: a soft dark bowl with a lit rim on its lower right
    for y in range(20, 28):
        for x in range(17, 27):
            d = ((x - 21.5) / 4.5) ** 2 + ((y - 23.5) / 3.5) ** 2
            if d < 1:
                px(im, x, y, scale(get(im, x, y), 0.78 + 0.18 * d))
            elif d < 1.35 and x > 21 and y > 23:
                px(im, x, y, mix(get(im, x, y), STEEL_HI, 0.3))
    crack(im, [(6, 21), (8, 23), (9, 22), (12, 25), (13, 27)])
    for (x, y) in ((4, 4), (26, 4), (4, 11), (26, 11), (4, 19), (26, 19), (4, 26), (26, 26)):
        rivet(im, x, y)
    for i, (x, y, l) in enumerate(((5, 6, 6), (27, 13, 3), (5, 21, 7), (26, 6, 5), (27, 28, 2))):
        streak(im, x, y, l, 90 + i)
    return im


def burnt_panel():
    im = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            px(im, x, y, STEEL)
    bevel(im, STEEL, STEEL_HI, STEEL_MID, STEEL_LO, STEEL_DK)
    r = random.Random(5)
    hot = (19.5, 12.5)                                    # where it burnt
    # the 3 x 3 cells inside a grid of light lines (as the solar panels' fronts)
    for y in range(4, S - 4):
        for x in range(4, S - 4):
            gx, gy = (x - 4) % 8, (y - 4) % 8
            d = math.hypot(x - hot[0], y - hot[1]) / 16.0
            if gx == 0 or gy == 0 or x == S - 5 or y == S - 5:
                c = GRID
                if d < 0.55 and r.random() < 0.75 - d:     # melted through near the hot spot
                    c = SOOT_L if r.random() < 0.6 else SOOT
            else:
                c = mix(CELL, CELL_L, 0.25 * (1 - gy / 8.0) + (0.25 if gx + gy == 2 else 0))
                burn = max(0.0, 1.15 - d) + r.uniform(-0.12, 0.12)
                c = mix(c, SOOT, min(1.0, burn))
            px(im, x, y, c)
    # two cells far from the fire keep a dim blue and a crack
    for (cx, cy) in ((5, 21), (13, 29 - 8)):
        for y in range(cy, cy + 7):
            for x in range(cx, cx + 7):
                if 4 <= x < S - 4 and 4 <= y < S - 4 and (x - 4) % 8 and (y - 4) % 8:
                    px(im, x, y, mix(CELL, CELL_L, 0.35 + 0.2 * ((x + y) % 3 == 0)))
    crack(im, [(6, 22), (8, 24), (9, 27)])
    # soot spread over the frame round the hot spot
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - hot[0], y - hot[1])
            if d < 15 and (x < 4 or y < 4 or x >= S - 4 or y >= S - 4):
                px(im, x, y, mix(get(im, x, y), SOOT, 0.55 * (1 - d / 15.0)))
    grain(im, 8, 0.07, 1, 1, S - 2, S - 2)
    return im


def tank_husk():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(77, 4)
    m = noise_grid(78, 8)
    for y in range(S):
        for x in range(S):
            # a rounded shell: lit a little left of the middle, dark to the edges
            t = (x + 0.5) / S
            shade = 0.62 + 0.55 * math.sin(math.pi * t) - 0.18 * max(0.0, t - 0.55)
            paint = mix(PAINT, PAINT_L, 0.3 + 0.2 * n[y][x])
            peel = n[y][x] * 0.6 + m[y][x] * 0.6
            base = paint if peel < 0.15 else (STEEL_MID if peel < 0.45 else mix(RUST, RUST_D, 0.4))
            if 0.12 < peel < 0.2:
                base = mix(paint, STEEL_HI, 0.5)          # the paint's peeling edge
            px(im, x, y, scale(base, shade))
    # the hoops: two riveted bands
    for hy in (5, 24):
        for x in range(S):
            t = (x + 0.5) / S
            shade = 0.6 + 0.5 * math.sin(math.pi * t)
            px(im, x, hy - 1, scale(STEEL_HI, shade))
            px(im, x, hy, scale(STEEL, shade))
            px(im, x, hy + 1, scale(STEEL, shade * 0.95))
            px(im, x, hy + 2, scale(STEEL_DK, shade))
            if x % 6 == 3:
                px(im, x, hy, scale(STEEL_HI, shade * 1.1))
                px(im, x, hy + 1, scale(STEEL_LO, shade))
    # a dent and rust drips from the lower hoop
    for y in range(11, 19):
        for x in range(9, 17):
            d = ((x - 12.5) / 3.5) ** 2 + ((y - 14.5) / 3.5) ** 2
            if d < 1:
                px(im, x, y, scale(get(im, x, y), 0.72 + 0.2 * d))
    for i, (x, l) in enumerate(((7, 5), (14, 3), (22, 6), (27, 4))):
        streak(im, x, 27, l, 300 + i)
    for i, (x, l) in enumerate(((10, 7), (19, 5))):
        streak(im, x, 8, l, 310 + i)
    grain(im, 9, 0.06)
    # the block's edge: a dark line all round (the husks stand side by side)
    for k in range(S):
        for (x, y) in ((k, 0), (k, S - 1)):
            px(im, x, y, mix(get(im, x, y), STEEL_DK, 0.7))
    return im


def machine_husk():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(55, 4)
    for y in range(S):
        for x in range(S):
            px(im, x, y, scale(STEEL, 0.95 + n[y][x] * 0.08))
    bevel(im, STEEL, STEEL_HI, STEEL_MID, STEEL_LO, STEEL_DK)
    r = random.Random(12)
    # the torn-out front: a ragged black hole
    cx, cy = 15.5, 16.0
    for y in range(4, S - 4):
        for x in range(4, S - 4):
            a = math.atan2(y - cy, x - cx)
            rad = 9.2 + 1.6 * math.sin(a * 5 + 1) + 1.1 * math.sin(a * 9 + 2)
            d = math.hypot(x - cx, y - cy)
            if d < rad - 1.2:
                px(im, x, y, mix(HOLE, SOOT_L, max(0.0, (d - rad + 4) / 4.0) * 0.5))
            elif d < rad:
                px(im, x, y, STEEL_DK)                     # the torn edge
            elif d < rad + 1.2 and (y > cy or x > cx):
                px(im, x, y, mix(get(im, x, y), STEEL_HI, 0.35))   # its lit lip
            elif d < rad + 6:
                px(im, x, y, mix(get(im, x, y), SOOT, 0.55 * (1 - (d - rad) / 6.0)))   # soot round it
    # a bent grate across the hole
    for k in range(-2, 3):
        for t in range(16):
            x = int(round(cx - 7 + t))
            y = int(round(cy + k * 4 + (t - 8) * 0.35 + (2 if k == 1 and t > 9 else 0)))
            d = math.hypot(x - cx, y - cy)
            if d < 9 and r.random() < 0.85:
                px(im, x, y, GRID if (t + k) % 3 else STEEL_MID)
    # copper wires hanging out of it
    for (x0, y0, length, sway) in ((11, 9, 13, 0.8), (18, 10, 10, -0.6), (14, 11, 15, 0.3)):
        for k in range(length):
            x = int(round(x0 + math.sin(k * 0.45) * sway * 2))
            y = y0 + k
            px(im, x, y, COPPER if k % 4 else COPPER_D)
        px(im, int(round(x0 + math.sin((length - 1) * 0.45) * sway * 2)), y0 + length, COPPER_D)
    for (x, y) in ((4, 4), (26, 4), (4, 26), (26, 26)):
        rivet(im, x, y)
    streak(im, 27, 6, 6, 400)
    oxide_film(im, 515, 0.35)
    grain(im, 10, 0.05, 1, 1, S - 2, S - 2)
    return im


# ---------------------------------------------------------------- the item

def outline(im):
    """A 1-pixel dark outline round everything drawn (the mod's items all have one)."""
    src = im.copy()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, OUTLINE)
                    break


def nanite_dust():
    im = Image.new('RGBA', (S, S), CLEAR)
    r = random.Random(14)
    dark, mid, light = (40, 42, 46), (66, 70, 76), (102, 108, 116)
    for y in range(10, 27):
        for x in range(4, 28):
            # a heap: a rounded cone, its top a little left of the middle
            hx = (x - 15.0) / 11.5
            top = 12.5 + 12.0 * hx * hx + (1.5 if x > 22 else 0)
            if y < top or y > 25 + (1 if 8 < x < 24 else 0):
                continue
            lit = (15.5 - x) * 0.04 + (top + 6 - y) * 0.05
            c = mix(mid, light, max(0.0, min(1.0, 0.45 + lit))) if lit > -0.1 else mix(dark, mid, 0.6 + lit)
            px(im, x, y, scale(c, 1 + r.uniform(-0.1, 0.1)))
    # loose grains beside it and the green glints of live nanites
    for (x, y) in ((3, 25), (28, 24), (27, 26), (6, 23), (25, 21)):
        px(im, x, y, mid)
    for (x, y, big) in ((12, 16, True), (18, 19, False), (9, 22, False), (21, 23, True), (15, 13, False)):
        px(im, x, y, (150, 255, 170))
        if big:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                px(im, x + dx, y + dy, (70, 200, 100))
    outline(im)
    return im


# ---------------------------------------------------------------- entities

def nanite():
    n = 32
    im = Image.new('RGBA', (n, n), CLEAR)
    c = (n - 1) / 2.0
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / (n / 2.0)
            if d >= 1:
                continue
            core = max(0.0, 1 - d / 0.28)
            col = mix((70, 255, 120), (235, 255, 240), core)
            a = (1 - d) ** 2.2
            im.putpixel((x, y), (col[0], col[1], col[2], clamp(255 * min(1.0, a * 1.6))))
    return im


def faces(u, v, w, h, d):
    """A model box's faces at texture offset (u, v), size w x h x d: name -> (u0, v0, width, height)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


def paint_box(im, u, v, w, h, d, fn):
    """fn(face, x, y, fw, fh) -> colour, (colour, alpha) or None for every pixel of every face of the box."""
    for name, (u0, v0, fw, fh) in faces(u, v, w, h, d).items():
        for y in range(fh):
            for x in range(fw):
                c = fn(name, x, y, fw, fh)
                if c is None:
                    continue
                if len(c) == 2 and isinstance(c[0], tuple):
                    px(im, u0 + x, v0 + y, c[0], c[1])
                else:
                    px(im, u0 + x, v0 + y, c)


ORANGE = (176, 92, 40)
ORANGE_L = (212, 124, 60)
ORANGE_D = (124, 62, 30)
HAZARD = (230, 190, 50)


def sentry_drone():
    im = Image.new('RGBA', (64, 32), CLEAR)
    r = random.Random(21)

    def hull(face, x, y, fw, fh):
        chip = r.random()
        base = ORANGE if face in ('top', 'front', 'back', 'left', 'right') else STEEL_LO
        if face == 'top':
            if x in (0, fw - 1) or y in (0, fh - 1):
                return ORANGE_D
            if 2 <= x <= 5 and y in (2, 4):                # a vent's slats
                return STEEL_DK
            if (x, y) in ((1, 1), (6, 6)):
                return STEEL_HI                            # bolts
        if face in ('front', 'back', 'left', 'right'):
            if y == 0:
                return ORANGE_L
            if y == fh - 1:
                return ORANGE_D
            if face in ('left', 'right') and y == 2:       # a hazard stripe along the sides
                return HAZARD if (x // 2) % 2 == 0 else STEEL_DK
        if face == 'bottom':
            return STEEL_DK if (x + y) % 3 == 0 else STEEL_LO
        if chip < 0.12:
            return STEEL_MID if chip < 0.08 else RUST      # paint chipped to steel and rust
        return scale(base, 1 + r.uniform(-0.06, 0.06))

    paint_box(im, 0, 0, 8, 5, 8, hull)
    # the lens' housing (dark) and its glass (amber-white: full-bright in game)
    paint_box(im, 0, 13, 3, 3, 2, lambda f, x, y, fw, fh: STEEL_DK if f == 'front' and x == 1 and y == 1 else
              (STEEL_LO if (x + y) % 2 else (46, 48, 56)))
    paint_box(im, 0, 19, 2, 2, 1, lambda f, x, y, fw, fh: (255, 246, 214) if f == 'front' and x == 0 and y == 0 else (250, 210, 130))
    # the cutter's barrel: steel, its muzzle ion-blue
    paint_box(im, 12, 13, 1, 1, 6, lambda f, x, y, fw, fh: (90, 200, 255) if f == 'front' else
              (STEEL_HI if f == 'top' else STEEL_MID))
    # the antenna: dark, a red tip
    paint_box(im, 28, 13, 1, 4, 1, lambda f, x, y, fw, fh: (220, 50, 40) if f == 'top' or y == 0 else STEEL_DK)
    # the arms: dark steel with a lit edge
    paint_box(im, 32, 0, 5, 1, 1, lambda f, x, y, fw, fh: STEEL_MID if f == 'top' else STEEL_LO)

    # the rotors: a blur - a translucent ring round a dark hub
    def rotor(face, x, y, fw, fh):
        if face in ('top', 'bottom'):
            d = math.hypot(x - 2, y - 2)
            if d < 0.8:
                return STEEL_DK
            if d < 2.6:
                return ((150, 156, 168), 120 if (x + y) % 2 else 90)
            return None
        return (STEEL_DK, 160)

    paint_box(im, 32, 4, 5, 1, 5, rotor)
    return im


TOOTH = (214, 218, 224)
TOOTH_D = (150, 154, 164)
MAW = (40, 14, 46)
MAW_D = (14, 6, 18)
EYE = (210, 120, 255)


def mimic():
    im = Image.new('RGBA', (64, 64), CLEAR)

    def leg(face, x, y, fw, fh):
        if face == 'bottom':
            return SOOT                                    # a rubber pad
        if face == 'top':
            return STEEL_DK
        if y < 2:
            return STEEL_LO                                # the joint's sleeve
        if x == fw // 2:
            return (196, 202, 212) if y % 2 else (168, 174, 186)   # the piston's chrome rod
        return STEEL_DK if y == fh - 1 else STEEL

    paint_box(im, 0, 0, 3, 6, 3, leg)
    paint_box(im, 16, 0, 2, 1, 1, lambda f, x, y, fw, fh: (250, 220, 255) if f == 'front' and x == 1 else EYE)

    # the maw: a deep violet-black, darkest at the back where the gullet is
    def maw(face, x, y, fw, fh):
        if face != 'top':
            return MAW_D
        d = math.hypot((x - 6.5) / 7.0, (y - 9.0) / 6.0)
        c = mix(MAW, MAW_D, min(1.0, d * 0.4 + (y / 13.0) * 0.6))
        if 4 <= x <= 9 and 9 <= y <= 12:
            c = HOLE
        if y in (2, 3) and 3 <= x <= 10 and (x + y) % 2:
            c = (86, 30, 70)                               # a ridge
        return c

    paint_box(im, 0, 10, 14, 1, 14, maw)

    # teeth: their tips sparse, their roots a solid row; lower teeth point up (row 0 the tips), the lid's hang down
    def teeth(up):
        def fn(face, x, y, fw, fh):
            if face in ('top', 'bottom'):
                tips = (face == 'top') == up
                return (TOOTH if x % 3 == 1 else None) if tips else TOOTH_D
            tip_row = 0 if up else fh - 1
            if y == tip_row:
                return TOOTH if x % 3 == 1 else None
            return TOOTH if x % 3 else TOOTH_D
        return fn

    paint_box(im, 0, 26, 14, 2, 1, teeth(True))
    paint_box(im, 0, 30, 1, 2, 13, teeth(True))
    paint_box(im, 32, 26, 14, 2, 1, teeth(False))
    return im


def main():
    out = {
        ('blocks', 'ruin_ruinMetal'): ruin_metal(),
        ('blocks', 'ruin_burntPanel'): burnt_panel(),
        ('blocks', 'ruin_tankHusk'): tank_husk(),
        ('blocks', 'ruin_machineHusk'): machine_husk(),
        ('items', 'loot_naniteDust'): nanite_dust(),
        ('entity', 'nanite'): nanite(),
        ('entity', 'sentryDrone'): sentry_drone(),
        ('entity', 'mimic'): mimic(),
    }
    for (folder, name), im in out.items():
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
        im.save(os.path.join(TEX, folder, name + '.png'))
    # the preview: the four blocks and the item x6, the entities' sheets x4, on grey
    z = 6
    blocks = [('blocks', 'ruin_ruinMetal'), ('blocks', 'ruin_burntPanel'), ('blocks', 'ruin_tankHusk'), ('blocks', 'ruin_machineHusk'),
              ('items', 'loot_naniteDust'), ('entity', 'nanite')]
    sheet = Image.new('RGBA', (len(blocks) * (S * z + 10) + 10, S * z + 64 * 4 + 40), (70, 72, 76, 255))
    x = 10
    for key in blocks:
        big = out[key].resize((S * z, S * z), Image.NEAREST)
        sheet.paste(big, (x, 10), big)
        x += S * z + 10
    x = 10
    for key in (('entity', 'sentryDrone'), ('entity', 'mimic')):
        im = out[key]
        big = im.resize((im.size[0] * 4, im.size[1] * 4), Image.NEAREST)
        sheet.paste(big, (x, S * z + 25), big)
        x += im.size[0] * 4 + 20
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
