# -*- coding: utf-8 -*-
"""Ground / Space Bridge item icons, stage 2 (Silicon Age, docs/plan-ground-bridge.md §2, §8).

Writes into src/main/resources/assets/siliconage/textures/items (32 x 32, the mod's item size):
  bridgeRemote     - a dark handheld remote: an antenna, a green screen with the ring and a swirl, buttons;
  spaceRemote      - the same body in blue steel: a blue-white starry screen and a violet Singular Matter window;
  coordinator      - a round teal-and-brass locator: a crosshair over a little map, a pin;
  bridgeLinkModule - a helmet module card: a violet circuit board with a green ring emblem and gold contacts;
and a preview sheet (x6) next to this script.

Usage: python gen_bridge_items_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'items')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bridge_items_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (14, 16, 18, 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def px(im, x, y, c, a=255):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def round_box(im, x0, y0, x1, y1, fill, hi, lo, r=2):
    """A box with rounded corners, an outline, a lit top-left and a shaded bottom-right edge."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            dx = max(x0 + r - x, 0, x - (x1 - r))
            dy = max(y0 + r - y, 0, y - (y1 - r))
            if dx * dx + dy * dy > r * r + 1:
                continue
            edge = dx * dx + dy * dy > (r - 1) * (r - 1) and (dx > 0 or dy > 0) or x in (x0, x1) or y in (y0, y1)
            if edge:
                px(im, x, y, OUTLINE[:3])
            elif x == x0 + 1 or y == y0 + 1:
                px(im, x, y, hi)
            elif x == x1 - 1 or y == y1 - 1:
                px(im, x, y, lo)
            else:
                px(im, x, y, fill)


def swirl(im, cx, cy, r, inner, outer, arms=2, twist=2.6):
    for y in range(int(cy - r) - 1, int(cy + r) + 2):
        for x in range(int(cx - r) - 1, int(cx + r) + 2):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r:
                continue
            a = math.atan2(dy, dx)
            v = 0.5 + 0.5 * math.cos(arms * a + twist * d)
            c = mix(outer, inner, max(0.0, 1.0 - d / r) * 0.7 + v * 0.3)
            px(im, x, y, c)


def remote(space):
    im = Image.new('RGBA', (S, S), CLEAR)
    body = (70, 86, 110) if space else (52, 58, 60)
    hi = mix(body, (255, 255, 255), 0.35)
    lo = mix(body, (0, 0, 0), 0.45)
    # the antenna
    for y in range(2, 9):
        px(im, 22, y, (150, 156, 160))
        px(im, 23, y, (90, 96, 100))
    px(im, 22, 1, (255, 90, 80) if not space else (140, 200, 255))
    px(im, 23, 1, (200, 60, 50) if not space else (90, 150, 230))
    round_box(im, 8, 7, 24, 30, body, hi, lo, r=3)
    # the screen
    rect(im, 10, 9, 22, 18, (8, 12, 10) if not space else (6, 8, 20))
    if space:
        swirl(im, 16, 13.5, 4.6, (230, 240, 255), (40, 90, 200), arms=3)
        for (sx, sy) in ((11, 10), (21, 11), (12, 17), (20, 17)):
            px(im, sx, sy, (255, 255, 255))
    else:
        # the ring (a little square of coils) with a green vortex inside
        for i in range(12, 21):
            px(im, i, 10, (120, 110, 160))
            px(im, i, 17, (120, 110, 160))
        for j in range(10, 18):
            px(im, 12, j, (120, 110, 160))
            px(im, 20, j, (120, 110, 160))
        swirl(im, 16.5, 14, 3.4, (200, 255, 210), (30, 170, 80))
    # buttons
    cols = [(90, 230, 120), (230, 200, 80), (230, 90, 80)] if not space else [(120, 190, 255), (200, 120, 255), (230, 90, 80)]
    for i, c in enumerate(cols):
        x = 11 + i * 4
        rect(im, x, 21, x + 2, 22, c)
        px(im, x, 21, mix(c, (255, 255, 255), 0.5))
    rect(im, 11, 25, 13, 26, (110, 116, 120))
    rect(im, 15, 25, 17, 26, (110, 116, 120))
    if space:
        # the Singular Matter window (the key)
        rect(im, 19, 24, 21, 27, (60, 20, 90))
        px(im, 20, 25, (200, 90, 255))
        px(im, 20, 26, (150, 60, 220))
    else:
        rect(im, 19, 25, 21, 26, (110, 116, 120))
    return im


