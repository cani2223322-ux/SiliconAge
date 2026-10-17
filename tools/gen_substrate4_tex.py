# -*- coding: utf-8 -*-
"""Textures of the Substrate's Ф4 world blocks (Silicon Age, docs/plan-substrate-tech-f4.md §3.3) - 16 x 16, in the style of
tools/gen_substrate_tex.py (the same Crystallite, oxide colours and copper).

Writes into src/main/resources/assets/siliconage/textures/blocks:
  substrate_singularGlass - the Rift's floor and the pillar: a see-through violet-black glass, a brighter rim, specks of light;
  substrate_pillarCore    - the pillar's glowing core: white-violet plasma running up;
  substrate_wireweed      - Wireweed (crossed quads, transparent ground): copper threads, a patina at the tips;
  substrate_diodeMoss     - the diode moss (crossed quads): a low dark-teal cushion studded with LED dots;
  substrate_fumarole      - the helium fumarole: frosted crust round a dark vent, white-blue rime;
  substrate_arcCrack      - the Breakdown crack: dark Crystallite split by a blue-white arc;
  substrate_ionGeyser     - the ion geyser: violet rock, cyan / violet rings round a bright orifice;
  substrate_crackedRtg    - the cracked RTG: a finned metal casing, a hazard band, cracks glowing green;
  substrate_monocrystal   - the Monocrystal (rock meta 9): polished blue-grey silicon, facets, a sheen.

Usage: python tools/gen_substrate4_tex.py [project dir] [--preview file.png]
  --preview: also a x8 sheet of all of them.
"""
import math
import os
import random
import sys

from PIL import Image

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if '--preview' in ARGS:
    i = ARGS.index('--preview')
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'substrate4_tex_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
S = 16

CRYSTALLITE = (118, 132, 146)
SPARK = (222, 242, 252)
SPARK_LO = (170, 192, 208)
OXIDES = {'violet': (112, 74, 152), 'blue': (62, 98, 172), 'gold': (186, 150, 62), 'green': (72, 150, 104)}
COPPER = (192, 112, 60)
COPPER_HI = (238, 166, 98)
COPPER_LO = (128, 70, 38)
PATINA = (86, 170, 150)
GLASS = (34, 14, 56)
GLASS_RIM = (132, 82, 204)
GLASS_HI = (226, 200, 255)
CORE = (252, 244, 255)
CORE_MID = (218, 160, 255)
CORE_LO = (150, 76, 220)
MOSS = (34, 74, 70)
MOSS_HI = (58, 112, 100)
LEDS = ((120, 255, 140), (110, 230, 255), (255, 176, 80), (255, 90, 120))
RIME = (226, 244, 252)
VENT = (18, 22, 34)
ARC = (236, 248, 255)
ARC_GLOW = (96, 168, 255)
ION = (100, 236, 255)
ION_V = (176, 96, 255)
ROCK_V = (62, 44, 92)
STEEL = (112, 118, 126)
STEEL_HI = (164, 172, 180)
STEEL_LO = (68, 72, 80)
HAZARD = (226, 190, 44)
GLOW = (130, 255, 96)
SILICON = (92, 108, 134)
SILICON_HI = (196, 214, 238)
SILICON_LO = (54, 62, 82)


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


def put(im, x, y, c, a=255):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), tuple(c[:3]) + (a,))


def blend(im, x, y, c, t):
    if 0 <= x < S and 0 <= y < S:
        p = im.getpixel((x, y))
        im.putpixel((x, y), mix(p[:3], c, t) + (p[3],))


def singular_glass(seed=1404):
    """Translucent violet-black: a brighter rim (two shades: top / left lit), a dark middle (the horizon), sheen streaks, specks."""
    r = random.Random(seed)
    n = noise_grid(seed + 1, 2)
    im = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            d = max(abs(x - 7.5), abs(y - 7.5)) / 7.5            # 0 in the middle, 1 at the rim
            c = mix(GLASS, GLASS_RIM, 0.04 + 0.16 * d * d + 0.04 * n[y][x])
            a = 160 + int(50 * d * d)
            im.putpixel((x, y), c + (a,))
    for i in range(S):                                          # the rim: lit top / left, darker bottom / right
        put(im, i, 0, mix(GLASS_RIM, GLASS_HI, 0.35), 225)
        put(im, 0, i, mix(GLASS_RIM, GLASS_HI, 0.25), 225)
        put(im, i, S - 1, scale(GLASS_RIM, 0.7), 215)
        put(im, S - 1, i, scale(GLASS_RIM, 0.75), 215)
    for k in range(3):                                          # sheen: short light diagonals
        x0, y0 = 3 + k * 4, 2 + k * 2
        for j in range(3 - (k % 2)):
            px, py = x0 + j, y0 + j
            p = im.getpixel((px, py))
            put(im, px, py, mix(p[:3], GLASS_HI, 0.3), min(255, p[3] + 30))
    for _ in range(4):                                          # specks of light caught inside
        x, y = r.randrange(2, S - 2), r.randrange(2, S - 2)
        put(im, x, y, GLASS_HI, 240)
    return im


