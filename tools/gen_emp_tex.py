# -*- coding: utf-8 -*-
"""ПЭ-1 «Подавитель электроники» item icons (Silicon Age, docs/plan-pe1.md §10, package D).

Writes into src/main/resources/assets/siliconage/textures/items (32 x 32, the mod's item size):
  empGun        - the «Сифон-капсула» side on, barrel right: steel body, the blue capsule in a cage between brass
                  rings, the needle between two claws, a cable to the wrapped grip (the 2D fallback / particle icon -
                  the item is drawn by RenderEmpGunSC's 3D model);
  empGunM       - ПЭ-1М: the same with a second row of copper rings and a violet capsule;
  empModule_<k> - the five modules (capacity, focus, cooling, efficient, wide): a dark module plate with gold
                  contacts, a stripe and an emblem in the module's colour;
and a preview sheet (x6) next to this script.

Usage: python gen_emp_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'items')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'emp_items_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (14, 16, 18)

STEEL = (178, 186, 198)
STEEL_D = (120, 127, 140)
GUN = (78, 83, 94)
GUN_D = (56, 60, 68)
COPPER = (214, 128, 64)
BRASS = (224, 186, 92)
WRAP = (66, 60, 56)
WRAP_D = (44, 40, 37)
POINT = (110, 215, 255)
PULSE = (186, 132, 255)
SIPHON = (130, 236, 100)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def lighter(c, t=0.35):
    return mix(c, (255, 255, 255), t)


def darker(c, t=0.35):
    return mix(c, (0, 0, 0), t)


def px(im, x, y, c, a=255):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def shaded(im, x0, y0, x1, y1, c):
    """A filled block: lit top row, shaded bottom row (the outline comes later)."""
    rect(im, x0, y0, x1, y1, c)
    for x in range(x0, x1 + 1):
        px(im, x, y0, lighter(c))
        px(im, x, y1, darker(c))


def outline(im):
    """A 1-pixel dark outline round everything drawn (the mod's items all have one)."""
    src = im.copy()
    for y in range(S):
        for x in range(S):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, OUTLINE)
                    break


def gun(upgraded):
    im = Image.new('RGBA', (S, S), CLEAR)
    glow = PULSE if upgraded else POINT
    # the grip (behind the body), wrapped, with rings
    rect(im, 5, 16, 9, 27, WRAP)
    for y in (19, 22, 25):
        rect(im, 5, y, 9, y, WRAP_D)
    for y in range(16, 28):
        px(im, 5, y, lighter(WRAP, 0.2))
    # trigger guard and trigger
    rect(im, 10, 21, 14, 21, GUN_D)
    rect(im, 14, 17, 14, 21, GUN_D)
    px(im, 11, 18, BRASS)
    px(im, 11, 19, BRASS)
    # the body
    shaded(im, 2, 11, 24, 16, STEEL)
    rect(im, 3, 10, 7, 10, STEEL_D)                     # rear sight rail
    for x in (19, 21, 23):                              # vents on the front block
        px(im, x, 10, GUN)
    # the needle's base, the needle, the claws
    rect(im, 25, 12, 27, 14, GUN_D)
    px(im, 25, 12, GUN)
    if upgraded:
        rect(im, 26, 12, 26, 14, BRASS)
    for x, c in ((28, glow), (29, lighter(glow, 0.3)), (30, lighter(glow, 0.6))):
        px(im, x, 13, c)
    px(im, 28, 12, darker(glow, 0.2))
    for sy in (-1, 1):
        y0 = 13 + sy * 3
        rect(im, 26, y0, 28, y0, STEEL)
        rect(im, 28, y0 + sy, 30, y0 + sy, STEEL)
        px(im, 30, y0, STEEL_D)
    # the capsule in its cage between the brass rings
    cx0, cx1 = 10, 16
    for x in range(cx0, cx1 + 1):
        for y in range(8, 19):
            d = abs(y - 13) / 5.5
            px(im, x, y, mix(lighter(glow, 0.45), darker(glow, 0.25), d))
    for x in range(cx0, cx1 + 1):                       # the cage bars run along it
        for y in (9, 13, 17):
            px(im, x, y, STEEL_D if y != 13 else mix(STEEL_D, glow, 0.35))
    for x in (cx0 + 1, cx0 + 4):                        # glints of energy
        px(im, x, 11, (240, 255, 240))
    for x in (cx0 - 1, cx1 + 1):                        # the brass rings
        for y in range(7, 20):
            px(im, x, y, BRASS if 8 <= y <= 18 else darker(BRASS, 0.2))
        px(im, x, 7, lighter(BRASS))
    if upgraded:                                        # ПЭ-1М: a second row of copper rings
        for x in (cx0 - 2, cx1 + 2):
            for y in range(8, 19):
                px(im, x, y, COPPER if y not in (8, 18) else darker(COPPER, 0.2))
    # the cable to the grip
    for (x, y) in ((11, 17), (10, 18), (10, 19), (9, 20)):
        px(im, x, y, COPPER)
    # side lights
    px(im, 18, 15, (240, 255, 240))
    px(im, 20, 15, mix((240, 255, 240), glow, 0.5))
    outline(im)
    return im


