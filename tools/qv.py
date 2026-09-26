# -*- coding: utf-8 -*-
"""Quantum armour mock-ups: "golden knight" + "cyan circuits" merged, six variants. Preview only."""
import math
import sys
sys.path.insert(0, sys.argv[1])
import armorforge3 as af
import exo_v2 as e
from PIL import Image, ImageDraw

hx, mix = af.hx, af.mix
P = af.SUITS["Quantum"]
GOLD, GOLDHI, GOLDSH = hx("d9a834"), hx("ffe08a"), hx("9c7420")
CY, CYHI = P["glow"], P["glowhi"]


class S(af.Img):
    def gold(self, x0, y0, x1, y1):
        self.rect(x0, y0, x1, y1, GOLD)
        self.hline(x0, x1, y0, GOLDHI)
        self.hline(x0, x1, y1 - 1, GOLDSH)
        self.vline(x1 - 1, y0 + 1, y1 - 1, GOLDSH)

    def circuit(self, pts, pad=True):
        for (ax, ay), (bx, by) in zip(pts, pts[1:]):
            if ax == bx:
                self.vline(ax, min(ay, by), max(ay, by) + 1, CY)
            elif ay == by:
                self.hline(min(ax, bx), max(ax, bx) + 1, ay, CY)
            else:
                self.line(ax, ay, bx, by, CY)
        if pad:
            for (x, y) in (pts[0], pts[-1]):
                self.p(x, y, GOLD)

    def qubit(self, cx, cy, r=5.2):
        self.disc(cx, cy, r, P["out"])
        self.ring(cx, cy, r - 1.2, r - 0.2, GOLD)
        self.disc(cx, cy, r - 1.2, hx("1d2a36"))
        for a in range(8):
            ang = a * math.pi / 4
            self.p(int(round(cx - 0.5 + math.cos(ang) * (r - 2.4))), int(round(cy - 0.5 + math.sin(ang) * (r - 2.4))),
                   CY if a % 2 else CYHI)
        self.disc(cx, cy, 1.2, CYHI)

    def tvisor(self, x, y, w, cyan=False):
        self.rect(x + 1, y + 3, x + w - 1, y + 7, GOLD)
        self.rect(x + 5, y + 7, x + w - 5, y + 13, GOLD)
        self.rect(x + 2, y + 4, x + w - 2, y + 7, P["out"])
        self.rect(x + 6, y + 7, x + w - 6, y + 12, P["out"])
        for j in range(5, 7):
            self.hline(x + 3, x + w - 3, y + j, CY if cyan else af.amber((j - 5) / 3.0))
        for j in range(7, 11):
            self.hline(x + 7, x + w - 7, y + j, CY if cyan else af.amber((j - 4) / 7.0))
        self.p(x + 3, y + 5, (255, 255, 255, 255))

    def widevisor(self, x, y, w):
        self.rect(x + 1, y + 3, x + w - 1, y + 12, GOLD)
        self.rect(x + 2, y + 4, x + w - 2, y + 11, P["out"])
        for j in range(5, 11):
            self.hline(x + 3, x + w - 3, y + j, af.amber((j - 5) / 6.0))
        for i in range(3):
            self.p(x + 4 + i, y + 8 - i, mix(af.amber(0.1), (255, 255, 255, 255), 0.7))


def box(s, ox, oy, w, h, d, painter):
    faces = {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h),
             "front": (ox + d, oy + d, w, h), "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}
    for name, (x, y, fw, fh) in faces.items():
        painter(s, name, x, y, fw, fh)


# variant knobs: visor, gold amount (1 light .. 3 heavy), circuit density (1 .. 3), extras
V = {
    1: dict(visor="t", gold=2, circ=2, extra=None),           # balanced
    2: dict(visor="wide", gold=2, circ=3, extra=None),        # wide amber visor, denser circuits
    3: dict(visor="t", gold=3, circ=1, extra="gorget"),       # heavy knight
    4: dict(visor="tcyan", gold=1, circ=3, extra=None),       # circuit-dominant, cyan T visor
    5: dict(visor="t", gold=2, circ=2, extra="crest"),        # royal: crest + back emblem
    6: dict(visor="t", gold=2, circ=2, extra="cross"),        # paladin: a gold cross behind the core
}


