# -*- coding: utf-8 -*-
"""Drill mock-ups in IC2's manner (a ring-shaped grip body, a cable loop at the back, a banded cone bit, the
tier shown by the tip and a clip), in our tiers' colours. Painted large and horizontal with soft layered
shading, turned 45 degrees and reduced by averaging - smooth tones like IC2's (about 100 colours an icon).
Variant A: the IC2 layout recoloured. Variant B: plus our details (the tier chip, a charge strip, a glowing
cable plug, a crystal / glowing tip)."""
import math
import sys
from PIL import Image, ImageDraw, ImageFilter


def hx(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mix(a, b, k):
    return tuple(int(a[i] + (b[i] - a[i]) * max(0.0, min(1.0, k))) for i in range(3))


W, BL = (255, 255, 255), (0, 0, 0)
TIERS = {
    "Nano": dict(body=hx("6a7a3e"), rim=hx("3c4526"), cable=hx("2a2c28"), plug=hx("c87533"), bit=hx("a4aab2"),
                 band=hx("6a7078"), tip=hx("c8ccd4"), glow=[hx("5cff6a")], chip="dip", out=hx("121410")),
    "Quantum": dict(body=hx("e6ebf1"), rim=hx("d9a834"), cable=hx("b08a2a"), plug=hx("3fd6ff"), bit=hx("e3c25a"),
                    band=hx("9c7420"), tip=hx("7ff0ff"), glow=[hx("3fd6ff")], chip="qubit", out=hx("2a2f36")),
    "Exo": dict(body=hx("342c44"), rim=hx("7040c0"), cable=hx("1a1524"), plug=hx("d84cf0"), bit=hx("4a3d68"),
                band=hx("1c1628"), tip=hx("ff7ae0"), glow=[hx("9a4cff"), hx("d84cf0"), hx("ff5ab8"), hx("5a7cff")],
                chip="cpu", out=hx("06040a")),
}
S = 16                      # canvas pixels per icon pixel
K, DV = 0.9, 1.3            # the whole drill a little smaller and shifted, so the cable loop fits the icon
CW = int(32 * S * 1.45)     # the horizontal canvas (turned 45 degrees later)


def rr(d, box, r, fill):
    d.rounded_rectangle(box, radius=r, fill=fill)


def paint(t, variant):
    p = TIERS[t]
    im = Image.new("RGBA", (CW, CW), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cy = CW // 2
    x = lambda u: int(CW / 2 + u * K * S)   # u in icon pixels along the drill, 0 at the centre
    y = lambda v: int(cy + (v * K + DV) * S)
    # the cable loop behind the body
    for k, col in ((0, mix(p["cable"], BL, 0.5)), (1, p["cable"]), (2, mix(p["cable"], W, 0.25))):
        d.arc([x(-18.5) + k * 6, y(-13) + k * 6, x(-8.5) - k * 6, y(-2.5) - k * 6], 110, 360, fill=col, width=int(1.5 * S) - k * 7)
    if variant == "B":                      # a glowing plug where the cable enters
        pass
    # the ring-shaped body (the grip): dark outline, the body, highlight, the hole
    body = [x(-16), y(-6.5), x(-2), y(6.5)]
    rr(d, body, 4 * S, mix(p["body"], BL, 0.45))
    rr(d, [body[0] + S, body[1] + S // 2, body[2] - S // 2, body[3] - S], 4 * S, p["body"])
    rr(d, [body[0] + 2 * S, body[1] + S, body[2] - 3 * S, body[1] + int(3.2 * S)], 2 * S, mix(p["body"], W, 0.45))
    rr(d, [body[0] + S, body[3] - int(2.6 * S), body[2] - S, body[3] - S], 2 * S, mix(p["body"], BL, 0.2))
    hole = [x(-13), y(-2.2), x(-6.5), y(3.2)]
    rr(d, hole, int(1.6 * S), (0, 0, 0, 0))
    rr(d, [hole[0] - S // 2, hole[1] - S // 2, hole[2] + S // 2, hole[3] + S // 2], int(2 * S), mix(p["rim"], BL, 0.1))
    rr(d, hole, int(1.6 * S), (0, 0, 0, 0))
    im.putalpha(im.getchannel("A"))
    # a rim / clip marking the tier (IC2's iridium drill has a red clip)
    rr(d, [x(-4.5), y(-6.8), x(-1.5), y(6.8)], S, p["rim"])
    rr(d, [x(-4.2), y(-6.4), x(-2.8), y(-2)], S // 2, mix(p["rim"], W, 0.45))
    # the banded cone
    L0, L1, w0 = -2, 15, 5.0
    d.polygon([(x(L0), y(-w0)), (x(L1), y(-1.0)), (x(L1), y(1.0)), (x(L0), y(w0))], fill=mix(p["bit"], BL, 0.35))
    d.polygon([(x(L0), y(-w0 + 0.8)), (x(L1), y(-0.8)), (x(L1), y(0.5)), (x(L0), y(w0 - 1.4))], fill=p["bit"])
    d.polygon([(x(L0), y(-w0 + 1.2)), (x(L1), y(-0.7)), (x(L1), y(-0.3)), (x(L0), y(-w0 + 2.4))], fill=mix(p["bit"], W, 0.55))
    for i in range(6):                       # the bands
        u = L0 + 1.5 + i * 2.6
        w = w0 + (1.0 - w0) * (u - L0) / (L1 - L0)
        d.polygon([(x(u), y(-w)), (x(u + 0.9), y(-w)), (x(u + 0.9), y(w)), (x(u), y(w))], fill=p["band"])
        d.line([(x(u + 0.9), y(-w)), (x(u + 0.9), y(w))], fill=mix(p["bit"], W, 0.35), width=S // 3)
    # the tip
    if variant == "B" and t == "Exo":        # a crystal tip
        d.polygon([(x(L1 - 1), y(-1.8)), (x(L1 + 4.2), y(0)), (x(L1 - 1), y(1.8))], fill=p["tip"])
        d.polygon([(x(L1 - 1), y(-1.8)), (x(L1 + 4.2), y(0)), (x(L1), y(-0.2))], fill=mix(p["tip"], W, 0.5))
    else:
        d.polygon([(x(L1), y(-1.1)), (x(L1 + 3.6), y(0)), (x(L1), y(1.1))], fill=p["tip"])
        d.polygon([(x(L1), y(-1.1)), (x(L1 + 3.6), y(0)), (x(L1 + 0.5), y(-0.2))], fill=mix(p["tip"], W, 0.5))
    # turn, crop, reduce
    im = im.rotate(45, resample=Image.BICUBIC)
    c0 = (CW - 32 * S) // 2
    im = im.crop((c0, c0, c0 + 32 * S, c0 + 32 * S)).resize((32, 32), Image.BOX)
    px = im.load()
    for yy in range(32):
        for xx in range(32):
            r, g, b, a = px[xx, yy]
            px[xx, yy] = (min(255, int(r * 255 / a)), min(255, int(g * 255 / a)), min(255, int(b * 255 / a)), 255) if a > 110 else (0, 0, 0, 0)
    # a dark outline on the outer edge only where it's shaded (bottom-right), a softer one elsewhere - as IC2
    solid = [[px[xx, yy][3] > 0 for yy in range(32)] for xx in range(32)]
    for yy in range(32):
        for xx in range(32):
            if solid[xx][yy]:
                continue
            nb = [(a, b) for a, b in ((1, 0), (-1, 0), (0, 1), (0, -1)) if 0 <= xx + a < 32 and 0 <= yy + b < 32 and solid[xx + a][yy + b]]
            if nb:
                a, b = nb[0]
                col = px[xx + a, yy + b][:3]
                px[xx, yy] = mix(col, p["out"], 0.85 if (a < 0 or b < 0) else 0.6) + (255,)
    if variant == "B":
        crisp(im, t, c0)
    return im


def to_px(u, v, c0):
    """Icon pixel of a point on the horizontal drawing (turned 45 degrees counter-clockwise)."""
    dx, dy = u * K * S, (v * K + DV) * S
    k = math.sqrt(0.5)
    xr, yr = dx * k + dy * k, -dx * k + dy * k
    return int((xr + CW / 2.0 - c0) / S), int((yr + CW / 2.0 - c0) / S)


def crisp(im, t, c0):
    """Variant B's details, pixel-sharp: the tier chip, a charge strip, a glowing cable plug."""
    p = TIERS[t]
    px = im.load()
    g = p["glow"]

    def put(x, y, col):
        if 0 <= x < 32 and 0 <= y < 32 and px[x, y][3]:
            px[x, y] = col + (255,)
    x0, y0 = to_px(-9.5, -3.8, c0)
    if p["chip"] == "dip":
        for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(x0 + a, y0 + b, hx("1c1e22"))
        for (a, b) in ((-1, 0), (2, 1), (0, -1), (1, 2)):
            put(x0 + a, y0 + b, hx("c8ccd4"))
        put(x0, y0, g[0])
    elif p["chip"] == "qubit":
        for (a, b) in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            put(x0 + a, y0 + b, hx("d9a834"))
        put(x0, y0, mix(g[0], W, 0.4))
    else:
        for (a, b) in ((0, 0), (1, 0), (0, 1), (1, 1)):
            put(x0 + a, y0 + b, hx("0c0912"))
        for (a, b) in ((-1, 0), (-1, 1), (2, 0), (2, 1), (0, -1), (1, -1), (0, 2), (1, 2)):
            put(x0 + a, y0 + b, hx("d9a834"))
        put(x0, y0, g[1])
        put(x0 + 1, y0 + 1, g[3])
    for i in range(5):                                        # the charge strip
        x, y = to_px(-14 + i * 2.2, 5.0, c0)
        put(x, y, g[i % len(g)])
    x, y = to_px(-10.5, -7.2, c0)                             # the cable plug
    for (a, b) in ((0, 0), (1, 0), (0, 1)):
        put(x + a, y + b, p["plug"])


def write(items_dir, preview):
    """The mod's three drill icons (variant B) and a preview sheet."""
    import os
    Z = 8
    sheet = Image.new("RGBA", (3 * (32 * Z + 16) + 16, 32 * Z + 32), (60, 60, 70, 255))
    for c, t in enumerate(("Nano", "Quantum", "Exo")):
        im = paint(t, "B")
        im.save(os.path.join(items_dir, "drill%s.png" % t))
        sheet.alpha_composite(im.resize((32 * Z, 32 * Z), Image.NEAREST), (16 + c * (32 * Z + 16), 16))
    sheet.save(preview)


if __name__ == "__main__" and len(sys.argv) > 2:
    write(sys.argv[1], sys.argv[2])
    print("ok")
    sys.exit(0)

if __name__ == "__main__":
    Z = 7
    sheet = Image.new("RGBA", (3 * (32 * Z + 16) + 16, 2 * (32 * Z + 26) + 30), (60, 60, 70, 255))
    dr = ImageDraw.Draw(sheet)
    for r, v in enumerate(("A", "B")):
        dr.text((16, 8 + r * (32 * Z + 26)), "Variant " + v + (": IC2 layout, our colours" if v == "A" else ": + chip, charge strip, glowing plug, tier tip"),
                fill=(255, 255, 255))
        for c, t in enumerate(("Nano", "Quantum", "Exo")):
            im = paint(t, v)
            sheet.alpha_composite(im.resize((32 * Z, 32 * Z), Image.NEAREST), (16 + c * (32 * Z + 16), 26 + r * (32 * Z + 26)))
    sheet.save(sys.argv[1])
    print("ok")
