# -*- coding: utf-8 -*-
"""Подложка (Si-14), the armoured mobs of phase 3 (Silicon Age, docs/plan-substrate-tech-f3.md §4.1-§4.5, package M).

Writes into src/main/resources/assets/siliconage/textures:
  entity/trooper.png       - «Штурмовик-отступник»: the dark crimson undersuit under its Quantum (ModelBiped's 64 x 32
                             layout drawn at 128 x 64, as tools/gen_substrate_mobs_tex.py does): black joints, red piping,
                             a glowing visor slit - mostly hidden by the worn pieces, seen in their gaps;
  entity/jammer.png        - «Глушитель»: a night-blue undersuit with silver piping and, on the free UV corner (56, 16),
                             its two antenna masts (segmented, a blue band under the tip) and the tips' box (56, 24) - the
                             renderer draws the tips full-bright anyway;
  entity/hollowShell.png   - «Пустая оболочка»: all but transparent (the alpha test cuts it) - only thin dark inner ribs
                             along the edges and the joint cables, so through the gaps of its Exo there is nothing inside;
  entity/repairDrone.png   - «Ремонтный дрон» (ModelRepairDroneSC's 64 x 32 drawn at 128 x 64): an olive hull with a
                             hazard stripe, the yellow work light underneath, dark arms, blurred grey rotors, the antenna
                             with a red tip, the nozzle's lens;
  entity/executioner.png   - «Экзо-палач»: a gunmetal undersuit, and on the hat layer's UV (32, 0) the helium tank
                             (pale steel with frost and ice-blue hoops) and its brass valve (48, 0);
  items/loot_quantumScrap  - «Лом брони Квант»: a bent silver-white Quantum plate with gold trim and a broken crimson
                             light strip (the renegades' colour);
  items/loot_exoScrap      - «Лом брони Экзо»: a jagged near-black Exo plate with violet facets and a cracked violet strip;
  items/loot_shieldCore    - «Сердечник щита»: a hexagonal emitter core - a dark frame round a cell of cyan honeycomb
                             glowing white at its heart;
and a preview sheet next to this script (the five skins x2, the three icons x4).

Usage: python gen_substrate_mobs3_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'substrate_mobs3_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# skins: the model's UV pixels drawn 2 x 2
K = 2

# the Trooper: a dark crimson undersuit
CRIMSON = (82, 22, 28)
CRIMSON_D = (52, 14, 18)
CRIMSON_L = (120, 36, 40)
JOINT = (26, 24, 28)
PIPE = (196, 48, 48)
VISOR = (255, 64, 64)
VISOR_L = (255, 190, 180)
# the Jammer: night blue
NIGHT = (34, 42, 70)
NIGHT_D = (22, 27, 46)
NIGHT_L = (54, 66, 104)
SILVER = (150, 160, 176)
MAST = (70, 76, 86)
MAST_D = (40, 44, 52)
BLUE = (74, 124, 255)
BLUE_L = (190, 214, 255)
# the Hollow Shell: dark ribs only
RIB = (30, 33, 38)
RIB_L = (58, 63, 70)
CABLE = (70, 74, 80)
# the Repair Drone
OLIVE = (122, 124, 96)
OLIVE_D = (84, 86, 64)
OLIVE_L = (160, 162, 128)
HAZARD = (224, 196, 52)
DARK = (40, 42, 38)
ROTOR = (150, 152, 150)
ROTOR_D = (96, 98, 96)
LAMP = (255, 230, 90)
LAMP_L = (255, 248, 200)
LENS = (190, 255, 110)
RED = (220, 40, 36)
# the Executioner
GUNMETAL = (56, 60, 68)
GUNMETAL_D = (36, 39, 45)
GUNMETAL_L = (84, 90, 100)
TANK = (150, 168, 186)
TANK_D = (104, 120, 138)
FROST = (228, 240, 252)
ICE = (140, 210, 255)
BRASS = (184, 148, 72)
BRASS_D = (120, 94, 44)
# items: Quantum (armorQuantum's silver, gold, cyan), Exo (near-black, violet)
Q_SILVER = (220, 226, 233)
Q_SILVER_D = (150, 158, 170)
Q_SILVER_L = (244, 246, 250)
Q_GOLD = (217, 168, 52)
Q_GOLD_D = (156, 116, 32)
Q_GLOW = (255, 64, 64)
Q_GLOW_L = (255, 196, 190)
E_BLACK = (24, 20, 31)
E_DARK = (44, 35, 62)
E_VIOLET = (83, 71, 112)
E_VIOLET_L = (126, 108, 168)
E_GLOW = (176, 96, 255)
E_GLOW_L = (232, 206, 255)
C_FRAME = (58, 62, 70)
C_FRAME_D = (32, 34, 40)
C_FRAME_L = (110, 116, 128)
C_CELL = (40, 150, 200)
C_CELL_L = (110, 220, 255)
C_HEART = (240, 252, 255)


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


# ---------------------------------------------------------------- skins: the models' boxes

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


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)       # the left arm is the same texture, mirrored
LEG = faces(0, 16, 4, 12, 4)


def paint(im, face, fn):
    """fn(x, y, w, h) -> colour or None for each REAL pixel of a face (x, y within it, w, h its real size)."""
    u, v, w, h = face
    for y in range(h * K):
        for x in range(w * K):
            px(im, u * K + x, v * K + y, fn(x, y, w * K, h * K))


def suit_fn(base, dark, light, joint, pipe, joint_rows=()):
    """An undersuit: shaded cloth, a darker hem, lit top-left edges, the joints' black bands at `joint_rows` (real px)."""
    def fn(x, y, w, h):
        if any(r0 <= y <= r1 for r0, r1 in joint_rows):
            return joint if (x + y) % 3 else mix(joint, (0, 0, 0), 0.4)
        if x == 0 or y == 0:
            return light
        if x == w - 1 or y == h - 1:
            return dark
        if pipe is not None and x in (w // 2 - 1, w // 2) and (y // 2) % 4 != 3:
            return pipe
        if (x * 7 + y * 11) % 17 == 0:
            return dark
        return base
    return fn


def trooper():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    for name, f in HEAD.items():
        paint(im, f, suit_fn(CRIMSON_D, JOINT, CRIMSON, JOINT, None))
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 2, y0 + 6, x0 + 13, y0 + 8, JOINT)                  # the visor's slit, glowing
    rect(im, x0 + 3, y0 + 7, x0 + 12, y0 + 7, VISOR)
    px(im, x0 + 5, y0 + 7, VISOR_L)
    px(im, x0 + 10, y0 + 7, VISOR_L)
    rect(im, x0 + 5, y0 + 12, x0 + 10, y0 + 13, JOINT)                # a breather grille
    for gx in range(6, 10, 2):
        px(im, x0 + gx, y0 + 12, CRIMSON_L)
    for name, f in BODY.items():
        paint(im, f, suit_fn(CRIMSON, CRIMSON_D, CRIMSON_L, JOINT, PIPE if name in ('front', 'back') else None, ((19, 20),)))
    for name, f in ARM.items():
        paint(im, f, suit_fn(CRIMSON, CRIMSON_D, CRIMSON_L, JOINT, None, ((10, 13), (20, 23)) if name != 'top' else ()))
    for name, f in LEG.items():
        paint(im, f, suit_fn(CRIMSON, CRIMSON_D, CRIMSON_L, JOINT, PIPE if name == 'front' else None, ((10, 13), (20, 23)) if name != 'top' else ()))
    return im


