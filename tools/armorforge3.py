# -*- coding: utf-8 -*-
"""Silicon Age armour art v3 - each tier its own design language.
Nano: tactical graphite + olive, hex nano-mesh, straps and pouches, a narrow green HUD slit, a hex reactor.
Quantum: white ceramic + gold trims, smooth large panels, cyan energy lines, a wide amber visor, a ringed round core.
Exo: black + violet, angular crystalline plates, glowing violet veins, a chevron visor, a singularity core.
Usage: armorforge3.py <textures dir> <preview png>"""
import math
import os
import sys
from PIL import Image


def hx(h, a=255):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), a)


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * k) for i in range(3)) + (255,)


CLEAR = (0, 0, 0, 0)
WHITE = (255, 255, 255, 255)
SUITS = {
    "Nano": dict(base=hx("4a5047"), hi=hx("6d7568"), hi2=hx("8c9485"), sh=hx("30352e"), sh2=hx("222620"), out=hx("0b0d0a"),
                 mesh=hx("41463e"), acc=hx("62703a"), acchi=hx("8a9a52"), accsh=hx("434e27"), trace=hx("62703a"), via=hx("c9c9b4"),
                 glow=hx("5cff6a"), glowhi=hx("d6ffd8"), core=hx("101a12")),
    "Quantum": dict(base=hx("dce2e9"), hi=hx("f1f4f8"), hi2=hx("ffffff"), sh=hx("aab2bc"), sh2=hx("858e99"), out=hx("2a2f36"),
                    mesh=hx("d3d9e0"), acc=hx("d9a834"), acchi=hx("ffe08a"), accsh=hx("9c7420"), trace=hx("d9a834"), via=hx("ffe08a"),
                    glow=hx("3fd6ff"), glowhi=hx("e0f8ff"), core=hx("17222e")),
    "Exo": dict(base=hx("27232f"), hi=hx("3e3849"), hi2=hx("5a5070"), sh=hx("18151d"), sh2=hx("0f0d13"), out=hx("040305"),
                mesh=hx("221e29"), acc=hx("6b38b8"), acchi=hx("a070f0"), accsh=hx("3e1f70"), trace=hx("6b38b8"), via=hx("c89cff"),
                glow=hx("b35cff"), glowhi=hx("f0dcff"), core=hx("08050d")),
}
AMBER = [hx("ffe3a0"), hx("ffc45a"), hx("f09a2a"), hx("c86a14"), hx("8a430c")]


def amber(t):
    t = max(0.0, min(0.999, t))
    i = t * (len(AMBER) - 1)
    return mix(AMBER[int(i)], AMBER[min(len(AMBER) - 1, int(i) + 1)], i - int(i))


