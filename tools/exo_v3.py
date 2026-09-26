# -*- coding: utf-8 -*-
"""Exo armour mock-ups, detail variants c / d (chip 1 + palette B). Preview only."""
import math
import sys
sys.path.insert(0, sys.argv[1])
import armorforge3 as af
import exo_v2 as e
from PIL import Image, ImageDraw

P, G1, G2, GOLD, GOLDHI, GOLDSH, hx = e.P, e.G1, e.G2, e.GOLD, e.GOLDHI, e.GOLDSH, e.hx
RIBBON = [hx("c04a6a"), hx("3a6ad0"), hx("d9a834"), hx("5aa05a"), hx("a0a0b0")]


def pipe(s, x, y0, y1):
    """A vertical heat pipe: a steel tube with a glowing core."""
    s.vline(x - 1, y0, y1, P["hi2"])
    s.vline(x, y0, y1, G1)
    s.vline(x + 1, y0, y1, P["sh2"])


def smd(s, x, y):
    """A tiny surface-mount chip: dark body, gold ends."""
    s.p(x, y, GOLD)
    s.hline(x + 1, x + 4, y, P["out"])
    s.p(x + 4, y, GOLD)
    s.p(x + 2, y, P["accsh"])


def helmet(v):
    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P)
        e.angular(s, x, y, w, h)
        if name == "front":
            if v == "c":                                            # a full-width visor band with a scan line, gold forehead contacts
                s.rect(x, y + 5, x + w, y + 10, P["out"])
                s.rect(x + 1, y + 6, x + w - 1, y + 9, G1)
                s.hline(x + 1, x + w - 1, y + 7, G2)
                for i in range(x + 2, x + w - 1, 2):
                    s.p(i, y + 3, GOLD)
            else:                                                   # two eye slits and a third round lens
                for (ex0, ex1) in ((x + 1, x + 6), (x + 10, x + 15)):
                    s.rect(ex0, y + 5, ex1, y + 8, P["out"])
                    s.hline(ex0 + 1, ex1 - 1, y + 6, G1)
                    s.hline(ex0 + 1, ex1 - 1, y + 7, G2)
                s.disc(x + 8, y + 8, 2.4, P["out"])
                s.disc(x + 8, y + 8, 1.3, G1)
                for j in (y + 11, y + 13):                          # cheek vents
                    s.hline(x + 2, x + 5, j, GOLD)
                    s.hline(x + 11, x + 14, j, GOLD)
            s.hline(x + 5, x + 11, y + 13, P["sh2"])
        elif name in ("left", "right"):
            if v == "c":
                pipe(s, x + 5, y + 3, y + 13)
                pipe(s, x + 10, y + 3, y + 13)
                s.hline(x + 3, x + 13, y + 13, P["out"])
            else:
                s.rect(x + 3, y + 4, x + 13, y + 11, P["out"])
                for j in range(y + 5, y + 11, 2):
                    s.hline(x + 4, x + 12, j, GOLD)
                s.p(x + 12, y + 12, G1)
        elif name == "top":
            if v == "c":                                            # three fins with glowing tips
                for fx in (x + 4, x + 8, x + 12):
                    s.vline(fx, y + 1, y + h - 1, P["hi2"])
                    s.vline(fx - 1, y + 1, y + h - 1, P["out"])
                    s.p(fx, y + 1, G2)
            else:
                s.vline(x + 7, y, y + h, P["out"])
                s.vline(x + 8, y, y + h, G1)
        elif name == "back":
            if v == "c":
                pipe(s, x + 5, y + 2, y + 14)
                pipe(s, x + 11, y + 2, y + 14)
            else:
                s.chip(x + 8, y + 8, big=False)
    return paint


