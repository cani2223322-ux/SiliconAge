# -*- coding: utf-8 -*-
"""Textures of the Substrate's world blocks (Silicon Age, docs/plan-substrate-tech.md §3.4) - 16 x 16, as the ores.

Writes into src/main/resources/assets/siliconage/textures/blocks:
  substrate_crystallite - the main rock: blue-grey with sparkles;
  substrate_oxide_violet / _blue / _gold / _green - the Oxide Crust, an SiO2 film's colours with a sheen;
  substrate_copper_trace - a copper tile (the traces are rows of them), substrate_trace_node - with a glowing pad;
  substrate_turf_top / substrate_turf_side - the Quartz Turf (the side: Crystallite under a pale fringe);
  substrate_druse / substrate_druse_top - the druse's crystals on a transparent ground (crossed quads, the top pointed);
  substrate_ore_<name> - every ore of the mod in Crystallite: the ore's own pattern (the pixels of ore_<name>.png that
  aren't its stone) over the Crystallite.

Usage: python tools/gen_substrate_tex.py [project dir] [--preview file.png]
  --preview: also a x8 sheet of all of them.
"""
import os
import random
import sys

from PIL import Image

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if '--preview' in ARGS:
    i = ARGS.index('--preview')
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'substrate_tex_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
S = 16

ORES = ['quartzite', 'chalcopyrite', 'cassiterite', 'galena', 'sphalerite', 'bauxite', 'ilmenite', 'wolframite', 'argyrodite',
        'tantalite', 'baddeleyite', 'sperrylite', 'monazite', 'halite', 'magnesite', 'spodumene']

