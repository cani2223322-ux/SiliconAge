# -*- coding: utf-8 -*-
"""ОМ-2 «Ремонт и рецепты» (Silicon Age, docs/plan-ideas-2026-10-26.md): items/nanitePaste - «Нанитовая паста»: a squeezed silver tube
with a blue cap, a bead of glittering grey-cyan paste out of its nozzle; the mod's dark outline.

Usage: python tools/gen_paste_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, line, outline, save, preview, shade, STEEL, STEEL_D, STEEL_L, CYAN, WHITE)

CAP = (46, 95, 200)
PASTE = (120, 150, 160)


def tube():
    im = new()
    # the tube lies diagonally: its crimped end low left, the nozzle high right
    for i in range(16):
        x, y = 6 + i, 25 - i
        w = 4 if i > 2 else 2 + i // 2
        for k in range(-w, w + 1):
            c = STEEL_L if k < -w + 2 else STEEL_D if k > w - 2 else STEEL
            px(im, x + k // 2, y + k // 2 + (k + 1) // 2, c)
    for i in range(5):                                          # the crimp
        px(im, 4 + i, 27 - i + 1, STEEL_D)
    rect(im, 21, 7, 4, 4, CAP)                                 # the cap, off, beside the nozzle
    rect(im, 21, 7, 4, 1, shade(CAP, 0.4))
    rect(im, 23, 12, 3, 3, STEEL_L)                            # the nozzle
    disc(im, 27, 10, 2.6, PASTE)                               # the bead of paste
    disc(im, 26.5, 9.5, 1.4, shade(PASTE, 0.25))
    for x, y in ((26, 9), (28, 11), (27, 8)):
        px(im, x, y, CYAN)
    px(im, 25, 9, WHITE)
    line(im, 9, 21, 15, 15, shade(STEEL, 0.3))                  # the label stripe
    outline(im)
    return im


def main():
    im = tube()
    save('items', 'nanitePaste', im)
    preview('paste_tex', [im], 6)


if __name__ == '__main__':
    main()
