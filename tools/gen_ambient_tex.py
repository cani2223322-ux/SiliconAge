# -*- coding: utf-8 -*-
"""ПД-5 «Сборщики и живность» (Silicon Age, docs/plan-ideas-2026-10-26.md):
  entity/gatherer_sack  - the Gatherer's sack (RenderGathererSC.SackModel, a 6 x 7 x 3 box at UV 0,0 of 32 x 32): burlap with a
                          darker weave, a leather strap across its front and a turquoise settlement tag;
  particles/tunerDrone  - EntityDroneFxSC: 32 x 16, two frames - a tiny quad drone (steel body, four rotor blurs), its diode on / off;
  particles/moth        - EntityMothFxSC: 32 x 16, two frames - a pale moth, the wings spread / folded.

Usage: python tools/gen_ambient_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, line, save, preview, shade, noise, STEEL, STEEL_D, STEEL_L, GREEN, DOME, DOME_D,
                          OUTLINE)

BURLAP = (176, 146, 98)
LEATHER = (110, 70, 40)


def sack():
    im = new(32, 32)
    for y in range(32):
        for x in range(32):
            weave = 0.08 if (x + y) % 2 == 0 else -0.06
            k = weave + (noise(x, y, 7) - 0.5) * 0.18
            px(im, x, y, shade(BURLAP, k))
    # the box's faces (w 6, h 7, d 3): top 3..9 x 0..3, front 3..9 x 3..10 - a seam under the top, the strap across the front
    rect(im, 3, 3, 6, 1, shade(BURLAP, -0.35))
    for i in range(7):
        px(im, 3 + i * 6 // 7, 3 + i, LEATHER)
        px(im, 4 + i * 6 // 7, 3 + i, shade(LEATHER, 0.2))
    rect(im, 5, 1, 2, 2, shade(LEATHER, -0.2))                 # the knot on top
    rect(im, 6, 7, 2, 2, DOME_D)                               # the settlement's tag
    px(im, 6, 7, DOME)
    rect(im, 12, 3, 6, 7, shade(BURLAP, -0.12))                # the back, worn darker
    return im


def drone():
    im = new(32, 16)
    for f in range(2):
        ox = f * 16
        for rx, ry in ((3, 3), (12, 3), (3, 12), (12, 12)):     # the rotor blurs
            disc(im, ox + rx + 0.5, ry + 0.5, 2.6, STEEL_L, 120)
            px(im, ox + rx, ry, STEEL_D)
        line(im, ox + 4, 4, ox + 11, 11, STEEL_D)               # the arms
        line(im, ox + 11, 4, ox + 4, 11, STEEL_D)
        rect(im, ox + 6, 6, 4, 4, STEEL)
        rect(im, ox + 6, 6, 4, 1, STEEL_L)
        rect(im, ox + 6, 9, 4, 1, STEEL_D)
        px(im, ox + 7, 7, GREEN if f == 0 else shade(GREEN, -0.7))
        px(im, ox + 8, 8, OUTLINE)
    return im


def moth():
    im = new(32, 16)
    wing, wing_d, body = (226, 214, 184), (170, 152, 120), (96, 80, 62)
    for f in range(2):
        ox = f * 16
        if f == 0:                                             # spread
            for side in (-1, 1):
                for i in range(6):
                    for j in range(5 - i // 2):
                        px(im, ox + 8 + side * (1 + i), 5 + j - (1 if i > 3 else 0), wing if j < 3 else wing_d)
                px(im, ox + 8 + side * 4, 6, wing_d)           # the eye spots
        else:                                                  # folded up
            for side in (-1, 1):
                for i in range(3):
                    for j in range(6):
                        px(im, ox + 8 + side * (1 + i // 2) - (1 if side < 0 else 0) * 0, 3 + j, wing if i < 2 else wing_d)
        rect(im, ox + 7, 4, 2, 7, body)
        px(im, ox + 7, 3, body)
        px(im, ox + 8, 3, body)
        px(im, ox + 6, 2, body)                                # the feelers
        px(im, ox + 9, 2, body)
    return im


def main():
    s, d, m = sack(), drone(), moth()
    save('entity', 'gatherer_sack', s)
    save('particles', 'tunerDrone', d)
    save('particles', 'moth', m)
    preview('ambient_tex', [s, d, m], 6)


if __name__ == '__main__':
    main()
