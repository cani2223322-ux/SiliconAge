# -*- coding: utf-8 -*-
"""Exo armour, the chosen design: processor chip + violet->magenta->blue iridescence, detail set "v (c)".
Writes exo_layer_1/2 and the four Exo icons.
Usage: armorforge_exo.py <tools dir> <textures dir> <preview png>"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import armorforge3 as af   # noqa: E402
import exo_v2 as e         # noqa: E402
import exo_v3 as c         # noqa: E402
from PIL import Image      # noqa: E402

P, G1, G2, GOLD, GOLDHI, hx, mix = e.P, e.G1, e.G2, e.GOLD, e.GOLDHI, e.hx, e.mix

# ---------------------------------------------------------------- icons (16-wide half masks, mirrored)
# o outline, h/b/s light/base/shade, y gold, g/G glow / glow highlight, V/W visor glow / its bright line
ICONS = {
    "Helmet": [
        "................", "................", "..........o.o.o.", ".........oGoGoGo", "........oobobobo", "......oohhbbbbbb",
        ".....ohhbbbbbbbb", "....ohbbbbbbbbbb", "....ohbybybybyby", "...ohbbbbbbbbbbb", "...ohooooooooooo", "...ohoVVVVVVVVVV",
        "..ohboWWWWWWWWWW", "..ohboVVVVVVVVVV", "..ohbooooooooooo", "..ohbbbbbbbbbbbb", "..obbbbbbbbbbbbb", "..osbbbbbsssssss",
        "...osbbssooooooo", "....ossoo......."],
    "Chestplate": af.ICONS[("Exo", "Chestplate")],
    "Leggings": af.ICONS[("Exo", "Leggings")],
    "Boots": ["................"] * 11 + [
        "....oooooo......", "....ohbbbo......", "....ohbbbo......", "....ohbbbo......", "....ohbbbboo....", "...ohbbbbbbbo...",
        "...ohbbbbbbbbo..", "...ossssssssso..", "...oWWWWWWWWWo..", "....VVVVVVVVV...", "...ooooooooooo.."],
}


def icon(piece):
    rows = af.mirror([r.ljust(16, ".")[:16] for r in ICONS[piece]])
    im = e.S(32, 32)
    for y, row in enumerate(rows[:32]):
        for x, ch in enumerate(row):
            col = {"o": P["out"], "h": P["hi"], "b": P["base"], "s": P["sh"], "y": GOLD,
                   "g": G1, "G": G2, "V": G1, "W": G2, "c": P["core"]}.get(ch)
            if col:
                im.p(x, y, col)
    for y in range(32):                                            # soft highlight inside the top-left edges
        for x in range(32):
            if im.g(x, y) == P["base"] and im.g(x - 1, y) == P["hi"] and im.g(x, y - 1) in (P["hi"], P["out"]):
                im.p(x, y, mix(P["base"], P["hi"], 0.5))
    if piece == "Chestplate":
        for (ox, oy) in ((5, 7), (26, 7)):                          # glowing orbs on the shoulders
            im.disc(ox + 0.5, oy + 0.5, 2.6, P["out"])
            im.ring(ox + 0.5, oy + 0.5, 1.0, 2.0, G1)
            im.p(ox, oy, G2)
        for (dx, dy) in ((-7, -2), (7, -2), (-7, 5), (7, 5), (-4, 8), (4, 8)):   # the wafer ring
            im.p(16 + dx, 13 + dy, G1)
        im.chip(16, 12)
        im.vline(15, 19, 21, G1)
        im.vline(16, 19, 21, G2)
    elif piece == "Leggings":
        for px0 in (6, 25):                                         # heat pipes and a finned knee
            c.pipe(im, px0 + 1, 7, 11)
            for j in (13, 15):
                im.hline(px0 - 1, px0 + 3, j, P["hi2"])
            im.vline(px0 + 1, 16, 20, G1)
    elif piece == "Boots":
        im.p(6, 13, G1)
        im.p(25, 13, G1)
    finish_icon(im)
    return im.im


def finish_icon(s):
    """Palette B on an icon: a violet-grey to black tint down the plates, iridescent glow."""
    for y in range(32):
        tint = mix(hx("4a3a70"), hx("120e1a"), y / 31.0)
        for x in range(32):
            col = s.px[x, y]
            if col[3] == 0:
                continue
            if col == G1 or col == G2:
                g = e.irid(x * 0.03 + y * 0.045)
                s.px[x, y] = mix(g, (255, 255, 255, 255), 0.55) if col == G2 else g
            elif col[:3] in (P["base"][:3], P["hi"][:3], P["sh"][:3], P["hi2"][:3]):
                s.px[x, y] = mix(col, tint, 0.4)


if __name__ == "__main__":
    tex, out = sys.argv[2], sys.argv[3]
    l1, l2 = c.layers("c")
    l1.save(os.path.join(tex, "models", "armor", "exo_layer_1.png"))
    l2.save(os.path.join(tex, "models", "armor", "exo_layer_2.png"))
    Z = 4
    sheet = Image.new("RGBA", (4 * 32 * Z + 2 * 32 * Z + 16 * Z + 120, 64 * Z + 20), (60, 60, 70, 255))
    for i, piece in enumerate(("Helmet", "Chestplate", "Leggings", "Boots")):
        im = icon(piece)
        im.save(os.path.join(tex, "items", "armorExo%s.png" % piece))
        sheet.alpha_composite(im.resize((32 * Z, 32 * Z), Image.NEAREST), (10 + i * 32 * Z, 10))
    sheet.alpha_composite(af.front_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (4 * 32 * Z + 40, 10))
    sheet.alpha_composite(af.side_view(l1, l2).resize((32 * Z, 64 * Z), Image.NEAREST), (5 * 32 * Z + 70, 10))
    sheet.alpha_composite(e.side_view(l1, l2).resize((16 * Z, 64 * Z), Image.NEAREST), (6 * 32 * Z + 100, 10))
    sheet.save(out)
    print("ok")