def helmet(v):
    k = V[v]

    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P, soft=True)
        if name == "front":
            if k["visor"] == "wide":
                s.widevisor(x, y, w)
                s.gold(x + 1, y, x + w - 1, y + 2)
            else:
                s.tvisor(x, y, w, cyan=k["visor"] == "tcyan")
                s.gold(x + 1, y, x + w - 1, y + 2)
            if k["circ"] >= 2:
                s.circuit([(x + 1, y + 14), (x + 4, y + 14), (x + 4, y + 11)])
                s.circuit([(x + 14, y + 14), (x + 11, y + 14), (x + 11, y + 11)])
        elif name in ("left", "right"):
            s.gold(x + 1, y, x + w - 1, y + 3)
            s.circuit([(x + 2, y + 13), (x + 7, y + 13), (x + 7, y + 6), (x + 13, y + 6)])
            if k["circ"] >= 3:
                s.circuit([(x + 3, y + 5), (x + 3, y + 9)])
        elif name == "top":
            s.gold(x + 1, y + 1, x + w - 1, y + 5)
            if k["extra"] == "crest":
                s.gold(x + 6, y, x + 10, y + h)
                s.vline(x + 8, y + 1, y + h - 1, CY)
            else:
                s.circuit([(x + 8, y + 6), (x + 8, y + 14)])
        elif name == "back":
            s.gold(x + 1, y, x + w - 1, y + 3)
            s.circuit([(x + 3, y + 5), (x + 8, y + 5), (x + 8, y + 13)])
            if k["circ"] >= 2:
                s.circuit([(x + 13, y + 6), (x + 11, y + 6), (x + 11, y + 13)])
    return paint


def body(v, waist=False):
    k = V[v]

    def paint(s, name, x, y, w, h):
        if waist:
            if name in ("top", "bottom"):
                return
            s.plate(x, y + h - 8, x + w, y + h, P, soft=True)
            if name in ("front", "back"):
                if k["gold"] >= 2:
                    s.gold(x, y + h - 6, x + w, y + h - 3)
                else:
                    s.hline(x, x + w, y + h - 5, GOLD)
                s.rect(x + w // 2 - 2, y + h - 6, x + w // 2 + 2, y + h - 3, CY)
            return
        s.plate(x, y, x + w, y + h, P, soft=True)
        cx, cy = x + w // 2, y + 10
        if name == "front":
            if k["extra"] == "gorget" or k["gold"] >= 3:           # a gold gorget over the chest
                s.gold(x, y, x + w, y + 3)
                s.gold(x + 2, y + 3, x + w - 2, y + 5)
            else:
                s.gold(x + 1, y, x + 5, y + 3)
                s.gold(x + w - 5, y, x + w - 1, y + 3)
            if k["extra"] == "cross":
                s.gold(cx - 2, y + 2, cx + 2, y + 22)
                s.gold(x + 2, cy - 2, x + w - 2, cy + 2)
            # circuits out of the core
            if k["circ"] >= 1:
                s.circuit([(cx - 6, cy), (x + 2, cy), (x + 2, y + 20)])
                s.circuit([(cx + 5, cy), (x + 13, cy), (x + 13, y + 20)])
            if k["circ"] >= 2:
                s.circuit([(cx - 2, cy + 6), (cx - 2, y + 19), (x + 5, y + 19), (x + 5, y + 22)])
                s.circuit([(cx + 1, cy + 6), (cx + 1, y + 19), (x + 10, y + 19), (x + 10, y + 22)])
            if k["circ"] >= 3:
                s.circuit([(cx - 4, cy - 5), (x + 4, y + 6), (x + 4, y + 3)])
                s.circuit([(cx + 3, cy - 5), (x + 11, y + 6), (x + 11, y + 3)])
            s.qubit(cx, cy)
        elif name == "back":
            if k["gold"] >= 2:
                s.gold(x, y, x + w, y + 3)
            if k["extra"] == "crest":                               # a royal emblem
                s.gold(cx - 4, y + 6, cx + 4, y + 15)
                s.qubit(cx, y + 10, 3.4)
            s.circuit([(x + 3, y + 5), (x + 3, y + 18), (cx - 1, y + 18)])
            s.circuit([(x + w - 4, y + 5), (x + w - 4, y + 18), (cx, y + 18)])
            if k["circ"] >= 3:
                s.circuit([(x + 6, y + 4), (x + 6, y + 14)])
                s.circuit([(x + w - 7, y + 4), (x + w - 7, y + 14)])
        elif name in ("left", "right"):
            s.circuit([(x + 2, y + 4), (x + 2, y + 16), (x + 5, y + 16)])
        elif name == "top":
            s.gold(x, y + 1, x + w, y + 3)
    return paint


def arm(v):
    k = V[v]

    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P, soft=True)
        if name == "bottom":
            return
        if name == "top":
            s.gold(x, y, x + w, y + h)
            return
        pad = 7 if k["gold"] >= 2 else 5
        s.gold(x, y, x + w, y + pad)                                # gold pauldron
        if k["gold"] >= 3:
            s.gold(x, y + pad + 1, x + w, y + pad + 3)
        s.circuit([(x + 2, y + pad + 2), (x + 2, y + 12), (x + 5, y + 12), (x + 5, y + 17)])
        if k["circ"] >= 3:
            s.circuit([(x + 6, y + pad + 2), (x + 6, y + 10)])
        s.rect(x, y + 18, x + w, y + 20, CY)                        # cyan wrist ring
        s.hline(x, x + w, y + 18, CYHI)
        s.gold(x, y + 20, x + w, y + 22) if k["gold"] >= 2 else s.hline(x, x + w, y + 21, GOLD)
    return paint


