# -*- coding: utf-8 -*-
"""ОМ-7 «Сингулярный энергобанк» (Silicon Age, docs/plan-ideas-2026-10-26.md): the Energy Bank's blocks (BlockEnergyBankSC:
energyBank_<name>, the same on every side):
  casing      - a dark crimson-steel panel (the SV casing's red, darker), hafnium bolts;
  glass       - a crimson frame round clear glass (the middle fully clear: the block draws it cut out) with two glints;
  controller  - the casing with a holo screen: a charge bar and a small graph, green in, orange out;
  cell4       - a cell: a capacitor stack in a violet glow, one bar of charge; cell16 - four bars, a brighter glow;
  portLV..SV  - the casing with a round socket and a ring in the tier's colour (GuiGaugeSC.tierColor), the tier's notches.

Usage: python tools/gen_energybank_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, ring, line, panel, save, preview, shade, mix, rgb, TIER_COLORS, TIERS, VIOLET,
                          VIOLET_L, VIOLET_D, GREEN, ORANGE, CYAN, OUTLINE, WHITE)

CASING = (96, 40, 50)
HAFNIUM = (150, 164, 182)


def casing():
    im = new()
    panel(im, CASING, seed=71)
    for bx, by in ((3, 3), (28, 3), (3, 28), (28, 28)):
        px(im, bx, by, HAFNIUM)
    rect(im, 6, 15, 20, 2, shade(CASING, -0.35))               # the seam
    rect(im, 6, 15, 20, 1, shade(CASING, 0.2))
    return im


def glass():
    im = new()
    frame = shade(CASING, 0.05)
    rect(im, 0, 0, 32, 32, shade(CASING, -0.45))
    rect(im, 1, 1, 30, 30, frame)
    rect(im, 1, 1, 30, 1, shade(CASING, 0.35))
    rect(im, 3, 3, 26, 26, shade(CASING, -0.45))
    for y in range(4, 28):                                      # the clear middle (cut out by the block)
        for x in range(4, 28):
            im.putpixel((x, y), (0, 0, 0, 0))
    for i in range(4):
        px(im, 8 + i, 12 - i, (220, 230, 240))
        px(im, 18 + i, 24 - i, (200, 210, 220))
    return im


def controller():
    im = casing()
    rect(im, 5, 5, 22, 16, OUTLINE)
    rect(im, 6, 6, 20, 14, (8, 14, 22))
    rect(im, 7, 7, 18, 2, shade(CYAN, -0.6))
    rect(im, 7, 7, 12, 2, CYAN)                                 # the charge bar
    ys_in = [17, 16, 16, 15, 14, 15, 13, 12, 13, 12, 11, 12, 11, 11, 12, 11, 10, 11]
    ys_out = [18, 18, 17, 17, 17, 16, 16, 17, 15, 16, 15, 15, 14, 15, 15, 14, 14, 13]
    for i in range(18):
        px(im, 7 + i, ys_in[i], GREEN)
        px(im, 7 + i, ys_out[i], ORANGE)
    rect(im, 6, 23, 3, 3, GREEN)
    px(im, 6, 23, WHITE)
    return im


def cell(bars, glow):
    im = new()
    panel(im, shade(CASING, -0.2), seed=72 + bars)
    rect(im, 6, 5, 20, 22, OUTLINE)
    rect(im, 7, 6, 18, 20, shade(VIOLET_D, -0.5))
    for k in range(4):                                          # the capacitor plates
        y = 8 + k * 4
        rect(im, 9, y, 14, 2, mix(VIOLET, VIOLET_L, glow) if k < bars else VIOLET_D)
        rect(im, 9, y, 14, 1, shade(mix(VIOLET, VIOLET_L, glow), 0.3) if k < bars else shade(VIOLET_D, 0.2))
    rect(im, 15, 24, 2, 2, HAFNIUM)
    return im


def port(t):
    im = casing()
    c = rgb(TIER_COLORS[t])
    disc(im, 16, 15, 8.6, OUTLINE)
    ring(im, 16, 15, 6.4, 8.0, shade(c, 0.25) if t != 6 else (120, 80, 200))   # XV's colour is too dark on the red: lightened
    disc(im, 16, 15, 5.2, (18, 20, 26))
    disc(im, 16, 15, 2.4, shade(c, 0.5) if t != 6 else VIOLET_L)
    for k in range(t + 1):                                      # the tier's notches
        rect(im, 7 + k * 2 + (8 - t - 1), 27, 1, 2, shade(c, 0.4) if t != 6 else VIOLET_L)
    return im


def main():
    out = [('casing', casing()), ('glass', glass()), ('controller', controller()), ('cell4', cell(1, 0.2)), ('cell16', cell(4, 0.7))]
    for t, name in enumerate(TIERS):
        out.append(('port' + name, port(t)))
    for name, im in out:
        save('blocks', 'energyBank_' + name, im)
    preview('energybank_tex', [im for _, im in out], 3)


if __name__ == '__main__':
    main()