class Img:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("RGBA", (w, h), CLEAR)
        self.px = self.im.load()

    def p(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[x, y] = c

    def g(self, x, y):
        return self.px[x, y] if 0 <= x < self.w and 0 <= y < self.h else CLEAR

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.p(x, y, c)

    def hline(self, x0, x1, y, c):
        for x in range(x0, x1):
            self.p(x, y, c)

    def vline(self, x, y0, y1, c):
        for y in range(y0, y1):
            self.p(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.p(int(round(x0 + (x1 - x0) * i / float(n))), int(round(y0 + (y1 - y0) * i / float(n))), c)

    def plate(self, x0, y0, x1, y1, p, soft=False):
        """A bevelled panel: light top-left, dark bottom-right."""
        self.rect(x0, y0, x1, y1, p["base"])
        for x in range(x0, x1):
            self.p(x, y0, p["hi2"])
            if not soft:
                self.p(x, y0 + 1, p["hi"])
            self.p(x, y1 - 1, p["sh2"])
            if not soft:
                self.p(x, y1 - 2, p["sh"])
        for y in range(y0 + 1, y1 - 1):
            self.p(x0, y, p["hi"])
            self.p(x1 - 1, y, p["sh"])

    def mesh(self, x0, y0, x1, y1, p):
        """Nano: a faint hex mesh on the plate."""
        for y in range(y0 + 2, y1 - 2):
            for x in range(x0 + 1, x1 - 1):
                if y % 3 == 0 and (x + (y // 3) % 2 * 2) % 4 == 0 and self.g(x, y) == p["base"]:
                    self.p(x, y, p["mesh"])

    def disc(self, cx, cy, r, c):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                    self.p(x, y, c)

    def ring(self, cx, cy, r0, r1, c):
        for y in range(int(cy - r1) - 1, int(cy + r1) + 2):
            for x in range(int(cx - r1) - 1, int(cx + r1) + 2):
                if r0 <= math.hypot(x + 0.5 - cx, y + 0.5 - cy) < r1:
                    self.p(x, y, c)

    def hexcore(self, cx, cy, p):
        """Nano: a small hexagonal reactor, 7 wide."""
        rows = ["..ooo..", ".oaaao.", "oaggGao", "oagGGao", "oaggGao", ".oaaao.", "..ooo.."]
        cols = {"o": p["out"], "a": p["acc"], "g": p["glow"], "G": p["glowhi"]}
        for j, r in enumerate(rows):
            for i, ch in enumerate(r):
                if ch != ".":
                    self.p(cx - 3 + i, cy - 3 + j, cols[ch])

    def roundcore(self, cx, cy, p, r=3.6):
        """Quantum: a round core in a gold ring."""
        self.disc(cx, cy, r + 0.9, p["out"])
        self.ring(cx, cy, r - 0.9, r + 0.1, p["acc"])
        self.disc(cx, cy, r - 1.0, p["glow"])
        self.disc(cx, cy, max(0.8, r - 2.4), p["glowhi"])
        self.p(int(cx - r + 1.5), int(cy - r + 1.5), p["acchi"])

    def singularity(self, cx, cy, p, r=3.8):
        """Exo: a black hole in a violet ring, a faint halo round it."""
        self.ring(cx, cy, r + 0.2, r + 1.2, p["accsh"])
        self.ring(cx, cy, r - 1.2, r + 0.2, p["glow"])
        self.ring(cx, cy, r - 0.6, r + 0.2, p["glowhi"])
        self.disc(cx, cy, r - 1.2, p["core"])
        self.p(int(cx), int(cy), p["out"])

    def vein(self, pts, p):
        """Exo: a glowing vein through the points, bright nodes at the joints."""
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            self.line(ax, ay, bx, by, p["glow"])
        for (x, y) in pts[1:-1]:
            self.p(x, y, p["glowhi"])

    def cyanline(self, pts, p):
        """Quantum: a thin energy line (axis-aligned steps)."""
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            if ax == bx:
                self.vline(ax, min(ay, by), max(ay, by) + 1, p["glow"])
            else:
                self.hline(min(ax, bx), max(ax, bx) + 1, ay, p["glow"])


# ================================================================ worn layers, 128x64 (vanilla layout x2)

def box(s, ox, oy, w, h, d, p, painter):
    faces = {
        "top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d),
        "right": (ox, oy + d, d, h), "front": (ox + d, oy + d, w, h),
        "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h),
    }
    for name, (x, y, fw, fh) in faces.items():
        painter(s, name, x, y, fw, fh, p)


def helmet(st):
    def paint(s, name, x, y, w, h, p):
        s.plate(x, y, x + w, y + h, p, soft=st == "Quantum")
        if st == "Nano":
            s.mesh(x, y, x + w, y + h, p)
            if name == "front":                                     # narrow green HUD slit, a vented jaw guard
                s.rect(x, y + 6, x + w, y + 9, p["out"])
                s.hline(x + 1, x + w - 1, y + 7, p["glow"])
                s.p(x + 4, y + 7, p["glowhi"])
                s.p(x + 11, y + 7, p["glowhi"])
                s.rect(x + 1, y + 6, x + 3, y + 9, p["acc"])            # side clamps
                s.rect(x + w - 3, y + 6, x + w - 1, y + 9, p["acc"])
                s.rect(x + 4, y + 11, x + 12, y + 15, p["sh"])
                for i in range(5, 12, 2):
                    s.vline(x + i, y + 12, y + 14, p["sh2"])
                s.hline(x + 4, x + 12, y + 11, p["hi"])
            elif name in ("left", "right"):
                s.rect(x + 3, y + 5, x + 12, y + 11, p["acc"])      # rail mount with a lamp
                s.hline(x + 3, x + 12, y + 5, p["acchi"])
                s.hline(x + 3, x + 12, y + 10, p["accsh"])
                s.rect(x + 5, y + 7, x + 7, y + 9, p["out"])
                s.p(x + 5, y + 7, p["glow"])
                for i in (9, 11):
                    s.p(x + i, y + 7, p["via"])
            elif name == "top":
                s.rect(x + 6, y + 2, x + 10, y + 14, p["acc"])      # a strip of olive webbing
                s.vline(x + 6, y + 2, y + 14, p["acchi"])
                s.vline(x + 9, y + 2, y + 14, p["accsh"])
            elif name == "back":
                s.rect(x + 3, y + 4, x + 13, y + 11, p["sh"])       # battery pack
                s.rect(x + 4, y + 5, x + 12, y + 10, p["acc"])
                s.hline(x + 4, x + 12, y + 5, p["acchi"])
                s.p(x + 10, y + 8, p["glow"])
        elif st == "Quantum":
            if name == "front":                                     # a wide amber visor in a gold frame
                s.rect(x + 1, y + 3, x + w - 1, y + 12, p["acc"])
                s.rect(x + 2, y + 4, x + w - 2, y + 11, p["out"])
                for j in range(5, 11):
                    for i in range(3, w - 3):
                        s.p(x + i, y + j, amber((j - 5) / 6.0))
                for i in range(4):                                   # a diagonal glint
                    s.p(x + 4 + i, y + 9 - i, mix(amber(0.1), WHITE, 0.6))
                s.p(x + 3, y + 5, WHITE)
                s.hline(x + 5, x + 11, y + 13, p["sh"])
                s.p(x + 7, y + 13, p["glow"])
                s.p(x + 8, y + 13, p["glow"])
            elif name in ("left", "right"):
                s.disc(x + 8, y + 8, 4.2, p["acc"])                 # round ear pod, cyan centre
                s.disc(x + 8, y + 8, 3.0, p["sh"])
                s.disc(x + 8, y + 8, 1.8, p["glow"])
                s.p(x + 7, y + 7, p["glowhi"])
            elif name == "top":
                s.vline(x + 7, y + 1, y + h - 1, p["acc"])          # gold crest line
                s.vline(x + 8, y + 1, y + h - 1, p["acchi"])
                s.cyanline([(x + 3, y + 3), (x + 3, y + 12)], p)
                s.cyanline([(x + 12, y + 3), (x + 12, y + 12)], p)
            elif name == "back":
                s.hline(x + 2, x + 14, y + 4, p["acc"])
                s.cyanline([(x + 8, y + 6), (x + 8, y + 13)], p)
        else:
            for i in range(0, w, 5):                                 # crystalline seams
                s.line(x + i, y + 1, x + min(w - 1, i + 4), y + 5, p["sh2"])
            if name == "front":                                     # a chevron visor with a scan line
                pts = [(x + 1, y + 5), (x + 8, y + 9), (x + 14, y + 5)]
                for dy in range(-1, 2):
                    s.line(pts[0][0], pts[0][1] + dy, pts[1][0], pts[1][1] + dy, p["glow"])
                    s.line(pts[1][0] + 1, pts[1][1] + dy, pts[2][0], pts[2][1] + dy, p["glow"])
                s.line(x + 1, y + 5, x + 7, y + 8, p["glowhi"])
                s.line(x + 8, y + 8, x + 14, y + 5, p["glowhi"])
                s.line(x + 1, y + 3, x + 7, y + 6, p["out"])
                s.line(x + 8, y + 6, x + 14, y + 3, p["out"])
                s.line(x + 1, y + 7, x + 7, y + 10, p["out"])
                s.line(x + 8, y + 10, x + 14, y + 7, p["out"])
                s.vein([(x + 8, y + 11), (x + 8, y + 14)], p)
            elif name in ("left", "right"):
                s.vein([(x + 2, y + 13), (x + 6, y + 9), (x + 11, y + 9), (x + 14, y + 4)], p)
                s.line(x + 3, y + 3, x + 8, y + 6, p["hi2"])
            elif name == "top":                                     # a ridge crest
                s.vline(x + 7, y, y + h, p["out"])
                s.vline(x + 8, y, y + h, p["hi2"])
                s.vein([(x + 4, y + 2), (x + 4, y + 7), (x + 6, y + 10)], p)
                s.vein([(x + 11, y + 2), (x + 11, y + 7), (x + 9, y + 10)], p)
            elif name == "back":
                s.vein([(x + 8, y + 2), (x + 8, y + 8), (x + 4, y + 13)], p)
                s.vein([(x + 8, y + 8), (x + 12, y + 13)], p)
    return paint


def body(st, waist=False):
    def paint(s, name, x, y, w, h, p):
        if waist:                                                   # layer 2: only the waist band
            s.plate(x, y + h - 8, x + w, y + h, p, soft=st == "Quantum")
            if name in ("front", "back"):
                band = {"Nano": p["acc"], "Quantum": p["acc"], "Exo": p["sh2"]}[st]
                s.rect(x, y + h - 6, x + w, y + h - 3, band)
                s.rect(x + w // 2 - 2, y + h - 6, x + w // 2 + 2, y + h - 3, p["glow"] if st != "Nano" else p["via"])
            return
        s.plate(x, y, x + w, y + h, p, soft=st == "Quantum")
        cx = x + w // 2
        if st == "Nano":
            s.mesh(x, y, x + w, y + h, p)
            if name == "front":                                     # w=16, h=24: a vest with straps, pouches, a hex reactor
                s.hline(x, x + w, y + 8, p["sh"])
                s.hline(x, x + w, y + 15, p["sh"])
                s.line(x + 1, y + 1, x + 5, y + 11, p["acc"])
                s.line(x + 2, y + 1, x + 6, y + 11, p["accsh"])
                s.line(x + 14, y + 1, x + 10, y + 11, p["acc"])
                s.line(x + 13, y + 1, x + 9, y + 11, p["accsh"])
                s.hexcore(cx, y + 7, p)
                for px0 in (x + 1, x + 10):                          # pouches
                    s.rect(px0, y + 16, px0 + 5, y + 21, p["acc"])
                    s.hline(px0, px0 + 5, y + 16, p["acchi"])
                    s.hline(px0, px0 + 5, y + 20, p["accsh"])
                    s.p(px0 + 2, y + 17, p["via"])
                s.rect(x, y + h - 3, x + w, y + h - 1, p["sh2"])
                s.p(cx - 1, y + h - 2, p["via"])
                s.p(cx, y + h - 2, p["via"])
            elif name == "back":
                s.rect(x + 2, y + 3, x + w - 2, y + 17, p["acc"])    # a pack with a battery gauge
                s.hline(x + 2, x + w - 2, y + 3, p["acchi"])
                s.hline(x + 2, x + w - 2, y + 16, p["accsh"])
                s.rect(x + 5, y + 6, x + w - 5, y + 13, p["out"])
                for i in range(7, 13, 2):
                    s.hline(x + 6, x + w - 6, y + i, p["glow"])
            elif name in ("left", "right"):
                s.rect(x + 2, y + 4, x + 6, y + 14, p["acc"])
                s.hline(x + 2, x + 6, y + 4, p["acchi"])
            elif name == "top":
                s.hline(x + 2, x + w - 2, y + 3, p["acc"])
        elif st == "Quantum":
            if name == "front":                                     # smooth plates, gold trims, a ringed core
                s.line(x + 1, y + 1, cx - 1, y + 5, p["acc"])        # V collar
                s.line(x + w - 2, y + 1, cx, y + 5, p["acc"])
                s.hline(x + 1, cx - 1, y + 7, p["sh"])
                s.hline(cx + 1, x + w - 1, y + 7, p["sh"])
                s.roundcore(cx, y + 11, p)
                s.cyanline([(cx - 4, y + 11), (x + 2, y + 11), (x + 2, y + 19)], p)
                s.cyanline([(cx + 3, y + 11), (x + 13, y + 11), (x + 13, y + 19)], p)
                s.cyanline([(cx - 1, y + 15), (cx - 1, y + 20)], p)
                s.cyanline([(cx, y + 15), (cx, y + 20)], p)
                s.rect(x, y + h - 3, x + w, y + h - 1, p["acc"])     # gold belt
                s.hline(x, x + w, y + h - 3, p["acchi"])
                s.rect(cx - 2, y + h - 3, cx + 2, y + h - 1, p["glow"])
            elif name == "back":
                s.hline(x + 2, x + w - 2, y + 2, p["acc"])
                s.cyanline([(x + 5, y + 4), (x + 5, y + 18)], p)
                s.cyanline([(x + w - 6, y + 4), (x + w - 6, y + 18)], p)
                s.rect(x, y + h - 3, x + w, y + h - 1, p["acc"])
            elif name in ("left", "right"):
                s.vline(x + 2, y + 2, y + h - 3, p["acc"])
            elif name == "top":
                s.hline(x + 1, x + w - 1, y + 3, p["acc"])
        else:
            if name == "front":                                     # angular plates, a singularity, veins
                s.line(x + 1, y + 1, cx - 1, y + 8, p["sh2"])
                s.line(x + w - 2, y + 1, cx, y + 8, p["sh2"])
                s.line(x, y + 16, cx - 1, y + 21, p["sh2"])
                s.line(x + w - 1, y + 16, cx, y + 21, p["sh2"])
                s.line(x + 2, y + 1, cx - 1, y + 6, p["hi2"])
                s.singularity(cx, y + 11, p)
                s.vein([(cx - 5, y + 11), (x + 3, y + 9), (x + 1, y + 3)], p)
                s.vein([(cx + 4, y + 11), (x + 12, y + 9), (x + 14, y + 3)], p)
                s.vein([(cx - 2, y + 15), (x + 4, y + 19), (x + 3, y + 22)], p)
                s.vein([(cx + 1, y + 15), (x + 11, y + 19), (x + 12, y + 22)], p)
            elif name == "back":
                s.vline(cx - 1, y + 1, y + h - 1, p["out"])          # spine ridge with glowing nodes
                s.vline(cx, y + 1, y + h - 1, p["hi2"])
                for j in range(3, h - 2, 5):
                    s.p(cx - 1, y + j, p["glow"])
                    s.p(cx, y + j, p["glowhi"])
                s.vein([(cx - 2, y + 6), (x + 3, y + 10), (x + 2, y + 16)], p)
                s.vein([(cx + 1, y + 6), (x + 12, y + 10), (x + 13, y + 16)], p)
            elif name in ("left", "right"):
                s.vein([(x + 2, y + 2), (x + 5, y + 9), (x + 3, y + 18)], p)
            elif name == "top":
                s.line(x + 1, y + 1, x + w - 2, y + d_top(h) - 1, p["sh2"])
    return paint


def d_top(h):
    return 8


def arm(st):
    def paint(s, name, x, y, w, h, p):
        s.plate(x, y, x + w, y + h, p, soft=st == "Quantum")
        if name in ("top", "bottom"):
            if name == "top":
                c = {"Nano": p["acc"], "Quantum": p["hi"], "Exo": p["hi"]}[st]
                s.rect(x + 1, y + 1, x + w - 1, y + h - 1, c)
            else:
                s.rect(x, y, x + w, y + h, p["sh"])
            return
        if st == "Nano":
            s.mesh(x, y, x + w, y + h, p)
            s.rect(x, y, x + w, y + 7, p["acc"])                    # olive shoulder pad, rivets
            s.hline(x, x + w, y, p["acchi"])
            s.hline(x, x + w, y + 6, p["accsh"])
            s.hline(x, x + w, y + 7, p["out"])
            s.p(x + 2, y + 3, p["via"])
            s.p(x + 5, y + 3, p["via"])
            s.hline(x, x + w, y + 12, p["sh"])                       # elbow strap
            s.hline(x, x + w, y + 13, p["acc"])
            s.rect(x, y + 19, x + w, y + h, p["sh"])                # gauntlet, a green diode
            s.hline(x, x + w, y + 19, p["out"])
            if name == "front":
                s.p(x + 3, y + 21, p["glow"])
        elif st == "Quantum":
            s.rect(x, y, x + w, y + 7, p["hi"])                     # rounded shoulder, gold trim
            s.hline(x, x + w, y, p["hi2"])
            s.hline(x, x + w, y + 6, p["acc"])
            s.hline(x, x + w, y + 7, p["accsh"])
            s.cyanline([(x + 3, y + 9), (x + 3, y + 17)], p)
            s.rect(x, y + 18, x + w, y + 20, p["glow"])             # cyan wrist ring
            s.hline(x, x + w, y + 18, p["glowhi"])
            s.rect(x, y + 20, x + w, y + h, p["sh"])
            s.hline(x, x + w, y + 21, p["acc"])
        else:
            for i in range(0, 7):                                   # a pointed shoulder plate
                s.hline(x, x + w - i // 2, y + i, p["hi"] if i < 2 else p["base"])
            s.line(x, y + 7, x + w - 1, y + 3, p["out"])
            s.line(x, y + 6, x + w - 1, y + 2, p["hi2"])
            s.vein([(x + 2, y + 8), (x + 4, y + 12), (x + 2, y + 17)], p)
            s.rect(x, y + 18, x + w, y + 20, p["glow"])             # violet wrist ring
            s.hline(x, x + w, y + 19, p["glowhi"])
            s.rect(x, y + 20, x + w, y + h, p["sh2"])
    return paint


def leg(st, boots):
    def paint(s, name, x, y, w, h, p):
        if boots:                                                   # layer 1: the boots, lower 10 rows
            if name == "top":
                return
            if name == "bottom":
                s.rect(x, y, x + w, y + h, p["sh2"])
                if st == "Nano":
                    for i in range(1, h, 2):
                        s.hline(x, x + w, y + i, p["out"])          # tread
                return
            s.plate(x, y + h - 10, x + w, y + h, p, soft=st == "Quantum")
            s.hline(x, x + w, y + h - 10, p["out"])
            if st == "Nano":
                s.rect(x, y + h - 3, x + w, y + h, p["sh2"])         # chunky tread sole
                for i in range(x, x + w, 2):
                    s.p(i, y + h - 1, p["out"])
                s.hline(x, x + w, y + h - 7, p["acc"])               # strap with a buckle
                s.p(x + 3, y + h - 7, p["via"])
                if name == "front":
                    s.p(x + 5, y + h - 5, p["glow"])
            elif st == "Quantum":
                s.hline(x, x + w, y + h - 9, p["acc"])
                s.rect(x, y + h - 2, x + w, y + h, p["glow"])        # cyan sole glow
                s.hline(x, x + w, y + h - 2, p["glowhi"])
                s.hline(x, x + w, y + h - 3, p["acc"])
            else:
                s.rect(x, y + h - 2, x + w, y + h, p["glow"])        # violet sole glow
                s.hline(x, x + w, y + h - 2, p["glowhi"])
                s.line(x, y + h - 9, x + 3, y + h - 5, p["hi2"])
                if name != "front":
                    s.vein([(x + 1, y + h - 4), (x + 4, y + h - 7), (x + w - 2, y + h - 7)], p)
            return
        s.plate(x, y, x + w, y + h, p, soft=st == "Quantum")        # layer 2: leggings
        if name in ("top", "bottom"):
            return
        if st == "Nano":
            s.mesh(x, y, x + w, y + h, p)
            if name in ("right", "left"):                            # thigh pouch
                s.rect(x + 1, y + 2, x + 7, y + 8, p["acc"])
                s.hline(x + 1, x + 7, y + 2, p["acchi"])
                s.p(x + 4, y + 4, p["via"])
            s.rect(x, y + 10, x + w, y + 15, p["acc"])               # knee pad
            s.hline(x, x + w, y + 10, p["acchi"])
            s.hline(x, x + w, y + 14, p["accsh"])
            s.hline(x, x + w, y + 15, p["out"])
        elif st == "Quantum":
            s.hline(x, x + w, y + 9, p["acc"])                       # gold knee trims, a cyan line
            s.hline(x, x + w, y + 15, p["acc"])
            s.rect(x, y + 10, x + w, y + 15, p["hi"])
            if name == "front":
                s.cyanline([(x + 4, y + 2), (x + 4, y + 8)], p)
                s.rect(x + 3, y + 11, x + 5, y + 14, p["glow"])
                s.cyanline([(x + 4, y + 16), (x + 4, y + 21)], p)
        else:
            s.line(x, y + 9, x + w - 1, y + 12, p["hi2"])            # an angular knee plate
            s.line(x, y + 10, x + w - 1, y + 13, p["out"])
            s.line(x, y + 15, x + w - 1, y + 13, p["sh2"])
            s.vein([(x + 2, y + 2), (x + 5, y + 7), (x + 3, y + 11)], p)
            if name == "front":
                s.vein([(x + 4, y + 14), (x + 2, y + 18), (x + 5, y + 22)], p)
    return paint


def layers(st, p):
    l1, l2 = Img(128, 64), Img(128, 64)
    box(l1, 0, 0, 16, 16, 16, p, helmet(st))
    box(l1, 32, 32, 16, 24, 8, p, body(st))
    box(l1, 80, 32, 8, 24, 8, p, arm(st))
    box(l1, 0, 32, 8, 24, 8, p, leg(st, True))
    box(l2, 32, 32, 16, 24, 8, p, body(st, True))
    box(l2, 0, 32, 8, 24, 8, p, leg(st, False))
    return l1.im, l2.im


# ================================================================ icons, 32x32: 16-wide half masks, mirrored, then decorated

ICONS = {
    ("Nano", "Helmet"): [
        "................", "................", "................", "................", "..........oooooo", "........oohhhhhh",
        "......oohhbbbbbb", ".....ohhbbbbbbbb", "....ohbbbbbbbbbb", "....ohbbbbbbbbbb", "...ohbbbbbbbbbbb", "...ohbbbbbbbbbbb",
        "..oaaooooooooooo", "..oaaoVVVVVVVVVV", "..oaaooooooooooo", "..oaabbbbbbbbbbb", "..oaabbbbbbsbbsb", "..ohbbbbbbbsbbsb",
        "...obbbbbbbsbbsb", "...obbssssssssss", "....oooooooooooo"],
    ("Quantum", "Helmet"): [
        "................", "................", "................", "...........ooooo", "........ooohhhhh", "......oohhhhhhhh",
        ".....ohhhbbbbbbb", "....ohhbbbbbbbbb", "....ohbbbbbbbbbb", "...ohbbttttttttt", "...ohbtooooooooo", "...ohbtoVVVVVVVV",
        "..ohbbtoVVVVVVVV", "..ohbbtoVVVVVVVV", "..ohbbtoVVVVVVVV", "..ohbbttoooooooo", "..ohbbbttttttttt", "..obbbbbbbbbbbbb",
        "..obgbbbbbbsssss", "...obbbbbssooooo", "....ooooooo....."],
    ("Exo", "Helmet"): [
        "................", "..............oo", ".............ohh", "............ohbb", "..........oohbbb", "........oohhbbbb",
        "......oohhbbbbbb", "....oohhbbbbbbbb", "...ohhbbbbbbbbbb", "...ohbbbbbbbbbbb", "..ohbbooobbbbbbb", "..ohbbogGoooobbb",
        "..ohbbbogggGoooo", "..ohbbbbooogggGG", "..ohbbbbbbbooooo", "..osbbbbbbbbbbbb", "..osbbssbbbbbbbb", "...osbbbssbbbbbb",
        "....osbbbbssssss", ".....ossoooooooo"],
    ("Nano", "Chestplate"): [
        "................", "................", "................", ".ooooo..........", "oaaaaaoooooooooo", "oaaaaahhhhhhhhhh",
        "ohbbbbbbbbbbbbbb", "ohbbbbbbbbbbbbbb", "ohbbbbbbbbbbbbbb", "oobbbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..ossssssssssss", "..oooooooooooooo"],
    ("Quantum", "Chestplate"): [
        "................", "................", "................", "..oooo..........", ".ohhhhoooooooooo", "ohhhhhhhhhhhhhhh",
        "ohhbbbbbbbbbbbbb", "ohbbbbbbbbbbbbbb", "obbbbbbbbbbbbbbb", ".obbbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..osssssssssssss", "...ooooooooooooo"],
    ("Exo", "Chestplate"): [
        "................", "o...............", "oo..............", "ohoo............", "ohhhoooooooooooo", "ohhhhhhhhhhhhhhh",
        ".ohbbbbbbbbbbbbb", ".ohbbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "...obbbbbbbbbbbb", "...osssssssssss", "....oooooooooooo"],
    ("Nano", "Leggings"): [
        "................", "................", "................", "....oooooooooooo", "....ohhhhhhhhhhh", "....ohbbbbbbbbbb",
        "....oaaaaaaaaaaa", "....ohbbbbbboooo", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...",
        "....oaaaaaaao...", "....oaaaaaaao...", "....ossssssso...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...",
        "....ohbbbbbbo...", "....ohbbbbbbo...", "....ossssssso...", "....ooooooooo..."],
    ("Quantum", "Leggings"): [
        "................", "................", "................", "....oooooooooooo", "....ohhhhhhhhhhh", "....ohbbbbbbbbbb",
        "....otttttttttttt"[:16], "....ohbbbbbboooo", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....otttttttto...",
        "....ohhhhhhhho...", "....ohhhhhhhho...", "....otttttttto...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...",
        "....ohbbbbbbo...", "....ohbbbbbbo...", "....ossssssso...", "....ooooooooo..."],
    ("Exo", "Leggings"): [
        "................", "................", "................", "....oooooooooooo", "....ohhhhhhhhhhh", "....ohbbbbbbbbbb",
        "....osssssssssss", "....ohbbbbbboooo", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...", "...oohbbbbbbo...",
        "..ohhhhhbbbbo...", "...oosssssbbo...", "....ohbbbssso...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...",
        "....ohbbbbbbo...", "....ohbbbbbbo...", "....ossssssso...", "....ooooooooo..."],
    ("Nano", "Boots"): ["................"] * 11 + [
        "....oooooo......", "....ohbbbo......", "....oaaaao......", "....ohbbbo......", "....ohbbbboo....", "...ohhbbbbbbo...",
        "...ohbbbbbbbbo..", "...ossssssssso..", "...ococococoo...", "...ooooooooooo.."],
    ("Quantum", "Boots"): ["................"] * 11 + [
        "....oooooo......", "....otttto......", "....ohbbbo......", "....ohbbbo......", "....ohbbbboo....", "...ohhbbbbbbo...",
        "...ohbbbbbbbbo..", "...ostttttttto..", "...ogggggggggo..", "...ooooooooooo.."],
    ("Exo", "Boots"): ["................"] * 10 + [
        "....o...........", "....oooooo......", "....ohbbbo......", "...oohbbbo......", "...ohbbbbo......", "....ohbbbboo....",
        "...ohbbbbbbbo...", "..oohbbbbbbbbo..", "..ossssssssssoo.", "...oggggggggggo.", "...ooooooooooo.."],
}


def mirror(left):
    swap = {"h": "s", "s": "h"}
    return [l + "".join(swap.get(ch, ch) for ch in reversed(l)) for l in left]


def icon(st, piece, p):
    rows = mirror([r.ljust(16, ".")[:16] for r in ICONS[(st, piece)]])
    im = Img(32, 32)
    for y, row in enumerate(rows[:32]):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            c = {"o": p["out"], "h": p["hi"], "b": p["base"], "s": p["sh"], "t": p["acc"], "a": p["acc"],
                 "g": p["glow"], "G": p["glowhi"], "c": p["core"]}.get(ch)
            if ch == "V":
                if st == "Quantum":
                    top = min(j for j, r in enumerate(rows) if "V" in r)
                    c = amber((y - top) / 4.0)
                else:
                    c = p["glowhi"] if x in (9, 22) else p["glow"]
            if c:
                im.p(x, y, c)
    # soft highlight inside the top-left edges
    for y in range(32):
        for x in range(32):
            if im.g(x, y) == p["base"] and im.g(x - 1, y) == p["hi"] and im.g(x, y - 1) in (p["hi"], p["out"]):
                im.p(x, y, mix(p["base"], p["hi"], 0.5))
    decorate(im, st, piece, p)
    return im.im


def decorate(im, st, piece, p):
    if st == "Nano":
        for y in range(32):                                        # the mesh
            for x in range(32):
                if y % 3 == 0 and (x + (y // 3) % 2 * 2) % 4 == 0 and im.g(x, y) == p["base"]:
                    im.p(x, y, p["mesh"])
    if piece == "Helmet":
        if st == "Quantum":
            for i in range(3):
                im.p(10 + i, 14 - i, mix(amber(0.1), WHITE, 0.7))
        if st == "Nano":
            im.p(8, 7, p["via"])
            im.p(23, 7, p["via"])
    elif piece == "Chestplate":
        if st == "Nano":
            im.line(4, 6, 11, 18, p["acc"])
            im.line(5, 6, 12, 18, p["accsh"])
            im.line(27, 6, 20, 18, p["acc"])
            im.line(26, 6, 19, 18, p["accsh"])
            im.hexcore(16, 11, p)
            for x0 in (4, 22):
                im.rect(x0, 15, x0 + 5, 19, p["acc"])
                im.hline(x0, x0 + 5, 15, p["acchi"])
                im.p(x0 + 2, 16, p["via"])
        elif st == "Quantum":
            im.line(4, 6, 14, 10, p["acc"])
            im.line(27, 6, 17, 10, p["acc"])
            im.roundcore(16, 12.5, p, 3.4)
            im.hline(3, 29, 18, p["acc"])
            im.hline(3, 29, 17, p["acchi"])
            im.p(15, 18, p["glow"])
            im.p(16, 18, p["glow"])
        else:
            im.line(4, 6, 14, 13, p["sh2"])
            im.line(27, 6, 17, 13, p["sh2"])
            im.singularity(16, 12, p, 3.6)
            im.vein([(11, 12), (7, 10), (4, 7)], p)
            im.vein([(20, 12), (24, 10), (27, 7)], p)
            im.vein([(14, 16), (10, 19)], p)
            im.vein([(17, 16), (21, 19)], p)
    elif piece == "Leggings":
        if st == "Nano":
            for x0 in (6, 21):
                im.p(x0 + 2, 15, p["via"])
        elif st == "Quantum":
            im.rect(8, 12, 10, 14, p["glow"])
            im.rect(22, 12, 24, 14, p["glow"])
            im.hline(15, 17, 5, p["glow"])
        else:
            im.vein([(7, 8), (9, 11)], p)
            im.vein([(24, 8), (22, 11)], p)
            im.vein([(8, 16), (9, 19)], p)
            im.vein([(23, 16), (22, 19)], p)
    elif piece == "Boots":
        if st == "Nano":
            im.p(7, 16, p["via"])
            im.p(24, 16, p["via"])
            im.p(9, 18, p["glow"])
            im.p(22, 18, p["glow"])
        elif st == "Exo":
            im.p(7, 13, p["glow"])
            im.p(24, 13, p["glow"])
            im.p(7, 14, p["glowhi"])
            im.p(24, 14, p["glowhi"])


def front_view(l1, l2):
    """Flat front view of a player wearing the set, 32x64 (for the preview)."""
    v = Image.new("RGBA", (32, 64), CLEAR)

    def blit(src, bx, at):
        v.alpha_composite(src.crop(bx), at)
    blit(l1, (16, 16, 32, 32), (8, 0))
    blit(l2, (40, 40, 56, 64), (8, 16))
    blit(l1, (40, 40, 56, 64), (8, 16))
    blit(l1, (88, 40, 96, 64), (0, 16))
    blit(l1, (88, 40, 96, 64), (24, 16))
    blit(l2, (8, 40, 16, 64), (8, 40))
    blit(l2, (8, 40, 16, 64), (16, 40))
    blit(l1, (8, 40, 16, 64), (8, 40))
    blit(l1, (8, 40, 16, 64), (16, 40))
    return v


def side_view(l1, l2):
    """Back view, 32x64 (for the preview)."""
    v = Image.new("RGBA", (32, 64), CLEAR)

    def blit(src, bx, at):
        v.alpha_composite(src.crop(bx), at)
    blit(l1, (48, 16, 64, 32), (8, 0))
    blit(l2, (64, 40, 80, 64), (8, 16))
    blit(l1, (64, 40, 80, 64), (8, 16))
    blit(l1, (104, 40, 112, 64), (0, 16))
    blit(l1, (104, 40, 112, 64), (24, 16))
    blit(l2, (24, 40, 32, 64), (8, 40))
    blit(l2, (24, 40, 32, 64), (16, 40))
    blit(l1, (24, 40, 32, 64), (8, 40))
    blit(l1, (24, 40, 32, 64), (16, 40))
    return v


if __name__ == "__main__":
    tex, out = sys.argv[1], sys.argv[2]
    Z = 4
    sheet = Image.new("RGBA", (4 * 32 * Z + 2 * 32 * Z + 90, 3 * (64 * Z + 10) + 10), (60, 60, 70, 255))
    for r, (st, p) in enumerate(SUITS.items()):
        for c, piece in enumerate(("Helmet", "Chestplate", "Leggings", "Boots")):
            im = icon(st, piece, p)
            if tex != "-":
                im.save(os.path.join(tex, "items", "armor%s%s.png" % (st, piece)))
            sheet.alpha_composite(im.resize((32 * Z, 32 * Z), Image.NEAREST), (c * 32 * Z + 10, r * (64 * Z + 10) + 10))
        l1, l2 = layers(st, p)
        if tex != "-":
            l1.save(os.path.join(tex, "models", "armor", st.lower() + "_layer_1.png"))
            l2.save(os.path.join(tex, "models", "armor", st.lower() + "_layer_2.png"))
        sheet.alpha_composite(front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (4 * 32 * Z + 40, r * (64 * Z + 10) + 10))
        sheet.alpha_composite(side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (5 * 32 * Z + 70, r * (64 * Z + 10) + 10))
    sheet.save(out)
    print("ok")
