# -*- coding: utf-8 -*-
"""Exo armour v2 mock-ups: chip 1 (processor) + palette B (violet -> magenta -> blue iridescence),
per-part detail variants a / b. Preview only."""
import math
import sys
sys.path.insert(0, sys.argv[1])
import armorforge3 as af
from PIL import Image, ImageDraw

hx, mix = af.hx, af.mix
P = dict(af.SUITS["Exo"])
GOLD, GOLDHI, GOLDSH = hx("d9a834"), hx("ffe08a"), hx("8c6a1c")
# markers painted first, turned iridescent afterwards
G1, G2 = (1, 2, 3, 255), (4, 5, 6, 255)
P["glow"], P["glowhi"] = G1, G2
IRID = [hx("9a4cff"), hx("d84cf0"), hx("ff5ab8"), hx("b05cff"), hx("5a7cff"), hx("8a5cff")]


def irid(t):
    t = t % 1.0
    i = t * len(IRID)
    a, b = IRID[int(i) % len(IRID)], IRID[(int(i) + 1) % len(IRID)]
    return mix(a, b, i - int(i))


class S(af.Img):
    def chip(self, cx, cy, big=True):
        """The processor: gold pins on four sides, a dark package, a glowing die, a key mark."""
        r = 5 if big else 3
        for i in range(-r + 1, r, 2):
            for (px, py) in ((cx + i, cy - r - 1), (cx + i, cy + r + 1), (cx - r - 1, cy + i), (cx + r + 1, cy + i)):
                self.p(px, py, GOLD)
        self.rect(cx - r, cy - r, cx + r + 1, cy + r + 1, P["out"])
        self.rect(cx - r + 1, cy - r + 1, cx + r, cy + r, P["sh2"])
        if big:
            self.rect(cx - 3, cy - 3, cx + 4, cy + 4, P["accsh"])
            self.rect(cx - 2, cy - 2, cx + 3, cy + 3, G1)
            self.rect(cx - 1, cy - 1, cx + 2, cy + 2, G2)
            self.p(cx - r + 1, cy - r + 1, GOLDHI)
        else:
            self.rect(cx - 1, cy - 1, cx + 2, cy + 2, G1)
            self.p(cx, cy, G2)

    def fins(self, x0, y0, x1, y1, horizontal=True):
        for j in range(y0, y1):
            for i in range(x0, x1):
                k = (j - y0) if horizontal else (i - x0)
                self.p(i, j, P["hi"] if k % 2 == 0 else P["sh2"])


def box(s, ox, oy, w, h, d, painter):
    faces = {"top": (ox + d, oy, w, d), "bottom": (ox + d + w, oy, w, d), "right": (ox, oy + d, d, h),
             "front": (ox + d, oy + d, w, h), "left": (ox + d + w, oy + d, d, h), "back": (ox + d + w + d, oy + d, w, h)}
    for name, (x, y, fw, fh) in faces.items():
        painter(s, name, x, y, fw, fh)


def angular(s, x, y, w, h):
    for i in range(0, w, 5):
        s.line(x + i, y + 1, x + min(w - 1, i + 4), y + 5, P["sh2"])


