# -*- coding: utf-8 -*-
"""The personal EMP screen's bubble (Silicon Age, Подложка Ф3, docs/plan-substrate-tech-f3.md §3.3, rule ПБ-19) - 64 x 64 RGBA.

Writes src/main/resources/assets/siliconage/textures/misc/emp_screen.png: a honeycomb of thin white lines on transparency
(MobArmorClientSC tints it the screen's blue, draws it additively and scrolls it over an ellipsoid round the mob), a few
cells faintly filled, a soft glow along the edges. It tiles both ways: two hex periods across, four down (the cells come
out a little squat - on the sphere nobody sees it).

Usage: python tools/gen_mob_screen_tex.py [project dir] [--preview file.png]
  --preview: also a x6 sheet (the texture 2 x 2, tinted, on a dark ground).
"""
import math
import os
import random
import sys

from PIL import Image

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if '--preview' in ARGS:
    i = ARGS.index('--preview')
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'mob_screen_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'misc')
S = 64
# hex periods in the tile: across (3 sides each, flat-topped cells) and down (sqrt(3) sides each)
PX, PY = 2, 4
SQ3 = math.sqrt(3.0)
# line half-width and the glow's reach, in lattice units (one hex side = 1)
LINE, GLOW = 0.07, 0.30
TINT = (90, 174, 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def centres():
    """Every hex centre of the periodic lattice near the tile (flat-topped: two offset rectangular sub-lattices)."""
    out = []
    for i in range(-1, PX + 2):
        for j in range(-1, PY + 2):
            out.append((3.0 * i, SQ3 * j, (i % PX, j % PY, 0)))
            out.append((3.0 * i + 1.5, SQ3 * (j + 0.5), (i % PX, j % PY, 1)))
    return out


def texture():
    rnd = random.Random(1414)
    cs = centres()
    lit = {}
    for i in range(PX):
        for j in range(PY):
            for k in range(2):
                lit[(i, j, k)] = rnd.random() < 0.22            # a few cells faintly filled (the same cell on both sides of a seam)
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    for y in range(S):
        for x in range(S):
            u = (x + 0.5) / S * 3.0 * PX                         # the tile spans PX periods across, PY down
            v = (y + 0.5) / S * SQ3 * PY
            d = sorted(((u - cx) ** 2 + (v - cy) ** 2, cell) for cx, cy, cell in cs)
            d1, cell = math.sqrt(d[0][0]), d[0][1]
            d2 = math.sqrt(d[1][0])
            edge = (d2 - d1) / 2.0                                # ~ distance to the cell's border
            a = 0.0
            if edge < LINE:
                a = 1.0
            elif edge < GLOW:
                a = 0.55 * (1.0 - (edge - LINE) / (GLOW - LINE)) ** 2
            if lit[cell]:
                a = max(a, 0.16 + 0.10 * (1.0 - d1 / 1.7))
            a = max(a, 0.05)                                      # a faint film everywhere: the bubble reads as a surface
            hi = 255 if edge < LINE else 236
            px[x, y] = (hi, hi, 255, clamp(a * 235))
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    img = texture()
    path = os.path.join(OUT, 'emp_screen.png')
    img.save(path)
    print('wrote', path)
    if PREVIEW:
        sheet = Image.new('RGB', (S * 2, S * 2), (14, 18, 26))
        tinted = Image.new('RGBA', (S, S))
        src, dst = img.load(), tinted.load()
        for y in range(S):
            for x in range(S):
                r, g, b, a = src[x, y]
                dst[x, y] = (r * TINT[0] // 255, g * TINT[1] // 255, b * TINT[2] // 255, a)
        for oy in (0, S):
            for ox in (0, S):
                sheet.paste(tinted, (ox, oy), tinted)
        sheet = sheet.resize((S * 2 * 6, S * 2 * 6), Image.NEAREST)
        sheet.save(PREVIEW)
        print('preview', PREVIEW)


if __name__ == '__main__':
    main()
