# -*- coding: utf-8 -*-
"""Подложка (Si-14), phase 6 «Посольства Наладчиков» (Silicon Age, docs/plan-substrate-tech-f6.md §6.3, package S).

Writes into src/main/resources/assets/siliconage/textures/blocks the looks of BlockShowcase2SC's Ф6 metas (32 x 32, as the
mod's machines; the front is side 3 - where the showcase's tile faces):
  showcase2_gravityCoil_side / _top - «Гравикатушка кольца», a segment of the Great Ring: the bridge's Gravity Coil
                                      (textures/blocks/gravityCoilOn.png - its windings in bands under a dark casing, the
                                      bobbin's bright seam) lit in the Tuners' turquoise; the ends - the windings seen
                                      end-on round a glowing core;
  showcase2_cryoTank_side / _front / _top - «Криобак» of the Vokzal: a frosted steel cylinder in its frame, hoops top and
                                      bottom, rivets; the front - the level gauge (a glass slot, the turquoise liquid two
                                      thirds up, the marks) and the snowflake plate; the top - the lid, the valve wheel
                                      (steel), the frost;
  showcase2_dispatchConsole_front / _side / _top - «Пульт Диспетчера»: the operator's side - two screens (the Great Ring
                                      with its vortex, the flights' list) over a row of keys; the sides - a vented steel
                                      panel with the turquoise stripe; the top - the desk: the keyboard, a small screen,
                                      the cable.
Every texture is opaque. A preview sheet (x4) goes next to this script.

Usage: python tools/gen_embassy_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..')
BLOCKS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
PREVIEW = os.path.join(HERE, 'embassy_tex_preview.png')

S = 32

# palettes (the settlements' steel, the dome's turquoise - «Бирюза Наладчиков» 0x48F0DC and its shades)
OUTLINE = (11, 13, 10)
STEEL = (120, 140, 160)
STEEL_D = (84, 100, 118)
STEEL_L = (170, 186, 204)
CASING = (44, 40, 52)
CASING_L = (70, 66, 82)
CASING_D = (26, 24, 32)
TURQ = (72, 240, 220)
TURQ_L = (176, 255, 244)
TURQ_M = (44, 178, 166)
TURQ_D = (20, 98, 92)
WHITE = (236, 255, 252)
FROST = (214, 232, 240)
FROST_D = (168, 194, 210)
TANK = (138, 160, 178)
TANK_L = (182, 202, 218)
TANK_D = (96, 114, 132)
GLASS = (36, 52, 60)
SCREEN = (10, 26, 28)
SCREEN_L = (18, 44, 46)
HAZARD = (226, 186, 40)
KEY = (58, 62, 72)
KEY_L = (96, 102, 116)
RED = (210, 64, 54)
AMBER = (232, 176, 52)
GREEN = (80, 230, 120)


def new_block(c):
    return Image.new('RGBA', (S, S), c + (255,))


def put(im, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), c + (255,))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(im, x, y, c)


def frame(im, light, dark, mid, width=2):
    """A bevelled frame round the face: lit top / left, dark bottom / right, the corners' rivets (gen_settlement3_tex.frame)."""
    for i in range(S):
        for k in range(width):
            put(im, i, k, light if k == 0 else mid)
            put(im, k, i, light if k == 0 else mid)
            put(im, i, S - 1 - k, dark)
            put(im, S - 1 - k, i, dark if k == 0 else mid)
    put(im, 0, 0, OUTLINE)
    for (x, y) in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        put(im, x, y, STEEL_L)
        put(im, x + 1, y + 1, STEEL_D)


