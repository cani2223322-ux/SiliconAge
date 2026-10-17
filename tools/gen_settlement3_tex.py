# -*- coding: utf-8 -*-
"""Подложка (Si-14), the settlements of phase 3 (Silicon Age, docs/plan-substrate-tech-f3.md §6.1-§6.5, package S).

Writes into src/main/resources/assets/siliconage/textures:
  entity/tuner_powerEngineer.png - «Энергетик»: the Tuner's overall (gen_settlement_mobs_tex.tuner, white diodes) and the
                                   coil on the back of his pack - copper windings round a steel core (the renderer lights
                                   its core);
  entity/tuner_armourer.png      - «Оружейник»: red diodes, Nano pieces over the overall - an olive chest plate with its
                                   lit seam and two shoulder plates;
  entity/tuner_salvager.png      - «Скупщик»: green diodes, a patched burlap bag of scrap over his pack, a steel rod and
                                   a bent Nano plate sticking out of it;
  entity/warden_captain.png      - «Капитан купола»: the guard's undersuit (gen_settlement_mobs_tex.warden) and his cloak
                                   in the dome's turquoise with a white trim and the dome's sign;
  entity/warden_commandant.png   - «Комендант города»: the cloak near-black with the dome's turquoise trim and the
                                   Singular violet lining, epaulettes in turquoise with a bright edge;
  blocks/showcase2_crate_side / _top - the warehouse's steel crate: a bevelled frame, cross braces, a hazard strip, a
                                   stencilled number;
  blocks/showcase2_scrapPile_side / _top - a heap of armour scrap: olive Nano and blue-grey Quantum shards, wires, rust;
  blocks/showcase2_rack_side / _front / _top - a steel rack: uprights, three shelves of parts and boxes.
Layout of the skins: as gen_settlement_mobs_tex.py (64 x 64 drawn at 128 x 128); the new parts' boxes and texture offsets
here must match RenderTunerSC.ModelTunerSC / RenderWardenSC.ModelWardenSC: the coil (50, 0) 4 x 4 x 3, the chest plate
(32, 0) 8 x 5 x 1, the shoulder plates (32, 7) 5 x 3 x 5, the bag (36, 32) 6 x 6 x 4, the rod (56, 32) 1 x 4 x 1, the
plate (56, 37) 3 x 3 x 1; the chiefs' cloak (16, 32) 9 x 14 x 1, the epaulettes (36, 32) 4 x 1 x 4. Blocks: 32 x 32 as
the mod's machines. A preview sheet goes next to this script.

Usage: python tools/gen_settlement3_tex.py [project dir]
"""
import importlib.util
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..')
ENTITY = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'entity')
BLOCKS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
PREVIEW = os.path.join(HERE, 'settlement3_preview.png')

# the phase 2 skins' painter: the overall, the undersuit, the box helpers (the same layout and K)
_spec = importlib.util.spec_from_file_location('gen_settlement_mobs_tex', os.path.join(HERE, 'gen_settlement_mobs_tex.py'))
M = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(M)
faces, paint_box, origin, px, rect, mix, clamp = M.faces, M.paint_box, M.origin, M.px, M.rect, M.mix, M.clamp

# the new professions: the diodes' colour (RenderTunerSC.DIODES), the hair
M.KINDS['powerEngineer'] = ((255, 255, 255), (64, 56, 50))
M.KINDS['armourer'] = ((255, 64, 64), (40, 36, 34))
M.KINDS['salvager'] = ((80, 255, 112), (120, 96, 70))

# palettes
COPPER = (196, 112, 58)
COPPER_L = (232, 160, 96)
COPPER_D = (128, 66, 32)
STEEL = M.STEEL
STEEL_L = M.STEEL_L
STEEL_D = M.STEEL_D
NANO = (98, 112, 58)
NANO_L = (138, 154, 82)
NANO_D = (67, 78, 39)
NANO_G = (74, 80, 71)
NANO_GD = (48, 53, 46)
NANO_GLOW = (92, 255, 106)
QUANTUM = (86, 110, 140)
QUANTUM_L = (130, 156, 186)
BURLAP = (150, 122, 80)
BURLAP_L = (178, 150, 104)
BURLAP_D = (110, 86, 54)
PATCH = (96, 104, 92)
ROPE = (200, 176, 120)
RUST = (140, 70, 36)
RUST_L = (178, 98, 52)
TURQ = (60, 210, 200)
TURQ_M = (48, 170, 164)
TURQ_D = (24, 96, 92)
WHITE = (226, 252, 248)
NIGHT = (24, 22, 30)
NIGHT_L = (44, 40, 54)
VIOLET = (120, 70, 170)
HAZARD = (226, 186, 40)
HAZARD_D = (40, 36, 30)

