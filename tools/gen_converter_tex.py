# Textures of the Energy Converter and its modules (32 x 32, the mod's style: a bevelled casing,
# chip-like modules with a coloured top bar and pins). Run from the project root:
#   python tools/gen_converter_tex.py [preview.png]
# Writes src/main/resources/assets/siliconage/textures/blocks/energyConverter*.png and
# .../items/converter*.png; with an argument, also a x8 preview sheet of all of them.
import os
import sys

from PIL import Image

ROOT = os.path.join('src', 'main', 'resources', 'assets', 'siliconage', 'textures')
BLOCKS = os.path.join(ROOT, 'blocks')
ITEMS = os.path.join(ROOT, 'items')

# the casing: a cool steel-violet, the frame lighter at the top-left, darker at the bottom-right
C_OUT = (26, 28, 34)
C_HI = (150, 156, 176)
C_MID = (112, 116, 138)
C_LO = (74, 76, 96)
C_PANEL = (44, 47, 58)
C_PANEL_HI = (60, 64, 78)
C_DARK = (18, 20, 26)
EU = (232, 196, 64)
EU_LO = (150, 116, 30)
PAIRS = {'': (110, 114, 124), 'RF': (226, 64, 64), 'J': (74, 144, 232), 'GJ': (70, 212, 110)}


def lo(c, k=0.6):
    return tuple(int(v * k) for v in c[:3])


def hi(c, d=60):
    return tuple(min(255, v + d) for v in c[:3])


def px(im, x, y, c):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((x, y), tuple(c[:3]) + (c[3] if len(c) > 3 else 255,))


def rect(im, x, y, w, h, c):
    for j in range(y, y + h):
        for i in range(x, x + w):
            px(im, i, j, c)


def casing():
    im = Image.new('RGBA', (32, 32), C_OUT + (255,))
    rect(im, 1, 1, 30, 30, C_MID)
    rect(im, 1, 1, 30, 2, C_HI)
    rect(im, 1, 1, 2, 30, C_HI)
    rect(im, 1, 29, 30, 2, C_LO)
    rect(im, 29, 1, 2, 30, C_LO)
    # the inner bevel
    rect(im, 5, 5, 22, 22, C_LO)
    rect(im, 6, 6, 21, 21, C_HI)
    rect(im, 6, 6, 20, 20, C_PANEL)
    for (x, y) in ((3, 3), (27, 3), (3, 27), (27, 27)):          # rivets
        px(im, x, y, hi(C_HI, 50))
        px(im, x + 1, y + 1, C_LO)
    return im


def window(im, x, y, w, h, col, level):
    """A gauge window: a dark well, filled to `level` (0..1) in `col`, a light line on top."""
    rect(im, x - 1, y - 1, w + 2, h + 2, C_DARK)
    rect(im, x, y, w, h, (10, 12, 16))
    fill = int(round(h * level))
    for j in range(fill):
        yy = y + h - 1 - j
        k = 0.55 + 0.45 * (j / max(1, h - 1))
        rect(im, x, yy, w, 1, lo(col, k))
    if fill > 0:
        rect(im, x, y + h - fill, w, 1, hi(col, 90))
    rect(im, x + w - 1, y, 1, h, (0, 0, 0))                      # the glass edge
    px(im, x, y, (70, 74, 86))


def front(pair):
    im = casing()
    col = PAIRS[pair]
    window(im, 8, 9, 6, 15, EU, 0.62)
    window(im, 18, 9, 6, 15, col, 0.4 if pair else 0.0)
    # the arrows between the windows: right (yellow) over left (the pair's colour)
    for i in range(3):
        px(im, 15 + i, 11 + i, EU)
        px(im, 15 + i, 15 - i, EU)
        px(im, 17 - i, 18 + i, col)
        px(im, 17 - i, 22 - i, col)
    # the labels under the windows: a yellow dot, the pair's dot
    rect(im, 10, 25, 2, 1, EU)
    rect(im, 20, 25, 2, 1, col)
    return im


def side():
    im = casing()
    # two terminals: a yellow one and a neutral one, cables between
    rect(im, 9, 10, 14, 12, C_PANEL_HI)
    for j in range(11, 21, 3):
        rect(im, 10, j, 12, 1, C_DARK)
    rect(im, 8, 14, 3, 4, EU_LO)
    rect(im, 8, 14, 3, 1, EU)
    rect(im, 21, 14, 3, 4, (90, 94, 104))
    rect(im, 21, 14, 3, 1, (150, 154, 166))
    return im


def top():
    im = casing()
    for j in range(9, 24, 3):                                    # a vent grille
        rect(im, 9, j, 14, 2, C_DARK)
        rect(im, 9, j, 14, 1, (8, 9, 12))
    rect(im, 14, 8, 4, 17, C_PANEL)
    rect(im, 15, 13, 2, 7, EU_LO)
    return im


