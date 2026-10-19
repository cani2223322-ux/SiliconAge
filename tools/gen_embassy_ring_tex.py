# -*- coding: utf-8 -*-
"""Textures of the Great Ring's core «Ядро Большого кольца» (Silicon Age, Si-14 phase 6, package R,
docs/plan-substrate-tech-f6.md §7.4) - 32 x 32, as the mod's machines, animated (a slow pulse).

The core stands under the middle of an embassy's Great Ring on the Vokzal: the bridge's gravity coils in the Tuners'
colours. A dark graphite casing with the machines' bevel and corner rivets (as the Settlement Core's), the coil's violet
windings (the bridge's gravity coil glowing - gravityCoilOn) round a turquoise lens - «Бирюза Наладчиков» 0x48F0DC, the
embassy's beam and the ring's glow in RenderEmbassyRingSC.

Writes into src/main/resources/assets/siliconage/textures/blocks:
  embassyRing.png (+ .mcmeta)     - the sides: the windings turning slowly round the lens, the ring's emblem in it, traces
                                    to the left and right edges with packets travelling out;
  embassyRing_top.png (+ .mcmeta) - the top and bottom: a round port, 12 coil segments lit in turn, a turquoise swirl in it.
Each is a vertical strip of FRAMES frames 32 x 32 (Minecraft 1.7.10 animates a strip by its .mcmeta, no interpolation).

Usage: python tools/gen_embassy_ring_tex.py [project dir] [--preview file.png]
  --preview: also a x6 sheet of the first frame of each and four frames of the side.
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
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'embassy_ring_tex_preview.png'
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
TEAL = (72, 240, 220)          # 0x48F0DC «Бирюза Наладчиков»
TEAL_HI = (214, 255, 250)
TEAL_DIM = (24, 92, 88)
VIOLET = (150, 110, 230)       # the gravity coil's glow (gen_bridge_tex.coil_on)
VIOLET_HI = (236, 222, 255)
VIOLET_DIM = (54, 40, 92)
SLOT = (10, 16, 20)
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


def traces_side(im, t):
    """Traces from the coil to the left and right frame, each with a pad, a packet travelling outwards."""
    rows = (8, 23)
    for i, y in enumerate(rows):
        for side in (-1, 1):
            xs = list(range(7, 3, -1)) if side < 0 else list(range(24, 28))
            for x in xs:
                put(im, x, y, TRACE)
            pad = xs[-1]
            for (dx, dy) in ((0, 0), (0, -1), (0, 1)):
                put(im, pad + dx, y + dy, PAD)
            pos = (t + i * 6 + (3 if side > 0 else 0)) % (FRAMES // 2)
            if pos < len(xs):
                put(im, xs[pos], y, TEAL_HI)


def side_frame(t):
    im = casing(1416)
    traces_side(im, t)
    g = glow(t)
    c = (S - 1) / 2.0
    turn = 2 * math.pi * t / FRAMES / 6          # the windings turn a sixth of a stripe a frame: a slow spin
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            ang = math.atan2(y - c, x - c)
            if 9.2 <= d <= 12.4:
                # the coil: 18 violet windings, lit violet-white, dark between them
                v = math.sin(18 * ang + turn * 18)
                if v > 0.1:
                    k = (v - 0.1) / 0.9
                    col = mix(VIOLET, VIOLET_HI, k * (0.45 + 0.4 * g))
                    edge = min(d - 9.2, 12.4 - d)
                    col = scale(col, 0.7 + 0.3 * min(1.0, edge / 1.2))
                else:
                    col = VIOLET_DIM
                put(im, x, y, col)
            elif 8.2 <= d < 9.2:
                put(im, x, y, scale(TEAL_DIM, 0.7 + 0.3 * g))             # the lens's rim
            elif d < 8.2:
                # the lens: turquoise, brighter to the middle, the light off its upper left
                base = mix(scale(TEAL, 0.45), TEAL, 1.0 - d / 8.2)
                col = scale(base, 0.75 + 0.3 * g)
                if 3.4 <= d <= 4.6:
                    col = mix(col, TEAL_HI, 0.55 + 0.35 * g)                # the Great Ring's emblem: a ring in the lens
                if d < 1.6:
                    col = mix(VIOLET, VIOLET_HI, 0.4 + 0.6 * g)              # the vortex's point in its middle
                hx, hy = x - (c - 3.0), y - (c - 3.0)
                if hx * hx + hy * hy < 2.2:
                    col = mix(col, TEAL_HI, 0.6)                             # a glint
                put(im, x, y, col)
            elif 12.4 < d <= 13.4:
                a = (0.25 + 0.3 * g) * (1.0 - (d - 12.4))
                put(im, x, y, mix(get(im, x, y), VIOLET, a))                # the coil's glow on the casing
    return im


def top_frame(t):
    im = casing(1417)
    g = glow(t)
    c = (S - 1) / 2.0
    lit_seg = t * 12 // FRAMES                    # the segment lit now: the light runs round the ring
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            ang = math.atan2(y - c, x - c)
            if 9.0 <= d <= 12.0:
                f = (ang / (2 * math.pi) * 12) % 12
                seg = int(f)
                if f - seg < 0.78:                 # 12 coil segments with gaps
                    run = (seg - lit_seg) % 12
                    lit = 1.0 if run == 0 else 0.6 if run == 11 else 0.35 if run == 10 else 0.0
                    col = mix(VIOLET, VIOLET_HI, lit * 0.8)
                    col = mix(col, TEAL, 0.25 * lit)
                    put(im, x, y, scale(col, 0.75 + 0.25 * g))
                else:
                    put(im, x, y, CASE_LO)
            elif 8.0 < d < 9.0:
                put(im, x, y, SLOT)
            elif d <= 8.0:
                # the swirl: three turquoise arms turning in, a white-hot middle
                v = math.sin(3 * ang + 0.9 * d - 2 * math.pi * t / FRAMES)
                v = (v + 1) / 2
                col = mix(scale(TEAL_DIM, 0.8), TEAL, v * (1.0 - d / 10.0))
                col = scale(col, 0.75 + 0.3 * g)
                if d < 2.4:
                    col = mix(col, TEAL_HI, (0.5 + 0.5 * g) * (1.0 - d / 2.4) + 0.3)
                put(im, x, y, col)
    # short traces from the port to the four corners
    for i, (sx, sy) in enumerate(((-1, -1), (1, -1), (1, 1), (-1, 1))):
        path = []
        for k in range(13, 18):
            x = int(round(c + sx * k * 0.707))
            y = int(round(c + sy * k * 0.707))
            path.append((x, y))
            put(im, x, y, TRACE)
        pos = (t + i * 4) % FRAMES
        if pos < len(path):
            put(im, path[pos][0], path[pos][1], TEAL_HI)
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
    save('embassyRing', side)
    save('embassyRing_top', top)
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
