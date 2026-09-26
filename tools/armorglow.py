# -*- coding: utf-8 -*-
"""Glow layers for the worn armour: <suit>_glow_1/2.png (the lit pixels only, their own colours) and
<suit>_glowred_1/2.png (the same pixels in red, for an empty suit). Drawn a second time over the armour,
full-bright, by ModelArmorGlowSC. Run after armorforge3 / armorforge_exo / armorforge_quantum.
Usage: armorglow.py <textures dir>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import armorforge3 as af   # noqa: E402
import exo_v2 as e         # noqa: E402
import exo_v3 as c         # noqa: E402
import qv                  # noqa: E402
from PIL import Image      # noqa: E402


def colour_mask(img, colours):
    keep = set(tuple(x[:3]) for x in colours)
    px = img.load()
    return [[px[x, y][3] > 0 and tuple(px[x, y][:3]) in keep for y in range(img.size[1])] for x in range(img.size[0])]


def exo_masks():
    """Exo's glow is iridescent (computed per pixel): find it from the unfinished layers' markers."""
    real = e.finish
    e.finish = lambda s: None
    try:
        raw1, raw2 = c.layers("c")
    finally:
        e.finish = real
    out = []
    for raw in (raw1, raw2):
        px = raw.load()
        out.append([[px[x, y] in (e.G1, e.G2) for y in range(raw.size[1])] for x in range(raw.size[0])])
    return out


def write(tex, suit, n, mask):
    base = Image.open(os.path.join(tex, "models", "armor", "%s_layer_%d.png" % (suit, n))).convert("RGBA")
    bp = base.load()
    glow = Image.new("RGBA", base.size, (0, 0, 0, 0))
    red = Image.new("RGBA", base.size, (0, 0, 0, 0))
    gp, rp = glow.load(), red.load()
    for x in range(base.size[0]):
        for y in range(base.size[1]):
            if mask[x][y]:
                r, g, b, a = bp[x, y]
                gp[x, y] = (r, g, b, 255)
                lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
                rp[x, y] = (255, int(40 + 150 * max(0.0, lum - 0.5)), int(30 + 140 * max(0.0, lum - 0.5)), 255)
    glow.save(os.path.join(tex, "models", "armor", "%s_glow_%d.png" % (suit, n)))
    red.save(os.path.join(tex, "models", "armor", "%s_glowred_%d.png" % (suit, n)))


if __name__ == "__main__":
    tex = sys.argv[1]
    nano = af.SUITS["Nano"]
    for n in (1, 2):
        img = Image.open(os.path.join(tex, "models", "armor", "nano_layer_%d.png" % n)).convert("RGBA")
        write(tex, "nano", n, colour_mask(img, [nano["glow"], nano["glowhi"]]))
        img = Image.open(os.path.join(tex, "models", "armor", "quantum_layer_%d.png" % n)).convert("RGBA")
        write(tex, "quantum", n, colour_mask(img, [qv.CY, qv.CYHI]))
    m1, m2 = exo_masks()
    write(tex, "exo", 1, m1)
    write(tex, "exo", 2, m2)
    print("ok")
