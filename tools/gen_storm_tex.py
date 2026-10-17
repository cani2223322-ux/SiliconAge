# -*- coding: utf-8 -*-
"""The EMP storm's screen texture and sounds (Silicon Age, docs/plan-substrate-tech-f4.md §5.4, §5.7 - package S).

Writes:
  src/main/resources/assets/siliconage/textures/misc/storm_scan.png - the «строки развёртки»: 128 x 128, tiling both ways,
    pale violet-white interference lines on a transparent ground (the alpha is the line; StormClientSC tiles it over the
    screen, one texel a GUI pixel, and runs it top to bottom);
  src/main/resources/assets/siliconage/sounds/storm/siren.ogg - the domes' siren: a two-tone wail, rising and falling, 2.6 s,
    mono 22 050 Hz (mono: OpenAL fades it with distance - sounds.json «storm.siren», heard within 64 at volume 4);
  src/main/resources/assets/siliconage/sounds/storm/crackle.ogg - the pulse's crackle: a burst of static with sparks, 0.9 s,
    mono 22 050 Hz («storm.crackle»).

Usage: python tools/gen_storm_tex.py [project dir] [--preview file.png]
  --preview: also the scan texture x4 over a dark ground.
Needs Pillow; the sounds need numpy and soundfile (libsndfile with Vorbis) - without them only the texture is written.
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
    PREVIEW = ARGS[i + 1] if i + 1 < len(ARGS) else 'storm_tex_preview.png'
    del ARGS[i:i + 2]
PROJECT = ARGS[0] if ARGS else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage')
W, H = 128, 128

LINE = (236, 222, 255)
LINE_LO = (184, 150, 246)
RATE = 22050


def clamp(v):
    return max(0, min(255, int(round(v))))


def scan_texture():
    """Interference lines: a bright thin one with a glow, a broken one, a band of noise, a wide faint one."""
    rnd = random.Random(0x5702A)
    alpha = [[0.0] * W for _ in range(H)]
    tint = [[0.0] * W for _ in range(H)]

    def add(y, x, a, t=0.0):
        y %= H
        if a > alpha[y][x]:
            alpha[y][x] = a
            tint[y][x] = t

    for x in range(W):
        # the main line: 1 px, its glow 2 px either side; a slow swell along it (period W - it tiles)
        swell = 0.75 + 0.25 * math.sin(2 * math.pi * x / W * 2)
        add(10, x, 0.95 * swell)
        for d, a in ((1, 0.45), (2, 0.18)):
            add(10 - d, x, a * swell, 0.4)
            add(10 + d, x, a * swell, 0.4)
    # a broken line: dashes of 5..14 px
    x = 0
    while x < W:
        n = rnd.randint(5, 14)
        gap = rnd.randint(2, 9)
        for k in range(n):
            add(78, (x + k) % W, 0.7, 0.2)
            add(79, (x + k) % W, 0.25, 0.6)
        x += n + gap
    for x in range(W):
        # a band of static: three rows of noise
        for y in (44, 45, 46):
            v = rnd.random()
            if v > 0.55:
                add(y, x, 0.25 + 0.45 * (v - 0.55) / 0.45, rnd.random())
        # the wide faint band (the CRT's roll bar)
        for y in range(98, 116):
            d = abs(y - 106.5) / 9.5
            add(y, x, 0.14 * (1 - d * d), 0.8)
        # a few loose sparks
        if rnd.random() < 0.06:
            add(rnd.randint(0, H - 1), x, 0.5 + 0.4 * rnd.random(), 0.1)
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    px = img.load()
    for y in range(H):
        for x in range(W):
            a = alpha[y][x]
            if a <= 0:
                continue
            t = tint[y][x]
            c = tuple(clamp(LINE[i] + (LINE_LO[i] - LINE[i]) * t) for i in range(3))
            px[x, y] = c + (clamp(a * 255),)
    return img


def siren():
    """A two-tone wail: the pitch sweeps 430 -> 780 -> 430 Hz, a little vibrato, odd harmonics (a horn), soft ends."""
    import numpy as np
    n = int(RATE * 2.6)
    t = np.arange(n) / RATE
    sweep = 0.5 - 0.5 * np.cos(2 * np.pi * t / 2.6)
    f = 430 + 350 * sweep + 6 * np.sin(2 * np.pi * 5.5 * t)
    ph = 2 * np.pi * np.cumsum(f) / RATE
    s = np.sin(ph) + 0.33 * np.sin(3 * ph) + 0.16 * np.sin(5 * ph) + 0.08 * np.sin(7 * ph)
    env = np.minimum(1, t / 0.12) * np.minimum(1, (2.6 - t) / 0.25)
    s = s * env
    return 0.6 * s / np.max(np.abs(s))


def crackle():
    """Static: noise bursts of 3..25 ms with a fast decay, thinning out; a low thump at the start."""
    import numpy as np
    rnd = np.random.RandomState(0x5702A)
    n = int(RATE * 0.9)
    s = np.zeros(n)
    t = 0
    while t < n:
        length = rnd.randint(int(RATE * 0.003), int(RATE * 0.025))
        amp = (1 - t / n) ** 1.5 * (0.4 + 0.6 * rnd.rand())
        burst = rnd.randn(length) * np.exp(-np.arange(length) / (length / 3.0)) * amp
        end = min(n, t + length)
        s[t:end] += burst[:end - t]
        t += length + rnd.randint(0, int(RATE * 0.03 * (1 + 3 * t / n)))
    s = np.diff(s, prepend=0)                      # the hiss up, the rumble out
    k = np.arange(int(RATE * 0.12))
    s[:len(k)] += 0.5 * np.sin(2 * np.pi * 70 * k / RATE) * np.exp(-k / (RATE * 0.03))
    return 0.7 * s / np.max(np.abs(s))


def main():
    img = scan_texture()
    out = os.path.join(ASSETS, 'textures', 'misc')
    os.makedirs(out, exist_ok=True)
    img.save(os.path.join(out, 'storm_scan.png'))
    print('textures/misc/storm_scan.png')
    if PREVIEW:
        bg = Image.new('RGBA', (W * 4, H * 4), (34, 18, 52, 255))
        tile = img.resize((W * 2, H * 2), Image.NEAREST)
        for gx in range(2):
            for gy in range(2):
                bg.alpha_composite(tile, (gx * W * 2, gy * H * 2))
        bg.save(PREVIEW)
        print('preview', PREVIEW)
    try:
        import soundfile
    except ImportError:
        print('no soundfile: the sounds are not written')
        return
    snd = os.path.join(ASSETS, 'sounds', 'storm')
    os.makedirs(snd, exist_ok=True)
    for name, data in (('siren', siren()), ('crackle', crackle())):
        soundfile.write(os.path.join(snd, name + '.ogg'), data, RATE, format='OGG', subtype='VORBIS')
        print('sounds/storm/%s.ogg' % name)


if __name__ == '__main__':
    main()
