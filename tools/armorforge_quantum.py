# -*- coding: utf-8 -*-
"""Quantum armour, the chosen design: "golden knight" + "cyan circuits", variant 4 (circuit-dominant, cyan T visor).
Writes quantum_layer_1/2 and the four Quantum icons.
Usage: armorforge_quantum.py <tools dir> <textures dir> <preview png>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import armorforge3 as af   # noqa: E402
import exo_v2 as e         # noqa: E402
import qv                  # noqa: E402
from PIL import Image      # noqa: E402

P, GOLD, GOLDHI, GOLDSH, CY, CYHI, mix = qv.P, qv.GOLD, qv.GOLDHI, qv.GOLDSH, qv.CY, qv.CYHI, qv.mix
VARIANT = 4

# o outline, h/b/s light/base/shade, y gold, g cyan line, V cyan visor
ICONS = {
    "Helmet": [
        "................", "................", "................", "...........ooooo", "........oooyyyyy", "......ooyyyyyyyy",
        ".....ohhhhhhhhhh", "....ohhbbbbbbbbb", "....ohbyyyyyyyyy", "...ohbyooooooooo", "...ohbyoVVVVVVVV", "..ohbbyyyyyyyoVV",
        "..ohbbbbbbbbyoVV", "..ohbbbbbbbbyoVV", "..ohbbbbbbbbyoVV", "..ohbbbbbbbbyooo", "..obbbbbbbbbyyyy", "..obgbbbbbbbbbbb",
        "..osgbbbbbssssss", "...osbbbbsoooooo", "....ooooo......."],
    "Chestplate": [
        "................", "................", "................", ".ooooo..........", "oyyyyyoooooooooo", "oyyyyyhhhhhhhhhh",
        "oyyyyybbbbbbbbbb", "osssssbbbbbbbbbb", "oobbbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..obbbbbbbbbbbbb",
        "..obbbbbbbbbbbbb", "..oyyyyyyyyyyyyy", "..osssssssssssss", "..oooooooooooooo"],
    "Leggings": [
        "................", "................", "................", "....oooooooooooo", "....ohhhhhhhhhhh", "....ohbbbbbbbbbb",
        "....oyyyyyyyyyyy", "....ohbbbbbboooo", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....oyyyyyyyo...",
        "....oyyyyyyyo...", "....oyyyyyyyo...", "....ossssssso...", "....ohbbbbbbo...", "....ohbbbbbbo...", "....ohbbbbbbo...",
        "....ohbbbbbbo...", "....ohbbbbbbo...", "....ossssssso...", "....ooooooooo..."],
    "Boots": ["................"] * 11 + [
        "....oooooo......", "....oyyyyo......", "....ohbbbo......", "....ohbbbo......", "....ohbbbboo....", "...ohhbbbbbbo...",
        "...ohbbbbbbbbo..", "...oyyyyyyyyyo..", "...oggggggggggo."[:16], "...ooooooooooo.."],
}


def icon(piece):
    rows = af.mirror([r.ljust(16, ".")[:16] for r in ICONS[piece]])
    im = qv.S(32, 32)
    vtop = min([j for j, r in enumerate(rows) if "V" in r] or [-1])
    for y, row in enumerate(rows[:32]):
        for x, ch in enumerate(row):
            col = {"o": P["out"], "h": P["hi"], "b": P["base"], "s": P["sh"], "y": GOLD, "g": CY,
                   "V": CYHI if y == vtop else CY}.get(ch)
            if col:
                im.p(x, y, col)
    for y in range(32):                                            # gold: a light top edge, a dark bottom edge
        for x in range(32):
            if im.g(x, y) == GOLD:
                above, below = im.g(x, y - 1), im.g(x, y + 1)
                if above not in (GOLD, GOLDHI, GOLDSH) and below == GOLD and im.g(x, y + 2) == GOLD:
                    im.p(x, y, GOLDHI)
                elif above in (GOLD, GOLDHI) and below not in (GOLD, GOLDHI, GOLDSH):
                    im.p(x, y, GOLDSH)
    for y in range(32):                                            # soft highlight inside the top-left edges
        for x in range(32):
            if im.g(x, y) == P["base"] and im.g(x - 1, y) == P["hi"] and im.g(x, y - 1) in (P["hi"], P["out"]):
                im.p(x, y, mix(P["base"], P["hi"], 0.5))
    if piece == "Chestplate":
        im.circuit([(10, 12), (5, 12), (5, 17)])
        im.circuit([(21, 12), (26, 12), (26, 17)])
        im.circuit([(12, 8), (8, 8)])
        im.circuit([(19, 8), (23, 8)])
        im.circuit([(14, 17), (14, 19)], pad=False)
        im.circuit([(17, 17), (17, 19)], pad=False)
        im.qubit(16, 12.5, 4.6)
    elif piece == "Leggings":
        for x0 in (7, 24):
            im.circuit([(x0, 7), (x0, 10)])
            im.circuit([(x0, 15), (x0, 19)])
        im.hline(15, 17, 5, CY)
    elif piece == "Boots":
        im.p(7, 13, CY)
        im.p(24, 13, CY)
    elif piece == "Helmet":
        im.p(14, 10, (255, 255, 255, 255))
    return im.im


if __name__ == "__main__":
    tex, out = sys.argv[2], sys.argv[3]
    l1, l2 = qv.layers(VARIANT)
    l1.save(os.path.join(tex, "models", "armor", "quantum_layer_1.png"))
    l2.save(os.path.join(tex, "models", "armor", "quantum_layer_2.png"))
    Z = 4
    sheet = Image.new("RGBA", (4 * 32 * Z + 2 * 32 * Z + 16 * Z + 120, 64 * Z + 20), (60, 60, 70, 255))
    for i, piece in enumerate(("Helmet", "Chestplate", "Leggings", "Boots")):
        im = icon(piece)
        im.save(os.path.join(tex, "items", "armorQuantum%s.png" % piece))
        sheet.alpha_composite(im.resize((32 * Z, 32 * Z), Image.NEAREST), (10 + i * 32 * Z, 10))
    sheet.alpha_composite(af.front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (4 * 32 * Z + 40, 10))
    sheet.alpha_composite(af.side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (5 * 32 * Z + 70, 10))
    sheet.alpha_composite(e.side_view(l1, l2).resize((16 * Z, 64 * Z), Image.NEAREST), (6 * 32 * Z + 100, 10))
    sheet.save(out)
    print("ok")