def noise(x, y, seed):
    """A small deterministic hash 0..255 (the same picture on every run)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


# ------------------------------------------------------------------ the Great Ring's segment

def coil_side():
    """The bridge's coil (gravityCoilOn: a dark casing, windings in two-pixel bands, the bobbin's seam) - lit turquoise."""
    im = new_block(CASING)
    for y in range(3, S - 3):
        band = (y - 3) % 3
        for x in range(4, S - 4):
            if band == 2:
                c = TURQ_D                                      # the gap between two turns
            else:
                c = TURQ_L if band == 0 else TURQ
                if (x + y * 3) % 11 == 0:
                    c = WHITE                                   # a glint on the wire
            put(im, x, y, c)
    for x in range(5, S - 5):                                   # the bobbin's bright seam across the middle
        put(im, x, 15, WHITE)
        put(im, x, 16, mix(TURQ_L, WHITE, 0.5))
    for y in range(3, S - 3):                                   # the casing's cheeks each side of the windings
        for x in (2, 3, S - 4, S - 3):
            put(im, x, y, CASING_L if x in (2, S - 4) else CASING_D)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def coil_top():
    """The windings end-on: rings of turquoise round the steel bobbin, its glowing core."""
    im = new_block(CASING)
    cx = cy = (S - 1) / 2.0
    for y in range(S):
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 3.2:
                put(im, x, y, WHITE if d < 1.8 else TURQ_L)     # the core
            elif d < 5.5:
                put(im, x, y, STEEL_D if d > 4.5 else STEEL)    # the bobbin
            elif d < 13.0:
                ring = int(d - 5.5) % 3
                put(im, x, y, TURQ_L if ring == 0 else TURQ if ring == 1 else TURQ_D)
            elif d < 14.2:
                put(im, x, y, CASING_D)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


# ------------------------------------------------------------------ the Vokzal's cryo tank

def tank_body(im, front):
    """The frosted cylinder: shaded across (lit left of the middle), hoops, rivets, frost creeping up from the bottom."""
    for y in range(2, S - 2):
        for x in range(3, S - 3):
            t = (x - 3) / float(S - 7)
            shade = 1.0 - abs(t - 0.38) * 1.4
            c = mix(TANK_D, TANK_L, max(0.0, min(1.0, shade)))
            frost = (S - 2 - y) / float(S) * 1.2 + noise(x, y, 5) / 255.0 * 0.6
            if frost < 0.55:
                c = mix(c, FROST, 0.75 if noise(x, y, 9) > 90 else 0.45)
            put(im, x, y, c)
    for y in (4, 5, S - 6, S - 5):                              # the hoops
        for x in range(3, S - 3):
            put(im, x, y, STEEL_L if y in (4, S - 6) else STEEL_D)
        for x in range(5, S - 5, 5):
            put(im, x, y if y in (4, S - 6) else y - 1, OUTLINE)    # rivets
    for y in range(2, S - 2):                                   # the frame's uprights
        for x in (0, 1, 2, S - 3, S - 2, S - 1):
            put(im, x, y, STEEL_L if x in (0, S - 3) else STEEL_D if x in (2, S - 1) else STEEL)
    for x in range(S):
        for y in (0, 1, S - 2, S - 1):
            put(im, x, y, STEEL_L if y == 0 else STEEL_D if y == S - 1 else STEEL)
    if not front:
        for y in range(8, S - 8, 4):                            # frost streaks running down the side
            for x in range(6, S - 6, 7):
                put(im, x + (y // 4) % 2, y, FROST)
                put(im, x + (y // 4) % 2, y + 1, FROST_D)


def tank_side():
    im = new_block(TANK)
    tank_body(im, False)
    return im


def tank_front():
    """The gauge: a glass slot with the turquoise liquid two thirds up and the marks; a plate with a snowflake beside it."""
    im = new_block(TANK)
    tank_body(im, True)
    gx0, gx1, gy0, gy1 = 8, 12, 7, S - 8
    level = gy0 + (gy1 - gy0) // 3
    for y in range(gy0, gy1 + 1):
        for x in range(gx0, gx1 + 1):
            if x in (gx0, gx1) or y in (gy0, gy1):
                put(im, x, y, OUTLINE)
            elif y >= level:
                put(im, x, y, TURQ_L if x == gx0 + 1 else TURQ if (y - level) % 5 else TURQ_M)
            else:
                put(im, x, y, GLASS)
    for y in range(level, gy1, 2):                              # bubbles in the liquid
        put(im, gx0 + 2 + (y % 3 == 0), y, WHITE if y % 4 == 0 else TURQ_L)
    for y in range(gy0 + 2, gy1, 3):                            # the marks
        put(im, gx1 + 1, y, OUTLINE)
        put(im, gx1 + 2, y, OUTLINE if (y - gy0) % 6 == 2 else TANK_D)
    put(im, gx1 + 1, level, RED)                                # the level's pointer
    put(im, gx1 + 2, level, RED)
    px0, py0 = 17, 11                                           # the plate: a snowflake on turquoise
    rect(im, px0, py0, px0 + 8, py0 + 8, TURQ_D)
    for i in range(9):
        put(im, px0 + i, py0, STEEL_L)
        put(im, px0, py0 + i, STEEL_L)
        put(im, px0 + i, py0 + 8, OUTLINE)
        put(im, px0 + 8, py0 + i, OUTLINE)
    cx, cy = px0 + 4, py0 + 4
    for k in range(-3, 4):
        put(im, cx + k, cy, WHITE)
        put(im, cx, cy + k, WHITE)
        if abs(k) <= 2:
            put(im, cx + k, cy + k, TURQ_L)
            put(im, cx + k, cy - k, TURQ_L)
    put(im, cx, cy, TURQ)
    return im


def tank_top():
    """The lid: a riveted steel disc, the valve wheel (steel spokes), the frost round its rim."""
    im = new_block(STEEL_D)
    cx = cy = (S - 1) / 2.0
    for y in range(S):
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 14.5:
                c = mix(TANK_D, TANK_L, 0.5 + 0.5 * ((cx - x) + (cy - y)) / 30.0)
                if d > 11.5:
                    c = mix(c, FROST, 0.6 if noise(x, y, 3) > 70 else 0.3)
                put(im, x, y, c)
            if 13.5 <= d < 14.5:
                put(im, x, y, STEEL_D)
    for a in range(12):                                         # the rivets round the rim
        ang = a * math.pi / 6.0
        put(im, int(round(cx + math.cos(ang) * 12.5)), int(round(cy + math.sin(ang) * 12.5)), OUTLINE)
    for y in range(S):                                          # the valve wheel: a rim and four spokes
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 6.4 <= d < 7.2:
                put(im, x, y, OUTLINE)                          # the wheel's shadow
            elif 5.0 <= d < 6.4:
                put(im, x, y, STEEL_L if y < cy else STEEL)
            elif d < 1.6:
                put(im, x, y, TURQ)
    for k in range(-5, 6):
        if abs(k) > 1:
            put(im, int(cx) + k, int(cy) + 1, OUTLINE)
            put(im, int(cx) + 1, int(cy) + k, OUTLINE)
            put(im, int(cx) + k, int(cy), STEEL_L)
            put(im, int(cx), int(cy) + k, STEEL_L)
    frame(im, STEEL_L, OUTLINE, STEEL)
    return im


# ------------------------------------------------------------------ the Dispatchers' desk

def console_front():
    """The operator's side: the Great Ring on the left screen (its coils, the vortex), the flights on the right, keys below."""
    im = new_block(CASING)
    # the left screen: the ring
    rect(im, 3, 3, 16, 17, SCREEN)
    cx, cy = 9.5, 10.0
    for y in range(4, 17):
        for x in range(4, 16):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if 4.6 <= d < 5.6:
                put(im, x, y, TURQ_L if (x + y) % 3 else TURQ)  # the ring's coils
            elif d < 2.2:
                put(im, x, y, WHITE if d < 1.0 else TURQ)       # the vortex
            elif d < 3.4 and (x + y) % 2 == 0:
                put(im, x, y, TURQ_D)
    for y in range(3, 18):
        put(im, 3, y, SCREEN_L)
        put(im, 16, y, OUTLINE)
    # the right screen: the flights - lines of text, one lit
    rect(im, 18, 3, 28, 17, SCREEN)
    for i, y in enumerate(range(5, 17, 3)):
        lit = i == 1
        for x in range(20, 27 - (i * 2) % 4):
            put(im, x, y, TURQ_L if lit else TURQ_M if x % 3 else TURQ_D)
        put(im, 19, y, GREEN if i < 2 else AMBER if i == 2 else RED)  # each flight's state
    for y in range(3, 18):
        put(im, 18, y, SCREEN_L)
        put(im, 28, y, OUTLINE)
    # the keys and the switches
    for row, y in enumerate((21, 24)):
        for x in range(4, 28, 3):
            put(im, x, y, KEY_L)
            put(im, x + 1, y, KEY)
            put(im, x, y + 1, KEY)
            put(im, x + 1, y + 1, OUTLINE)
    put(im, 6, 21, TURQ)
    put(im, 24, 24, AMBER)
    for x in range(3, 29):                                      # the desk's lit edge
        put(im, x, 27, TURQ_M if x % 4 else TURQ_L)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def console_side():
    """A vented steel panel with the Tuners' turquoise stripe."""
    im = new_block(STEEL_D)
    for y in range(3, S - 3):
        for x in range(3, S - 3):
            put(im, x, y, STEEL if (x + y) % 9 else mix(STEEL, STEEL_L, 0.4))
    for y in range(6, 18, 3):                                   # the vents
        for x in range(7, S - 7):
            put(im, x, y, OUTLINE)
            put(im, x, y + 1, STEEL_L)
    for x in range(3, S - 3):                                   # the stripe
        for y in (22, 23):
            put(im, x, y, TURQ if y == 22 else TURQ_M)
    frame(im, STEEL_L, OUTLINE, STEEL)
    return im