MODULES = {
    'capacity': (255, 210, 80),
    'focus': (110, 215, 255),
    'cooling': (120, 200, 255),
    'efficient': (130, 236, 100),
    'wide': (186, 132, 255),
}


def plate(col):
    im = Image.new('RGBA', (S, S), CLEAR)
    body = (58, 64, 74)
    rect(im, 5, 6, 26, 25, body)
    for x in range(5, 27):
        px(im, x, 6, lighter(body, 0.3))
        px(im, x, 25, darker(body, 0.4))
    for y in range(6, 26):
        px(im, 5, y, lighter(body, 0.2))
        px(im, 26, y, darker(body, 0.3))
    # the colour stripe at the top and a little ПЭ-1 capsule mark at the left
    rect(im, 7, 8, 24, 9, col)
    for x in range(7, 25):
        px(im, x, 8, lighter(col, 0.3))
    # gold contacts at the bottom
    for x in range(8, 24, 3):
        rect(im, x, 26, x + 1, 28, BRASS)
        px(im, x, 28, darker(BRASS, 0.3))
    # screws
    for (x, y) in ((7, 23), (24, 23)):
        px(im, x, y, STEEL_D)
    return im


def emblem(im, key, col):
    hi = lighter(col, 0.5)
    lo = darker(col, 0.35)
    if key == 'capacity':                               # a fat energy cell with three charge bars
        rect(im, 11, 12, 20, 22, (30, 34, 40))
        rect(im, 14, 11, 17, 11, STEEL_D)
        for i, y in enumerate((19, 16, 13)):
            rect(im, 12, y, 19, y + 1, col if i < 2 else lo)
            px(im, 12, y, hi)
    elif key == 'focus':                                # a lens: a ring with a bright centre and a cross-hair
        for y in range(10, 25):
            for x in range(8, 24):
                d = math.hypot(x + 0.5 - 16, y + 0.5 - 17)
                if 4.6 <= d <= 6.2:
                    px(im, x, y, STEEL if y < 17 else STEEL_D)
                elif d < 4.6:
                    px(im, x, y, mix(hi, lo, d / 4.6))
        px(im, 16, 17, (255, 255, 255))
        px(im, 14, 15, (255, 255, 255))
    elif key == 'cooling':                              # a snowflake over fins
        for x in range(9, 24, 2):
            rect(im, x, 21, x, 23, STEEL_D)
        c = (16, 16)
        for dx, dy in ((1, 0), (0, 1), (1, 1), (1, -1)):
            for t in range(-4, 5):
                px(im, c[0] + dx * t, c[1] + dy * t, hi if abs(t) < 2 else col)
        for t in (-4, 4):
            px(im, c[0] + t, c[1] - 1, col)
            px(im, c[0] + t, c[1] + 1, col)
    elif key == 'efficient':                            # a capacitor with a loop-arrow round it
        rect(im, 14, 13, 15, 21, STEEL)
        rect(im, 17, 13, 18, 21, STEEL)
        px(im, 13, 17, BRASS)
        px(im, 19, 17, BRASS)
        for a in range(0, 300, 12):
            r = math.radians(a)
            px(im, int(round(16 + 6.5 * math.cos(r))), int(round(17 + 5.5 * math.sin(r))), col)
        for (x, y) in ((21, 12), (22, 13), (20, 13)):
            px(im, x, y, hi)
    elif key == 'wide':                                 # three channels widening to the right
        for i, y in enumerate((13, 17, 21)):
            for x in range(8, 24):
                w = (x - 8) / 16.0
                px(im, x, y, mix(lo, hi, w))
                if x > 15:
                    px(im, x, y - 1 if i == 0 else y + 1 if i == 2 else y, mix(lo, col, w))
        rect(im, 8, 12, 9, 22, STEEL_D)


def module(key):
    col = MODULES[key]
    im = plate(col)
    emblem(im, key, col)
    outline(im)
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    out = [('empGun', gun(False)), ('empGunM', gun(True))]
    out += [('empModule_' + k, module(k)) for k in MODULES]
    for name, im in out:
        im.save(os.path.join(OUT, name + '.png'))
    sheet = Image.new('RGBA', (len(out) * 200, 200), (60, 60, 60, 255))
    for i, (name, im) in enumerate(out):
        big = im.resize((192, 192), Image.NEAREST)
        sheet.paste(big, (i * 200 + 4, 4), big)
    sheet.save(PREVIEW)
    print('wrote', ', '.join(n for n, _ in out))


if __name__ == '__main__':
    main()
