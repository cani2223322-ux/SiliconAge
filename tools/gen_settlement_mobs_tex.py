# -*- coding: utf-8 -*-
"""Подложка (Si-14), the settlements' creatures of phase 2 (Silicon Age, docs/plan-substrate-tech-f2.md §6, package N).

Writes into src/main/resources/assets/siliconage/textures/entity:
  tuner_<kind>.png  - «Наладчик», one per profession (elder, technologist, metallurgist, gasTech, cartographer): a thin
                      technician in a faded turquoise overall, a dark band across the eyes where the two diode eyes sit
                      (the renderer draws them full-bright in the profession's colour), the respirator box, a stripe
                      of the profession's colour on the sleeves, dark gloves and boots, the battery pack on the back;
                      the profession's own parts - the Elder's mantle and cloak in the field's turquoise, the
                      Technologist's brass loupe, the Metallurgist's scorched leather apron, the Gas Technician's two
                      banded cylinders, the Cartographer's antenna;
  warden.png        - «Страж купола»: the dark slate undersuit under the mod's armour, the face under the helmet, and
                      the armband in the dome's turquoise with a white stripe (it shows over the armour's sleeve).
Layout: ModelBiped's 64 x 64 (RenderTunerSC.ModelTunerSC / RenderWardenSC.ModelWardenSC) drawn at 128 x 128 like
the mod's other skins - vanilla's boxes in the upper half (the hat area left clear), the extra parts below; the box
sizes and texture offsets here must match those models. A preview sheet goes next to this script.

Usage: python gen_settlement_mobs_tex.py [project dir]
"""
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'entity')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'settlement_mobs_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the biped's UV pixels drawn 2 x 2, a 64 x 64 layout
K = 2
W, H = 64, 64

# the overall
SUIT = (104, 158, 152)
SUIT_D = (74, 118, 114)
SUIT_L = (138, 190, 182)
SEAM = (60, 96, 94)
WORN = (124, 168, 160)
SKIN = (196, 172, 152)
SKIN_D = (160, 136, 120)
BAND = (30, 34, 38)
BAND_L = (54, 60, 66)
GLOVE = (58, 62, 66)
BOOT = (46, 44, 42)
BELT = (40, 40, 44)
BUCKLE = (170, 176, 186)
# the pack, the respirator
PACK = (70, 76, 82)
PACK_L = (104, 112, 120)
PACK_D = (44, 48, 54)
CABLE = (196, 112, 58)
RUBBER = (44, 48, 52)
GRILLE = (96, 102, 108)
# the professions
FIELD = (60, 210, 200)
FIELD_M = (48, 170, 164)
FIELD_D = (30, 120, 116)
TRIM = (190, 250, 244)
LEATHER = (120, 78, 46)
LEATHER_D = (88, 56, 32)
SCORCH = (58, 40, 26)
STEEL = (120, 140, 160)
STEEL_D = (84, 100, 118)
STEEL_L = (170, 186, 204)
HELIUM = (230, 200, 60)
DEUTERIUM = (200, 70, 60)
ROD = (60, 64, 70)
ROD_L = (110, 116, 124)
BRASS = (196, 156, 72)
BRASS_D = (140, 104, 44)
LENS = (150, 210, 230)
# the guard
UNDER = (52, 60, 68)
UNDER_L = (78, 88, 98)
UNDER_D = (36, 42, 48)
ARMBAND = (60, 210, 200)
ARMBAND_D = (24, 96, 92)
ARMBAND_S = (220, 255, 250)

# by profession: the diodes' colour (RenderTunerSC.DIODES), the hair
KINDS = {
    'elder': ((64, 224, 255), (206, 206, 200)),
    'technologist': ((74, 124, 255), (52, 44, 40)),
    'metallurgist': ((255, 154, 48), (110, 62, 40)),
    'gasTech': ((176, 96, 255), (34, 32, 36)),
    'cartographer': ((255, 224, 64), (150, 120, 80)),
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def px(im, x, y, c):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], 255))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def faces(u, v, w, h, d):
    """A box's faces at texture offset (u, v), size w x h x d (UV pixels): name -> (u0, v0, width, height)."""
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),          # -X: the mob's own right side
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


