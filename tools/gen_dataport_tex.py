# -*- coding: utf-8 -*-
"""ОМ-5 «Редстоун для машин и реакторов» (Silicon Age, docs/plan-ideas-2026-10-26.md): the Data Port (BlockDataPortSC) - it stands in
a reactor's lead wall, so it is the lead block's grey:
  blocks/dataPort       - the top and bottom: a lead plate with a redstone trace round a square socket;
  blocks/dataPort_side  - the sides: the lead panel with the connector (a dark socket, gold pins, a redstone core) and the two lamps -
                          STOP red, PERMIT green - labelled by their colour stripes.

Usage: python tools/gen_dataport_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, panel, lamp, line, save, preview, shade, LEAD, LEAD_D, LEAD_L, GOLD, GOLD_L, RED, GREEN, OUTLINE)

REDSTONE = (200, 20, 10)


def top():
    im = new()
    panel(im, LEAD, seed=51)
    rect(im, 11, 11, 10, 10, LEAD_D)
    rect(im, 12, 12, 8, 8, (30, 32, 38))
    rect(im, 14, 14, 4, 4, REDSTONE)
    px(im, 14, 14, shade(REDSTONE, 0.5))
    for x0, y0, x1, y1 in ((5, 16, 11, 16), (21, 16, 27, 16), (16, 5, 16, 11), (16, 21, 16, 27)):
        line(im, x0, y0, x1, y1, shade(REDSTONE, -0.2))
    return im


def side():
    im = new()
    panel(im, LEAD, seed=52)
    rect(im, 8, 6, 16, 12, OUTLINE)                            # the connector
    rect(im, 9, 7, 14, 10, (30, 32, 38))
    for i in range(5):
        rect(im, 10 + i * 3, 8, 1, 3, GOLD)
        px(im, 10 + i * 3, 8, GOLD_L)
    rect(im, 13, 12, 6, 3, REDSTONE)
    px(im, 13, 12, shade(REDSTONE, 0.5))
    lamp(im, 9, 23, RED)                                       # STOP
    lamp(im, 21, 23, GREEN)                                    # PERMIT
    rect(im, 7, 27, 6, 1, RED)
    rect(im, 19, 27, 6, 1, GREEN)
    rect(im, 14, 22, 4, 4, LEAD_D)                             # the selector
    px(im, 15, 23, LEAD_L)
    return im


def main():
    t, s = top(), side()
    save('blocks', 'dataPort', t)
    save('blocks', 'dataPort_side', s)
    preview('dataport_tex', [t, s], 6)


if __name__ == '__main__':
    main()