def chip(bar, body=(54, 58, 70), pins=(178, 142, 60)):
    """The module look of the mod: a dark chip with a coloured top bar and pins under it."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    rect(im, 4, 5, 24, 21, C_OUT)
    rect(im, 5, 6, 22, 19, body)
    rect(im, 5, 6, 22, 3, bar)
    rect(im, 5, 6, 22, 1, hi(bar, 60))
    rect(im, 5, 24, 22, 1, lo(body, 0.7))
    for i in range(5):
        x = 7 + i * 4
        rect(im, x, 26, 2, 3, pins)
        px(im, x, 28, lo(pins, 0.6))
        px(im, x + 1, 28, lo(pins, 0.6))
    return im


def amplifier():
    im = chip((240, 150, 40))
    c = (255, 176, 64)
    for k in (0, 6):                                              # a double chevron: x2
        for i in range(5):
            px(im, 9 + k + i, 11 + i, c)
            px(im, 10 + k + i, 11 + i, lo(c, 0.75))
            px(im, 9 + k + i, 19 - i, c)
            px(im, 10 + k + i, 19 - i, lo(c, 0.75))
    rect(im, 22, 13, 2, 5, (230, 230, 236))                      # a little "2"
    return im


def efficiency():
    im = chip((70, 200, 100))
    c = (110, 236, 140)
    # a % sign
    rect(im, 9, 11, 3, 3, c)
    rect(im, 19, 18, 3, 3, c)
    for i in range(10):
        px(im, 20 - i, 11 + i, c)
        px(im, 21 - i, 11 + i, lo(c, 0.7))
    # a down arrow (less loss)
    rect(im, 23, 11, 1, 7, (230, 230, 236))
    px(im, 22, 16, (230, 230, 236))
    px(im, 24, 16, (230, 230, 236))
    return im


def card(colour, emblem):
    """An energy card: a coloured card with a contact strip and an emblem."""
    im = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    rect(im, 3, 7, 26, 19, C_OUT)
    rect(im, 4, 8, 24, 17, lo(colour, 0.8))
    rect(im, 4, 8, 24, 1, hi(colour, 50))
    rect(im, 4, 24, 24, 1, lo(colour, 0.5))
    rect(im, 6, 12, 6, 5, (208, 176, 70))                        # the contact
    rect(im, 7, 13, 4, 1, (150, 120, 40))
    rect(im, 7, 15, 4, 1, (150, 120, 40))
    emblem(im)
    rect(im, 6, 20, 12, 1, lo(colour, 0.55))                     # a label line
    return im


def mek_emblem(im):
    c = (236, 240, 248)
    # an "M" made of strokes
    for y in range(11, 19):
        px(im, 15, y, c)
        px(im, 23, y, c)
    for i in range(4):
        px(im, 16 + i, 12 + i, c)
        px(im, 22 - i, 12 + i, c)


def gc_emblem(im):
    c = (236, 240, 248)
    # a planet with a ring
    for (x, y) in ((18, 12), (19, 12), (20, 12), (17, 13), (21, 13), (17, 14), (21, 14), (17, 15), (21, 15), (18, 16), (19, 16), (20, 16)):
        px(im, x, y, c)
    rect(im, 18, 13, 3, 3, (120, 200, 255))
    for i in range(9):
        px(im, 15 + i, 16 - i // 3, (255, 210, 90))


def main():
    out = {}
    out[os.path.join(BLOCKS, 'energyConverterSide.png')] = side()
    out[os.path.join(BLOCKS, 'energyConverterTop.png')] = top()
    for p in PAIRS:
        out[os.path.join(BLOCKS, 'energyConverterFront%s.png' % p)] = front(p)
    out[os.path.join(ITEMS, 'converterAmplifier.png')] = amplifier()
    out[os.path.join(ITEMS, 'converterEfficiency.png')] = efficiency()
    out[os.path.join(ITEMS, 'converterCardMekanism.png')] = card((60, 120, 210), mek_emblem)
    out[os.path.join(ITEMS, 'converterCardGalacticraft.png')] = card((50, 160, 90), gc_emblem)
    for path, im in out.items():
        im.save(path)
        print('wrote', path)
    if len(sys.argv) > 1:
        sheet = Image.new('RGBA', (len(out) * 264, 256), (40, 40, 40, 255))
        for i, im in enumerate(out.values()):
            big = im.resize((256, 256), Image.NEAREST)
            sheet.paste(big, (i * 264, 0), big)
        sheet.save(sys.argv[1])


if __name__ == '__main__':
    main()