def leg(v, boots):
    k = V[v]

    def paint(s, name, x, y, w, h):
        if boots:
            if name == "top":
                return
            if name == "bottom":
                s.rect(x, y, x + w, y + h, P["sh2"])
                return
            s.plate(x, y + h - 10, x + w, y + h, P, soft=True)
            s.hline(x, x + w, y + h - 10, P["out"])
            s.gold(x, y + h - 9, x + w, y + h - 7) if k["gold"] >= 2 else s.hline(x, x + w, y + h - 9, GOLD)
            if k["gold"] >= 3 and name == "front":
                s.gold(x + 1, y + h - 6, x + w - 1, y + h - 3)
            s.circuit([(x + 1, y + h - 5), (x + w - 3, y + h - 5), (x + w - 3, y + h - 3)])
            s.rect(x, y + h - 2, x + w, y + h, CY)
            s.hline(x, x + w, y + h - 2, CYHI)
            return
        s.plate(x, y, x + w, y + h, P, soft=True)
        if name in ("top", "bottom"):
            return
        s.gold(x, y + 10, x + w, y + 14)                            # gold knee
        if k["gold"] >= 3:
            s.gold(x, y + 1, x + w, y + 4)
        s.circuit([(x + 2, y + 2 + (3 if k["gold"] >= 3 else 0)), (x + 2, y + 8), (x + 5, y + 8)])
        s.circuit([(x + 4, y + 16), (x + 4, y + 20), (x + 1, y + 20), (x + 1, y + 22)])
        if k["circ"] >= 3:
            s.circuit([(x + 6, y + 16), (x + 6, y + 22)])
        if name == "front":
            s.rect(x + 3, y + 11, x + 5, y + 13, CY)
    return paint


def layers(v):
    l1, l2 = S(128, 64), S(128, 64)
    box(l1, 0, 0, 16, 16, 16, helmet(v))
    box(l1, 32, 32, 16, 24, 8, body(v))
    box(l1, 80, 32, 8, 24, 8, arm(v))
    box(l1, 0, 32, 8, 24, 8, leg(v, True))
    box(l2, 32, 32, 16, 24, 8, body(v, True))
    box(l2, 0, 32, 8, 24, 8, leg(v, False))
    return l1.im, l2.im


if __name__ == "__main__":
    out = sys.argv[2]
    Z = 4
    SW = 32 * Z * 2 + 16 * Z + 40
    names = {1: "1 balanced", 2: "2 wide visor, dense circuits", 3: "3 heavy knight", 4: "4 circuit-dominant, cyan T",
             5: "5 royal (crest + emblem)", 6: "6 paladin (gold cross)"}
    sheet = Image.new("RGBA", (2 * SW + 40, 3 * (64 * Z + 40) + 20), (60, 60, 70, 255))
    d = ImageDraw.Draw(sheet)
    for i, v in enumerate(range(1, 7)):
        l1, l2 = layers(v)
        x0 = 20 + (i % 2) * (SW + 20)
        y0 = 30 + (i // 2) * (64 * Z + 40)
        d.text((x0, y0 - 22), names[v], fill=(255, 255, 255, 255))
        sheet.alpha_composite(af.front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (x0, y0))
        sheet.alpha_composite(af.side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (x0 + 32 * Z + 12, y0))
        sheet.alpha_composite(e.side_view(l1, l2).resize((16 * Z, 64 * Z), Image.NEAREST), (x0 + 64 * Z + 24, y0))
    sheet.save(out)
    print("ok")
