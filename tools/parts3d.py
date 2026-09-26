# -*- coding: utf-8 -*-
"""The 3D add-on parts of the worn armour (ModelArmorGlowSC's extra boxes): their texture nets, painted into
free corners of the armour layers (the hat area of layer 1, the head area of layer 2, 56..64 x 16..32 of layer 1).
Box positions / sizes here and in ModelArmorGlowSC must match (texture units, the layer is drawn at 2x)."""
import armorforge3 as af

GOLD, GOLDHI, GOLDSH = af.hx("d9a834"), af.hx("ffe08a"), af.hx("9c7420")

# name: (layer, u, v, w, h, d) in texture units
PARTS = {
    "Nano": {"pack": (1, 32, 0, 6, 7, 2), "pad": (1, 32, 9, 5, 2, 5), "lamp": (1, 48, 0, 1, 2, 3), "knee": (2, 0, 0, 4, 3, 1)},
    "Quantum": {"pauldron": (1, 32, 0, 6, 3, 7), "spine": (1, 58, 0, 2, 9, 1), "ear": (1, 32, 10, 1, 3, 3),
                "knee": (2, 0, 0, 4, 3, 1)},
    "Exo": {"orb": (1, 32, 0, 3, 3, 3), "ring": (1, 32, 8, 6, 1, 6), "fin": (1, 44, 0, 1, 2, 6), "radiator": (1, 56, 16, 3, 6, 1),
            "knee": (2, 0, 0, 4, 3, 1)},
}


def faces(u, v, w, h, d):
    """Pixel rectangles (x, y, w, h) of a box's six faces, vanilla net layout, at 2x."""
    k = 2
    return {"top": ((u + d) * k, v * k, w * k, d * k), "bottom": ((u + d + w) * k, v * k, w * k, d * k),
            "right": (u * k, (v + d) * k, d * k, h * k), "front": ((u + d) * k, (v + d) * k, w * k, h * k),
            "left": ((u + d + w) * k, (v + d) * k, d * k, h * k), "back": ((u + d + w + d) * k, (v + d) * k, w * k, h * k)}


def paint(suit, l1, l2, p):
    for name, (layer, u, v, w, h, d) in PARTS[suit].items():
        s = l1 if layer == 1 else l2
        for face, (x, y, fw, fh) in faces(u, v, w, h, d).items():
            globals()["_" + suit.lower()](s, name, face, x, y, fw, fh, p)


def _gold(s, x, y, w, h):
    s.rect(x, y, x + w, y + h, GOLD)
    s.hline(x, x + w, y, GOLDHI)
    s.hline(x, x + w, y + h - 1, GOLDSH)


def _nano(s, name, face, x, y, w, h, p):
    if name in ("pack", "pad", "knee"):
        s.rect(x, y, x + w, y + h, p["acc"])
        s.hline(x, x + w, y, p["acchi"])
        s.hline(x, x + w, y + h - 1, p["accsh"])
        if name == "pack" and face == "back":              # the outward face: a battery gauge
            s.rect(x + 2, y + 2, x + w - 2, y + h - 2, p["out"])
            for j in range(y + 4, y + h - 3, 3):
                s.hline(x + 3, x + w - 3, j, p["glow"])
        elif name == "pad" and face == "top":
            s.p(x + 2, y + 2, p["via"])
            s.p(x + w - 3, y + 2, p["via"])
        elif name == "knee" and face == "front":
            s.p(x + w // 2, y + h // 2, p["via"])
    else:                                                  # the helmet lamp
        s.rect(x, y, x + w, y + h, p["sh"])
        if face in ("front", "right", "left"):
            s.rect(x, y + 1, x + w, y + h - 1, p["glow"])


def _quantum(s, name, face, x, y, w, h, p):
    cy, cyhi = p["glow"], p["glowhi"]
    if name in ("pauldron", "knee"):
        _gold(s, x, y, w, h)
        if name == "pauldron" and face == "top":
            s.hline(x + 2, x + w - 2, y + h // 2, cy)
            s.p(x + 1, y + h // 2, GOLDHI)
            s.p(x + w - 2, y + h // 2, GOLDHI)
        elif name == "knee" and face == "front":
            s.rect(x + w // 2 - 1, y + 2, x + w // 2 + 1, y + h - 2, cy)
    elif name == "spine":
        s.plate(x, y, x + w, y + h, p, soft=True)
        if face == "back":
            s.vline(x + w // 2 - 1, y + 1, y + h - 1, cy)
            s.vline(x + w // 2, y + 1, y + h - 1, cyhi)
            s.p(x + w // 2 - 1, y + 1, GOLD)
            s.p(x + w // 2, y + h - 2, GOLD)
    else:                                                  # the ear discs
        _gold(s, x, y, w, h)
        if face in ("right", "left"):
            s.rect(x + 1, y + 1, x + w - 1, y + h - 1, cy)
            s.p(x + w // 2, y + h // 2, cyhi)


def _exo(s, name, face, x, y, w, h, p):
    g1, g2 = p["glow"], p["glowhi"]                        # iridescence markers (exo_v2.finish turns them)
    if name == "orb":
        s.rect(x, y, x + w, y + h, p["out"])
        s.rect(x + 1, y + 1, x + w - 1, y + h - 1, g1)
        s.rect(x + 2, y + 2, x + w - 2, y + h - 2, g2)
    elif name == "ring":
        s.rect(x, y, x + w, y + h, g1)
        if face in ("top", "bottom"):
            s.rect(x + 2, y + 2, x + w - 2, y + h - 2, p["out"])
        else:
            s.hline(x, x + w, y, g2)
    elif name == "fin":
        s.rect(x, y, x + w, y + h, p["base"])
        s.hline(x, x + w, y, p["hi2"])
        if face in ("right", "left"):
            s.hline(x, x + w, y, g2)
            s.hline(x, x + w, y + h - 1, p["out"])
    elif name == "radiator":
        for j in range(h):
            s.hline(x, x + w, y + j, p["hi"] if j % 2 == 0 else p["sh2"])
        s.hline(x, x + w, y, g1)
    else:                                                  # the knee
        s.rect(x, y, x + w, y + h, p["base"])
        s.line(x, y + 1, x + w - 1, y + h - 2, g1)
        s.hline(x, x + w, y, p["hi2"])
