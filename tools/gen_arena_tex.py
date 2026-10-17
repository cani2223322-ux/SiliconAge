# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 5, package A: the Breakdown Epicentre, its Console and the arena's relics (Silicon Age,
docs/plan-substrate-tech-f5.md §4.7).

The arena is the Lithographers' last work: plating darker and more violet than their ruins' steel (gen_ruins_tex.py,
gen_camps_tex.py's Vault), scarred by the Breakdown - thin violet-white cracks with a burnt rim. The canyons' oxide bands
(substrate_oxide_*) are read for the columns.

Writes into src/main/resources/assets/siliconage/textures (32 x 32, as the ruins and the mod's casings):
  blocks/arena_arenaFloor             - «Плита Эпицентра»: four bevelled plates, rivets at their corners, a Breakdown scar across
                                        (it enters and leaves at the same height, so the floor's tiles join);
  blocks/arena_arenaWall              - «Стена Эпицентра»: four upright panels, a ledge across, a scar running down one panel, soot;
  blocks/arena_arenaColumn            - «Колонна Эпицентра»: the canyons' four oxide bands between two riveted plating straps;
  blocks/arena_coilPedestal           - «Постамент катушки», its side: plating, two copper bands, a glowing blue slit;
  blocks/arena_coilPedestal_top       - its top: a ring of copper round a blue core, four bolts;
  blocks/arena_breakdownConsole       - «Консоль Пробоя», its sides: a dark slab, the reactor's pale round window, small lamps,
                                        a grille;
  blocks/arena_breakdownConsole_top   - its top: the key's round socket (four notches - the four Fragments), dark, a pale glow;
  blocks/arena_breakdownConsole_key   - the same with the Breakdown Key in it, its traces lit violet-white;
  blocks/arena_arenaLamp              - «Лампа Эпицентра»: wall plating round a cold white strip behind a grille;
  items/relic_breakdownKey            - «Ключ Пробоя»: the four Fragments' quarters joined into a whole dark disc, the seams
                                        showing, the violet traces running on across them to a white-hot core;
  items/relic_breakdownMatrix         - «Матрица Пробоя»: a dark crystal wafer on its point, a lattice of glowing cells, a white
                                        fracture through it;
  items/arenaCoordinator              - «Координатор арены»: a handheld of the Lithographers' plating - a round violet screen with
                                        a target's rings and cross, a blip, an antenna with a violet tip;
and a preview sheet next to this script (arena_tex_preview.png).

Usage: python tools/gen_arena_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'arena_tex_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the Epicentre's plating: the Lithographers' steel, darker and drawn to violet (their ruins' STEEL is (86, 90, 104))
PLATE = (46, 44, 60)
PLATE_HI = (92, 90, 114)
PLATE_MID = (66, 64, 84)
PLATE_LO = (32, 30, 42)
PLATE_DK = (17, 16, 23)
# the Breakdown: violet-white, a burnt rim round it
VIOLET = (132, 86, 200)
VIOLET_L = (214, 186, 255)
VIOLET_D = (70, 40, 110)
WHITE = (246, 238, 255)
BURNT = (24, 18, 30)
SOOT = (26, 25, 28)
# the coils' copper and their blue core (the Field Generator's shard blue)
COPPER = (196, 112, 58)
COPPER_L = (232, 158, 96)
COPPER_D = (128, 70, 36)
BLUE = (60, 150, 230)
BLUE_L = (170, 226, 255)
BLUE_D = (24, 60, 110)
# the reactor's pale glow and the lamp's cold white
PALE = (196, 214, 236)
COLD = (232, 244, 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def px(im, x, y, c, a=255):
    w, h = im.size
    x, y = int(round(x)), int(round(y))
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def get(im, x, y):
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[:3]


def alpha(im, x, y):
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[3]


def noise_grid(seed, cells=4, size=S):
    """A smooth size x size value noise (-1..1) that tiles (gen_ruins_tex.py's)."""
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
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if alpha(im, x, y):
                px(im, x, y, scale(get(im, x, y), 1 + r.uniform(-k, k)))


def rivet(im, x, y):
    px(im, x, y, PLATE_HI)
    px(im, x + 1, y, PLATE_MID)
    px(im, x, y + 1, PLATE_LO)
    px(im, x + 1, y + 1, PLATE_DK)


def load(rel, size=S):
    p = os.path.join(TEX, rel)
    im = Image.open(p).convert('RGBA')
    if im.size != (size, size):
        im = im.resize((size, size), Image.NEAREST)
    return im


def outline(im):
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


def plating(seed, k=0.07):
    """The bare plating: the base colour with a soft tiling noise."""
    im = Image.new('RGBA', (S, S))
    n = noise_grid(seed, 4)
    for y in range(S):
        for x in range(S):
            px(im, x, y, scale(PLATE, 1 + n[y][x] * k))
    return im


def plate_box(im, x0, y0, x1, y1):
    """One plate's bevel inside the plating: a lit top-left line, a shaded bottom-right one, the seam dark."""
    for x in range(x0, x1 + 1):
        px(im, x, y0, PLATE_DK)
        px(im, x, y0 + 1, mix(get(im, x, y0 + 1), PLATE_HI, 0.55))
        px(im, x, y1, mix(get(im, x, y1), PLATE_LO, 0.7))
    for y in range(y0, y1 + 1):
        px(im, x0, y, PLATE_DK)
        px(im, x0 + 1, y, mix(get(im, x0 + 1, y), PLATE_HI, 0.45))
        px(im, x1, y, mix(get(im, x1, y), PLATE_LO, 0.7))
    px(im, x0, y0, PLATE_DK)


def scar(im, points, seed, width=1, glow=True):
    """A Breakdown scar along `points`: a violet-white core line, violet beside it, a burnt rim round both."""
    r = random.Random(seed)
    path = []
    for (ax, ay), (bx, by) in zip(points, points[1:]):
        n = max(abs(bx - ax), abs(by - ay), 1)
        for t in range(n + 1):
            path.append((int(round(ax + (bx - ax) * t / float(n))), int(round(ay + (by - ay) * t / float(n)))))
    for (x, y) in path:                                      # the burnt rim first
        for dx in range(-width - 1, width + 2):
            for dy in range(-width - 1, width + 2):
                if 0 <= x + dx < S and 0 <= y + dy < S:
                    d = math.hypot(dx, dy)
                    if d <= width + 1.2:
                        px(im, x + dx, y + dy, mix(get(im, x + dx, y + dy), BURNT, 0.55 * (1 - d / (width + 2.0))))
    for (x, y) in path:
        for dx in range(-width + 1, width):
            for dy in range(-width + 1, width):
                px(im, x + dx, y + dy, VIOLET)
    if glow:
        for k, (x, y) in enumerate(path):
            px(im, x, y, WHITE if r.random() < 0.35 else VIOLET_L)


# ---------------------------------------------------------------- the blocks

def arena_floor():
    im = plating(101)
    for (x0, y0) in ((0, 0), (16, 0), (0, 16), (16, 16)):
        plate_box(im, x0, y0, x0 + 15, y0 + 15)
        rivet(im, x0 + 3, y0 + 3)
        rivet(im, x0 + 11, y0 + 11)
    # a short Breakdown scar across one plate (the long ones are laid in wall plating by the carve - ComponentArenaSC.scar)
    scar(im, [(19, 27), (22, 24), (21, 21), (25, 19), (28, 20)], 102, glow=False)
    for (x, y) in ((22, 24), (25, 19)):
        px(im, x, y, VIOLET_L)
    # a scorch on the opposite plate
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 8.5, y - 8.5)
            if d < 5:
                px(im, x, y, mix(get(im, x, y), BURNT, 0.4 * (1 - d / 5.0)))
    grain(im, 104, 0.04)
    return im


def arena_wall():
    im = plating(111, 0.05)
    for k in range(4):                                       # four upright panels
        x0 = k * 8
        plate_box(im, x0, 0, x0 + 7, S - 1)
    for x in range(S):                                       # the ledge across
        px(im, x, 14, PLATE_HI)
        px(im, x, 15, PLATE_MID)
        px(im, x, 16, PLATE_LO)
        px(im, x, 17, PLATE_DK)
    for k in range(4):
        rivet(im, k * 8 + 3, 4)
        rivet(im, k * 8 + 3, 25)
    # a short scar down the third panel (dim: the ring repeats it all round), and soot rising from the floor
    scar(im, [(20, 3), (19, 6), (21, 9), (19, 12)], 112, glow=False)
    px(im, 19, 6, VIOLET_L)
    for y in range(24, S):
        for x in range(S):
            px(im, x, y, mix(get(im, x, y), SOOT, (y - 23) / 9.0 * 0.35))
    grain(im, 114, 0.04)
    return im


def arena_column():
    bands = ['substrate_oxide_violet', 'substrate_oxide_blue', 'substrate_oxide_gold', 'substrate_oxide_green']
    im = Image.new('RGBA', (S, S))
    for k, name in enumerate(bands):                         # the canyons' oxide, four bands of 8
        src = load('blocks/%s.png' % name)
        for y in range(8):
            for x in range(S):
                c = get(src, x, k * 8 + y)
                if y == 0:
                    c = scale(c, 1.12)
                elif y == 7:
                    c = scale(c, 0.78)
                px(im, x, k * 8 + y, c)
    for x0 in (0, S - 4):                                    # two riveted plating straps up the sides
        for y in range(S):
            for x in range(x0, x0 + 4):
                c = PLATE_MID if x == x0 + 1 else (PLATE_DK if x == x0 + 3 or x == x0 else PLATE)
                px(im, x, y, c)
        for y in range(3, S, 8):
            px(im, x0 + 1, y, PLATE_HI)
            px(im, x0 + 2, y, PLATE_MID)
            px(im, x0 + 1, y + 1, PLATE_LO)
    grain(im, 121, 0.04)
    return im


def pedestal_side():
    im = plating(131, 0.05)
    plate_box(im, 0, 0, S - 1, S - 1)
    for (y0, y1) in ((4, 6), (25, 27)):                      # the copper bands
        for y in range(y0, y1 + 1):
            for x in range(1, S - 1):
                c = COPPER_L if y == y0 else (COPPER_D if y == y1 else COPPER)
                px(im, x, y, c)
    for y in range(10, 22):                                  # the blue slit, glowing from its middle
        for x in range(13, 19):
            t = 1 - max(abs(x - 15.5) / 3.0, abs(y - 15.5) / 6.0)
            c = mix(BLUE_D, BLUE_L, t ** 0.7)
            if x in (13, 18) or y in (10, 21):
                c = PLATE_DK
            px(im, x, y, c)
    grain(im, 132, 0.04)
    return im


def pedestal_top():
    im = plating(141, 0.05)
    plate_box(im, 0, 0, S - 1, S - 1)
    cx = cy = 15.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if 8.6 <= d <= 12.4:                             # the copper ring, lit from the top left
                lit = 0.35 + 0.45 * max(0.0, (cx - x + cy - y) / 24.0)
                c = mix(COPPER_D, COPPER_L, lit)
                if d > 11.6 or d < 9.2:
                    c = COPPER_D
                if int(math.degrees(math.atan2(y - cy, x - cx)) + 360) % 30 < 4:
                    c = scale(c, 0.7)                        # the windings
                px(im, x, y, c)
            elif d < 6.6:                                    # the blue core
                px(im, x, y, mix(BLUE_L, BLUE_D, d / 6.6) if d > 1.2 else WHITE)
            elif d < 8.6:
                px(im, x, y, PLATE_DK)
    for (bx, by) in ((3, 3), (27, 3), (3, 27), (27, 27)):
        rivet(im, bx, by)
    grain(im, 142, 0.03)
    return im


def console_side():
    im = plating(151, 0.05)
    plate_box(im, 0, 0, S - 1, S - 1)
    # the reactor's window: a pale round glow behind a dark rim, high on the face
    cx, cy = 15.5, 11.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d < 7.2:
                c = mix(WHITE, PALE, d / 5.0) if d < 5.4 else PLATE_DK
                if 5.4 <= d < 6.2:
                    c = mix(VIOLET_D, VIOLET, 0.5)
                px(im, x, y, c)
    # small indicator lamps and a grille below
    for k, c in enumerate((VIOLET_L, PALE, VIOLET, PALE)):
        px(im, 6 + k * 6, 22, c)
        px(im, 7 + k * 6, 22, scale(c, 0.8))
    for y in range(25, 29):
        for x in range(4, 28):
            px(im, x, y, PLATE_DK if (x % 2 == 0) else PLATE_LO)
    rivet(im, 3, 3)
    rivet(im, 27, 3)
    grain(im, 152, 0.03)
    return im


def socket(im, filled):
    """The key's round socket in the console's top (four notches - the four Fragments); `filled` - the Key in it, lit."""
    cx = cy = 15.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            ang = math.degrees(math.atan2(y - cy, x - cx)) % 90
            notch = 8.2 <= d < 10.4 and (ang < 9 or ang > 81)
            if d < 8.2 or notch:
                if filled:
                    lit = 0.35 + 0.35 * max(0.0, (cx - x + cy - y) / 20.0)
                    c = mix((22, 16, 32), (62, 48, 84), lit)
                else:
                    c = mix((8, 7, 12), (26, 24, 34), d / 8.2)
                px(im, x, y, c)
            elif d < 11.2:
                px(im, x, y, mix(PALE, VIOLET, 0.5) if not filled else VIOLET_L)   # the socket's glowing lip
            elif d < 12.2:
                px(im, x, y, PLATE_DK)
    if filled:
        traces(im, cx, cy, 7.8)
        for x in range(S):                                   # the quarters' seams
            for y in range(S):
                d = math.hypot(x - cx, y - cy)
                if 1.6 < d < 8.0 and (abs(x - cx) < 0.6 or abs(y - cy) < 0.6):
                    px(im, x, y, (12, 9, 18))


def traces(im, cx, cy, rad):
    """The Breakdown Key's traces: two rings and the spokes of each quarter, violet-white, a white-hot core."""
    for rr in (rad * 0.42, rad * 0.78):
        for a in range(0, 360, 3):
            ang = math.radians(a)
            x, y = int(round(cx + math.cos(ang) * rr)), int(round(cy - math.sin(ang) * rr))
            if a % 90 not in (0, 3, 87):
                px(im, x, y, VIOLET_L if a % 12 < 5 else VIOLET)
    for a in (20, 55, 110, 145, 200, 235, 290, 325):
        ang = math.radians(a)
        for t in range(2, int(rad)):
            x, y = int(round(cx + math.cos(ang) * t)), int(round(cy - math.sin(ang) * t))
            px(im, x, y, VIOLET)
    for y in range(int(cy) - 2, int(cy) + 3):
        for x in range(int(cx) - 2, int(cx) + 3):
            d = math.hypot(x - cx, y - cy)
            if d < 2.2:
                px(im, x, y, WHITE if d < 1.2 else VIOLET_L)


def console_top(filled):
    im = plating(161, 0.05)
    plate_box(im, 0, 0, S - 1, S - 1)
    socket(im, filled)
    for (bx, by) in ((2, 2), (28, 2), (2, 28), (28, 28)):
        rivet(im, bx, by)
    if not filled:                                           # a faint pale glow seeping round the empty socket
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - 15.5, y - 15.5)
                if 12.2 <= d < 14.5:
                    px(im, x, y, mix(get(im, x, y), PALE, 0.14 * (14.5 - d)))
    grain(im, 162, 0.03)
    return im


def arena_lamp():
    im = arena_wall()
    for y in range(9, 23):                                   # the strip's housing
        for x in range(2, 30):
            if y in (9, 22) or x in (2, 29):
                px(im, x, y, PLATE_DK)
            else:
                t = 1 - abs(y - 15.5) / 6.5
                c = mix(PALE, COLD, t ** 0.6)
                if (x - 2) % 5 == 0:
                    c = mix(PLATE_MID, PLATE_HI, 0.4)        # the grille's bars
                px(im, x, y, c)
    return im


# ---------------------------------------------------------------- the items

def breakdown_key():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx = cy = 15.5
    rad = 14.0
    r = random.Random(171)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d < rad:
                lit = 0.35 + 0.35 * max(0.0, (cx - x + cy - y) / 28.0)
                c = mix((22, 16, 32), (62, 48, 84), lit)
                if d > rad - 1.6:
                    c = (84, 66, 112)                        # the rim
                px(im, x, y, c)
    traces(im, cx, cy, rad - 2.0)
    for y in range(S):                                       # the four Fragments' seams, jagged
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if 2.4 < d < rad - 0.4:
                jx = abs(x - cx - (0.6 if (y // 3) % 2 else -0.4))
                jy = abs(y - cy - (0.6 if (x // 3) % 2 else -0.4))
                if jx < 0.55 or jy < 0.55:
                    px(im, x, y, (10, 8, 16))
    for k in range(6):                                       # a few sparks off the core
        a = r.uniform(0, math.pi * 2)
        t = r.uniform(3.0, 5.0)
        px(im, cx + math.cos(a) * t, cy + math.sin(a) * t, WHITE)
    outline(im)
    return im


def breakdown_matrix():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx = cy = 15.5
    half = 13.0
    for y in range(S):
        for x in range(S):
            u, v = (x - cx + y - cy) / 1.414, (x - cx - (y - cy)) / 1.414   # the wafer on its point
            m = max(abs(u), abs(v))
            if m < half:
                lit = 0.3 + 0.4 * max(0.0, -(x - cx + y - cy) / 30.0)
                c = mix((20, 14, 34), (70, 50, 100), lit)
                if m > half - 1.4:
                    c = (110, 84, 150)                       # its bevelled edge
                cu, cv = (u + half) % 4.0, (v + half) % 4.0
                if m < half - 2 and (cu < 0.8 or cv < 0.8):
                    c = mix(c, VIOLET, 0.75)                 # the lattice
                elif m < half - 2 and 1.6 < cu < 2.6 and 1.6 < cv < 2.6:
                    c = mix(VIOLET_L, PALE, 0.3) if (int(u + 40) + int(v + 40)) % 3 else VIOLET   # a lit cell
                px(im, x, y, c)
    scar(im, [(9, 7), (13, 12), (12, 16), (17, 19), (16, 23), (21, 25)], 181)   # the white fracture
    for (x, y) in ((13, 12), (17, 19)):
        px(im, x, y, WHITE)
    # keep the scar's rim inside the wafer
    for y in range(S):
        for x in range(S):
            u, v = (x - cx + y - cy) / 1.414, (x - cx - (y - cy)) / 1.414
            if max(abs(u), abs(v)) >= half:
                px(im, x, y, CLEAR, 0)
    outline(im)
    return im


def arena_coordinator():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the grip: the plating, a copper band
    for y in range(19, 30):
        for x in range(12, 20):
            c = PLATE_DK if x in (12, 19) else (PLATE_MID if x == 13 else PLATE)
            if y in (23, 24):
                c = COPPER if y == 23 else COPPER_D
            px(im, x, y, c)
    # the round body and its screen
    for y in range(2, 22):
        for x in range(4, 28):
            d = math.hypot(x - 15.5, y - 11.5)
            if d < 10.2:
                if d > 8.6:
                    lit = 0.3 + 0.5 * max(0.0, (15.5 - x + 11.5 - y) / 16.0)
                    px(im, x, y, mix(PLATE_LO, PLATE_HI, lit))
                else:
                    c = mix((26, 12, 46), (78, 44, 130), max(0.0, 1 - d / 8.6))
                    if abs(d - 3.6) < 0.5 or abs(d - 6.6) < 0.45:
                        c = VIOLET                           # the target's rings
                    if (abs(x - 15.5) < 0.6 or abs(y - 11.5) < 0.6) and d < 8.0:
                        c = mix(c, VIOLET_L, 0.6)            # its cross
                    px(im, x, y, c)
    for (x, y) in ((19, 7), (20, 7), (19, 8), (20, 8)):      # the blip: the arena, to the north-east
        px(im, x, y, WHITE if (x, y) == (19, 7) else VIOLET_L)
    # the antenna and its violet tip
    for t in range(6):
        px(im, 24 + t // 2, 4 - t, PLATE_HI if t % 2 else PLATE_MID)
    px(im, 27, 0, VIOLET_L)
    px(im, 27, 1, VIOLET)
    outline(im)
    return im


def save(rel, im):
    im.save(os.path.join(TEX, rel))
    return im


def main():
    blocks = [('blocks/arena_arenaFloor.png', arena_floor()), ('blocks/arena_arenaWall.png', arena_wall()),
              ('blocks/arena_arenaColumn.png', arena_column()), ('blocks/arena_coilPedestal.png', pedestal_side()),
              ('blocks/arena_coilPedestal_top.png', pedestal_top()), ('blocks/arena_breakdownConsole.png', console_side()),
              ('blocks/arena_breakdownConsole_top.png', console_top(False)),
              ('blocks/arena_breakdownConsole_key.png', console_top(True)), ('blocks/arena_arenaLamp.png', arena_lamp())]
    items = [('items/relic_breakdownKey.png', breakdown_key()), ('items/relic_breakdownMatrix.png', breakdown_matrix()),
             ('items/arenaCoordinator.png', arena_coordinator())]
    for rel, im in blocks + items:
        save(rel, im)
    # the preview: every texture x6 on the mod's dark grey, and a 3 x 3 patch of the floor (its tiles joining)
    k = 6
    cols = 7
    all_ = blocks + items
    rows = (len(all_) + cols - 1) // cols
    cell = S * k + 8
    sheet = Image.new('RGBA', (cols * cell + 8, rows * cell + 8 + S * 3 * 3 + 8), (64, 64, 68, 255))
    for i, (rel, im) in enumerate(all_):
        big = im.resize((S * k, S * k), Image.NEAREST)
        sheet.paste(big, (8 + (i % cols) * cell, 8 + (i // cols) * cell), big)
    floor = blocks[0][1].resize((S * 3, S * 3), Image.NEAREST)
    for a in range(3):
        for b in range(3):
            sheet.paste(floor, (8 + a * S * 3, 8 + rows * cell + b * S * 3), floor)
    sheet.save(PREVIEW)
    print('wrote %d textures and %s' % (len(all_), PREVIEW))


if __name__ == '__main__':
    main()
