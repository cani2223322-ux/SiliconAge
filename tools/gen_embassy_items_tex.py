# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 6, package T: the embassies' item (Silicon Age, docs/plan-substrate-tech-f6.md §8.4, rule ПО-26).

Writes into src/main/resources/assets/siliconage/textures:
  items/embassyFinder - «Пеленгатор посольства»: what its recipe is made of - a compass's steel case round a round screen (the
                        Great Ring drawn on it in the Tuners' turquoise of the embassies' beam, a beam rising from it, a needle
                        with a red tip), set on a silicon wafer plate with two transistors soldered to it, and an antenna with
                        a turquoise tip;
and a preview sheet next to this script (embassy_items_preview.png). 32 x 32 as the mod's other items (gen_camps_tex.py's
ruinFinder is its cousin: the same outline and light from the top left).

Usage: python tools/gen_embassy_items_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'embassy_items_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the compass's steel
STEEL = (120, 140, 160)
STEEL_L = (176, 192, 210)
STEEL_D = (78, 92, 108)
STEEL_DK = (44, 52, 62)
# the screen and the Tuners' turquoise (RenderTunerSC.BEAM, EmbassyBeamClientSC's 0x48F0DC)
SCREEN_IN = (30, 96, 90)
SCREEN_OUT = (10, 34, 34)
BEAM = (72, 240, 220)
BEAM_L = (200, 255, 248)
BEAM_D = (36, 150, 138)
NEEDLE = (226, 236, 240)
NEEDLE_RED = (226, 58, 58)
# the silicon wafer (the mod's Si wafer: blue-grey with a sheen) and the transistors
WAFER = (62, 70, 98)
WAFER_L = (104, 116, 150)
WAFER_D = (40, 46, 66)
CHIP = (30, 30, 34)
CHIP_L = (70, 72, 80)
LEG = (196, 200, 208)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def px(im, x, y, c, a=255):
    w, h = im.size
    x, y = int(round(x)), int(round(y))
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


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


def embassy_finder():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy = 15.5, 12.5
    # the wafer plate under the case: a rounded square, a grid of dies, a sheen across it
    for y in range(18, 30):
        for x in range(6, 26):
            if (x in (6, 25)) and (y in (18, 29)):
                continue                                        # the rounded corners
            c = WAFER
            if (x - 6) % 4 == 0 or (y - 18) % 4 == 0:
                c = WAFER_D                                     # the dies' streets
            if x + y in (37, 38, 39):
                c = mix(c, WAFER_L, 0.6)                        # the sheen
            if x == 6 or y == 18:
                c = mix(c, WAFER_L, 0.4)
            elif x == 25 or y == 29:
                c = mix(c, WAFER_D, 0.5)
            px(im, x, y, c)
    # two transistors soldered on it: black bodies with a light edge and a tab, three legs each
    for x0 in (8, 20):
        for y in range(23, 26):
            for x in range(x0, x0 + 4):
                px(im, x, y, CHIP_L if y == 23 or x == x0 else CHIP)
        px(im, x0 + 1, 22, LEG)
        px(im, x0 + 2, 22, mix(LEG, WAFER_D, 0.3))
        for lx in (x0, x0 + 2, x0 + 3):
            px(im, lx, 26, LEG)
            px(im, lx, 27, mix(LEG, WAFER_D, 0.4))
    # the compass's case: a steel ring lit from the top left
    for y in range(1, 25):
        for x in range(4, 28):
            d = math.hypot(x - cx, y - cy)
            if d < 10.6:
                if d > 9.2:
                    lit = ((cx - x) + (cy - y)) / (2 * d + 1e-6)
                    px(im, x, y, mix(STEEL, STEEL_L if lit > 0 else STEEL_D, abs(lit) * 1.2))
                elif d > 8.2:
                    px(im, x, y, STEEL_DK)                      # the bezel's dark groove
    # the screen: dark teal deepening outwards
    for y in range(3, 23):
        for x in range(6, 26):
            d = math.hypot(x - cx, y - cy)
            if d < 8.2:
                px(im, x, y, mix(SCREEN_IN, SCREEN_OUT, d / 8.2))
    # the Great Ring on it, the beam rising from its middle, four ticks of the compass
    for y in range(3, 23):
        for x in range(6, 26):
            d = math.hypot(x - cx, y - cy)
            if abs(d - 4.6) < 0.55:
                px(im, x, y, BEAM if (x + y) % 4 else BEAM_D)
    for y in range(5, 13):
        px(im, 15, y, BEAM_L if y < 8 else BEAM)
        px(im, 16, y, BEAM if y < 8 else BEAM_D)
    for tx, ty in ((15.5, 4.8), (23.2, 12.5), (15.5, 20.2), (7.8, 12.5)):
        px(im, tx, ty, mix(BEAM_D, SCREEN_OUT, 0.3))
    # the needle: a short one inside the ring - its red tip to the top right (the embassy's bearing), a pale tail
    for t in range(1, 4):
        px(im, 15.5 + t * 0.8, 12.5 - t * 0.8, NEEDLE_RED if t > 1 else mix(NEEDLE_RED, NEEDLE, 0.4))
        px(im, 15.5 - t * 0.8, 12.5 + t * 0.8, mix(NEEDLE, SCREEN_IN, 0.35))
    px(im, 15, 12, STEEL_L)
    px(im, 16, 13, STEEL_D)
    # the antenna off the case's top right and its turquoise tip
    for t in range(6):
        px(im, 24 + t // 2, 5 - t, STEEL_L if t % 2 else STEEL)
    px(im, 27, 0, BEAM_L)
    px(im, 28, 0, BEAM)
    px(im, 27, 1, BEAM_D)
    outline(im)
    return im


def main():
    items = [('items/embassyFinder.png', embassy_finder())]
    for rel, im in items:
        p = os.path.join(TEX, rel)
        d = os.path.dirname(p)
        if not os.path.isdir(d):
            os.makedirs(d)
        im.save(p)
    # the preview: x8 on the mod's dark grey
    k = 8
    sheet = Image.new('RGBA', (len(items) * (S * k + 8) + 8, S * k + 16), (64, 64, 68, 255))
    for i, (rel, im) in enumerate(items):
        big = im.resize((S * k, S * k), Image.NEAREST)
        sheet.paste(big, (8 + i * (S * k + 8), 8), big)
    sheet.save(PREVIEW)
    print('wrote %d textures and %s' % (len(items), PREVIEW))


if __name__ == '__main__':
    main()