def jammer():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    for name, f in HEAD.items():
        paint(im, f, suit_fn(NIGHT_D, NIGHT_D, NIGHT, JOINT, None))
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    for gx in (2, 9):                                                   # two round sensor lenses
        rect(im, x0 + gx, y0 + 5, x0 + gx + 4, y0 + 8, JOINT)
        rect(im, x0 + gx + 1, y0 + 6, x0 + gx + 3, y0 + 7, BLUE)
        px(im, x0 + gx + 1, y0 + 6, BLUE_L)
    for name, f in BODY.items():
        paint(im, f, suit_fn(NIGHT, NIGHT_D, NIGHT_L, JOINT, SILVER if name in ('front', 'back') else None, ((19, 20),)))
    u, v, w, h = BODY['back']                                           # the emitter's power pack on the back
    rect(im, u * K + 3, v * K + 3, u * K + 12, v * K + 14, JOINT)
    rect(im, u * K + 4, v * K + 4, u * K + 11, v * K + 13, MAST)
    for yy in range(5, 13, 3):
        rect(im, u * K + 5, v * K + yy, u * K + 10, v * K + yy, BLUE)
    for name, f in ARM.items():
        paint(im, f, suit_fn(NIGHT, NIGHT_D, NIGHT_L, JOINT, None, ((10, 13), (20, 23)) if name != 'top' else ()))
    for name, f in LEG.items():
        paint(im, f, suit_fn(NIGHT, NIGHT_D, NIGHT_L, JOINT, SILVER if name == 'front' else None, ((10, 13), (20, 23)) if name != 'top' else ()))
    # the antenna masts: box 1 x 7 x 1 at (56, 16), segmented metal, a blue band near the top
    for name, f in faces(56, 16, 1, 7, 1).items():
        def mast(x, y, w, h, name=name):
            if name in ('top', 'bottom'):
                return MAST_D
            if y < 3:
                return BLUE if x % 2 == 0 else BLUE_L
            return MAST_D if y % 4 == 3 else (MAST if x == 0 else mix(MAST, MAST_D, 0.4))
        paint(im, f, mast)
    # the tips: box 2 x 2 x 2 at (56, 24) (full-bright in game; here their own blue)
    for name, f in faces(56, 24, 2, 2, 2).items():
        paint(im, f, lambda x, y, w, h: BLUE_L if (x + y) % 3 == 0 else BLUE)
    return im


