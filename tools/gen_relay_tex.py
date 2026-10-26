# -*- coding: utf-8 -*-
"""ПД-7 «Ретрансляционные посты» (Silicon Age, docs/plan-ideas-2026-10-26.md): the Relay's blocks (BlockRelaySC: relay_<name>_side /
relay_<name>_top):
  relay_relay_side / _top - the Relay: a settlement-turquoise steel housing, a dish emblem and two signal lamps on the side, a round
                            hatch with the antenna's socket on top;
  relay_mast_side / _top  - the MAST: a dark steel lattice (truss) on the housing's colour, a capped plate on top.

Usage: python tools/gen_relay_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, ring, line, panel, lamp, save, preview, shade, STEEL, STEEL_D, STEEL_L, DOME, DOME_D,
                          CYAN, GREEN, OUTLINE)

HOUSING = (92, 130, 136)


def relay_side():
    im = new()
    panel(im, HOUSING, seed=21)
    rect(im, 6, 6, 20, 16, shade(HOUSING, -0.4))
    rect(im, 7, 7, 18, 14, (16, 30, 36))
    ring(im, 14, 16, 5.5, 6.6, DOME)                          # the dish
    ring(im, 14, 16, 3.0, 3.8, DOME_D)
    line(im, 14, 16, 20, 10, CYAN)
    px(im, 20, 10, (230, 255, 255))
    lamp(im, 8, 25, GREEN)
    lamp(im, 13, 25, CYAN)
    rect(im, 18, 25, 8, 2, shade(HOUSING, -0.5))               # the vent
    return im


def relay_top():
    im = new()
    panel(im, HOUSING, seed=22)
    disc(im, 16, 16, 9.5, shade(HOUSING, -0.45))
    disc(im, 16, 16, 8.5, shade(HOUSING, 0.1))
    ring(im, 16, 16, 7.5, 8.5, shade(HOUSING, -0.2))
    disc(im, 16, 16, 3.2, STEEL_D)
    disc(im, 16, 16, 2.2, STEEL_L)
    for a, b in ((16, 6), (16, 25), (6, 16), (25, 16)):
        px(im, a, b, STEEL_L)
    return im


def mast_side():
    im = new()
    for y in range(32):
        for x in range(32):
            px(im, x, y, shade(HOUSING, -0.55))
    for x in (2, 3, 28, 29):                                    # the posts
        rect(im, x, 0, 1, 32, STEEL if x in (2, 28) else STEEL_D)
    for k in range(0, 32, 8):                                   # the cross bracing
        line(im, 4, k, 27, k + 7, STEEL)
        line(im, 27, k, 4, k + 7, STEEL_D)
        rect(im, 4, k, 24, 1, STEEL_L)
    return im


def mast_top():
    im = new()
    panel(im, shade(HOUSING, -0.3), seed=23, scratches=False)
    disc(im, 16, 16, 4.5, STEEL_D)
    disc(im, 16, 16, 3.5, STEEL)
    px(im, 15, 15, STEEL_L)
    px(im, 16, 16, OUTLINE)
    return im


def main():
    out = [('relay_relay_side', relay_side()), ('relay_relay_top', relay_top()), ('relay_mast_side', mast_side()),
           ('relay_mast_top', mast_top())]
    for name, im in out:
        save('blocks', name, im)
    preview('relay_tex', [im for _, im in out], 4)


if __name__ == '__main__':
    main()