def helmet(v):
    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P)
        angular(s, x, y, w, h)
        if name == "front":
            pts = [(x + 1, y + 6), (x + 8, y + 10), (x + 14, y + 6)]
            for dy in range(-1, 2):
                s.line(pts[0][0], pts[0][1] + dy, pts[1][0], pts[1][1] + dy, G1)
                s.line(pts[1][0] + 1, pts[1][1] + dy, pts[2][0], pts[2][1] + dy, G1)
            s.line(x + 1, y + 6, x + 7, y + 9, G2)
            s.line(x + 8, y + 9, x + 14, y + 6, G2)
            s.line(x + 1, y + 4, x + 7, y + 7, P["out"])
            s.line(x + 8, y + 7, x + 14, y + 4, P["out"])
            s.line(x + 1, y + 8, x + 7, y + 11, P["out"])
            s.line(x + 8, y + 11, x + 14, y + 8, P["out"])
            if v == "a":
                s.chip(x + 8, y + 2, big=False)                    # a co-processor over the visor
            else:
                s.vline(x + 7, y, y + 4, GOLD)                      # the bus down the crest to the visor
                s.vline(x + 8, y, y + 4, G1)
            s.hline(x + 5, x + 11, y + 13, P["sh2"])
            s.p(x + 7, y + 13, G1)
            s.p(x + 8, y + 13, G1)
        elif name in ("left", "right"):
            if v == "a":                                            # radiator "antenna" fins, glowing tips
                s.fins(x + 3, y + 3, x + 12, y + 12, horizontal=False)
                s.hline(x + 3, x + 12, y + 2, G1)
                s.hline(x + 3, x + 12, y + 12, P["out"])
            else:
                if name == "right":                                 # optics: a round lens
                    s.disc(x + 8, y + 8, 4.4, P["out"])
                    s.ring(x + 8, y + 8, 2.6, 3.8, G1)
                    s.disc(x + 8, y + 8, 2.2, P["core"])
                    s.p(x + 7, y + 7, G2)
                else:                                               # a vent grille
                    s.rect(x + 3, y + 4, x + 13, y + 12, P["out"])
                    for j in range(5, 12, 2):
                        s.hline(x + 4, x + 12, y + j, P["sh"])
                    s.p(x + 12, y + 12, G1)
        elif name == "top":
            s.vline(x + 7, y, y + h, P["out"])
            s.vline(x + 8, y, y + h, P["hi2"])
            if v == "b":
                s.vline(x + 9, y, y + h, G1)
        elif name == "back":
            s.vein([(x + 8, y + 2), (x + 8, y + 8), (x + 4, y + 13)], P)
            s.vein([(x + 8, y + 8), (x + 12, y + 13)], P)
    return paint


