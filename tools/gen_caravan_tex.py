# -*- coding: utf-8 -*-
"""Подложка (Si-14), the caravan master's pack of phase 5 (Silicon Age, docs/plan-substrate-tech-f5.md §8.2, package K).

Writes into src/main/resources/assets/siliconage/textures/entity:
  caravanMaster.png - the frame and the crates the caravan master carries over his Tuner's overall (RenderCaravanMasterSC.ModelPackSC;
                      the master himself is drawn with the Cartographer's skin, tuner_cartographer.png): two steel rails with rivets and
                      two cross bars; the big low crate - dark slate-teal panels on a lighter frame, the Tuners' turquoise stencil
                      stripe and a yellow-black hazard tag at the back; the crate over it - oxidised copper with dark bands and a
                      turquoise seal. The caravan's lamp on the left rail is the renderer's (full-bright, untextured).
Layout: a 64 x 32 model layout drawn at 128 x 64 like the mod's other skins; the box sizes and texture offsets here must match
ModelPackSC. A preview sheet goes next to this script.

Usage: python gen_caravan_tex.py [project dir]
"""
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'entity')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'caravan_tex_preview.png')
CLEAR = (0, 0, 0, 0)

# the model's UV pixels drawn 2 x 2, a 64 x 32 layout
K = 2
W, H = 64, 32

# the frame
STEEL = (120, 140, 160)
STEEL_D = (84, 100, 118)
STEEL_L = (170, 186, 204)
RIVET = (196, 206, 218)
# the low crate
SLATE = (54, 74, 78)
SLATE_D = (36, 50, 54)
SLATE_L = (88, 112, 116)
EDGE = (124, 140, 138)
FIELD = (60, 210, 200)
FIELD_D = (30, 120, 116)
HAZARD = (232, 196, 52)
HAZARD_D = (34, 30, 26)
# the high crate
COPPER = (176, 104, 60)
COPPER_D = (122, 70, 40)
COPPER_L = (206, 140, 88)
PATINA = (92, 162, 140)
BAND = (52, 48, 46)


def clamp(v):
    return max(0, min(255, int(round(v))))


def px(im, x, y, c):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], 255))


def faces(u, v, w, h, d):
    """A box's faces at texture offset (u, v), size w x h x d (UV pixels): name -> (u0, v0, width, height)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


def paint(im, face, fn):
    """fn(x, y, w, h) -> colour or None for each REAL pixel of a face (x, y within it, w, h its real size)."""
    u, v, w, h = face
    for y in range(h * K):
        for x in range(w * K):
            px(im, u * K + x, v * K + y, fn(x, y, w * K, h * K))


def paint_box(im, box, fn):
    for name, f in box.items():
        paint(im, f, fn(name))


# RenderCaravanMasterSC.ModelPackSC
RAIL = faces(0, 0, 1, 13, 1)
BAR = faces(0, 16, 7, 1, 1)
CRATE_LOW = faces(8, 0, 8, 5, 4)
CRATE_HIGH = faces(32, 0, 7, 5, 4)


def steel(name):
    def fn(x, y, w, h):
        if name in ('top', 'bottom'):
            return STEEL_L
        if x == 0:
            return STEEL_L
        if x == w - 1:
            return STEEL_D
        if h > 6 and y % 8 == 3:
            return RIVET                                     # a rivet every 4 UV pixels down the rail
        return STEEL if (x + y) % 9 else STEEL_D
    return fn


def low_crate(name):
    def fn(x, y, w, h):
        rim = x == 0 or y == 0 or x == w - 1 or y == h - 1
        if rim:
            return EDGE if x == 0 or y == 0 else SLATE_D
        if name in ('top', 'bottom'):
            return SLATE_L if (x // 2 + y // 2) % 2 else SLATE
        if name in ('front', 'back') and 3 <= y <= 4:
            return FIELD if name == 'back' else FIELD_D       # the Tuners' stencil stripe
        if name == 'back' and 6 <= y <= 8 and w - 6 <= x <= w - 2:
            return HAZARD if (x + y) % 3 else HAZARD_D       # the hazard tag
        if x % 6 == 3:
            return SLATE_D                                   # the planks' joints
        return SLATE if (x * 3 + y * 5) % 17 else SLATE_L
    return fn


def high_crate(name):
    def fn(x, y, w, h):
        rim = x == 0 or y == 0 or x == w - 1 or y == h - 1
        if rim:
            return COPPER_L if x == 0 or y == 0 else COPPER_D
        if name not in ('top', 'bottom') and y in (2, h - 3):
            return BAND                                      # two dark bands round it
        if name == 'back' and w // 2 - 1 <= x <= w // 2 and h // 2 - 1 <= y <= h // 2:
            return FIELD                                     # the turquoise seal
        if (x * 7 + y * 11) % 23 == 0:
            return PATINA
        return COPPER if (x + 2 * y) % 7 else COPPER_D
    return fn


def pack():
    im = Image.new('RGBA', (W * K, H * K), CLEAR)
    paint_box(im, RAIL, steel)
    paint_box(im, BAR, steel)
    paint_box(im, CRATE_LOW, low_crate)
    paint_box(im, CRATE_HIGH, high_crate)
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    im = pack()
    im.save(os.path.join(TEX, 'caravanMaster.png'))
    sheet = Image.new('RGBA', (W * K * 4, H * K * 4), (40, 40, 44, 255))
    sheet.paste(im.resize((W * K * 4, H * K * 4), Image.NEAREST), (0, 0), im.resize((W * K * 4, H * K * 4), Image.NEAREST))
    sheet.save(PREVIEW)
    print('caravanMaster.png ->', TEX)


if __name__ == '__main__':
    main()