# the new parts (RenderTunerSC.ModelTunerSC, RenderWardenSC.ModelWardenSC)
COIL = faces(50, 0, 4, 4, 3)
PLATE = faces(32, 0, 8, 5, 1)
PAULDRON = faces(32, 7, 5, 3, 5)
SACK = faces(36, 32, 6, 6, 4)
ROD = faces(56, 32, 1, 4, 1)
SCRAP_PLATE = faces(56, 37, 3, 3, 1)
CLOAK = faces(16, 32, 9, 14, 1)
EPAULETTE = faces(36, 32, 4, 1, 4)


def clear_box(im, box):
    """A box's faces cleared to transparent (the phase 2 painter left another profession's part there)."""
    for f in box.values():
        u, v, w, h = f
        for y in range(h * M.K):
            for x in range(w * M.K):
                im.putpixel((u * M.K + x, v * M.K + y), (0, 0, 0, 0))


# ------------------------------------------------------------------ the Tuners

def power_engineer():
    im = M.tuner('powerEngineer')

    # the coil: copper windings in bands round a steel core, the end faces steel with the core's socket
    def coil(name):
        def fn(x, y, w, h):
            if name in ('front', 'back'):                       # the coil's ends (the back: the renderer lights its core)
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    return STEEL_D
                return M.ROD if 2 <= x <= w - 3 and 2 <= y <= h - 3 else STEEL
            band = (y if name in ('left', 'right') else x) % 3
            c = COPPER_L if band == 0 else COPPER if band == 1 else COPPER_D
            if (x * 5 + y * 3) % 17 == 0:
                c = mix(c, (255, 255, 255), 0.35)               # a glint on the wire
            return c
        return fn

    paint_box(im, COIL, coil)
    return im


def armourer():
    im = M.tuner('armourer')

    def plate(name):
        def fn(x, y, w, h):
            if x == 0 or y == 0:
                return NANO_L
            if x == w - 1 or y == h - 1:
                return NANO_D
            if name == 'front':
                if y == h // 2 and 2 <= x <= w - 3:
                    return NANO_GLOW if x % 4 else mix(NANO_GLOW, NANO, 0.5)   # the lit seam
                if (x == 3 or x == w - 4) and 2 <= y <= h - 3:
                    return NANO_GD                               # the plate's ribs
            return NANO if (x * 3 + y * 5) % 13 else NANO_D
        return fn

    paint_box(im, PLATE, plate)

    def pauldron(name):
        def fn(x, y, w, h):
            if name == 'top':
                return NANO_L if (x + y) % 6 else NANO
            if y == 0:
                return NANO_L
            if y == h - 1:
                return NANO_GD
            return NANO if x % 5 else NANO_D
        return fn

    paint_box(im, PAULDRON, pauldron)
    return im


def salvager():
    im = M.tuner('salvager')
    clear_box(im, faces(36, 32, 8, 14, 1))                      # the Elder's cloak under the bag's UV

    def sack(name):
        def fn(x, y, w, h):
            if name == 'top':                                   # the bag's mouth, tied with rope, dark inside
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    return ROPE if (x + y) % 2 else mix(ROPE, (0, 0, 0), 0.2)
                return (34, 30, 26)
            c = BURLAP if (x + 2 * y) % 4 else BURLAP_D         # the weave
            if (x * 7 + y * 11) % 29 == 0:
                c = BURLAP_L
            if name == 'back' and 3 <= x <= 7 and 5 <= y <= 9:
                c = PATCH if x not in (3, 7) and y not in (5, 9) else mix(PATCH, (0, 0, 0), 0.3)   # a patch
            if y <= 1:
                c = ROPE if x % 2 else mix(ROPE, (0, 0, 0), 0.2)   # the rope round its neck
            return c
        return fn

    paint_box(im, SACK, sack)
    paint_box(im, ROD, lambda name: (lambda x, y, w, h: STEEL_L if x == 0 else STEEL_D if (x + y) % 3 == 0 else STEEL))

    def scrap(name):
        def fn(x, y, w, h):
            if (x * 3 + y * 7) % 11 == 0:
                return RUST if (x + y) % 2 else RUST_L
            if x == 0 or y == 0:
                return NANO_L
            return NANO if (x + y) % 4 else NANO_D
        return fn

    paint_box(im, SCRAP_PLATE, scrap)
    return im


# ------------------------------------------------------------------ the chiefs