def pillar_core(seed=1414):
    """Opaque: white-violet plasma rising in streaks, brighter in the middle."""
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S))
    phase = [r.uniform(0, 6.28) for _ in range(S)]
    for y in range(S):
        for x in range(S):
            d = abs(x - 7.5) / 7.5
            wave = 0.5 + 0.5 * math.sin(y * 0.8 + phase[x])
            t = max(0.0, 1.0 - d * 1.15) * (0.65 + 0.35 * wave)
            c = mix(CORE_LO, CORE_MID, min(1.0, t * 1.6))
            c = mix(c, CORE, max(0.0, t - 0.45) * 1.8)
            im.putpixel((x, y), c + (255,))
    for _ in range(5):                                          # bright motes running up
        x, y = r.randrange(3, 13), r.randrange(S)
        put(im, x, y, CORE)
        put(im, x, (y + 1) % S, mix(CORE, CORE_MID, 0.5))
    return im


def wireweed(seed=1501):
    """Copper threads from the ground, bending a little; a lit side, a patina or a bright bead at the tips."""
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for (x0, h) in ((1, 9), (3, 13), (5, 7), (7, 15), (9, 11), (11, 14), (13, 8), (14, 12)):
        x = float(x0)
        drift = r.uniform(-0.25, 0.25)
        for k in range(h):
            y = S - 1 - k
            xi = int(round(x)) % S
            t = k / float(max(1, h - 1))
            c = mix(COPPER_LO, COPPER, min(1.0, 0.4 + t))
            if k % 3 == 1:
                c = mix(c, COPPER_HI, 0.5)
            put(im, xi, y, c)
            x += drift + (0.2 if k % 4 == 3 else 0.0) * (1 if x0 % 2 else -1)
        tip = (int(round(x)) % S, S - h)
        put(im, tip[0], tip[1], PATINA if x0 % 3 == 0 else COPPER_HI)
    return im


def diode_moss(seed=1502):
    """A low cushion (the bottom rows) of dark teal tufts, LED dots on it."""
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for x in range(S):
        h = 3 + r.randint(0, 3) + (1 if 4 <= x <= 11 else 0)
        for k in range(h):
            y = S - 1 - k
            c = mix(MOSS, MOSS_HI, r.uniform(0, 0.7) * (k / float(h)))
            if k == h - 1 and r.random() < 0.5:
                continue                                        # a ragged top edge
            put(im, x, y, c)
    for i, (x, y) in enumerate(((3, 12), (7, 10), (11, 11), (13, 13), (5, 14))):
        led = LEDS[i % len(LEDS)]
        put(im, x, y, led)
        put(im, x + 1, y, mix(led, MOSS, 0.55))
    return im


def fumarole(seed=1503):
    """Frosted crust round a dark vent: rime crystals radiating from the hole."""
    r = random.Random(seed)
    im = stone(mix(CRYSTALLITE, OXIDES['blue'], 0.25), seed, grain=0.06, blotch=0.07)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 2.3:
                put(im, x, y, mix(VENT, (40, 60, 90), max(0.0, d - 1.2) / 1.1))
            elif d < 3.4:
                put(im, x, y, mix(RIME, CRYSTALLITE, (d - 2.3) / 1.6))
            else:
                blend(im, x, y, RIME, max(0.0, 0.45 - (d - 3.4) * 0.08))
    for _ in range(9):                                          # rime needles out of the vent
        a = r.uniform(0, 6.28)
        for k in range(3, 3 + r.randint(2, 4)):
            put(im, int(round(7.5 + math.cos(a) * k)), int(round(7.5 + math.sin(a) * k)), mix(RIME, SPARK, 0.5))
    return im