def hollow_shell():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def ribs(rib_every):
        def fn(x, y, w, h):
            edge = min(x, y, w - 1 - x, h - 1 - y)
            if edge == 0 and (x + y) % 4 != 0:
                return RIB_L if (x + 2 * y) % 7 == 0 else RIB
            if rib_every and y % rib_every == rib_every // 2 and 2 <= x <= w - 3 and x % 3 != 0:
                return RIB
            return None
        return fn

    for name, f in HEAD.items():
        paint(im, f, ribs(0))
    for name, f in BODY.items():
        paint(im, f, ribs(8))
    for part in (ARM, LEG):
        for name, f in part.items():
            paint(im, f, ribs(8 if name not in ('top', 'bottom') else 0))
    # the joint cables hanging loose at the shoulders and the hips
    for f in (ARM['front'], LEG['front']):
        u, v, w, h = f
        for cy in range(0, 7):
            px(im, u * K + 3 + (cy // 3) % 2, v * K + cy, CABLE)
    return im


def repair_drone():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def hull(name):
        def fn(x, y, w, h):
            if name == 'bottom':
                return OLIVE_D
            if name == 'top':
                if min(x, y, w - 1 - x, h - 1 - y) == 0:
                    return OLIVE_D
                if 4 <= x <= 7 and 4 <= y <= 7:
                    return DARK                                         # the antenna's hatch
                return OLIVE_L if (x + y) % 9 == 0 else OLIVE
            if y in (0, h - 1):
                return OLIVE_D
            if 2 <= y <= 3:                                             # the hazard stripe round the hull
                return HAZARD if ((x + y) // 2) % 2 == 0 else DARK
            return OLIVE
        return fn

    for name, f in faces(0, 0, 6, 3, 6).items():
        paint(im, f, hull(name))
    for name, f in faces(0, 9, 4, 1, 4).items():                        # the belly: the work light
        paint(im, f, lambda x, y, w, h, name=name: (LAMP_L if (x + y) % 5 == 0 else LAMP) if name == 'bottom' else DARK)
    for name, f in faces(0, 14, 4, 1, 1).items():                        # the arms
        paint(im, f, lambda x, y, w, h: DARK if x % 3 else OLIVE_D)
    for name, f in faces(16, 14, 4, 1, 1).items():                       # the rotors, blurred
        paint(im, f, lambda x, y, w, h: ROTOR if x % 2 else ROTOR_D)
    for name, f in faces(24, 0, 1, 3, 1).items():                        # the antenna, a red tip
        paint(im, f, lambda x, y, w, h: RED if y < 2 else DARK)
    for name, f in faces(28, 0, 2, 2, 1).items():                        # the nozzle's lens
        paint(im, f, lambda x, y, w, h, name=name: (LENS if (x + y) % 3 else (240, 255, 210)) if name == 'front' else DARK)
    return im


def executioner():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    for name, f in HEAD.items():
        paint(im, f, suit_fn(GUNMETAL_D, GUNMETAL_D, GUNMETAL, JOINT, None))
    u, v, w, h = HEAD['front']
    rect(im, u * K + 2, v * K + 6, u * K + 13, v * K + 8, JOINT)
    rect(im, u * K + 4, v * K + 7, u * K + 11, v * K + 7, ICE)
    for name, f in BODY.items():
        paint(im, f, suit_fn(GUNMETAL, GUNMETAL_D, GUNMETAL_L, JOINT, None, ((19, 20),)))
    for name, f in ARM.items():
        paint(im, f, suit_fn(GUNMETAL, GUNMETAL_D, GUNMETAL_L, JOINT, None, ((10, 13), (20, 23)) if name != 'top' else ()))
    for name, f in LEG.items():
        paint(im, f, suit_fn(GUNMETAL, GUNMETAL_D, GUNMETAL_L, JOINT, None, ((10, 13), (20, 23)) if name != 'top' else ()))

    # the helium tank: box 4 x 7 x 3 at (32, 0) - frost creeping up from the bottom, ice-blue hoops
    def tank(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return FROST if (x + y) % 3 == 0 else TANK_D
            if y in (2, h - 3):
                return ICE
            frost_line = h - 4 - int(3 * math.sin(x * 0.9 + (1.3 if name == 'back' else 0.0)) + 3)
            if y >= frost_line:
                return FROST if (x * 3 + y) % 5 else ICE
            if x == 0:
                return mix(TANK, FROST, 0.3)
            if x == w - 1:
                return TANK_D
            return TANK if (x + y * 2) % 13 else mix(TANK, FROST, 0.5)
        return fn

    for name, f in faces(32, 0, 4, 7, 3).items():
        paint(im, f, tank(name))
    for name, f in faces(48, 0, 2, 2, 2).items():                        # the valve
        paint(im, f, lambda x, y, w, h: BRASS if (x + y) % 3 else BRASS_D)
    return im


# ---------------------------------------------------------------- 32 x 32 item icons

S = 32


def outline(im):
    """A 1-pixel dark outline round everything drawn (the mod's items all have one)."""
    src = im.copy()
    for y in range(S):
        for x in range(S):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, OUTLINE)
                    break


def inside(poly, x, y):
    n = len(poly)
    c = False
    j = n - 1
    for i in range(n):
        xi, yi = poly[i]
        xj, yj = poly[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / float(yj - yi) + xi:
            c = not c
        j = i
    return c


def quantum_scrap():
    im = Image.new('RGBA', (S, S), CLEAR)
    # a bent shard of a Quantum plate: silver, lighter top-left, a gold rim along its intact edge
    poly = [(4, 11), (10, 6), (19, 5), (27, 8), (28, 15), (25, 22), (27, 27), (17, 26), (10, 28), (6, 21)]
    for y in range(S):
        for x in range(S):
            if inside(poly, x + 0.5, y + 0.5):
                t = (x + y) / 58.0
                c = mix(Q_SILVER_L, Q_SILVER_D, t)
                if (x * 5 + y * 3) % 23 == 0:
                    c = Q_SILVER_D
                px(im, x, y, c)
    for i in range(16):                                                 # the gold rim of the intact upper edge
        x, y = 10 + i, 6 + (i * 3) // 16
        for dy in (0, 1):
            if im.getpixel((x, y + dy))[3]:
                px(im, x, y + dy, Q_GOLD if dy == 0 else Q_GOLD_D)
    for i in range(17):                                                 # the bend: a crease across
        x, y = 7 + i, 22 - (i * 2) // 3
        if im.getpixel((x, y))[3]:
            px(im, x, y, Q_SILVER_D)
        if im.getpixel((x, y + 1))[3]:
            px(im, x, y + 1, mix(Q_SILVER_D, (0, 0, 0), 0.25))
    for i, x in enumerate(range(9, 24)):                                # the broken crimson light strip
        if x in (15, 16):
            continue
        y = 14 - (x - 9) // 6
        px(im, x, y, Q_GLOW if i % 3 else Q_GLOW_L)
    for bx, by in ((8, 12), (23, 20)):                                  # bolts
        rect(im, bx, by, bx + 1, by + 1, Q_GOLD_D)
    outline(im)
    return im


def exo_scrap():
    im = Image.new('RGBA', (S, S), CLEAR)
    # a jagged near-black Exo plate, faceted in violet, a cracked violet strip
    poly = [(6, 7), (14, 4), (24, 6), (28, 12), (26, 19), (28, 26), (19, 28), (11, 25), (4, 27), (5, 17)]
    for y in range(S):
        for x in range(S):
            if inside(poly, x + 0.5, y + 0.5):
                facet = ((x // 5) + (y // 5)) % 3
                c = (E_BLACK, E_DARK, E_VIOLET)[facet]
                if (x + y) % 11 == 0:
                    c = E_VIOLET_L
                px(im, x, y, c)
    for i in range(18):                                                 # the cracked glow strip
        x, y = 7 + i, 18 - i // 3
        if (x, y) in ((13, 16), (14, 16), (19, 14)):
            continue
        if im.getpixel((x, y))[3]:
            px(im, x, y, E_GLOW if i % 4 else E_GLOW_L)
    for i, (x, y) in enumerate([(16, 9), (17, 10), (17, 11), (18, 12), (19, 13), (19, 14), (20, 15)]):
        px(im, x, y, E_BLACK)                                           # the crack
    for bx, by in ((9, 9), (22, 22)):
        rect(im, bx, by, bx + 1, by + 1, E_VIOLET_L)
    outline(im)
    return im


def shield_core():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy = 15.5, 15.5

    def hex_d(x, y):
        """A hexagon's «radius» of (x, y) round the middle (pointy left and right)."""
        dx, dy = abs(x - cx), abs(y - cy)
        return max(dx * 0.866 + dy * 0.5, dy)

    for y in range(S):
        for x in range(S):
            d = hex_d(x + 0.5, y + 0.5)
            if d > 13.2:
                continue
            if d > 10.5:                                                # the frame
                c = C_FRAME_L if y < cy - 4 else (C_FRAME_D if y > cy + 4 else C_FRAME)
                if (x + y) % 6 == 0:
                    c = C_FRAME_D
            else:                                                       # the honeycomb, brighter to the heart
                t = d / 10.5
                c = mix(C_HEART, C_CELL, min(1.0, t * 1.3))
                hx = (x + (y // 4 % 2) * 2) % 4
                if y % 4 == 0 or hx == 0:
                    c = mix(c, C_CELL_L if t < 0.5 else C_CELL, 0.6) if t < 0.35 else mix(C_FRAME_D, C_CELL, 0.45)
            px(im, x, y, c)
    for (x, y) in ((4, 15), (27, 15), (10, 5), (21, 5), (10, 26), (21, 26)):    # the six mounting studs
        rect(im, x, y, x + 1, y + 1, C_FRAME_L)
    px(im, 14, 13, (255, 255, 255))
    px(im, 15, 13, (255, 255, 255))
    outline(im)
    return im


def main():
    for folder in ('entity', 'items'):
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
    skins = [('trooper', trooper()), ('jammer', jammer()), ('hollowShell', hollow_shell()), ('repairDrone', repair_drone()),
             ('executioner', executioner())]
    icons = [('loot_quantumScrap', quantum_scrap()), ('loot_exoScrap', exo_scrap()), ('loot_shieldCore', shield_core())]
    for name, im in skins:
        im.save(os.path.join(TEX, 'entity', name + '.png'))
    for name, im in icons:
        im.save(os.path.join(TEX, 'items', name + '.png'))
    # the preview: the five skins x2 on two rows, the three icons x4, on grey
    z = 2
    w = 3 * 128 * z + 40
    sheet = Image.new('RGBA', (w + 3 * S * 4 + 20, 2 * 64 * z + 30), (70, 72, 76, 255))
    for i, (name, im) in enumerate(skins):
        big = im.resize((128 * z, 64 * z), Image.NEAREST)
        sheet.paste(big, (10 + (i % 3) * (128 * z + 10), 10 + (i // 3) * (64 * z + 10)), big)
    for i, (name, im) in enumerate(icons):
        big = im.resize((S * 4, S * 4), Image.NEAREST)
        sheet.paste(big, (w + i * (S * 4 + 5), 10), big)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
