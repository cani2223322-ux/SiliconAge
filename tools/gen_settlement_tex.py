# -*- coding: utf-8 -*-
"""Textures of the settlements' showcase machines that have no real machine to borrow from (Silicon Age, Подложка Ф2,
docs/plan-substrate-tech-f2.md §4.6) - 32 x 32, as the mod's machines. The other showcases wear the real machines' icons.

Writes into src/main/resources/assets/siliconage/textures/blocks:
  showcase_gasTank_side - the steel tank's frame (tank/steelSide.png) with its window filled: liquid helium up to 60 %,
                          a bright meniscus, dark glass above (the real tank's side is see-through; a showcase is a solid
                          cube, so its window is painted, the glints kept);
  showcase_cloneCapsule_side / _front - the same frame, the pod's turquoise glow (the Tuners' colour 0x3CD2C8, as the
                          core's dome) - the front with a figure floating in it;
  showcase_cloneCapsule_top - the tank's lid (tank/steelTop.png) with a turquoise lamp in its middle.

Usage: python tools/gen_settlement_tex.py [project dir] [--preview file.png]
  --preview: also a x8 sheet of them.
"""
import os
import sys

from PIL import Image

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if '--preview' in ARGS:
    i = ARGS.index('--preview')
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'settlement_tex_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')

GLASS = (36, 46, 54)
GLASS_HI = (58, 72, 82)
HELIUM = (176, 232, 246)
HELIUM_LO = (110, 188, 222)
MENISCUS = (226, 252, 255)
TURQ = (60, 210, 200)
TURQ_HI = (176, 255, 246)
TURQ_LO = (22, 104, 112)
FIGURE = (18, 58, 64)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def window(im):
    """The see-through pixels of the frame (alpha < 128): {(x, y)} and their box."""
    w, h = im.size
    px = set((x, y) for y in range(h) for x in range(w) if im.getpixel((x, y))[3] < 128)
    xs = [p[0] for p in px]
    ys = [p[1] for p in px]
    return px, (min(xs), min(ys), max(xs), max(ys))


def gas_tank_side(frame):
    im = frame.copy()
    px, (x0, y0, x1, y1) = window(im)
    level = y1 - int((y1 - y0 + 1) * 0.6)                  # the liquid's top row
    for (x, y) in px:
        if y > level:
            t = (y - level) / float(max(1, y1 - level))
            c = mix(HELIUM, HELIUM_LO, t)
            if (x * 7 + y * 13) % 23 == 0:                  # a few bubbles
                c = mix(c, MENISCUS, 0.6)
        elif y == level:
            c = MENISCUS
        else:
            t = (y - y0) / float(max(1, level - y0))
            c = mix(GLASS_HI, GLASS, t)
        im.putpixel((x, y), c + (255,))
    for y in range(y0 + 2, y1 - 1, 3):                       # the gauge's ticks on the window's right edge
        im.putpixel((x1, y), (200, 210, 220, 255))
    return im


def capsule_side(frame, figure):
    im = frame.copy()
    px, (x0, y0, x1, y1) = window(im)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    rx, ry = (x1 - x0) / 2.0 + 0.5, (y1 - y0) / 2.0 + 0.5
    for (x, y) in px:
        d = ((x - cx) / rx) ** 2 + ((y - cy) / ry) ** 2       # brighter in the middle of the pod
        c = mix(TURQ_HI, TURQ, min(1.0, d * 1.3))
        if d > 0.75:
            c = mix(c, TURQ_LO, (d - 0.75) * 2.0)
        if (x + y * 3) % 11 == 0:
            c = mix(c, TURQ_HI, 0.5)                          # rising motes
        im.putpixel((x, y), c + (255,))
    if figure:                                                # a figure floating in the pod: head, body, legs
        mx = int(round(cx))
        head_y = y0 + 3
        for dy in range(3):
            for dx in (-1, 0, 1):
                im.putpixel((mx + dx, head_y + dy), FIGURE + (255,))
        for y in range(head_y + 4, min(y1 - 5, head_y + 13)):
            for dx in range(-2, 3):
                im.putpixel((mx + dx, y), mix(FIGURE, TURQ_LO, 0.25 if abs(dx) == 2 else 0.0) + (255,))
            if y < head_y + 10:                               # the arms
                im.putpixel((mx - 3, y), mix(FIGURE, TURQ, 0.35) + (255,))
                im.putpixel((mx + 3, y), mix(FIGURE, TURQ, 0.35) + (255,))
        for y in range(head_y + 13, y1 - 1):
            im.putpixel((mx - 1, y), FIGURE + (255,))
            im.putpixel((mx + 1, y), FIGURE + (255,))
    return im


def capsule_top(lid):
    im = lid.copy()
    w, h = im.size
    cx, cy = (w - 1) / 2.0, (h - 1) / 2.0
    for y in range(h):
        for x in range(w):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d <= 5.2:
                c = mix(TURQ_HI, TURQ, d / 5.2)
                im.putpixel((x, y), c + (255,))
            elif d <= 6.4:
                im.putpixel((x, y), TURQ_LO + (255,))
    return im


def main():
    frame = Image.open(os.path.join(OUT, 'tank', 'steelSide.png')).convert('RGBA')
    lid = Image.open(os.path.join(OUT, 'tank', 'steelTop.png')).convert('RGBA')
    tex = {'showcase_gasTank_side': gas_tank_side(frame), 'showcase_cloneCapsule_side': capsule_side(frame, False),
           'showcase_cloneCapsule_front': capsule_side(frame, True), 'showcase_cloneCapsule_top': capsule_top(lid)}
    for name, im in tex.items():
        assert all(im.getpixel((x, y))[3] == 255 for x in range(im.size[0]) for y in range(im.size[1])), name + ' must be opaque'
        im.save(os.path.join(OUT, name + '.png'))
    if PREVIEW:
        names = sorted(tex)
        k = 8
        sheet = Image.new('RGBA', (len(names) * (32 * k + 6) + 6, 32 * k + 12), (34, 30, 44, 255))
        for i, name in enumerate(names):
            big = tex[name].resize((32 * k, 32 * k), Image.NEAREST)
            sheet.paste(big, (6 + i * (32 * k + 6), 6), big)
        sheet.save(PREVIEW)
    print('wrote %d textures into %s' % (len(tex), OUT))


if __name__ == '__main__':
    main()
