# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 5, package J: the Lithographers' lore - journals and the bridge wrecks (Silicon Age,
docs/plan-substrate-tech-f5.md §6). In the style of tools/gen_ruins_tex.py (the same Lithographers' steel, oxide film, soot, copper).

Writes into src/main/resources/assets/siliconage/textures:
  blocks/ruin_burntCoil - «Выгоревшая катушка»: a segment of the bridge ring's coil - copper windings round its core in the
                          Lithographers' steel housing (two clamps across them), burnt out in the Breakdown: soot from one hot spot,
                          turns melted through and broken, a violet-blue discharge film, rust from the clamps (32 x 32);
  items/journal         - «Журнал Литографа» №1..№11: a worn logbook - a dark slate-violet cover with a darker spine and its bands,
                          the yellowed pages' edge, a copper plate with an etched chip, one corner burnt (32 x 32, outlined);
  items/journal12       - №12: the same book bound in black-indigo with gilt corners, on the cover a green world and its one small
                          moon (32 x 32, outlined);
and a preview sheet next to this script (finale_lore_tex_preview.png).

Usage: python tools/gen_finale_lore_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'finale_lore_tex_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the Lithographers' steel (gen_ruins_tex.py)
STEEL = (86, 90, 104)
STEEL_HI = (134, 140, 156)
STEEL_MID = (104, 109, 124)
STEEL_LO = (60, 62, 74)
STEEL_DK = (34, 35, 43)
OXIDE_V = (118, 78, 160)
OXIDE_B = (70, 104, 180)
RUST = (142, 86, 50)
RUST_D = (98, 58, 36)
SOOT = (26, 25, 28)
SOOT_L = (48, 46, 50)
COPPER = (196, 112, 58)
COPPER_HI = (232, 162, 104)
COPPER_D = (128, 70, 36)
COPPER_DK = (78, 42, 24)
# the journals
COVER = (64, 56, 86)              # slate-violet leather
COVER_HI = (92, 84, 120)
COVER_LO = (42, 36, 58)
SPINE = (34, 29, 46)
PAPER = (222, 208, 170)
PAPER_LO = (178, 162, 124)
PLATE = (176, 112, 64)
PLATE_HI = (220, 160, 104)
PLATE_LO = (112, 66, 38)
ETCH = (60, 128, 120)             # the oxide-teal of the etched chip
BLACK = (24, 22, 34)              # №12's binding
BLACK_HI = (48, 44, 66)
GILT = (214, 176, 84)
GILT_LO = (150, 116, 48)
WORLD = (84, 176, 96)
WORLD_HI = (150, 220, 140)
WORLD_LO = (40, 104, 70)
SEA = (54, 108, 150)
MOON = (226, 228, 236)


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
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[:3]


def alpha(im, x, y):
    w, h = im.size
    return im.getpixel((x, y))[3] if 0 <= x < w and 0 <= y < h else 0


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


def bevel_rows(im, rows, base, hi, lo, dk):
    """A horizontal band of the housing over rows (y0, y1): a lit top line, a shaded bottom one."""
    y0, y1 = rows
    for y in range(y0, y1 + 1):
        for x in range(S):
            c = hi if y == y0 else dk if y == y1 else lo if y == y1 - 1 else base
            px(im, x, y, c)


def streak(im, x, y, length, seed, col=RUST, dark=RUST_D):
    r = random.Random(seed)
    for k in range(length):
        t = 1 - k / float(length)
        px(im, x, y + k, mix(get(im, x, y + k), col if k < 2 else dark, 0.7 * t + 0.15))
        if r.random() < 0.3 * t:
            px(im, x + r.choice((-1, 1)), y + k, mix(get(im, x + 1, y + k), dark, 0.4 * t))


def outline(im):
    """The mod's item outline: a dark pixel on every clear one next to the drawing (4-neighbours)."""
    w, h = im.size
    edge = []
    for y in range(h):
        for x in range(w):
            if alpha(im, x, y):
                continue
            if any(alpha(im, x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                edge.append((x, y))
    for (x, y) in edge:
        px(im, x, y, OUTLINE)


# ---------------------------------------------------------------- the block

def burnt_coil():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(1407, 4)
    m = noise_grid(1408, 8)
    r = random.Random(7)
    hot = (13.5, 17.5)                                    # where the discharge burnt through
    # the windings: copper turns three pixels each, round - lit on top, a dark seam under every turn - tarnished dark
    broken = set()
    for k in range(5):
        broken.add((r.randrange(4, 28), 4 + 3 * r.randrange(0, 8)))
    for y in range(S):
        for x in range(S):
            turn = (y - 4) % 3
            c = (COPPER_HI, COPPER, COPPER_DK)[turn]
            c = mix(c, COPPER_D, 0.35 + 0.15 * n[y][x])  # tarnished all over
            px(im, x, y, c)
    # broken turns: a black gap with molten beads either side
    for (bx, by) in broken:
        for x in range(bx - 1, bx + 2):
            px(im, x, by, SOOT)
            px(im, x, by + 1, SOOT_L)
        px(im, bx - 2, by, COPPER_HI)
        px(im, bx + 2, by + 1, COPPER)
    # the burn: soot from the hot spot over most of the face, the turns melted together near it
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - hot[0], (y - hot[1]) * 1.15) / 17.0
            burn = max(0.0, 1.1 - d) + 0.3 * n[y][x] + 0.15 * m[y][x]
            if burn > 0:
                c = mix(get(im, x, y), SOOT if burn > 0.55 else SOOT_L, min(0.9, burn * 1.1))
                if burn > 0.8 and (x * 7 + y * 3) % 5 == 0:
                    c = mix(c, COPPER_D, 0.55)            # melted copper showing through the soot
                px(im, x, y, c)
    # the discharge's film: violet to blue blooms over the cooler turns
    for y in range(S):
        for x in range(S):
            v = -n[y][x] * 0.7 + m[y][x] * 0.5 - 0.15
            if v > 0:
                film = mix(OXIDE_V, OXIDE_B, (m[y][x] + 1) / 2)
                px(im, x, y, mix(get(im, x, y), film, min(0.6, v * 1.6)))
    # the housing: a steel band on top and at the bottom (as the bridge ring's casing), two clamps across the windings
    bevel_rows(im, (0, 3), STEEL, STEEL_HI, STEEL_LO, STEEL_DK)
    bevel_rows(im, (28, 31), STEEL, STEEL_HI, STEEL_LO, STEEL_DK)
    for cx in (7, 23):
        for y in range(4, 28):
            px(im, cx - 1, y, STEEL_HI)
            px(im, cx, y, STEEL_MID)
            px(im, cx + 1, y, STEEL)
            px(im, cx + 2, y, STEEL_DK)
        for y in (6, 14, 22):
            px(im, cx, y, STEEL_HI)                       # the clamp's bolts
            px(im, cx + 1, y, STEEL_LO)
    for i, (x, y, l) in enumerate(((8, 4, 5), (24, 4, 7), (9, 28, 3), (3, 1, 2), (28, 1, 2))):
        streak(im, x, y, l, 500 + i)
    # soot over the housing near the hot spot, the steel's grain
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - hot[0], y - hot[1])
            if d < 13 and (y < 4 or y > 27 or x in (6, 7, 8, 9)):
                px(im, x, y, mix(get(im, x, y), SOOT, 0.5 * (1 - d / 13.0)))
    grain(im, 9, 0.06)
    return im


# ---------------------------------------------------------------- the journals

def book(cover, cover_hi, cover_lo, spine, edge_col, seed, burnt=True):
    """The book's body (no emblem): cover x 6..27, y 3..27, the spine on the left, the pages' edge right and bottom."""
    im = Image.new('RGBA', (S, S), CLEAR)
    n = noise_grid(seed, 4)
    m = noise_grid(seed + 1, 8)
    x0, x1, y0, y1 = 6, 26, 3, 27
    # the pages, a pixel out from under the cover (right and bottom), their lines
    for y in range(y0 + 2, y1 + 3):
        for x in range(x0 + 3, x1 + 3):
            if x > x1 or y > y1:
                c = PAPER if (x + y) % 2 else mix(PAPER, PAPER_LO, 0.5)
                if y > y1 and x % 3 == 0 or x > x1 and y % 3 == 0:
                    c = PAPER_LO
                px(im, x, y, c)
    # the cover: leather - the noise, lit along the top, a shade to the bottom right
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            t = (x - x0) / float(x1 - x0) * 0.5 + (y - y0) / float(y1 - y0) * 0.5
            c = mix(cover_hi, cover_lo, t)
            c = scale(c, 1 + 0.09 * n[y][x] + 0.05 * m[y][x])
            px(im, x, y, c)
    for x in range(x0, x1 + 1):
        px(im, x, y0, mix(cover_hi, (255, 255, 255), 0.15))
    for y in range(y0, y1 + 1):
        px(im, x1, y, mix(cover_lo, (0, 0, 0), 0.3))
    for x in range(x0, x1 + 1):
        px(im, x, y1, mix(cover_lo, (0, 0, 0), 0.3))
    # the spine: darker, round, three raised bands
    for y in range(y0, y1 + 1):
        for x in range(x0 - 2, x0 + 3):
            k = (x - (x0 - 2)) / 4.0
            c = scale(spine, 0.75 + 0.6 * math.sin(math.pi * (0.2 + 0.6 * k)))
            px(im, x, y, c)
        for by in (y0 + 4, (y0 + y1) // 2, y1 - 4):
            if abs(y - by) <= 0:
                for x in range(x0 - 2, x0 + 3):
                    px(im, x, y, scale(spine, 1.6))
            elif abs(y - by) == 1:
                for x in range(x0 - 2, x0 + 3):
                    px(im, x, y, scale(spine, 0.6))
    px(im, x0 + 2, y0, edge_col)
    # wear: scuffs along the edges
    r = random.Random(seed + 2)
    for _ in range(14):
        x = r.choice((r.randrange(x0 + 3, x1), x1 - 1, x0 + 3))
        y = r.randrange(y0 + 1, y1)
        px(im, x, y, mix(get(im, x, y), cover_hi, 0.45))
    if burnt:
        # the bottom-right corner burnt: soot creeping in, the very corner gone
        for y in range(y1 - 7, y1 + 3):
            for x in range(x1 - 7, x1 + 3):
                d = math.hypot(x - (x1 + 2), y - (y1 + 2))
                if d < 2.6:
                    im.putpixel((x, y), CLEAR)
                elif d < 4.2 and alpha(im, x, y):
                    px(im, x, y, SOOT)
                elif d < 8.5 and alpha(im, x, y):
                    px(im, x, y, mix(get(im, x, y), SOOT, 0.75 * (1 - (d - 4.2) / 4.3)))
    return im


def journal():
    im = book(COVER, COVER_HI, COVER_LO, SPINE, PLATE_HI, 1414)
    # the copper plate: a bevelled rectangle, an etched chip (a die with its pins) in the oxide's teal
    px0, px1, py0, py1 = 11, 22, 8, 17
    for y in range(py0, py1 + 1):
        for x in range(px0, px1 + 1):
            c = PLATE
            if y == py0 or x == px0:
                c = PLATE_HI
            elif y == py1 or x == px1:
                c = PLATE_LO
            px(im, x, y, c)
    for y in range(11, 15):
        for x in range(14, 20):
            px(im, x, y, ETCH if (x in (14, 19) or y in (11, 14)) else mix(ETCH, PLATE_LO, 0.5))
    for x in (15, 17, 18):
        px(im, x, 10, ETCH)
        px(im, x, 15, ETCH)
    for y in (12, 13):
        px(im, 13, y, ETCH)
        px(im, 20, y, ETCH)
    # a line of tooling under the plate, the plate's rivets
    for x in range(10, 24, 2):
        px(im, x, 20, mix(COVER_HI, PLATE, 0.3))
    for (x, y) in ((12, 9), (21, 9), (12, 16), (21, 16)):
        px(im, x, y, PLATE_HI)
    grain(im, 15, 0.04)
    outline(im)
    return im


def journal12():
    im = book(BLACK, BLACK_HI, scale(BLACK, 0.8), scale(BLACK, 0.7), GILT, 1412, burnt=False)
    # gilt corners
    for (cx, cy, sx, sy) in ((6, 3, 1, 1), (26, 3, -1, 1), (6, 27, 1, -1), (26, 27, -1, -1)):
        for k in range(4):
            px(im, cx + sx * k, cy, GILT if k < 3 else GILT_LO)
            px(im, cx, cy + sy * k, GILT if k < 3 else GILT_LO)
        px(im, cx + sx, cy + sy, GILT_LO)
    # the green world: a disc lit from the upper left, seas, a thin bright rim
    wx, wy, wr = 15.5, 15.0, 6.2
    r = random.Random(12)
    land = noise_grid(1212, 4)
    for y in range(6, 24):
        for x in range(8, 24):
            d = math.hypot(x + 0.5 - wx, y + 0.5 - wy)
            if d <= wr:
                lit = 0.55 + 0.45 * max(0.0, ((wx - x) * 0.6 + (wy - y) * 0.8) / wr + 0.3)
                c = WORLD if land[y][x] > -0.45 else SEA
                if land[y][x] > 0.45:
                    c = WORLD_HI
                c = scale(c, lit)
                if d > wr - 1.0:
                    c = mix(c, WORLD_HI if (x < wx and y < wy) else WORLD_LO, 0.6)
                px(im, x, y, c)
    # its one small moon, up and to the right
    for y in range(4, 11):
        for x in range(19, 26):
            d = math.hypot(x + 0.5 - 22.5, y + 0.5 - 7.0)
            if d <= 1.9:
                px(im, x, y, MOON if d < 1.2 else mix(MOON, BLACK_HI, 0.4))
    # a faint gilt orbit line round the world
    for t in range(0, 360, 9):
        a = math.radians(t)
        x = int(round(wx + math.cos(a) * 8.3 - 0.5))
        y = int(round(wy + math.sin(a) * 4.0 - 0.5))
        if 7 <= x <= 25 and 4 <= y <= 26 and math.hypot(x + 0.5 - wx, y + 0.5 - wy) > wr + 0.4:
            px(im, x, y, mix(get(im, x, y), GILT_LO, 0.55))
    grain(im, 16, 0.03)
    outline(im)
    return im


def main():
    out = {
        ('blocks', 'ruin_burntCoil'): burnt_coil(),
        ('items', 'journal'): journal(),
        ('items', 'journal12'): journal12(),
    }
    for (folder, name), im in out.items():
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
        im.save(os.path.join(TEX, folder, name + '.png'))
    # the preview: the three x6 on grey, the block also tiled 3 x 3
    z = 6
    keys = [('blocks', 'ruin_burntCoil'), ('items', 'journal'), ('items', 'journal12')]
    sheet = Image.new('RGBA', (len(keys) * (S * z + 10) + 10 + S * 3 * 2 + 10, S * z + 20), (70, 72, 76, 255))
    x = 10
    for key in keys:
        big = out[key].resize((S * z, S * z), Image.NEAREST)
        sheet.paste(big, (x, 10), big)
        x += S * z + 10
    tile = out[('blocks', 'ruin_burntCoil')].resize((S * 2, S * 2), Image.NEAREST)
    for i in range(3):
        for j in range(3):
            sheet.paste(tile, (x + i * S * 2, 10 + j * S * 2), tile)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
