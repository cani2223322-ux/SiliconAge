# -*- coding: utf-8 -*-
"""ПД-6 «Протоколы прорыва» (Silicon Age, docs/plan-ideas-2026-10-26.md): items/breachProtocol_<key> - the five protocol cartridges
(BreachProtocolsSC.KEYS): a dark data card with a gold contact edge and a coloured stripe, its emblem by protocol -
  deafCoils     - a containment coil with a slash through it (cyan);
  shortFuse     - a burning fuse to a timer's tick (orange);
  swarm         - a cluster of red dots round a bigger one (red);
  eclipseShadow - a dark disc with a violet corona (violet);
  jamming       - two broken waves (yellow-green).

Usage: python tools/gen_protocol_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import (new, px, rect, disc, ring, line, outline, save, preview, shade, GOLD, GOLD_D, GOLD_L, CYAN, ORANGE, RED,
                          VIOLET, VIOLET_L, WHITE, OUTLINE)

KEYS = ['deafCoils', 'shortFuse', 'swarm', 'eclipseShadow', 'jamming']
COLORS = {'deafCoils': CYAN, 'shortFuse': ORANGE, 'swarm': RED, 'eclipseShadow': VIOLET, 'jamming': (200, 230, 80)}
CARD = (44, 50, 60)


def card(c):
    im = new()
    rect(im, 6, 3, 20, 26, shade(CARD, -0.3))
    rect(im, 7, 4, 18, 24, CARD)
    rect(im, 7, 4, 18, 1, shade(CARD, 0.35))
    rect(im, 7, 4, 1, 24, shade(CARD, 0.2))
    for i in range(5):                                          # the gold contacts at the bottom edge
        rect(im, 9 + i * 3, 25, 2, 3, GOLD)
        px(im, 9 + i * 3, 25, GOLD_L)
        px(im, 10 + i * 3, 27, GOLD_D)
    rect(im, 7, 7, 18, 2, c)                                   # the protocol's stripe
    rect(im, 7, 7, 18, 1, shade(c, 0.4))
    rect(im, 9, 11, 14, 12, shade(CARD, -0.45))               # the emblem's window
    return im


def emblem(im, key, c):
    cx, cy = 16, 17
    if key == 'deafCoils':
        for y in range(12, 22, 2):
            rect(im, 12, y, 8, 1, c)
            px(im, 11, y + 1, shade(c, -0.3))
        line(im, 10, 22, 21, 11, RED)
        line(im, 11, 22, 22, 11, shade(RED, -0.3))
    elif key == 'shortFuse':
        ring(im, cx + 2, cy + 1, 3.2, 4.2, c)
        line(im, cx + 2, cy + 1, cx + 2, cy - 2, WHITE)
        line(im, cx + 2, cy + 1, cx + 4, cy + 1, WHITE)
        line(im, 10, 12, 13, 15, shade(c, -0.3))
        px(im, 10, 12, (255, 240, 140))
        px(im, 11, 11, (255, 200, 80))
    elif key == 'swarm':
        disc(im, cx, cy, 2.6, c)
        for x, y in ((11, 13), (20, 12), (12, 21), (21, 20), (16, 12), (10, 17), (22, 16)):
            px(im, x, y, c)
            px(im, x + 1, y, shade(c, -0.4))
    elif key == 'eclipseShadow':
        disc(im, cx, cy, 5.2, c)
        disc(im, cx, cy, 4.2, VIOLET_L)
        disc(im, cx + 0.8, cy - 0.4, 4.0, OUTLINE)
    else:
        for x in range(10, 23):
            y1 = cy - 3 + (1 if (x // 2) % 2 else -1)
            y2 = cy + 3 + (-1 if (x // 2) % 2 else 1)
            if x not in (15, 16):                               # broken in the middle
                px(im, x, y1, c)
                px(im, x, y2, shade(c, -0.25))


def main():
    out = []
    for k in KEYS:
        im = card(COLORS[k])
        emblem(im, k, COLORS[k])
        outline(im)
        save('items', 'breachProtocol_' + k, im)
        out.append(im)
    preview('protocol_tex', out, 4)


if __name__ == '__main__':
    main()
