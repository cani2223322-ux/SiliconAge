# -*- coding: utf-8 -*-
"""Textures of the Settlement Core (Silicon Age, Si-14 phase 2, docs/plan-substrate-tech-f2.md §5.1) - 32 x 32, as the
mod's machines, animated (a slow pulse).

The core is «a surviving splinter of the Lithographers' reactor»: a dark graphite casing with the machines' bevel and
corner rivets, a turquoise crystal shard behind a window, lithography traces running from it to the frame with bright
packets travelling out along them. The turquoise is the dome's shell (TileEntitySettlementCoreSC.SHELL_RGB 0x3CD2C8).

Writes into src/main/resources/assets/siliconage/textures/blocks:
  settlementCore_side.png (+ .mcmeta) - the shard upright in a tall window, traces to the left and right edges;
  settlementCore_top.png (+ .mcmeta)  - a round port: a segmented ring round the shard's top, traces to the corners.
Each is a vertical strip of FRAMES frames 32 x 32 (Minecraft 1.7.10 animates a strip by its .mcmeta, no interpolation).

Usage: python tools/gen_settlement_core_tex.py [project dir] [--preview file.png]
  --preview: also a x6 sheet of the first frame of each and the pulse of the side.
"""
import json
import math
import os
import random
import sys

from PIL import Image

ARGS = [a for a in sys.argv[1:]]
PREVIEW = None
if '--preview' in ARGS:
    i = ARGS.index('--preview')
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'settlement_core_tex_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
S = 32
FRAMES = 16
FRAMETIME = 3

CASE = (40, 50, 56)
CASE_HI = (86, 104, 110)
CASE_LO = (18, 24, 28)
PANEL = (28, 36, 40)
RIVET = (150, 170, 172)
TEAL = (60, 210, 200)          # 0x3CD2C8
TEAL_HI = (206, 255, 248)
TEAL_DIM = (22, 86, 84)
TRACE = (34, 122, 116)
PAD = (90, 200, 190)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def put(im, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), tuple(c[:3]) + (255,))


def get(im, x, y):
    return im.getpixel((x, y))[:3]


def casing(seed):
    """The frame: a 3 px bevelled border (light top-left, dark bottom-right), corner rivets, a dark panel with grain."""
    r = random.Random(seed)
    im = Image.new('RGBA', (S, S))
    for y in range(S):
        for x in range(S):
            edge = min(x, y, S - 1 - x, S - 1 - y)
            if edge == 0:
                c = CASE_LO
            elif edge <= 2:
                c = CASE_HI if (x <= 2 or y <= 2) and x + y < S else CASE
                if (x >= S - 3 or y >= S - 3) and x + y >= S - 1:
                    c = scale(CASE, 0.78)
            elif edge == 3:
                c = CASE_LO
            else:
                c = scale(PANEL, 1.0 + r.uniform(-0.06, 0.06))
            put(im, x, y, c)
    for (x, y) in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        put(im, x, y, RIVET)
    return im


def glow(t):
    """The pulse, 0..1, of frame t."""
    return 0.5 + 0.5 * math.sin(2 * math.pi * t / FRAMES)