def body(v, waist=False):
    def paint(s, name, x, y, w, h):
        if waist:
            s.plate(x, y + h - 8, x + w, y + h, P)
            if name in ("front", "back"):
                s.rect(x, y + h - 6, x + w, y + h - 3, P["sh2"])
                s.rect(x + w // 2 - 2, y + h - 6, x + w // 2 + 2, y + h - 3, G1)
            return
        s.plate(x, y, x + w, y + h, P)
        cx, cy = x + w // 2, y + 10
        if name == "front":
            if v == "c":                                            # a wafer halo: small dies round the processor
                for a in range(12):
                    ang = a * math.pi / 6
                    px_ = int(round(cx - 0.5 + math.cos(ang) * 8.5))
                    py_ = int(round(cy + math.sin(ang) * 8.5))
                    s.rect(px_ - 1, py_ - 1, px_ + 1, py_ + 1, P["accsh"] if a % 2 else G1)
                s.chip(cx, cy)
                s.vline(cx - 1, cy + 9, y + h - 1, G1)
                s.vline(cx, cy + 9, y + h - 1, G2)
            else:                                                   # ribbon cables over the shoulders
                s.chip(cx, cy)
                for k, col in enumerate(RIBBON):
                    s.line(cx - 6, cy - 5 + k, x + 1 + k // 2, y + 1 + k, col)
                    s.line(cx + 6, cy - 5 + k, x + w - 2 - k // 2, y + 1 + k, col)
                for j in range(y + 17, y + 23, 2):
                    smd(s, x + 2, j)
                    smd(s, x + 10, j)
        elif name == "back":
            if v == "c":                                            # two heat pipes into a small radiator
                pipe(s, x + 4, y + 2, y + 14)
                pipe(s, x + w - 5, y + 2, y + 14)
                s.fins(x + 2, y + 14, x + w - 2, y + 21)
                s.hline(x + 2, x + w - 2, y + 13, G1)
            else:                                                   # three RAM sticks
                for k in range(3):
                    sx = x + 2 + k * 4
                    s.rect(sx, y + 3, sx + 3, y + 19, P["out"])
                    s.rect(sx + 1, y + 4, sx + 2, y + 17, P["accsh"])
                    s.p(sx + 1, y + 6, G1)
                    s.p(sx + 1, y + 10, G1)
                    for j in range(y + 17, y + 19):
                        s.hline(sx, sx + 3, j, GOLD)
        elif name in ("left", "right"):
            if v == "d":
                for k, col in enumerate(RIBBON):
                    s.vline(x + 1 + k, y + 1, y + 8, col)
            else:
                pipe(s, x + 4, y + 2, y + 20)
    return paint


def arm(v):
    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P)
        if name in ("top", "bottom"):
            s.rect(x, y, x + w, y + h, P["hi"] if name == "top" else P["sh"])
            return
        if v == "c":                                                # a round shoulder orb, a forearm blade fin
            s.disc(x + 4, y + 4, 3.4, P["out"])
            s.ring(x + 4, y + 4, 1.4, 2.6, G1)
            s.p(x + 4, y + 4, G2)
            if name in ("right", "left", "front"):
                for j in range(8):
                    s.hline(x + w - 1 - j // 2, x + w, y + 9 + j, G1 if j % 3 == 0 else P["hi2"])
                s.line(x + w - 1, y + 9, x + w - 5, y + 17, G2)
        else:                                                       # a ribbon cable spiralling down, contact fingers
            for j in range(0, 17):
                col = RIBBON[(j // 2) % len(RIBBON)]
                s.p(x + (j * 2) % w, y + 2 + j, col)
                s.p(x + (j * 2 + 1) % w, y + 2 + j, col)
            for i in range(x, x + w, 2):
                s.vline(i, y + 21, y + h, GOLD)
        s.rect(x, y + 18, x + w, y + 20, G1)
        s.hline(x, x + w, y + 19, G2)
        s.rect(x, y + 20, x + w, y + 21, P["sh2"])
    return paint


def leg(v, boots):
    def paint(s, name, x, y, w, h):
        if boots:
            if name == "top":
                return
            if name == "bottom":
                s.rect(x, y, x + w, y + h, P["sh2"])
                return
            s.plate(x, y + h - 10, x + w, y + h, P)
            s.hline(x, x + w, y + h - 10, P["out"])
            if v == "c":                                            # anti-grav: a glowing band floating over the sole
                s.rect(x, y + h - 2, x + w, y + h, P["out"])
                s.hline(x, x + w, y + h - 4, G1)
                s.hline(x, x + w, y + h - 3, G2)
                if name == "front":
                    s.line(x + 1, y + h - 5, x + 4, y + h - 8, P["hi2"])
                    s.line(x + 6, y + h - 5, x + 4, y + h - 8, P["hi2"])
            else:                                                   # a heel radiator, gold toe contacts, a side stripe
                s.rect(x, y + h - 2, x + w, y + h, G1)
                if name == "back":
                    s.fins(x + 1, y + h - 8, x + w - 1, y + h - 2)
                elif name == "front":
                    for i in range(x + 1, x + w, 2):
                        s.p(i, y + h - 4, GOLD)
                else:
                    s.line(x, y + h - 5, x + w - 1, y + h - 8, G1)
            return
        s.plate(x, y, x + w, y + h, P)
        if name in ("top", "bottom"):
            return
        if v == "c":                                                # heat pipes down the leg, a finned knee
            if name in ("right", "left", "front"):
                pipe(s, x + 2, y + 1, y + 10)
                pipe(s, x + 6, y + 1, y + 10)
            s.fins(x, y + 10, x + w, y + 15)
            s.hline(x, x + w, y + 15, P["out"])
            s.vein([(x + 4, y + 17), (x + 4, y + 22)], P)
        else:                                                       # a column of SMD chips, a long shin slit
            if name in ("front", "right", "left"):
                for j in (y + 2, y + 5, y + 8):
                    smd(s, x + 2, j)
            s.line(x, y + 11, x + w - 1, y + 12, P["hi2"])
            s.line(x, y + 12, x + w - 1, y + 13, P["out"])
            s.vline(x + 3, y + 15, y + 23, P["out"])
            s.vline(x + 4, y + 15, y + 23, G1)
    return paint


def layers(v):
    l1, l2 = e.S(128, 64), e.S(128, 64)
    e.box(l1, 0, 0, 16, 16, 16, helmet(v))
    e.box(l1, 32, 32, 16, 24, 8, body(v))
    e.box(l1, 80, 32, 8, 24, 8, arm(v))
    e.box(l1, 0, 32, 8, 24, 8, leg(v, True))
    e.box(l2, 32, 32, 16, 24, 8, body(v, True))
    e.box(l2, 0, 32, 8, 24, 8, leg(v, False))
    import parts3d
    parts3d.paint("Exo", l1, l2, P)                         # the 3D add-on parts' nets
    for im in (l1, l2):
        e.finish(im)
    return l1.im, l2.im


if __name__ == "__main__":
    out = sys.argv[2]
    Z = 5
    sheet = Image.new("RGBA", (2 * 32 * Z + 16 * Z + 80, 2 * (64 * Z + 40) + 20), (60, 60, 70, 255))
    d = ImageDraw.Draw(sheet)
    for r, name in enumerate(("c", "d")):
        l1, l2 = layers(name)
        y0 = 30 + r * (64 * Z + 40)
        d.text((20, y0 - 22), "Variant " + {"c": "v (c)", "d": "g (d)"}[name] + ":  front / back / side", fill=(255, 255, 255, 255))
        sheet.alpha_composite(af.front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (20, y0))
        sheet.alpha_composite(af.side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (40 + 32 * Z, y0))
        sheet.alpha_composite(e.side_view(l1, l2).resize((16 * Z, 64 * Z), Image.NEAREST), (60 + 64 * Z, y0))
    sheet.save(out)
    print("ok")
