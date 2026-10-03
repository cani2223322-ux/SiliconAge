# -*- coding: utf-8 -*-
"""Singular Service Station + Gravitational Stabiliser block textures (Silicon Age, docs/plan-singular-armor.md §7, look В1).

Writes into src/main/resources/assets/siliconage/textures/blocks (32 x 32, the mod's block size):
  singularStationTop / TopOn / Side / Front / Back / Bottom.png - a dark pedestal in the Singular A palette
      (plate base (34,22,48), accent (190,110,255)); Side keeps the Armour Service Station's eight window
      recesses where ArmorStationRendererSC draws the tank levels (x 4 + 3k .. +2, y 7 .. 25);
  gravStabiliser.png / gravStabiliserTop.png - the stabiliser's pillar (the block draws only x 10..22,
      y 8..32 of the side and the 12 x 12 middle of the top: its bounds are 5/16..11/16 wide, 12/16 high).
and a preview sheet (x8) next to this script.

Usage: python gen_singular_station_tex.py [project dir]
"""
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'singular_station_tex_preview.png')

BASE = (34, 22, 48)
ACCENT = (190, 110, 255)
S = 32


def clamp(v):
    return max(0, min(255, int(round(v))))


def scale(c, k, add=0):
    return tuple(clamp(v * k + add) for v in c)


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


OUTLINE = (10, 6, 16)
PLATE = BASE
PLATE_HI = scale(BASE, 1.55, 10)
PLATE_LO = scale(BASE, 0.62)
PLATE_MID = scale(BASE, 1.2, 4)
RECESS = (12, 8, 18)
RIVET = (120, 100, 150)
GLOW = ACCENT
GLOW_HI = mix(ACCENT, (255, 255, 255), 0.55)
GLOW_DIM = scale(ACCENT, 0.42)


def new():
    return Image.new('RGBA', (S, S), PLATE + (255,))


