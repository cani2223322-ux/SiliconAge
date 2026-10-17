# -*- coding: utf-8 -*-
"""Подложка Ф2, package B (docs/plan-substrate-tech-f2.md §8): the dock's blocks and the travel items (Silicon Age).

Writes (32 x 32, the mod's block and item size):
  textures/blocks/dockPlate      - «Причальная плита» from above: dark settlement metal, the dome's turquoise landing
                                   brackets in the corners, a faint grid and four rivets (it tiles 7 x 7);
  textures/blocks/dockPlateSide  - its side: the same metal under a turquoise edge band with dashes;
  textures/blocks/dockBeaconTop  - «Причальный маяк» from above: the plate with a glowing turquoise lens in a ring;
  textures/blocks/dockBeaconSide - its side: lit turquoise strips in a dark slot (the Receiver Beacon's look, in the
                                   dome's colour);
  textures/items/returnCapsule   - «Капсула возврата»: a diagonal pill, silver over teal, a dark band with a lit
                                   green button and a round turquoise window;
  textures/items/settlementMap   - «Карта поселений»: a folded blue-grey data sheet with a turquoise grid, three dome
                                   marks joined by a dashed orange route;
and a preview sheet (x6) next to this script.

Usage: python tools/gen_travel_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
BLOCKS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
ITEMS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'items')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'travel_tex_preview.png')
S = 32

CLEAR = (0, 0, 0, 0)
OUTLINE = (12, 15, 16)
METAL = (56, 64, 70)          # the settlement's metal: the Substrate's blue-grey, darker
METAL_HI = (92, 104, 112)
METAL_LO = (32, 37, 42)
RIVET = (128, 142, 150)
DOME = (60, 210, 200)         # the dome's turquoise (TileEntitySettlementCoreSC: 0x3CD2C8)
DOME_HI = (200, 255, 248)
DOME_LO = (30, 120, 116)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def px(im, x, y, c, a=255):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def get(im, x, y):
    return im.getpixel((x, y))[:3]


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def glow(im, x, y, c, t):
    if 0 <= x < S and 0 <= y < S:
        px(im, x, y, mix(get(im, x, y), c, t))


def noise(im, x0, y0, x1, y1, seed, amount=0.18):
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if r.random() < amount:
                px(im, x, y, scale(get(im, x, y), 1.07 if r.random() < 0.5 else 0.93))


# ------------------------------------------------------------------ blocks

def metal(seed):
    """The settlement's plate metal: brushed, outlined, a bevel - the base of all four block faces."""
    im = Image.new('RGBA', (S, S), METAL + (255,))
    r = random.Random(seed)
    for y in range(S):
        k = 1.0 + (r.random() - 0.5) * 0.06               # brushed: each row a little lighter / darker
        for x in range(S):
            px(im, x, y, scale(METAL, k))
    noise(im, 0, 0, S - 1, S - 1, seed + 1)
    rect(im, 0, 0, S - 1, 0, OUTLINE)
    rect(im, 0, S - 1, S - 1, S - 1, OUTLINE)
    rect(im, 0, 0, 0, S - 1, OUTLINE)
    rect(im, S - 1, 0, S - 1, S - 1, OUTLINE)
    rect(im, 1, 1, S - 2, 1, METAL_HI)
    rect(im, 1, 1, 1, S - 2, METAL_HI)
    rect(im, 1, S - 2, S - 2, S - 2, METAL_LO)
    rect(im, S - 2, 1, S - 2, S - 2, METAL_LO)
    return im


def rivets(im):
    for (x, y) in ((4, 4), (S - 5, 4), (4, S - 5), (S - 5, S - 5)):
        px(im, x, y, RIVET)
        px(im, x + 1, y + 1, METAL_LO)
        px(im, x - 1, y - 1, mix(METAL, RIVET, 0.4))