def arc_crack(seed=1504):
    """Dark Crystallite, a jagged crack across it: a white-blue arc in its middle, a blue glow on its lips."""
    r = random.Random(seed)
    im = stone(scale(CRYSTALLITE, 0.6), seed, grain=0.06, blotch=0.08)
    path = []
    x, y = 0, 6 + r.randint(-1, 1)
    while x < S:
        path.append((x, y))
        if r.random() < 0.45:
            y += r.choice((-1, 1))
            y = max(2, min(S - 3, y))
            path.append((x, y))
        x += 1
    branch = []
    bx, by = path[len(path) // 2]
    for k in range(5):                                          # a fork downwards
        by += 1
        bx += r.choice((-1, 0, 1))
        branch.append((bx, by))
    for (px, py) in path + branch:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            blend(im, px + dx, py + dy, ARC_GLOW, 0.55)
    for (px, py) in path + branch:
        put(im, px, py, ARC)
    for _ in range(3):
        p = r.choice(path)
        put(im, p[0], p[1] - 2, ARC_GLOW)
    return im


def ion_geyser(seed=1505):
    """Violet rock; cyan and violet rings round a bright orifice."""
    im = stone(ROCK_V, seed, grain=0.07, blotch=0.08)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 1.6:
                put(im, x, y, mix((255, 255, 255), ION_V, d / 1.6 * 0.6))
            elif d < 2.6:
                put(im, x, y, mix(ION_V, VENT, 0.35))
            elif 3.6 <= d < 4.5:
                blend(im, x, y, ION, 0.75)
            elif 5.6 <= d < 6.3:
                blend(im, x, y, ION_V, 0.6)
    r = random.Random(seed + 3)
    for _ in range(4):                                          # stray ion sparks
        a, k = r.uniform(0, 6.28), r.uniform(5, 7)
        put(im, int(round(7.5 + math.cos(a) * k)), int(round(7.5 + math.sin(a) * k)), ION)
    return im


def cracked_rtg(seed=1506):
    """A finned steel casing (vertical fins), a hazard band across, cracks glowing green."""
    r = random.Random(seed)
    im = stone(STEEL, seed, grain=0.04, blotch=0.05)
    for x in range(S):
        fin = x % 3
        for y in range(S):
            c = im.getpixel((x, y))[:3]
            c = mix(c, STEEL_HI, 0.4) if fin == 0 else mix(c, STEEL_LO, 0.35) if fin == 2 else c
            put(im, x, y, c)
    for x in range(S):                                          # the hazard band: yellow, black chevrons
        for y in range(6, 10):
            put(im, x, y, (24, 24, 26) if (x + y) % 4 < 2 else HAZARD)
    for i in range(S):
        put(im, i, 0, STEEL_HI)
        put(im, i, S - 1, STEEL_LO)
    cracks = []
    x, y = 4, 1
    for _ in range(14):                                         # a crack down the casing
        cracks.append((x, y))
        x += r.choice((-1, 0, 1, 1))
        y += 1
    x, y = 12, 15
    for _ in range(6):
        cracks.append((x, y))
        x += r.choice((-1, 0, 1))
        y -= 1
    for (px, py) in cracks:
        blend(im, px - 1, py, GLOW, 0.3)
        blend(im, px + 1, py, GLOW, 0.3)
    for (px, py) in cracks:
        put(im, px, py, GLOW if (px + py) % 3 else mix(GLOW, (255, 255, 255), 0.5))
    return im


def monocrystal(seed=1509):
    """Polished blue-grey silicon: three large facets (light, mid, dark), bright edges between them, a soft sheen."""
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            if x + y < 11:
                c = mix(SILICON, SILICON_HI, 0.35)              # the upper-left facet catches the light
            elif x - y > 3:
                c = SILICON
            else:
                c = mix(SILICON, SILICON_LO, 0.4)
            k = 1.0 + r.uniform(-0.035, 0.035)
            im.putpixel((x, y), scale(c, k) + (255,))
    for i in range(S):                                          # the facets' edges
        if 0 <= 10 - i < S:
            blend(im, i, 10 - i, SILICON_HI, 0.7)
        if 0 <= i - 4 < S:
            blend(im, i, i - 4, SILICON_HI, 0.5)
    sheen = noise_grid(seed + 2, 2)
    for y in range(S):
        for x in range(S):
            t = max(0.0, 0.18 * sheen[y][x] + 0.12 * math.sin((x - y) * 0.5))
            if t > 0:
                blend(im, x, y, (196, 170, 236), t)             # a faint violet interference sheen
    for (x, y) in ((3, 3), (12, 6), (8, 13)):
        put(im, x, y, SPARK)
    for i in range(S):
        blend(im, i, 0, SILICON_HI, 0.3)
        blend(im, 0, i, SILICON_HI, 0.3)
        blend(im, i, S - 1, SILICON_LO, 0.4)
        blend(im, S - 1, i, SILICON_LO, 0.4)
    return im


def main():
    tex = {'substrate_singularGlass': singular_glass(), 'substrate_pillarCore': pillar_core(), 'substrate_wireweed': wireweed(),
           'substrate_diodeMoss': diode_moss(), 'substrate_fumarole': fumarole(), 'substrate_arcCrack': arc_crack(),
           'substrate_ionGeyser': ion_geyser(), 'substrate_crackedRtg': cracked_rtg(), 'substrate_monocrystal': monocrystal()}
    for name, im in tex.items():
        im.save(os.path.join(OUT, name + '.png'))
    if PREVIEW:
        names = sorted(tex)
        cols, k = 5, 8
        rows = (len(names) + cols - 1) // cols
        sheet = Image.new('RGBA', (cols * (S * k + 6) + 6, rows * (S * k + 6) + 6), (34, 30, 44, 255))
        for i, name in enumerate(names):
            big = tex[name].resize((S * k, S * k), Image.NEAREST)
            sheet.paste(big, (6 + (i % cols) * (S * k + 6), 6 + (i // cols) * (S * k + 6)), big)
        sheet.save(PREVIEW)
    print('wrote %d textures into %s' % (len(tex), OUT))


if __name__ == '__main__':
    main()