def cloak_painter(base, mid, dark, trim, lining):
    def painter(name):
        def fn(x, y, w, h):
            if name == 'front':                                 # the side against the armour: the lining
                return lining if (x + y) % 5 else mix(lining, (0, 0, 0), 0.3)
            if name in ('top', 'bottom'):
                return dark
            if x == 0 or x == w - 1 or y == h - 1:
                return trim
            c = mid
            if (x * 3 + y * 5) % 19 == 0:
                c = base
            elif y % 10 == 9:
                c = dark                                        # the folds
            return c
        return fn
    return painter


def dome_sign(im, face, ring, dot):
    """The dome's sign (a ring with a dot) on a cloak's back, a third of the way down."""
    x0, y0 = origin(face)
    w = face[2] * M.K
    cx, cy = x0 + w // 2, y0 + 6
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            d = dx * dx + dy * dy
            if 6 <= d <= 12:
                px(im, cx + dx, cy + dy, ring)
            elif d <= 2:
                px(im, cx + dx, cy + dy, dot)


def captain():
    im = M.warden()
    paint_box(im, CLOAK, cloak_painter(TURQ, TURQ_M, TURQ_D, WHITE, M.UNDER))
    dome_sign(im, CLOAK['back'], WHITE, TURQ_D)
    return im


def commandant():
    im = M.warden()
    paint_box(im, CLOAK, cloak_painter(NIGHT_L, NIGHT, (14, 12, 18), TURQ, VIOLET))
    dome_sign(im, CLOAK['back'], TURQ, (176, 255, 246))

    def epaulette(name):
        def fn(x, y, w, h):
            if name == 'top':
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    return (176, 255, 246)
                return TURQ if (x + y) % 3 else TURQ_M
            return TURQ_D if y == h - 1 else TURQ
        return fn

    paint_box(im, EPAULETTE, epaulette)
    return im


# ------------------------------------------------------------------ the warehouse's blocks (32 x 32)

S = 32


def new_block(c):
    return Image.new('RGBA', (S, S), c + (255,))


def put(im, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), c + (255,))


def frame(im, light, dark, mid, width=2):
    """A bevelled steel frame round the face: lit top / left, dark bottom / right, the corners' rivets."""
    for i in range(S):
        for k in range(width):
            put(im, i, k, light if k == 0 else mid)
            put(im, k, i, light if k == 0 else mid)
            put(im, i, S - 1 - k, dark)
            put(im, S - 1 - k, i, dark if k == 0 else mid)
    put(im, 0, 0, M.OUTLINE)
    for (x, y) in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        put(im, x, y, STEEL_L)
        put(im, x + 1, y + 1, STEEL_D)