def console_top():
    """The desk: the keyboard, a small screen with the countdown, a cable run along the back."""
    im = new_block(CASING_L)
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            put(im, x, y, CASING if (x * 7 + y * 3) % 13 else CASING_L)
    for y in range(17, 27, 3):                                  # the keyboard
        for x in range(5, 27, 3):
            put(im, x, y, KEY_L)
            put(im, x + 1, y, KEY)
            put(im, x, y + 1, KEY)
            put(im, x + 1, y + 1, OUTLINE)
    put(im, 14, 20, TURQ)
    rect(im, 9, 5, 22, 12, SCREEN)                              # the small screen: «00:10» in turquoise
    digits = [(11, 7), (12, 7), (11, 8), (11, 9), (12, 9), (14, 8), (17, 7), (18, 7), (18, 8), (17, 9), (18, 9), (20, 7), (20, 8),
              (20, 9)]
    for (x, y) in digits:
        put(im, x, y, TURQ_L)
    for x in range(9, 23):
        put(im, x, 13, OUTLINE)
    for x in range(3, S - 3):                                   # the cable along the back
        put(im, x, 3, OUTLINE if x % 5 else TURQ_D)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def main():
    blocks = [('showcase2_gravityCoil_side', coil_side()), ('showcase2_gravityCoil_top', coil_top()),
              ('showcase2_cryoTank_side', tank_side()), ('showcase2_cryoTank_front', tank_front()), ('showcase2_cryoTank_top', tank_top()),
              ('showcase2_dispatchConsole_front', console_front()), ('showcase2_dispatchConsole_side', console_side()),
              ('showcase2_dispatchConsole_top', console_top())]
    os.makedirs(BLOCKS, exist_ok=True)
    for name, im in blocks:
        assert all(im.getpixel((x, y))[3] == 255 for x in range(S) for y in range(S)), name + ' must be opaque'
        im.save(os.path.join(BLOCKS, name + '.png'))
    bz = 4 * S
    sheet = Image.new('RGBA', (len(blocks) * (bz + 8) + 8, bz + 16), (70, 72, 76, 255))
    for i, (name, im) in enumerate(blocks):
        big = im.resize((bz, bz), Image.NEAREST)
        sheet.paste(big, (8 + i * (bz + 8), 8), big)
    sheet.save(PREVIEW)
    print('wrote %d block textures into %s' % (len(blocks), BLOCKS))


if __name__ == '__main__':
    main()