def px(im, x, y, c):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((x, y), tuple(c) + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def bevel(im, x0, y0, x1, y1, hi, lo, fill=None):
    if fill is not None:
        rect(im, x0, y0, x1, y1, fill)
    rect(im, x0, y0, x1, y0, hi)
    rect(im, x0, y0, x0, y1, hi)
    rect(im, x0, y1, x1, y1, lo)
    rect(im, x1, y0, x1, y1, lo)


def plate_noise(im, x0, y0, x1, y1, seed=1):
    """A faint brushed look: every few pixels a slightly lighter / darker one (deterministic)."""
    v = seed * 7919
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            v = (v * 1103515245 + 12345) & 0x7fffffff
            r = v % 17
            if r == 0:
                px(im, x, y, scale(im.getpixel((x, y))[:3], 1.12, 2))
            elif r == 1:
                px(im, x, y, scale(im.getpixel((x, y))[:3], 0.88))


def frame(im):
    """The pedestal's outer frame: outline, a bevelled rim, the inner plate."""
    rect(im, 0, 0, S - 1, S - 1, OUTLINE)
    bevel(im, 1, 1, S - 2, S - 2, PLATE_HI, PLATE_LO, PLATE_MID)
    bevel(im, 3, 3, S - 4, S - 4, PLATE_LO, PLATE_HI, PLATE)
    plate_noise(im, 4, 4, S - 5, S - 5)


def rivets(im, pts):
    for x, y in pts:
        px(im, x, y, RIVET)
        px(im, x + 1, y + 1, PLATE_LO)


def side():
    im = new()
    frame(im)
    # the window strip: a recessed band with eight slots (the renderer fills them with the gas levels)
    bevel(im, 3, 6, 28, 26, PLATE_LO, PLATE_HI, scale(BASE, 0.75))
    for k in range(8):
        x = 4 + 3 * k
        rect(im, x, 7, x + 1, 25, RECESS)
        px(im, x, 7, OUTLINE)
        px(im, x + 1, 7, OUTLINE)
    # accent lines under and over the band
    rect(im, 4, 4, 27, 4, GLOW_DIM)
    rect(im, 4, 28, 27, 28, GLOW_DIM)
    rect(im, 13, 28, 18, 28, GLOW)
    rivets(im, [(2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)])
    return im


def front():
    im = new()
    frame(im)
    # a dark screen with three glowing bars (the look in sing_3_station_block.png) and the singularity ring
    bevel(im, 6, 6, 25, 25, PLATE_LO, PLATE_HI, RECESS)
    for i, x in enumerate((9, 13, 17)):
        h = (13, 11, 9)[i]
        rect(im, x, 23 - h, x + 1, 22, GLOW_DIM)
        rect(im, x, 23 - h, x + 1, 23 - h, GLOW)
    # the ring, right
    cx, cy, r = 22, 11, 2.6
    for y in range(6, 18):
        for x in range(17, 27):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if abs(d - r) < 0.75:
                px(im, x, y, GLOW)
            elif d < r - 0.75:
                px(im, x, y, OUTLINE)
    px(im, cx - 1, cy - 2, GLOW_HI)
    rivets(im, [(2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)])
    return im


def back():
    im = new()
    frame(im)
    # two vents and a plate
    for y in (8, 11, 14, 17, 20, 23):
        rect(im, 7, y, 24, y, PLATE_LO)
        rect(im, 7, y + 1, 24, y + 1, PLATE_HI)
    rect(im, 13, 26, 18, 26, GLOW_DIM)
    rivets(im, [(2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)])
    return im


def bottom():
    im = new()
    rect(im, 0, 0, S - 1, S - 1, OUTLINE)
    bevel(im, 1, 1, S - 2, S - 2, PLATE_MID, PLATE_LO, scale(BASE, 0.8))
    plate_noise(im, 2, 2, S - 3, S - 3, 3)
    rivets(im, [(4, 4), (S - 6, 4), (4, S - 6), (S - 6, S - 6)])
    return im


def top(lit):
    im = new()
    frame(im)
    c = (S - 1) / 2.0
    ring_out, ring_in = 12.2, 9.6
    for y in range(S):
        for x in range(S):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if ring_in <= d <= ring_out:
                edge = d - ring_in < 0.9 or ring_out - d < 0.9
                if lit:
                    px(im, x, y, GLOW_HI if not edge else GLOW)
                else:
                    px(im, x, y, PLATE_HI if not edge else PLATE_LO)
            elif d < 4.2:
                px(im, x, y, OUTLINE if d < 2.6 else (GLOW if lit else GLOW_DIM))
            elif d < ring_in and d > 8.6:
                px(im, x, y, PLATE_LO)
    if lit:                                 # four little emitters on the ring
        for x, y in ((15, 4), (16, 27), (4, 16), (27, 15)):
            px(im, x, y, (255, 255, 255))
    rivets(im, [(2, 2), (S - 4, 2), (2, S - 4), (S - 4, S - 4)])
    return im


def stabiliser_side():
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    # only x 10..21 and y 8..31 are drawn by the block
    x0, x1, y0, y1 = 10, 21, 8, 31
    rect(im, x0, y0, x1, y1, OUTLINE)
    bevel(im, x0 + 1, y0 + 1, x1 - 1, y1 - 1, PLATE_HI, PLATE_LO, PLATE)
    plate_noise(im, x0 + 2, y0 + 2, x1 - 2, y1 - 2, 5)
    # bands and an accent groove up the middle
    for y in (12, 20, 27):
        rect(im, x0 + 1, y, x1 - 1, y, PLATE_LO)
        rect(im, x0 + 1, y + 1, x1 - 1, y + 1, PLATE_MID)
    rect(im, 15, y0 + 3, 16, y1 - 3, RECESS)
    rect(im, 15, y0 + 4, 16, y0 + 10, GLOW_DIM)
    px(im, 15, y0 + 4, GLOW)
    px(im, 16, y0 + 4, GLOW)
    # the cap
    rect(im, x0, y0, x1, y0 + 1, PLATE_HI)
    rect(im, x0, y0, x1, y0, OUTLINE)
    return im


def stabiliser_top():
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    x0, x1 = 10, 21
    rect(im, x0, x0, x1, x1, OUTLINE)
    bevel(im, x0 + 1, x0 + 1, x1 - 1, x1 - 1, PLATE_HI, PLATE_LO, PLATE)
    rect(im, 14, 14, 17, 17, RECESS)
    rect(im, 15, 15, 16, 16, GLOW_DIM)
    return im


def main():
    out = {
        'singularStationTop': top(False),
        'singularStationTopOn': top(True),
        'singularStationSide': side(),
        'singularStationFront': front(),
        'singularStationBack': back(),
        'singularStationBottom': bottom(),
        'gravStabiliser': stabiliser_side(),
        'gravStabiliserTop': stabiliser_top(),
    }
    os.makedirs(OUT, exist_ok=True)
    for name, im in out.items():
        im.save(os.path.join(OUT, name + '.png'))
        print('wrote', name)
    k = 8
    sheet = Image.new('RGBA', (len(out) * (S * k + 8), S * k), (24, 26, 32, 255))
    for i, im in enumerate(out.values()):
        sheet.paste(im.resize((S * k, S * k), Image.NEAREST), (i * (S * k + 8), 0), im.resize((S * k, S * k), Image.NEAREST))
    sheet.save(PREVIEW)
    print('preview', PREVIEW)


if __name__ == '__main__':
    main()