def origin(face):
    return face[0] * K, face[1] * K


# vanilla's biped
HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)       # the left arm is the same texture, mirrored
LEG = faces(0, 16, 4, 12, 4)
# the Tuner's parts (RenderTunerSC.ModelTunerSC)
PACKBOX = faces(0, 32, 6, 8, 3)
STALK = faces(20, 32, 1, 3, 1)
MASK = faces(24, 32, 5, 3, 1)
COLLAR = faces(0, 44, 9, 2, 5)
CLOAK = faces(36, 32, 8, 14, 1)
APRON = faces(0, 52, 7, 10, 1)
CYLINDER = faces(16, 52, 3, 9, 3)
ANTENNA = faces(28, 52, 1, 9, 1)
LOUPE = faces(40, 52, 3, 3, 2)
# the guard's armband (RenderWardenSC.ModelWardenSC)
ARMBANDBOX = faces(0, 32, 4, 2, 4)


def cloth(base, dark, light, seam):
    """Worn fabric: a lit top / left edge, a dark bottom / right one, seams and wear specks."""
    def fn(x, y, w, h):
        c = base
        if (x * 7 + y * 13) % 43 == 0:
            c = WORN
        elif (x * 5 + y * 3) % 23 == 0:
            c = dark
        elif x == 0 or y == 0:
            c = light
        elif x == w - 1 or y == h - 1:
            c = seam
        return c
    return fn


