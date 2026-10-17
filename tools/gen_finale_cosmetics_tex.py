# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 5, package P: the cosmetics' and the Lithographers' items (Silicon Age,
docs/plan-substrate-tech-f5.md §9.1, §9.4, §10.2).

Writes into src/main/resources/assets/siliconage/textures/items (32 x 32, the mod's dark outline):
  paintScheme_lithographer - «Схема окраски «Литограф»»: a sample card in the scheme's own plate (SingularScheme.L 0x1C1A24),
                             the singularity core in its accent (0xF0E2B4) in the middle, three swatches along the bottom;
                             a gold rim (the Breakdown Warden's, epic);
  paintScheme_tuner        - «Схема окраски «Наладчик»»: the same card in SingularScheme.M (0x24423F / 0x3CD2C8), a pale
                             turquoise rim (the Elder's);
  lithoModule              - «Модуль Литографов»: a field generator upgrade's board (the mod's upgrades: a coloured top bar,
                             gold pins) in the Lithographers' dark steel, the top bar their violet, a half-lit disc (the upkeep
                             halved) with traces running off it;
  navRecord                - «Навигационная запись Si-14»: a dark navy record plate, the half of an entangled crystal set in
                             its corner, a dotted course over an orbit to the Substrate's violet point, a contact strip;
and a preview sheet (finale_cosmetics_preview.png next to this script, or the path given).

Usage: python tools/gen_finale_cosmetics_tex.py [project dir] [preview png]
"""
import math
import os
import sys

from PIL import Image

ARGS = sys.argv[1:]
PROJECT = ARGS[0] if len(ARGS) > 0 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
PREVIEW = ARGS[1] if len(ARGS) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), 'finale_cosmetics_preview.png')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the schemes (SingularScheme.L, M: base, accent)
LITHO = ((28, 26, 36), (240, 226, 180))
TUNER = ((36, 66, 63), (60, 210, 200))
# rims: the Warden's gold, the Tuners' pale turquoise
GOLD, GOLD_HI, GOLD_LO = (214, 168, 72), (250, 222, 140), (138, 98, 36)
TURQ, TURQ_HI, TURQ_LO = (120, 200, 192), (196, 244, 236), (62, 120, 116)
# the Lithographers' steel (gen_ruins_tex.py) and their violet (gen_camps_tex.py)
STEEL, STEEL_HI, STEEL_LO, STEEL_DK = (86, 90, 104), (134, 140, 156), (60, 62, 74), (34, 35, 43)
VIOLET, VIOLET_L, VIOLET_D = (132, 86, 200), (214, 186, 255), (70, 40, 110)
# the mod's upgrade pins
PIN, PIN_LO = (206, 160, 58), (140, 104, 34)
# the record: navy, its rim, the crystal's cyan, the course's teal
NAVY, NAVY_HI, NAVY_LO = (22, 32, 56), (70, 98, 140), (12, 18, 32)
CYAN, CYAN_HI, CYAN_LO = (96, 220, 240), (210, 250, 255), (40, 120, 150)
COURSE = (86, 170, 160)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k, add=0):
    return tuple(clamp(v * k + add) for v in c[:3])


def px(im, x, y, c, a=255):
    w, h = im.size
    x, y = int(round(x)), int(round(y))
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def get(im, x, y):
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[:3]


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


def rounded(x0, y0, x1, y1, x, y, r=1):
    """(x, y) inside the box with its corners cut by r."""
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = x0 + r if x < x0 + r else (x1 - r if x > x1 - r else x)
    cy = y0 + r if y < y0 + r else (y1 - r if y > y1 - r else y)
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.5


# ---------------------------------------------------------------- the paint scheme card

def paint_card(scheme, rim):
    base, acc = scheme
    rim_c, rim_hi, rim_lo = rim
    im = Image.new('RGBA', (S, S), CLEAR)
    x0, y0, x1, y1 = 4, 3, 27, 28
    plate_lo, plate_hi = scale(base, 0.6), scale(base, 1.35, 28)
    acc_hi, acc_dim = mix(acc, (255, 255, 255), 0.55), mix(acc, base, 0.45)
    for y in range(S):
        for x in range(S):
            if not rounded(x0, y0, x1, y1, x, y, 2):
                continue
            if x in (x0, x0 + 1) or y in (y0, y0 + 1):
                c = rim_hi if (x == x0 or y == y0) else rim_c
            elif x in (x1, x1 - 1) or y in (y1, y1 - 1):
                c = rim_lo
            else:
                t = 1.0 - ((x - x0) + (y - y0)) / float((x1 - x0) + (y1 - y0))     # lit from the top left
                c = mix(plate_lo, plate_hi, 0.25 + 0.55 * t)
            px(im, x, y, c)
    # the plate's seams (the armour's panel lines)
    for x in range(x0 + 3, x1 - 2):
        if x % 6 != 1:
            px(im, x, 20, mix(get(im, x, 20), scale(base, 0.4), 0.7))
    for y in range(y0 + 3, 20):
        px(im, 9, y, mix(get(im, 9, y), scale(base, 0.45), 0.5))
    # the singularity core: a ring in the accent round a dark middle, two orbit ticks
    cx, cy = 15.5, 11.5
    for y in range(y0 + 2, 20):
        for x in range(x0 + 2, x1 - 1):
            d = math.hypot(x - cx, y - cy)
            if d < 2.2:
                px(im, x, y, mix((4, 3, 8), acc, 0.08))
            elif d < 3.4:
                px(im, x, y, acc_hi if (x < cx and y < cy) else acc)
            elif d < 4.3:
                px(im, x, y, acc_dim)
    for k in range(3):
        px(im, 21 + k, 6 + k, acc)
        px(im, 9 - k, 16 + k, acc_dim)
    # three swatches along the bottom: the plate, the accent, its highlight
    for i, c in enumerate((plate_hi, acc, acc_hi)):
        sx = 8 + i * 6
        for y in range(22, 26):
            for x in range(sx, sx + 4):
                px(im, x, y, c if (x, y) != (sx, 22) else mix(c, (255, 255, 255), 0.4))
        for x in range(sx, sx + 4):
            px(im, x, 26, scale(c, 0.55))
    outline(im)
    return im


# ---------------------------------------------------------------- the Lithographers' Module

def litho_module():
    im = Image.new('RGBA', (S, S), CLEAR)
    x0, y0, x1, y1 = 3, 5, 28, 25
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if not rounded(x0, y0, x1, y1, x, y, 1):
                continue
            if y <= y0 + 2:
                c = VIOLET_L if y == y0 else (VIOLET if y == y0 + 1 else VIOLET_D)     # the upgrade's coloured top bar
            elif x == x0 or y == y1:
                c = STEEL_DK if y == y1 else STEEL_LO
            elif x == x1:
                c = STEEL_DK
            elif x == x0 + 1:
                c = STEEL_HI
            else:
                c = mix(STEEL_LO, STEEL, 0.55 + 0.25 * ((x1 - x) / float(x1 - x0)))
            px(im, x, y, c)
    # the traces off the disc (violet, dim), to the board's edges
    for x in list(range(5, 10)) + list(range(21, 27)):
        px(im, x, 15, VIOLET_D)
    for y in range(9, 12):
        px(im, 15, y, VIOLET_D)
    for k in range(4):
        px(im, 21 + k, 19 + k // 2, VIOLET_D)
        px(im, 6 + k, 20 - k // 2, VIOLET_D)
    for (x, y) in ((5, 15), (26, 15), (15, 9), (24, 21), (6, 20)):
        px(im, x, y, VIOLET)
    # the disc: its left half lit (the upkeep halved), the right half dark with a lit rim
    cx, cy, r = 15.5, 15.5, 5.2
    for y in range(9, 23):
        for x in range(9, 23):
            d = math.hypot(x - cx, y - cy)
            if d > r:
                continue
            if d > r - 1.0:
                c = VIOLET_L if x < cx else VIOLET
            elif x < cx:
                c = mix(VIOLET, (250, 240, 255), max(0.0, 0.7 - d / r * 0.5))
            else:
                c = mix(STEEL_DK, VIOLET_D, 0.35)
            px(im, x, y, c)
    for y in range(11, 21):
        px(im, 15.5, y, (250, 240, 255) if y < 13 else VIOLET_L)
    # the gold pins under it
    for i in range(6):
        x = 6 + i * 4
        for y in (26, 27):
            px(im, x, y, PIN if y == 26 else PIN_LO)
            px(im, x + 1, y, PIN_LO)
    outline(im)
    return im


# ---------------------------------------------------------------- the Si-14 Navigation Record

def nav_record():
    im = Image.new('RGBA', (S, S), CLEAR)
    x0, y0, x1, y1 = 3, 6, 28, 25
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if not rounded(x0, y0, x1, y1, x, y, 2):
                continue
            if x == x0 or y == y0:
                c = NAVY_HI
            elif x == x1 or y == y1:
                c = NAVY_LO
            else:
                t = ((x - x0) + (y - y0)) / float((x1 - x0) + (y1 - y0))
                c = mix(mix(NAVY, NAVY_HI, 0.22), NAVY, t)
            px(im, x, y, c)
    # the faint chart grid
    for x in range(x0 + 2, x1 - 1):
        for y in range(y0 + 2, y1 - 3):
            if (x - x0) % 5 == 0 or (y - y0) % 5 == 0:
                px(im, x, y, mix(get(im, x, y), NAVY_HI, 0.25))
    # the orbit (an ellipse round the target) and the Substrate's violet point
    tx, ty = 21.5, 13.5
    for a in range(0, 360, 6):
        ang = math.radians(a)
        x, y = tx + math.cos(ang) * 5.2, ty + math.sin(ang) * 3.2
        px(im, x, y, mix(COURSE, NAVY, 0.35))
    for (x, y, c) in ((21, 13, VIOLET_L), (22, 13, VIOLET), (21, 14, VIOLET), (22, 14, VIOLET_D)):
        px(im, x, y, c)
    # the dotted course from the crystal to the orbit
    for k in range(4):
        x, y = 13 + k * 1.6, 19.5 - k * 1.3
        px(im, x, y, mix(COURSE, CYAN_HI, 0.35))
    # the half crystal set in the corner (the entangled crystal's cut face to the right)
    for y in range(9, 21):
        for x in range(5, 12):
            dx, dy = x - 5, y - 15
            if dx <= 6 and abs(dy) <= 6 - dx * 0.35 and dx >= abs(dy) * 0.5 - 1:
                face = x >= 10
                lit = (y < 15) != face
                c = CYAN_HI if (lit and dx + abs(dy) < 4) else (CYAN if lit else CYAN_LO)
                if face:
                    c = mix(c, (255, 255, 255), 0.25)
                px(im, x, y, c)
    # the contact strip along the bottom
    for x in range(8, 24):
        px(im, x, 23, PIN if x % 3 else PIN_LO)
    outline(im)
    return im


def main():
    out = [('items/paintScheme_lithographer.png', paint_card(LITHO, (GOLD, GOLD_HI, GOLD_LO))),
           ('items/paintScheme_tuner.png', paint_card(TUNER, (TURQ, TURQ_HI, TURQ_LO))),
           ('items/lithoModule.png', litho_module()),
           ('items/navRecord.png', nav_record())]
    for rel, im in out:
        im.save(os.path.join(TEX, rel))
    k = 6
    sheet = Image.new('RGBA', (len(out) * (S * k + 8) + 8, S * k + 16), (64, 64, 68, 255))
    for i, (rel, im) in enumerate(out):
        big = im.resize((S * k, S * k), Image.NEAREST)
        sheet.paste(big, (8 + i * (S * k + 8), 8), big)
    sheet.save(PREVIEW)
    print('wrote %d textures and %s' % (len(out), PREVIEW))


if __name__ == '__main__':
    main()
