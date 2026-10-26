# -*- coding: utf-8 -*-
"""ПД-4 «Объявлен в розыск» (Silicon Age, docs/plan-ideas-2026-10-26.md): items/wantedToken - «Жетон розыска»: a round brass token on a
cord, its face dark red with the white crosshair of the settlements' wanted board and four rivets; the mod's dark outline.

Usage: python tools/gen_wanted_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, ring, line, outline, save, preview, shade, GOLD, GOLD_D, GOLD_L, RED, RED_D, WHITE,
                          STEEL_D, STEEL_L)


def token():
    im = new()
    cx, cy = 16, 18
    # the cord through the eye at the top
    for x, y in ((12, 2), (13, 3), (14, 4), (19, 2), (18, 3), (17, 4)):
        px(im, x, y, STEEL_D)
    ring(im, 16, 6, 1.2, 2.6, GOLD_D)
    disc(im, cx, cy, 11.5, GOLD_D)
    disc(im, cx, cy, 10.5, GOLD)
    ring(im, cx, cy, 9.6, 10.5, GOLD_L)
    disc(im, cx, cy, 8.6, RED_D)
    disc(im, cx - 0.6, cy - 0.6, 7.4, RED)
    # the crosshair
    ring(im, cx, cy, 4.2, 5.2, WHITE)
    for d in range(-8, 9):
        if abs(d) > 2:
            px(im, cx + d, cy, WHITE)
            px(im, cx, cy + d, WHITE)
    px(im, cx, cy, WHITE)
    # the rivets on the rim
    for x, y in ((cx - 8, cy - 8), (cx + 7, cy - 8), (cx - 8, cy + 7), (cx + 7, cy + 7)):
        px(im, x, y, GOLD_L)
        px(im, x + 1, y + 1, GOLD_D)
    # a scratch and a glint
    line(im, cx - 5, cy + 6, cx - 2, cy + 3, shade(RED, 0.25))
    px(im, cx - 6, cy - 9, STEEL_L)
    outline(im)
    return im


def main():
    im = token()
    save('items', 'wantedToken', im)
    preview('wanted_tex', [im], 6)


if __name__ == '__main__':
    main()