def body(v, waist=False):
    def paint(s, name, x, y, w, h):
        if waist:
            s.plate(x, y + h - 8, x + w, y + h, P)
            if name in ("front", "back"):
                s.rect(x, y + h - 6, x + w, y + h - 3, P["sh2"])
                for i in range(x + 1, x + w, 3):
                    s.p(i, y + h - 5, GOLD)
                s.rect(x + w // 2 - 2, y + h - 6, x + w // 2 + 2, y + h - 3, G1)
            return
        s.plate(x, y, x + w, y + h, P)
        cx, cy = x + w // 2, y + 10
        if name == "front":
            s.line(x + 1, y + 1, x + 7, y + 4, P["hi2"])
            s.chip(cx, cy)
            if v == "a":                                            # motherboard: capacitors, resistors, traces
                for (px0, py0) in ((x + 1, y + 16), (x + 13, y + 16)):
                    s.rect(px0, py0, px0 + 2, py0 + 4, P["hi2"])
                    s.hline(px0, px0 + 2, py0, hx("c8c0d8"))
                    s.p(px0, py0 + 1, G1)
                for (rx, ry) in ((x + 4, y + 18), (x + 9, y + 18), (x + 4, y + 21), (x + 9, y + 21)):
                    s.p(rx, ry, GOLD)
                    s.hline(rx + 1, rx + 3, ry, hx("3a6ad0") if (rx + ry) % 2 else hx("c04a6a"))
                    s.p(rx + 3, ry, GOLD)
                for (sx, sy, ex, ey) in ((cx - 6, cy - 3, x + 1, y + 2), (cx + 6, cy - 3, x + w - 2, y + 2)):
                    s.line(sx, sy, ex, ey, G1)
                s.vline(cx - 2, cy + 7, y + h - 1, G1)
                s.vline(cx + 1, cy + 7, y + h - 1, G1)
            else:                                                   # energy bus to the shoulders, segmented abs
                for dx in (0, 1):
                    s.line(cx - 6 - dx, cy - 4, x + 1, y + 1 + dx, G1)
                    s.line(cx + 6 + dx, cy - 4, x + w - 2, y + 1 + dx, G1)
                for j in (y + 16, y + 19, y + 22):
                    s.hline(x + 2, x + w - 2, j, P["sh2"])
                    s.hline(x + 2, x + w - 2, j - 1, P["hi"])
                s.vline(cx - 1, y + 15, y + h - 1, P["out"])
                s.vline(cx, y + 15, y + h - 1, G1)
        elif name == "back":
            if v == "a":                                            # a radiator with a glowing fan
                s.fins(x + 2, y + 2, x + w - 2, y + 20)
                s.disc(cx, y + 11, 5.2, P["out"])
                s.ring(cx, y + 11, 3.2, 4.6, G1)
                for a in range(4):
                    ang = a * math.pi / 2 + 0.4
                    s.line(cx, y + 11, int(round(cx + math.cos(ang) * 3)), int(round(y + 11 + math.sin(ang) * 3)), P["hi2"])
                s.p(cx, y + 11, G2)
            else:                                                   # a power pack with LEDs and contacts
                s.rect(x + 2, y + 3, x + w - 2, y + 19, P["out"])
                s.rect(x + 3, y + 4, x + w - 3, y + 18, P["sh"])
                s.hline(x + 3, x + w - 3, y + 4, P["hi2"])
                for i, j in enumerate((y + 7, y + 10, y + 13)):
                    s.rect(x + 5, j, x + 8, j + 2, G1 if i < 2 else P["accsh"])
                for j in range(y + 6, y + 17, 2):
                    s.p(x + w - 5, j, GOLD)
        elif name in ("left", "right"):
            s.vein([(x + 2, y + 2), (x + 5, y + 9), (x + 3, y + 18)], P)
    return paint


def arm(v):
    def paint(s, name, x, y, w, h):
        s.plate(x, y, x + w, y + h, P)
        if name in ("top", "bottom"):
            s.rect(x, y, x + w, y + h, P["hi"] if name == "top" else P["sh"])
            return
        if v == "a":                                                # crystal shoulder, a memory slot, a wrist ring
            for i in range(0, 7):
                s.hline(x, x + w - i // 2, y + i, P["hi"] if i < 2 else P["base"])
            s.line(x, y + 7, x + w - 1, y + 3, G1)
            s.line(x, y + 8, x + w - 1, y + 4, P["out"])
            if name in ("front", "right", "left"):
                s.rect(x + 2, y + 11, x + 6, y + 17, P["out"])
                s.rect(x + 3, y + 11, x + 5, y + 17, P["accsh"])
                for j in range(y + 12, y + 17, 2):
                    s.p(x + 2, j, GOLD)
                    s.p(x + 5, j, GOLD)
        else:                                                       # layered shoulder, a forearm module
            for k in range(3):
                s.hline(x, x + w, y + k * 2, P["hi2"])
                s.hline(x, x + w, y + k * 2 + 1, P["sh"])
            s.hline(x, x + w, y + 6, G1)
            s.hline(x, x + w, y + 7, P["out"])
            if name in ("front", "right", "left"):
                s.chip(x + 4, y + 13, big=False)
        s.rect(x, y + 18, x + w, y + 20, G1)
        s.hline(x, x + w, y + 19, G2)
        s.rect(x, y + 20, x + w, y + h, P["sh2"])
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
            if v == "a":                                            # thrusters: a heel nozzle, a glowing sole
                s.rect(x, y + h - 2, x + w, y + h, G1)
                s.hline(x, x + w, y + h - 2, G2)
                if name == "back":
                    s.disc(x + 4, y + h - 5, 2.6, P["out"])
                    s.disc(x + 4, y + h - 5, 1.4, G1)
                    s.p(x + 4, y + h - 5, G2)
                elif name == "front":
                    s.line(x, y + h - 9, x + 3, y + h - 5, P["hi2"])
                    s.line(x + w - 1, y + h - 9, x + 4, y + h - 5, P["hi2"])
                else:
                    s.line(x + 1, y + h - 9, x + 3, y + h - 12, P["out"])     # an ankle spike
                    s.vein([(x + 1, y + h - 4), (x + 4, y + h - 7), (x + w - 2, y + h - 7)], P)
            else:                                                   # magnetic soles with contact pins, a side chip
                s.rect(x, y + h - 4, x + w, y + h, P["out"])
                for i in range(x, x + w, 2):
                    s.p(i, y + h - 4, GOLD)
                s.hline(x, x + w, y + h - 1, G1)
                if name in ("right", "left"):
                    s.chip(x + 4, y + h - 7, big=False)
            return
        s.plate(x, y, x + w, y + h, P)
        if name in ("top", "bottom"):
            return
        if v == "a":                                                # a data bus down the thigh, a knee chip
            if name in ("front", "right", "left"):
                s.vline(x + 2, y + 1, y + 9, P["accsh"])
                s.vline(x + 4, y + 1, y + 9, G1)
                s.vline(x + 6, y + 1, y + 9, P["accsh"])
                for i in (2, 4, 6):
                    s.p(x + i, y + 9, GOLD)
            s.line(x, y + 10, x + w - 1, y + 12, P["hi2"])
            s.line(x, y + 11, x + w - 1, y + 13, P["out"])
            s.line(x, y + 16, x + w - 1, y + 14, P["sh2"])
            if name == "front":
                s.rect(x + 2, y + 13, x + 6, y + 16, P["out"])
                s.p(x + 3, y + 14, G1)
                s.p(x + 4, y + 14, G2)
                s.p(x + 1, y + 14, GOLD)
                s.p(x + 6, y + 14, GOLD)
        else:                                                       # a knee servo, a thigh plate with contacts
            s.rect(x + 1, y + 1, x + w - 1, y + 8, P["hi"])
            for j in range(y + 2, y + 8, 2):
                s.p(x + 1, j, GOLD)
                s.p(x + w - 2, j, GOLD)
            if name == "front" or name == "right" or name == "left":
                s.disc(x + 4, y + 13, 3.4, P["out"])
                s.ring(x + 4, y + 13, 1.6, 2.8, G1)
                s.p(x + 4, y + 13, G2)
            s.vein([(x + 3, y + 17), (x + 5, y + 21)], P)
    return paint


def layers(v):
    l1, l2 = S(128, 64), S(128, 64)
    box(l1, 0, 0, 16, 16, 16, helmet(v["helmet"]))
    box(l1, 32, 32, 16, 24, 8, body(v["chest"]))
    box(l1, 80, 32, 8, 24, 8, arm(v["arms"]))
    box(l1, 0, 32, 8, 24, 8, leg(v["boots"], True))
    box(l2, 32, 32, 16, 24, 8, body(v["chest"], True))
    box(l2, 0, 32, 8, 24, 8, leg(v["legs"], False))
    for im in (l1, l2):
        finish(im)
    return l1.im, l2.im


def finish(s):
    """Palette B: plates shade violet-grey at the top to near black at the bottom; the glow becomes iridescent."""
    for y in range(s.h):
        k = max(0.0, min(1.0, ((y % 32) - 8) / 24.0)) if y >= 32 else (y % 16) / 16.0
        tint = mix(hx("4a3a70"), hx("120e1a"), k)
        for x in range(s.w):
            c = s.px[x, y]
            if c[3] == 0:
                continue
            if c == G1 or c == G2:
                g = irid((x * 0.035 + y * 0.05))
                s.px[x, y] = mix(g, (255, 255, 255, 255), 0.55) if c == G2 else g
            elif c[:3] in (P["base"][:3], P["hi"][:3], P["sh"][:3], P["hi2"][:3]):
                s.px[x, y] = mix(c, tint, 0.4)


def side_view(l1, l2):
    v = Image.new("RGBA", (16, 64), (0, 0, 0, 0))

    def blit(src, bx, at):
        v.alpha_composite(src.crop(bx), at)
    blit(l1, (0, 16, 16, 32), (0, 0))            # head, right side
    blit(l2, (32, 40, 40, 64), (4, 16))          # waist side
    blit(l1, (32, 40, 40, 64), (4, 16))          # body side
    blit(l1, (80, 40, 88, 64), (4, 16))          # arm, outer side
    blit(l2, (0, 40, 8, 64), (4, 40))            # leg side
    blit(l1, (0, 40, 8, 64), (4, 40))            # boot side
    return v


if __name__ == "__main__":
    out = sys.argv[2]
    Z = 5
    sets = [("a", dict(helmet="a", chest="a", arms="a", legs="a", boots="a")),
            ("b", dict(helmet="b", chest="b", arms="b", legs="b", boots="b"))]
    sheet = Image.new("RGBA", (2 * 32 * Z + 16 * Z + 80, 2 * (64 * Z + 40) + 20), (60, 60, 70, 255))
    d = ImageDraw.Draw(sheet)
    for r, (name, v) in enumerate(sets):
        l1, l2 = layers(v)
        y0 = 30 + r * (64 * Z + 40)
        d.text((20, y0 - 22), "Variant " + name + ":  front / back / side", fill=(255, 255, 255, 255))
        sheet.alpha_composite(af.front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (20, y0))
        sheet.alpha_composite(af.side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (40 + 32 * Z, y0))
        sheet.alpha_composite(side_view(l1, l2).resize((16 * Z, 64 * Z), Image.NEAREST), (60 + 64 * Z, y0))
    sheet.save(out)
    print("ok")