def dock_plate():
    im = metal(21)
    # a faint grid every 8 px (the pad's panels)
    for i in (8, 16, 24):
        for j in range(2, S - 2):
            glow(im, i, j, METAL_LO, 0.55)
            glow(im, j, i, METAL_LO, 0.55)
    # the landing brackets in the corners - over 7 x 7 tiles they read as a lit grid of squares
    for (cx, cy, sx, sy) in ((3, 3, 1, 1), (S - 4, 3, -1, 1), (3, S - 4, 1, -1), (S - 4, S - 4, -1, -1)):
        for k in range(6):
            for (x, y) in ((cx + sx * k, cy), (cx, cy + sy * k)):
                c = DOME if k < 5 else DOME_LO
                for gx in (-1, 0, 1):
                    for gy in (-1, 0, 1):
                        glow(im, x + gx, y + gy, DOME, 0.18)
                px(im, x, y, c)
        px(im, cx, cy, DOME_HI)
    # the middle: a small turquoise diamond (the way to the beacon)
    for d in range(4):
        for (x, y) in ((15 - d, 12 + d), (16 + d, 12 + d), (15 - d, 19 - d), (16 + d, 19 - d)):
            px(im, x, y, mix(DOME_LO, DOME, d / 3.0))
    rivets(im)
    return im


def dock_plate_side():
    im = metal(22)
    # the edge band: turquoise under the top, dark dashes in it
    rect(im, 1, 2, S - 2, 6, DOME_LO)
    rect(im, 1, 3, S - 2, 5, DOME)
    for x in range(2, S - 2):
        if (x // 3) % 2 == 0:
            px(im, x, 4, DOME_HI)
    for x in range(1, S - 1):
        glow(im, x, 7, DOME, 0.25)
    # panel seams and two bolts below
    for x in range(2, S - 2):
        glow(im, x, 18, METAL_LO, 0.6)
    for (x, y) in ((5, 24), (S - 6, 24)):
        px(im, x, y, RIVET)
        px(im, x + 1, y + 1, METAL_LO)
    return im


def dock_beacon_top():
    im = dock_plate()
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 5.2:
                px(im, x, y, mix(DOME_HI, DOME, d / 5.2))
            elif d < 6.4:
                px(im, x, y, OUTLINE)
            elif d < 8.6:
                px(im, x, y, mix(METAL_HI, METAL, (d - 6.4) / 2.2))
            elif d < 9.6:
                glow(im, x, y, DOME, 0.35)
    px(im, 14, 14, (255, 255, 255))
    px(im, 13, 15, (240, 255, 252))
    return im


def dock_beacon_side():
    im = dock_plate_side()
    rect(im, 12, 9, 19, 28, (20, 26, 28))
    rect(im, 12, 9, 19, 9, OUTLINE)
    rect(im, 12, 28, 19, 28, METAL_HI)
    for i, y in enumerate(range(11, 27, 3)):
        c = DOME if i % 2 == 0 else scale(DOME, 0.65)
        for gx in range(12, 20):
            for gy in (y - 1, y + 1):
                glow(im, gx, gy, c, 0.25)
        rect(im, 13, y, 18, y, c)
        px(im, 13, y, DOME_HI)
    return im


# ------------------------------------------------------------------ items

def outline(im):
    """A 1 px dark outline round the opaque pixels (the mod's items)."""
    src = im.copy()
    for y in range(S):
        for x in range(S):
            if src.getpixel((x, y))[3] != 0:
                continue
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, OUTLINE)
                    break


def return_capsule():
    im = Image.new('RGBA', (S, S), CLEAR)
    ax, ay, bx, by, rad = 9.0, 22.5, 22.5, 9.0, 6.0
    vx, vy = bx - ax, by - ay
    ln = math.hypot(vx, vy)
    ux, uy = vx / ln, vy / ln
    silver, silver_hi, silver_lo = (196, 204, 210), (246, 250, 252), (110, 118, 126)
    teal, teal_hi, teal_lo = (48, 150, 146), (130, 224, 214), (22, 80, 80)
    for y in range(S):
        for x in range(S):
            qx, qy = x + 0.5 - ax, y + 0.5 - ay
            t = max(0.0, min(ln, qx * ux + qy * uy))      # along the axis
            cx, cy = ax + ux * t, ay + uy * t
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > rad:
                continue
            # the light from the top left: the side of the pill facing it is lit
            nz = math.sqrt(max(0.0, 1 - (d / rad) ** 2))
            nxn, nyn = dx / rad, dy / rad
            light = max(0.0, -0.55 * nxn - 0.55 * nyn + 0.62 * nz)
            top = t > ln * 0.52
            base, hi, lo = (silver, silver_hi, silver_lo) if top else (teal, teal_hi, teal_lo)
            c = mix(lo, base, min(1.0, light * 1.6)) if light < 0.62 else mix(base, hi, (light - 0.62) / 0.38)
            if abs(t - ln * 0.52) < 1.3:
                c = (34, 40, 44)                          # the seam band
            px(im, x, y, c)
    # the button on the band and the return arrow on the silver half
    mx, my = ax + ux * ln * 0.52, ay + uy * ln * 0.52
    px(im, int(mx), int(my), (120, 255, 150))
    px(im, int(mx) + 1, int(my), (70, 210, 110))
    px(im, int(mx), int(my) - 1, (200, 255, 210))
    # a little round window on the silver half: the turquoise light of the way back
    wx, wy = ax + ux * ln * 0.78, ay + uy * ln * 0.78
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - wx, y + 0.5 - wy)
            if d <= 2.6:
                px(im, x, y, (24, 34, 38) if d > 1.8 else mix(DOME_HI, DOME, d / 1.8))
    outline(im)
    return im