def coordinator():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy, r = 15.5, 16.5, 12.5
    brass = (196, 160, 70)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                if d > r - 1.1:
                    px(im, x, y, OUTLINE[:3])
                elif d > r - 3:
                    t = (x - y) / 40.0 + 0.5
                    px(im, x, y, mix(mix(brass, (255, 230, 150), 0.4), mix(brass, (90, 60, 20), 0.5), t))
                else:
                    # a little map: teal ground with contour lines
                    v = math.sin((x * 0.9 + y * 0.5)) * 0.5 + 0.5
                    base = mix((20, 70, 70), (40, 120, 110), v * 0.6)
                    if int(d) % 4 == 0:
                        base = mix(base, (90, 190, 170), 0.35)
                    px(im, x, y, base)
    # the crosshair
    for i in range(int(cx - 8), int(cx + 9)):
        if abs(i + 0.5 - cx) > 2:
            px(im, i, 16, (230, 255, 240))
            px(im, 15, i, (230, 255, 240))
    # the pin
    for y in range(9, 15):
        w = max(0, 3 - (y - 9) // 2)
        for x in range(15 - w, 16 + w + 1):
            if (x, y) != (15, 15):
                px(im, x, y, (240, 80, 70) if x < 16 else (190, 50, 45))
    px(im, 15, 10, (255, 200, 190))
    px(im, 15, 16, (255, 255, 255))
    return im


def link_module():
    im = Image.new('RGBA', (S, S), CLEAR)
    board = (70, 40, 100)
    round_box(im, 4, 7, 27, 25, board, mix(board, (255, 255, 255), 0.3), mix(board, (0, 0, 0), 0.4), r=2)
    # gold contacts at the bottom
    for i in range(6, 26, 3):
        rect(im, i, 24, i + 1, 27, (230, 190, 70))
        px(im, i, 27, (150, 110, 30))
    # circuit traces
    tr = (170, 120, 230)
    for x in range(6, 12):
        px(im, x, 11, tr)
    for y in range(11, 19):
        px(im, 11, y, tr)
    for x in range(20, 26):
        px(im, x, 20, tr)
    for y in range(13, 21):
        px(im, 20, y, tr)
    px(im, 6, 11, (240, 220, 255))
    px(im, 25, 20, (240, 220, 255))
    # the ring emblem with a green vortex
    cx, cy = 16, 15.5
    for y in range(9, 23):
        for x in range(10, 23):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if 4.6 < d <= 6.2:
                px(im, x, y, (150, 140, 190) if (x + y) % 2 else (110, 100, 150))
    swirl(im, cx, cy, 4.4, (210, 255, 220), (30, 160, 80))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    icons = [('bridgeRemote', remote(False)), ('spaceRemote', remote(True)), ('coordinator', coordinator()), ('bridgeLinkModule', link_module())]
    sheet = Image.new('RGBA', (len(icons) * (S * 6 + 12) + 12, S * 6 + 24), (40, 40, 44, 255))
    for i, (name, im) in enumerate(icons):
        im.save(os.path.join(OUT, name + '.png'))
        sheet.paste(im.resize((S * 6, S * 6), Image.NEAREST), (12 + i * (S * 6 + 12), 12), im.resize((S * 6, S * 6), Image.NEAREST))
    sheet.save(PREVIEW)
    print('wrote', ', '.join(n for n, _ in icons))


if __name__ == '__main__':
    main()