def shard_side(im, t):
    """The upright crystal shard in its window (x 12..19, y 6..25): a hexagonal prism, a hot core line, the glow round it."""
    g = glow(t)
    cx = 15.5
    # the window: a darker slot with a teal rim, the glow spilling on its inner edge
    for y in range(5, 27):
        for x in range(11, 21):
            rim = x in (11, 20) or y in (5, 26)
            put(im, x, y, scale(TEAL_DIM, 0.6 + 0.4 * g) if rim else (10, 18, 20))
    for y in range(6, 26):
        # the hexagon's half-width: pointed at both ends
        k = min(y - 6, 25 - y)
        half = min(3.6, 0.9 + k * 0.9)
        for x in range(12, 20):
            d = abs(x + 0.5 - cx)
            if d > half:
                # the glow round the shard
                a = max(0.0, 1.0 - (d - half) / 2.5) * (0.35 + 0.35 * g)
                if a > 0:
                    put(im, x, y, mix(get(im, x, y), TEAL, a))
                continue
            facet = 0.72 if x + 0.5 < cx - half * 0.35 else (1.0 if x + 0.5 < cx + half * 0.35 else 0.82)
            c = scale(TEAL, facet * (0.8 + 0.25 * g))
            if d < 0.8:
                c = mix(c, TEAL_HI, 0.55 + 0.45 * g)      # the hot core line
            put(im, x, y, c)
    # a sparkle on the upper facet, wandering down the frames
    sy = 8 + (t * 13 // FRAMES) % 14
    put(im, 14, sy, mix(get(im, 14, sy), TEAL_HI, 0.9))


def traces_side(im, t):
    """Traces from the window to the left and right frame, each with a pad, a packet travelling outwards."""
    rows = (9, 16, 22)
    for i, y in enumerate(rows):
        for side in (-1, 1):
            xs = list(range(10, 3, -1)) if side < 0 else list(range(21, 28))
            for x in xs:
                put(im, x, y, TRACE)
            pad = xs[-2]
            for (dx, dy) in ((0, 0), (0, -1), (0, 1)):
                put(im, pad + dx, y + dy, PAD)
            # the packet: one bright pixel and a tail, phase per trace
            pos = (t + i * 5 + (3 if side > 0 else 0)) % FRAMES
            if pos < len(xs):
                put(im, xs[pos], y, TEAL_HI)
                if pos > 0:
                    put(im, xs[pos - 1], y, mix(TRACE, TEAL_HI, 0.5))


def side_frame(t):
    im = casing(1414)
    traces_side(im, t)
    shard_side(im, t)
    return im


def top_frame(t):
    im = casing(1415)
    g = glow(t)
    c = (S - 1) / 2.0
    # diagonal traces to the corners, packets outwards
    for i, (sx, sy) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        path = []
        for k in range(11, 17):
            x = int(round(c + sx * k * 0.707))
            y = int(round(c + sy * k * 0.707))
            path.append((x, y))
            put(im, x, y, TRACE)
        pos = (t + i * 4) % FRAMES
        if pos < len(path):
            put(im, path[pos][0], path[pos][1], TEAL_HI)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            if 8.6 <= d <= 10.6:
                ang = math.atan2(y - c, x - c)
                seg = (ang / (2 * math.pi) * 12) % 1.0
                if seg < 0.8:                          # 12 segments with gaps
                    lit = 0.55 + 0.45 * math.sin(ang * 2 + 2 * math.pi * t / FRAMES)
                    put(im, x, y, mix(TEAL_DIM, TEAL, lit * (0.6 + 0.4 * g)))
                else:
                    put(im, x, y, CASE_LO)
            elif d < 8.6 and d > 7.4:
                put(im, x, y, (10, 18, 20))
            elif d <= 7.4:
                # the shard's top: a hexagon seen from above, facets, a hot centre
                hx = abs(x - c)
                hy = abs(y - c)
                inside = hx * 0.866 + hy * 0.5 <= 5.2 and hy <= 5.2
                if inside:
                    facet = 0.75 + 0.25 * ((x - c) * 0.5 - (y - c) * 0.866 < 0)
                    col = scale(TEAL, facet * (0.8 + 0.25 * g))
                    if d < 2.2:
                        col = mix(col, TEAL_HI, 0.5 + 0.5 * g)
                    put(im, x, y, col)
                else:
                    a = max(0.0, 1.0 - (d - 5.0) / 2.5) * (0.3 + 0.3 * g)
                    put(im, x, y, mix((10, 18, 20), TEAL, a))
    return im


def strip(frame):
    out = Image.new('RGBA', (S, S * FRAMES))
    for t in range(FRAMES):
        out.paste(frame(t), (0, S * t))
    return out


def save(name, im):
    path = os.path.join(OUT, name + '.png')
    im.save(path)
    with open(path + '.mcmeta', 'w') as f:
        json.dump({'animation': {'frametime': FRAMETIME}}, f)
    print('wrote', path)


def main():
    if not os.path.isdir(OUT):
        sys.exit('no textures folder: ' + OUT)
    side = strip(side_frame)
    top = strip(top_frame)
    save('settlementCore_side', side)
    save('settlementCore_top', top)
    if PREVIEW:
        k = 6
        sheet = Image.new('RGBA', ((S * k + 8) * 2 + 8 * 8 + S * 4 * 4, S * k + 16), (32, 32, 32, 255))
        sheet.paste(side.crop((0, 0, S, S)).resize((S * k, S * k), Image.NEAREST), (8, 8))
        sheet.paste(top.crop((0, 0, S, S)).resize((S * k, S * k), Image.NEAREST), (S * k + 16, 8))
        for i, t in enumerate((0, 4, 8, 12)):
            sheet.paste(side.crop((0, S * t, S, S * t + S)).resize((S * 4, S * 4), Image.NEAREST),
                        (2 * (S * k + 16) + i * (S * 4 + 8), 8))
        sheet.save(PREVIEW)
        print('preview', PREVIEW)


if __name__ == '__main__':
    main()