def settlement_map():
    im = Image.new('RGBA', (S, S), CLEAR)
    sheet, sheet_hi, sheet_lo = (150, 170, 182), (196, 214, 222), (104, 122, 134)
    x0, y0, x1, y1 = 3, 6, 28, 26
    panels = 4
    pw = (x1 - x0 + 1) / float(panels)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            p = int((x - x0) / pw)
            # the folds: panels alternately lit and shaded, the sheet zigzags a little
            k = 1.06 if p % 2 == 0 else 0.86
            px(im, x, y, scale(sheet, k))
    # the turquoise grid
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if im.getpixel((x, y))[3] == 0:
                continue
            if (x - x0) % 5 == 2 or (y - y0) % 5 == 2:
                glow(im, x, y, DOME_LO, 0.35)
    # the fold lines
    for p in range(1, panels):
        fx = int(round(x0 + p * pw))
        for y in range(y0, y1 + 1):
            if im.getpixel((fx, y))[3] != 0:
                px(im, fx, y, sheet_lo if p % 2 == 1 else sheet_hi)
    # the route between three domes
    domes = [(8, 21), (16, 11), (24, 18)]
    for (a, b) in ((domes[0], domes[1]), (domes[1], domes[2])):
        n = int(max(abs(b[0] - a[0]), abs(b[1] - a[1])))
        for i in range(n + 1):
            if i % 2 == 0:
                x = int(round(a[0] + (b[0] - a[0]) * i / float(n)))
                y = int(round(a[1] + (b[1] - a[1]) * i / float(n)))
                px(im, x, y, (214, 128, 40))
    for (cx, cy) in domes:
        for y in range(cy - 3, cy + 1):
            for x in range(cx - 3, cx + 4):
                d = math.hypot(x - cx, (y - cy) * 1.1)
                if d <= 3.2:
                    px(im, x, y, mix(DOME_HI, DOME, d / 3.2) if d < 2.4 else DOME_LO)
        rect(im, cx - 3, cy + 1, cx + 3, cy + 1, (40, 60, 66))
    outline(im)
    return im


def main():
    os.makedirs(BLOCKS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    tex = [
        (BLOCKS, 'dockPlate', dock_plate()),
        (BLOCKS, 'dockPlateSide', dock_plate_side()),
        (BLOCKS, 'dockBeaconTop', dock_beacon_top()),
        (BLOCKS, 'dockBeaconSide', dock_beacon_side()),
        (ITEMS, 'returnCapsule', return_capsule()),
        (ITEMS, 'settlementMap', settlement_map()),
    ]
    k = 6
    sheet = Image.new('RGBA', (len(tex) * (S * k + 12) + 12 + 3 * S * 2 + 12, S * k + 24), (40, 40, 44, 255))
    for i, (folder, name, im) in enumerate(tex):
        im.save(os.path.join(folder, name + '.png'))
        big = im.resize((S * k, S * k), Image.NEAREST)
        sheet.paste(big, (12 + i * (S * k + 12), 12), big)
    # the plate tiled 3 x 3 round the beacon (how the 7 x 7 dock reads)
    ox = 12 + len(tex) * (S * k + 12)
    plate, top = tex[0][2].resize((S * 2, S * 2), Image.NEAREST), tex[2][2].resize((S * 2, S * 2), Image.NEAREST)
    for gx in range(3):
        for gy in range(3):
            sheet.paste(top if (gx, gy) == (1, 1) else plate, (ox + gx * S * 2, 12 + gy * S * 2))
    sheet.save(PREVIEW)
    print('wrote', ', '.join(n for _, n, _ in tex), '- preview', PREVIEW)


if __name__ == '__main__':
    main()