def crate_side():
    im = new_block((92, 102, 112))
    for y in range(S):                                          # the sheet: faint vertical ribs
        for x in range(S):
            if x % 6 == 2:
                put(im, x, y, (104, 116, 128))
            elif x % 6 == 3:
                put(im, x, y, (80, 88, 98))
    for i in range(3, S - 3):                                   # the cross braces
        put(im, i, i, STEEL_D)
        put(im, i, i + 1, STEEL_L)
        put(im, S - 1 - i, i, STEEL_D)
        put(im, S - 1 - i, i + 1, STEEL_L)
    for x in range(2, S - 2):                                   # the hazard strip along the bottom
        for y in range(S - 7, S - 4):
            put(im, x, y, HAZARD if ((x + y) // 3) % 2 == 0 else HAZARD_D)
    for (x, y) in ((11, 6), (12, 6), (13, 6), (13, 7), (12, 8), (13, 9), (11, 10), (12, 10), (13, 10),     # a stencilled «3» and «7»
                   (17, 6), (18, 6), (19, 6), (19, 7), (19, 8), (18, 9), (18, 10)):
        put(im, x, y, (230, 232, 220))
    frame(im, STEEL_L, M.OUTLINE, STEEL)
    return im


def crate_top():
    im = new_block((86, 96, 106))
    for y in range(4, S - 4):
        for x in range(4, S - 4):
            if (x - 4) % 8 in (0, 7) or (y - 4) % 8 in (0, 7):
                put(im, x, y, (70, 78, 88))                     # the lid's panels
    for y in range(13, 19):                                     # the lifting handle
        put(im, 9, y, STEEL_L)
        put(im, 22, y, STEEL_D)
    for x in range(9, 23):
        put(im, x, 13, STEEL_L)
        put(im, x, 18, STEEL_D)
    frame(im, STEEL_L, M.OUTLINE, STEEL)
    return im


def scrap(top):
    im = new_block((46, 42, 38))
    shards = [NANO, NANO_L, NANO_D, QUANTUM, QUANTUM_L, (150, 156, 164), RUST, RUST_L, NANO_G]
    seed = 7 if top else 3
    for n in range(46 if top else 38):                          # overlapping shards, darker towards the bottom of a side
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        cx, cy = seed % S, (seed >> 8) % S
        w, h = 3 + (seed >> 16) % 6, 2 + (seed >> 20) % 4
        c = shards[(seed >> 12) % len(shards)]
        for dy in range(h):
            for dx in range(w - dy // 2):
                x, y = cx + dx + dy // 2, cy + dy
                shade = 1.0 if top else 1.0 - 0.35 * (y / float(S))
                cc = mix(c, (0, 0, 0), 1 - shade)
                put(im, x % S, y % S, mix(cc, (255, 255, 255), 0.25) if dy == 0 else cc)
    for n in range(6):                                          # wires
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        x, y = seed % S, (seed >> 8) % S
        for i in range(8):
            put(im, (x + i) % S, (y + (i * (1 + n % 2)) // 3) % S, COPPER if i % 3 else COPPER_L)
    for n in range(4):                                          # a few dead green diodes
        seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF
        put(im, seed % S, (seed >> 8) % S, mix(NANO_GLOW, (0, 0, 0), 0.5))
    return im


def rack(front):
    im = new_block((28, 30, 34))
    shelves = (9, 19, 29)
    for y in range(S):                                          # the back panel / the side's mesh
        for x in range(S):
            if not front and (x + y) % 4 == 0:
                put(im, x, y, (52, 56, 62))
    goods = [((176, 122, 60), (120, 80, 36)), (NANO, NANO_D), (QUANTUM, (60, 80, 104)), ((150, 156, 164), (96, 100, 108)),
             (HAZARD, (150, 120, 20)), ((190, 70, 60), (130, 40, 34))]
    for si, sy in enumerate(shelves):
        for x in range(1, S - 1):
            put(im, x, sy, STEEL_L)
            put(im, x, sy + 1, STEEL_D)
        if front:                                               # boxes and parts on the shelf
            x = 3 + si
            k = si
            while x < S - 4:
                w, h = 3 + (k * 5) % 4, 4 + (k * 3) % 4
                c, d = goods[k % len(goods)]
                for yy in range(sy - h, sy):
                    for xx in range(x, min(S - 3, x + w)):
                        put(im, xx, yy, c if xx != x + w - 1 and yy != sy - h else d)
                x += w + 1 + (k % 2)
                k += 1
    for y in range(S):                                          # the uprights
        for x in (0, 1, S - 2, S - 1):
            put(im, x, y, STEEL_L if x in (0, S - 2) else STEEL_D)
    return im


def rack_top():
    im = new_block((40, 44, 50))
    for y in range(S):
        for x in range(S):
            if (x // 4 + y // 4) % 2 == 0:
                put(im, x, y, (52, 56, 62))                     # the top grating
    frame(im, STEEL_L, STEEL_D, STEEL)
    return im


def main():
    os.makedirs(ENTITY, exist_ok=True)
    skins = [('tuner_powerEngineer', power_engineer()), ('tuner_armourer', armourer()), ('tuner_salvager', salvager()),
             ('warden_captain', captain()), ('warden_commandant', commandant())]
    for name, im in skins:
        im.save(os.path.join(ENTITY, name + '.png'))
    blocks = [('showcase2_crate_side', crate_side()), ('showcase2_crate_top', crate_top()), ('showcase2_scrapPile_side', scrap(False)),
              ('showcase2_scrapPile_top', scrap(True)), ('showcase2_rack_side', rack(False)), ('showcase2_rack_front', rack(True)),
              ('showcase2_rack_top', rack_top())]
    for name, im in blocks:
        assert all(im.getpixel((x, y))[3] == 255 for x in range(S) for y in range(S)), name + ' must be opaque'
        im.save(os.path.join(BLOCKS, name + '.png'))
    # the preview: the skins x2 in their UV layout, the blocks x4, on grey
    z = 2
    size = M.W * M.K * z
    bz = 4 * S
    width = max(len(skins) * (size + 8) + 8, len(blocks) * (bz + 8) + 8)
    sheet = Image.new('RGBA', (width, size + bz + 24), (70, 72, 76, 255))
    for i, (name, im) in enumerate(skins):
        big = im.resize((size, size), Image.NEAREST)
        sheet.paste(big, (8 + i * (size + 8), 8), big)
    for i, (name, im) in enumerate(blocks):
        big = im.resize((bz, bz), Image.NEAREST)
        sheet.paste(big, (8 + i * (bz + 8), size + 16), big)
    sheet.save(PREVIEW)
    print('wrote %d skins into %s, %d block textures into %s' % (len(skins), ENTITY, len(blocks), BLOCKS))


if __name__ == '__main__':
    main()
