# -*- coding: utf-8 -*-
"""ОМ-4 «ЭУФ-литограф» (Silicon Age, docs/plan-ideas-2026-10-26.md):
  blocks/machineEUVLithographFront - the EUV Lithograph's front: the Stepper EV's (machineStepperEVFront) with its window's light turned
                                     the tin plasma's violet-white and a violet frame;
  items/euvMask         - the reflective EUV mask: a square multilayer mirror (violet-silver stripes) in a steel frame;
  items/processorWafer  - a wafer with a fine grid of violet-gold dies;
  items/processor       - «Процессор»: a dark package with gold pins all round, a violet die under glass;
  items/exoProcessor    - «Экзо-процессор»: a bigger package under a hafnium heat spreader, a cyan glow at its seam;
  items/singularMatrix  - «Сингулярная матрица»: a black chip with a red-violet singular swirl in its window.

Usage: python tools/gen_euv_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (TEX, new, px, rect, disc, ring, line, outline, save, preview, shade, mix, rgb, GOLD, GOLD_D, GOLD_L, STEEL,
                          STEEL_D, STEEL_L, VIOLET, VIOLET_D, VIOLET_L, CYAN, WHITE, RED, OUTLINE)

BODY = (28, 30, 36)
HAFNIUM = (120, 136, 156)


def front():
    base = Image.open(os.path.join(TEX, 'blocks', 'machineStepperEVFront.png')).convert('RGBA')
    im = base.copy()
    for y in range(32):
        for x in range(32):
            r, g, b, a = base.getpixel((x, y))
            if b > r + 30 and b > g:                            # the stepper's blue light -> the plasma's violet
                lum = (r + g + b) / 3.0 / 255.0
                c = mix(VIOLET_D, VIOLET_L, min(1.0, lum * 1.4))
                im.putpixel((x, y), (c[0], c[1], c[2], a))
    disc(im, 16, 9, 1.6, WHITE)                                 # the tin droplet's flash
    ring(im, 16, 9, 1.6, 2.6, VIOLET_L)
    return im


def mask():
    im = new()
    rect(im, 5, 5, 22, 22, STEEL_D)
    rect(im, 6, 6, 20, 20, STEEL)
    rect(im, 6, 6, 20, 1, STEEL_L)
    for y in range(8, 24):
        c = VIOLET_L if y % 3 == 0 else VIOLET if y % 3 == 1 else mix(VIOLET_D, STEEL_L, 0.4)
        rect(im, 8, y, 16, 1, c)
    for x, y in ((10, 10), (14, 14), (18, 18)):                 # the pattern's absorber lines
        rect(im, x, 9, 1, 14, OUTLINE)
    line(im, 9, 22, 13, 18, WHITE)
    outline(im)
    return im


def wafer():
    im = new()
    disc(im, 16, 16, 13, mix(VIOLET_D, STEEL, 0.5))
    disc(im, 15.5, 15.5, 12, mix(VIOLET, STEEL_L, 0.45))
    for y in range(5, 28, 3):
        for x in range(5, 28, 3):
            if (x + 1 - 16) ** 2 + (y + 1 - 16) ** 2 < 100:
                rect(im, x, y, 2, 2, GOLD if (x + y) % 2 else VIOLET_D)
    rect(im, 13, 28, 6, 1, OUTLINE)                             # the flat
    line(im, 8, 22, 20, 10, (230, 220, 255))
    outline(im)
    return im


def package(size, die, cover=None):
    im = new()
    o = (32 - size) // 2
    for i in range(o + 2, o + size - 2, 3):                     # the pins all round
        for x, y in ((i, o - 3), (i, o + size), (o - 3, i), (o + size, i)):
            rect(im, x, y, 2, 3 if y in (o - 3, o + size) else 2, GOLD)
            px(im, x, y, GOLD_L)
        for x, y in ((o - 3, i), (o + size, i)):
            rect(im, x, y, 3, 2, GOLD)
            px(im, x, y + 1, GOLD_D)
    rect(im, o, o, size, size, OUTLINE)
    rect(im, o + 1, o + 1, size - 2, size - 2, BODY)
    rect(im, o + 1, o + 1, size - 2, 1, shade(BODY, 0.4))
    if cover:
        rect(im, o + 2, o + 2, size - 4, size - 4, cover)
        rect(im, o + 2, o + 2, size - 4, 1, shade(cover, 0.4))
        rect(im, o + 2, o + size - 3, size - 4, 1, shade(cover, -0.35))
    d = size // 2 - 1
    c = 16
    rect(im, c - d // 2 - 1, c - d // 2 - 1, d + 2, d + 2, OUTLINE)
    rect(im, c - d // 2, c - d // 2, d, d, die)
    px(im, c - d // 2, c - d // 2, shade(die, 0.6))
    return im


def processor():
    im = package(18, VIOLET)
    outline(im)
    return im


def exo_processor():
    im = package(22, CYAN, HAFNIUM)
    rect(im, 6, 15, 1, 2, CYAN)
    rect(im, 25, 15, 1, 2, CYAN)
    outline(im)
    return im


def singular_matrix():
    im = package(22, (8, 6, 12))
    for t in range(40):                                         # the swirl
        a = t * 0.45
        r = 1.0 + t * 0.12
        x, y = int(round(16 + math.cos(a) * r)), int(round(16 + math.sin(a) * r))
        px(im, x, y, mix(RED, VIOLET_L, t / 40.0))
    px(im, 16, 16, WHITE)
    outline(im)
    return im


def main():
    out = [('blocks', 'machineEUVLithographFront', front()), ('items', 'euvMask', mask()), ('items', 'processorWafer', wafer()),
           ('items', 'processor', processor()), ('items', 'exoProcessor', exo_processor()), ('items', 'singularMatrix', singular_matrix())]
    for folder, name, im in out:
        save(folder, name, im)
    preview('euv_tex', [im for _, _, im in out], 4)


if __name__ == '__main__':
    main()