CRYSTALLITE = (118, 132, 146)
SPARK = (222, 242, 252)
SPARK_LO = (170, 192, 208)
OXIDES = {'violet': (112, 74, 152), 'blue': (62, 98, 172), 'gold': (186, 150, 62), 'green': (72, 150, 104)}
# the film's next colour (the sheen drifts towards it): violet -> blue -> gold -> green -> violet
NEXT = {'violet': 'blue', 'blue': 'gold', 'gold': 'green', 'green': 'violet'}
COPPER = (192, 112, 60)
COPPER_HI = (238, 166, 98)
COPPER_LO = (128, 70, 38)
PAD = (255, 236, 160)
PAD_RING = (252, 192, 92)
TURF = (150, 198, 188)
TURF_HI = (226, 246, 240)
TURF_LO = (104, 150, 146)
CRYSTAL = (176, 222, 246)
CRYSTAL_HI = (240, 252, 255)
CRYSTAL_LO = (136, 118, 214)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def noise_grid(seed, cells=4):
    """A smooth 16 x 16 value noise (-1..1) that tiles: a cells x cells grid of random values, bilinear."""
    r = random.Random(seed)
    g = [[r.uniform(-1, 1) for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * S for _ in range(S)]
    step = S / float(cells)
    for y in range(S):
        for x in range(S):
            fx, fy = x / step, y / step
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
            b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
            out[y][x] = a + (b - a) * ty
    return out


def stone(base, seed, grain=0.07, blotch=0.08):
    """A stone-like tile: per-pixel grain plus soft blotches, as the mod's ore stone."""
    r = random.Random(seed)
    n = noise_grid(seed + 1)
    im = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            k = 1.0 + r.uniform(-grain, grain) + n[y][x] * blotch
            im.putpixel((x, y), scale(base, k) + (255,))
    return im


def crystallite(seed=1414):
    im = stone(CRYSTALLITE, seed)
    r = random.Random(seed + 7)
    for _ in range(4):                                  # sparkles: a bright pixel, a dimmer one beside it
        x, y = r.randrange(S), r.randrange(S)
        im.putpixel((x, y), SPARK + (255,))
        dx, dy = r.choice(((1, 0), (0, 1), (-1, 0), (0, -1)))
        im.putpixel(((x + dx) % S, (y + dy) % S), SPARK_LO + (255,))
    for _ in range(3):                                  # faint facets: short light diagonals
        x, y = r.randrange(S), r.randrange(S)
        for k in range(3):
            p = ((x + k) % S, (y + k) % S)
            im.putpixel(p, mix(im.getpixel(p)[:3], SPARK_LO, 0.35) + (255,))
    return im


def oxide(name, seed):
    base, nxt = OXIDES[name], OXIDES[NEXT[name]]
    im = stone(base, seed, grain=0.06, blotch=0.06)
    sheen = noise_grid(seed + 3, 2)
    r = random.Random(seed + 5)
    for y in range(S):
        for x in range(S):
            c = im.getpixel((x, y))[:3]
            t = max(0.0, 0.18 + 0.22 * sheen[y][x] + 0.12 * (((x + y) % 8) / 7.0 - 0.5))   # the film's sheen
            im.putpixel((x, y), mix(c, nxt, t) + (255,))
    for _ in range(2):                                  # hairline cracks of the crust
        x, y = r.randrange(S), r.randrange(S)
        for k in range(r.randint(3, 5)):
            im.putpixel((x % S, y % S), scale(im.getpixel((x % S, y % S)), 0.7) + (255,))
            x += r.choice((1, 1, 0))
            y += r.choice((0, 1, 1))
    for _ in range(3):                                  # glints
        p = (r.randrange(S), r.randrange(S))
        im.putpixel(p, mix(im.getpixel(p)[:3], (255, 255, 255), 0.45) + (255,))
    return im


def copper_tile(seed=55):
    im = stone(COPPER, seed, grain=0.05, blotch=0.07)
    for i in range(S):                                  # a bevel: light top / left, dark bottom / right
        im.putpixel((i, 0), COPPER_HI + (255,))
        im.putpixel((0, i), COPPER_HI + (255,))
        im.putpixel((i, S - 1), COPPER_LO + (255,))
        im.putpixel((S - 1, i), COPPER_LO + (255,))
    for (x, y) in ((4, 4), (11, 4), (4, 11), (11, 11)):    # etched vias
        im.putpixel((x, y), COPPER_LO + (255,))
        im.putpixel((x + 1, y), scale(COPPER_LO, 0.8) + (255,))
        im.putpixel((x, y + 1), scale(COPPER_LO, 0.9) + (255,))
        im.putpixel((x + 1, y + 1), COPPER_HI + (255,))
    r = random.Random(seed + 1)
    for _ in range(5):
        p = (r.randrange(2, S - 2), r.randrange(2, S - 2))
        im.putpixel(p, mix(im.getpixel(p)[:3], COPPER_HI, 0.6) + (255,))
    return im


def trace_node():
    im = copper_tile(77)
    for y in range(5, 11):
        for x in range(5, 11):
            edge = x in (5, 10) or y in (5, 10)
            corner = x in (5, 10) and y in (5, 10)
            if corner:
                im.putpixel((x, y), COPPER_HI + (255,))
            elif edge:
                im.putpixel((x, y), PAD_RING + (255,))
            else:
                d = abs(x - 7.5) + abs(y - 7.5)
                im.putpixel((x, y), mix(PAD, (255, 255, 240), max(0.0, 1 - d / 2.0)) + (255,))
    return im


def turf_top(seed=707):
    im = stone(TURF, seed, grain=0.08, blotch=0.07)
    r = random.Random(seed + 2)
    for _ in range(12):                                 # quartz grains
        x, y = r.randrange(S), r.randrange(S)
        im.putpixel((x, y), TURF_HI + (255,))
        if r.random() < 0.5:
            im.putpixel(((x + 1) % S, y), mix(TURF_HI, TURF, 0.5) + (255,))
    for _ in range(8):
        p = (r.randrange(S), r.randrange(S))
        im.putpixel(p, TURF_LO + (255,))
    return im


def turf_side():
    im = crystallite(1415)
    top = turf_top(708)
    r = random.Random(709)
    for x in range(S):
        depth = 3 + (1 if r.random() < 0.5 else 0) + (1 if r.random() < 0.2 else 0)
        for y in range(depth):
            im.putpixel((x, y), top.getpixel((x, y)))
        im.putpixel((x, depth), mix(TURF_LO, CRYSTALLITE, 0.4) + (255,))
    return im


def druse(tip):
    """Crystals on a transparent ground: three prisms (a light left face, a violet right one, a white edge)."""
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    prisms = ((2, 3, 7), (6, 4, 1), (11, 3, 4))        # (left x, width, the tip's y on the top tile)
    for (x0, w, ty) in prisms:
        top_y = ty if tip else 0
        for y in range(top_y, S):
            if tip:
                grow = y - top_y                        # the point: one pixel wider per row down to the full width
                half = min(w, 1 + grow)
                lo, hi = x0 + (w - half) // 2, x0 + (w - half) // 2 + half
            else:
                lo, hi = x0, x0 + w
            for x in range(lo, hi):
                t = (x - lo) / float(max(1, hi - lo - 1))
                c = mix(CRYSTAL, CRYSTAL_LO, t)
                if x == lo:
                    c = CRYSTAL_HI
                k = 0.92 + 0.08 * ((y * 7 + x * 3) % 5) / 4.0
                im.putpixel((x, y), scale(c, k) + (255,))
    if not tip:
        for (x, y) in ((7, 5), (3, 11), (12, 8)):       # inner flashes
            im.putpixel((x, y), CRYSTAL_HI + (255,))
    return im


def is_stone(c):
    """The mod's ore textures share one grey stone: r == g, b == r + 3 (the ore's own pixels are anything else)."""
    r, g, b = c[:3]
    return r == g and b - r == 3 and 105 <= r <= 145


def ore(name, seed):
    im = crystallite(seed)
    src = Image.open(os.path.join(OUT, 'ore_' + name + '.png')).convert('RGBA')
    if src.size != (S, S):
        src = src.resize((S, S), Image.NEAREST)
    n = 0
    for y in range(S):
        for x in range(S):
            c = src.getpixel((x, y))
            if c[3] > 0 and not is_stone(c):
                im.putpixel((x, y), c[:3] + (255,))
                n += 1
    return im, n


def main():
    tex = {'substrate_crystallite': crystallite(), 'substrate_copper_trace': copper_tile(), 'substrate_trace_node': trace_node(),
           'substrate_turf_top': turf_top(), 'substrate_turf_side': turf_side(), 'substrate_druse': druse(False),
           'substrate_druse_top': druse(True)}
    for i, name in enumerate(('violet', 'blue', 'gold', 'green')):
        tex['substrate_oxide_' + name] = oxide(name, 300 + i * 17)
    for i, name in enumerate(ORES):
        im, n = ore(name, 2000 + i)
        if n < 6:
            print('warning: ore_%s.png gave only %d ore pixels' % (name, n))
        tex['substrate_ore_' + name] = im
    for name, im in tex.items():
        im.save(os.path.join(OUT, name + '.png'))
    if PREVIEW:
        names = sorted(tex)
        cols, k = 9, 8
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * (S * k + 6) + 6, rows * (S * k + 6) + 6), (34, 30, 44, 255))
        for i, name in enumerate(names):
            big = tex[name].resize((S * k, S * k), Image.NEAREST)
            sheet.paste(big, (6 + (i % cols) * (S * k + 6), 6 + (i // cols) * (S * k + 6)), big)
        sheet.save(PREVIEW)
    print('wrote %d textures into %s' % (len(tex), OUT))


if __name__ == '__main__':
    main()