def tuner(kind):
    diode, hair = KINDS[kind]
    accent = mix(diode, SUIT, 0.35)
    im = Image.new('RGBA', (W * K, H * K), CLEAR)
    suit = cloth(SUIT, SUIT_D, SUIT_L, SEAM)

    # the head: short hair on top and at the back, a gaunt pale face, the dark band across the eyes
    def head(name):
        def fn(x, y, w, h):
            if name == 'top':
                return hair if (x * 5 + y * 3) % 7 else mix(hair, (0, 0, 0), 0.25)
            if name == 'bottom':
                return SKIN_D
            if y < 3 or (name == 'back' and y < 10):
                return hair if (x + y) % 5 else mix(hair, (0, 0, 0), 0.25)
            if 5 <= y <= 8:
                return BAND if name != 'front' or y in (6, 7) else BAND_L
            c = SKIN
            if name == 'front' and 11 <= y <= 12 and (x <= 2 or x >= 13):
                c = SKIN_D                                   # hollow cheeks
            elif name != 'front' and (x + y) % 7 == 0:
                c = SKIN_D
            return c
        return fn

    paint_box(im, HEAD, head)
    x0, y0 = origin(HEAD['front'])
    for ex in (2, 10):                                       # the diode eyes' sockets (the light is the renderer's)
        rect(im, x0 + ex, y0 + 6, x0 + ex + 3, y0 + 7, mix(diode, BAND, 0.45))
    rect(im, x0 + 7, y0 + 9, x0 + 8, y0 + 10, SKIN_D)        # the nose's shadow over the respirator
    # the overall: zip, belt, the collar's dark rim, the chest strap
    paint_box(im, BODY, lambda name: suit)
    x0, y0 = origin(BODY['front'])
    rect(im, x0, y0, x0 + 15, y0 + 1, SEAM)                  # collar
    rect(im, x0 + 7, y0 + 2, x0 + 8, y0 + 18, mix(SUIT_D, (0, 0, 0), 0.15))
    rect(im, x0, y0 + 19, x0 + 15, y0 + 20, BELT)
    rect(im, x0 + 7, y0 + 19, x0 + 8, y0 + 20, BUCKLE)
    for i in range(12):                                      # the pack's strap across the chest, left shoulder down
        px(im, x0 + 13 - i // 2, y0 + 2 + i, PACK_D)
        px(im, x0 + 12 - i // 2, y0 + 2 + i, PACK)
    rect(im, x0 + 3, y0 + 6, x0 + 5, y0 + 7, BAND_L)         # a pocket with a tag in the profession's colour
    px(im, x0 + 4, y0 + 6, accent)
    x0, y0 = origin(BODY['back'])
    for i in range(12):
        px(im, x0 + 2 + i // 3, y0 + 2 + i, PACK_D)
        px(im, x0 + 13 - i // 3, y0 + 2 + i, PACK_D)

    # sleeves with the profession's stripe, gloves
    def arm(name):
        def fn(x, y, w, h):
            if name == 'bottom' or (name != 'top' and y >= h - 5):
                return GLOVE if (x + y) % 5 else mix(GLOVE, (0, 0, 0), 0.25)
            if name != 'top' and y in (6, 7):
                return accent if y == 6 else mix(accent, (0, 0, 0), 0.2)
            return suit(x, y, w, h)
        return fn

    paint_box(im, ARM, arm)

    # trousers of the overall, knee patches, boots
    def leg(name):
        def fn(x, y, w, h):
            if name == 'bottom' or (name != 'top' and y >= h - 6):
                return BOOT if (x + 2 * y) % 6 else mix(BOOT, (0, 0, 0), 0.3)
            if name == 'front' and 9 <= y <= 13 and 1 <= x <= w - 2:
                return SUIT_D if (x + y) % 3 else SEAM
            return suit(x, y, w, h)
        return fn

    paint_box(im, LEG, leg)

    # the battery pack: dark steel, vents, a copper lead, the lamp's socket
    def pack(name):
        def fn(x, y, w, h):
            if x == 0 or y == 0:
                return PACK_L
            if x == w - 1 or y == h - 1:
                return PACK_D
            if name == 'back' and 3 <= x <= w - 4 and y % 4 == 2 and y < h - 4:
                return PACK_D                                # vents
            return PACK
        return fn

    paint_box(im, PACKBOX, pack)
    x0, y0 = origin(PACKBOX['back'])
    for i, (cx, cy) in enumerate([(1, 12), (2, 13), (2, 14), (3, 15)]):
        px(im, x0 + cx, y0 + cy, CABLE if i % 2 else mix(CABLE, (0, 0, 0), 0.3))
    rect(im, x0 + 8, y0 + 1, x0 + 10, y0 + 2, BAND)          # the stalk's socket
    paint_box(im, STALK, lambda name: (lambda x, y, w, h: ROD if (x + y) % 3 else ROD_L))
    # the respirator: rubber, a grille, two filter cans at the sides
    paint_box(im, MASK, lambda name: (lambda x, y, w, h: RUBBER if (x + y) % 2 or name != 'front' else GRILLE))
    x0, y0 = origin(MASK['front'])
    for gx in (0, 1, 8, 9):
        for gy in range(1, 5):
            px(im, x0 + gx, y0 + gy, PACK_L if gy % 2 else PACK)

    # the Elder: mantle and cloak in the field's turquoise, a light trim
    def field(name):
        def fn(x, y, w, h):
            if x == 0 or x == w - 1 or (name in ('front', 'back') and y == h - 1):
                return TRIM
            c = FIELD_M
            if (x * 3 + y * 5) % 17 == 0:
                c = FIELD
            elif y % 8 == 7:
                c = FIELD_D
            return c
        return fn

    paint_box(im, COLLAR, field)
    paint_box(im, CLOAK, field)
    x0, y0 = origin(CLOAK['back'])
    rect(im, x0 + 6, y0 + 4, x0 + 9, y0 + 7, TRIM)           # the dome's sign: a ring with a dot
    rect(im, x0 + 7, y0 + 5, x0 + 8, y0 + 6, FIELD_D)

    # the Metallurgist: a scorched leather apron
    def apron(name):
        def fn(x, y, w, h):
            if x == 0 or y == 0:
                return mix(LEATHER, (255, 255, 255), 0.12)
            c = LEATHER
            if (x * 11 + y * 7) % 23 < 2 and y > h // 3:
                c = SCORCH
            elif (x + y * 2) % 9 == 0:
                c = LEATHER_D
            return c
        return fn

    paint_box(im, APRON, apron)

    # the Gas Technician: two cylinders, a helium band and a deuterium band, dark valves on top
    def cylinder(name):
        def fn(x, y, w, h):
            if name == 'top':
                return ROD if 1 <= x <= w - 2 and 1 <= y <= h - 2 else STEEL_D
            if name == 'bottom':
                return STEEL_D
            if 3 <= y <= 4:
                return HELIUM
            if 12 <= y <= 13:
                return DEUTERIUM
            return STEEL_L if x == 1 else STEEL_D if x == w - 1 else STEEL
        return fn

    paint_box(im, CYLINDER, cylinder)
    # the Cartographer: the antenna rod
    paint_box(im, ANTENNA, lambda name: (lambda x, y, w, h: ROD_L if x == 0 else ROD))

    # the Technologist: the brass loupe and its lens
    def loupe(name):
        def fn(x, y, w, h):
            if name == 'front':
                if 1 <= x <= w - 2 and 1 <= y <= h - 2:
                    return LENS if (x + y) % 4 else (230, 250, 255)
                return BRASS
            return BRASS if (x + y) % 3 else BRASS_D
        return fn

    paint_box(im, LOUPE, loupe)
    return im


def warden():
    im = Image.new('RGBA', (W * K, H * K), CLEAR)
    under = cloth(UNDER, UNDER_D, UNDER_L, UNDER_D)

    def head(name):
        def fn(x, y, w, h):
            if name == 'top' or y < 3 or (name == 'back' and y < 12):
                return UNDER_D if (x + y) % 4 else UNDER                  # a padded hood
            c = SKIN
            if name == 'front' and 6 <= y <= 7 and (2 <= x <= 5 or 10 <= x <= 13):
                c = (40, 44, 48)                                          # the eyes
            elif name == 'front' and y >= 12 and (x * 3 + y) % 4 == 0:
                c = SKIN_D                                                # a short beard
            elif name != 'front':
                c = UNDER
            return c
        return fn

    paint_box(im, HEAD, head)
    paint_box(im, BODY, lambda name: under)
    x0, y0 = origin(BODY['front'])
    rect(im, x0, y0 + 19, x0 + 15, y0 + 20, BELT)
    rect(im, x0 + 7, y0 + 19, x0 + 8, y0 + 20, BUCKLE)
    rect(im, x0 + 9, y0 + 3, x0 + 12, y0 + 5, ARMBAND_D)                  # the dome's tab on the chest
    rect(im, x0 + 10, y0 + 4, x0 + 11, y0 + 4, ARMBAND)

    def limb(name):
        def fn(x, y, w, h):
            if name == 'bottom' or (name != 'top' and y >= h - 5):
                return GLOVE if (x + y) % 5 else mix(GLOVE, (0, 0, 0), 0.25)
            return under(x, y, w, h)
        return fn

    paint_box(im, ARM, limb)
    paint_box(im, LEG, limb)

    def band(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return ARMBAND_D
            if y == 0 or y == h - 1:
                return ARMBAND_D
            return ARMBAND_S if y in (h // 2 - 1, h // 2) else ARMBAND
        return fn

    paint_box(im, ARMBANDBOX, band)
    return im


def main():
    os.makedirs(TEX, exist_ok=True)
    out = []
    for kind in KINDS:
        im = tuner(kind)
        im.save(os.path.join(TEX, 'tuner_' + kind + '.png'))
        out.append(im)
    im = warden()
    im.save(os.path.join(TEX, 'warden.png'))
    out.append(im)
    # the preview: the six skins in their UV layout x2, on grey
    z = 2
    size = W * K * z
    sheet = Image.new('RGBA', (len(out) * (size + 8) + 8, size + 16), (70, 72, 76, 255))
    for i, im in enumerate(out):
        big = im.resize((size, size), Image.NEAREST)
        sheet.paste(big, (8 + i * (size + 8), 8), big)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
