# -*- coding: utf-8 -*-
"""Подложка (Si-14), the mobs of phase 1 (Silicon Age, docs/plan-substrate-tech.md §5.8, package M).

Writes into src/main/resources/assets/siliconage/textures:
  entity/lineman.png        - «Обходчик»: the skin of an empty Nano shell on vanilla's biped (ModelBiped's 64 x 32 layout
                              drawn at 128 x 64, as the mod's armour is): only a dark frame along the edges, ribs, a torn
                              copper cable and one yellow diode on the face - everything else transparent (the renderer's
                              alpha test cuts it), so the worn pieces hang on an empty frame;
  entity/marauder.png       - «Мародёр»: a thin outcast in a dirty orange coverall, welding goggles, a battery on the chest
                              wrapped in blue insulating tape, dark gloves and boots;
  items/loot_shellFirmware  - «Прошивка оболочки»: a torn green board with a chip, gold contacts and the yellow diode;
  items/loot_nanoScrap      - «Лом брони Нано»: a bent olive shard of a Nano plate with bolts and a broken light strip;
  items/trophy_overlay      - the trophy's second pass over the real item's icon: a dull red tag with a string in the
                              lower left corner and a few scratches in the middle (opaque pixels only: the GUI may draw
                              the pass without blending);
and a preview sheet next to this script (the two skins in their UV layout x2, the three icons x4).

Usage: python gen_substrate_mobs_tex.py [project dir]
"""
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'substrate_mobs_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# skins: the biped's UV pixels drawn 2 x 2
K = 2

# the Nano palette (armorNano*.png)
NANO = (74, 80, 71)
NANO_D = (48, 53, 46)
OLIVE = (98, 112, 58)
OLIVE_D = (67, 78, 39)
OLIVE_L = (138, 154, 82)
GLOW = (92, 255, 106)
# the Lineman
FRAME = (40, 45, 51)
FRAME_L = (74, 82, 92)
FRAME_D = (24, 27, 31)
COPPER = (196, 112, 58)
COPPER_D = (128, 70, 36)
DIODE = (255, 224, 64)
DIODE_L = (255, 246, 190)
# the Marauder
SUIT = (184, 104, 44)
SUIT_D = (140, 78, 34)
SUIT_L = (216, 132, 64)
GRIME = (110, 90, 68)
SKIN = (184, 154, 132)
SKIN_D = (146, 118, 100)
HAIR = (58, 46, 38)
STRAP = (42, 36, 32)
LENS = (224, 160, 64)
GLOVE = (58, 52, 48)
BOOT = (60, 46, 36)
TAPE = (46, 95, 200)
TAPE_L = (90, 140, 230)
BATT = (60, 64, 68)
BATT_L = (98, 104, 110)
RED = (180, 40, 36)
# items
PCB = (46, 90, 58)
PCB_D = (30, 60, 40)
PCB_L = (95, 160, 112)
GOLD = (224, 184, 80)
CHIP = (34, 38, 42)
PIN = (170, 176, 186)
BOLT = (154, 160, 142)
TAG = (150, 52, 40)
TAG_D = (96, 30, 24)
TAG_L = (196, 90, 70)
STRING = (210, 196, 160)
SCRATCH = (214, 214, 204)
RUST = (150, 74, 44)


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


# ---------------------------------------------------------------- skins: ModelBiped's boxes

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


def frame_fn(rib_every=0, rib_from=0):
    """The Lineman's frame: a 2-px dark edge with a 1-px lit outer line, ribs every `rib_every` real px across."""
    def fn(x, y, w, h):
        edge = min(x, y, w - 1 - x, h - 1 - y)
        if edge == 0:
            return FRAME_L if (x + y) % 5 else FRAME
        if edge == 1:
            return FRAME_D
        if rib_every and y >= rib_from and (y - rib_from) % rib_every in (0, 1):
            return FRAME if (y - rib_from) % rib_every == 0 else FRAME_D
        return None
    return fn


def lineman():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    for name, f in HEAD.items():
        paint(im, f, frame_fn())
    for name, f in BODY.items():
        paint(im, f, frame_fn(6 if name in ('front', 'back', 'right', 'left') else 0, 5))
    for part in (ARM, LEG):
        for name, f in part.items():
            paint(im, f, frame_fn(6 if name not in ('top', 'bottom') else 0, 4))
    # the head's front: a dark slit across the eyes and the one yellow diode in the middle of it
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 3, y0 + 6, x0 + 12, y0 + 7, FRAME_D)
    rect(im, x0 + 6, y0 + 5, x0 + 9, y0 + 8, FRAME)
    rect(im, x0 + 7, y0 + 6, x0 + 8, y0 + 7, DIODE)
    px(im, x0 + 7, y0 + 6, DIODE_L)
    # a spine down the body's front and back, the torn copper cable hanging from the left shoulder
    for f in (BODY['front'], BODY['back']):
        u, v, w, h = f
        rect(im, u * K + 7, v * K + 2, u * K + 8, v * K + h * K - 3, FRAME)
    u, v, w, h = BODY['front']
    x0, y0 = u * K, v * K
    cable = [(11, 2), (11, 3), (12, 4), (12, 5), (12, 6), (11, 7), (11, 8), (10, 9), (10, 10), (10, 11), (11, 12), (11, 13)]
    for i, (cx, cy) in enumerate(cable):
        px(im, x0 + cx, y0 + cy, COPPER if i % 3 else COPPER_D)
        px(im, x0 + cx + 1, y0 + cy, COPPER_D)
    px(im, x0 + 11, y0 + 14, (255, 210, 140))           # the bare strands at the torn end
    px(im, x0 + 12, y0 + 15, COPPER)
    # the cable's end goes on down the right arm's front
    u, v, w, h = ARM['front']
    for cy in range(2, 14):
        px(im, u * K + 3 + (cy // 4) % 2, v * K + cy, COPPER if cy % 3 else COPPER_D)
    return im


def marauder():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def suit(x, y, w, h):
        c = SUIT
        if (x * 7 + y * 13) % 23 == 0 or (x * 3 + y * 5) % 31 == 0:
            c = GRIME
        elif (x + y * 3) % 11 == 0:
            c = SUIT_D
        elif x == 0 or y == 0:
            c = SUIT_L
        elif x == w - 1 or y == h - 1:
            c = SUIT_D
        return c

    # the head: hair on top and at the back, the gaunt face, the goggles' strap round it
    def head(name):
        def fn(x, y, w, h):
            if name == 'top':
                return HAIR if (x * 5 + y * 3) % 7 else mix(HAIR, (0, 0, 0), 0.3)
            if name == 'bottom':
                return SKIN_D
            if y < 3 or (name == 'back' and y < 11):
                return HAIR if (x + y) % 4 else mix(HAIR, (0, 0, 0), 0.3)
            if 5 <= y <= 7:
                return STRAP
            c = SKIN
            if name == 'front':
                if y >= 12 and (x * 3 + y) % 3 == 0:
                    c = SKIN_D                               # stubble
                if 11 <= y <= 12 and (x <= 3 or x >= 12):
                    c = SKIN_D                               # hollow cheeks
            elif y > 7 and (x + y) % 6 == 0:
                c = SKIN_D
            return c
        return fn

    for name, f in HEAD.items():
        paint(im, f, head(name))
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    for gx in (2, 9):                                        # the goggles' two round lenses
        rect(im, x0 + gx, y0 + 4, x0 + gx + 4, y0 + 8, STRAP)
        rect(im, x0 + gx + 1, y0 + 5, x0 + gx + 3, y0 + 7, LENS)
        px(im, x0 + gx + 1, y0 + 5, (255, 220, 150))
    rect(im, x0 + 5, y0 + 13, x0 + 10, y0 + 13, SKIN_D)      # a thin mouth
    rect(im, x0 + 7, y0 + 9, x0 + 8, y0 + 11, SKIN_D)        # the nose's shadow
    # the coverall
    for name, f in BODY.items():
        paint(im, f, suit)
    u, v, w, h = BODY['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 7, y0, x0 + 8, y0 + h * K - 1, (120, 120, 116))        # the zip
    rect(im, x0, y0 + 19, x0 + w * K - 1, y0 + 20, STRAP)                # the belt
    rect(im, x0 + 7, y0 + 19, x0 + 8, y0 + 20, (170, 160, 120))
    # the battery on the chest, wrapped in blue insulating tape, a red lead to the belt
    rect(im, x0 + 2, y0 + 3, x0 + 11, y0 + 10, OUTLINE)
    rect(im, x0 + 3, y0 + 4, x0 + 10, y0 + 9, BATT)
    rect(im, x0 + 3, y0 + 4, x0 + 10, y0 + 4, BATT_L)
    for tx in (4, 8):
        rect(im, x0 + tx, y0 + 3, x0 + tx + 1, y0 + 10, TAPE)
        px(im, x0 + tx, y0 + 3, TAPE_L)
    px(im, x0 + 10, y0 + 5, RED)
    for i, (cx, cy) in enumerate([(11, 6), (12, 7), (12, 8), (13, 9), (13, 10), (13, 11), (12, 12), (12, 13)]):
        px(im, x0 + cx, y0 + cy, RED if i % 2 else mix(RED, (0, 0, 0), 0.3))
    # the back: a patch and the battery's straps
    u, v, w, h = BODY['back']
    x0, y0 = u * K, v * K
    rect(im, x0 + 4, y0 + 8, x0 + 11, y0 + 14, SUIT_D)
    for sx in range(4, 12, 2):
        px(im, x0 + sx, y0 + 8, GRIME)
    # sleeves, tape on the forearm, gloves
    def arm(name):
        def fn(x, y, w, h):
            if name == 'bottom' or (name != 'top' and y >= h - 6):
                return GLOVE if (x + y) % 5 else mix(GLOVE, (0, 0, 0), 0.25)
            if name != 'top' and y in (13, 14):
                return TAPE if y == 13 else mix(TAPE, (0, 0, 0), 0.2)
            return suit(x, y, w, h)
        return fn

    for name, f in ARM.items():
        paint(im, f, arm(name))
    # trousers, a darker knee patch, boots
    def leg(name):
        def fn(x, y, w, h):
            if name == 'bottom' or (name != 'top' and y >= h - 6):
                return BOOT if (x + 2 * y) % 6 else mix(BOOT, (0, 0, 0), 0.3)
            if name == 'front' and 8 <= y <= 13 and 1 <= x <= w - 2:
                return SUIT_D if (x + y) % 3 else GRIME
            return suit(x, y, w, h)
        return fn

    for name, f in LEG.items():
        paint(im, f, leg(name))
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


def firmware():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the board, its top-left corner torn off along a jagged line
    for y in range(7, 25):
        for x in range(4, 28):
            if x + y < 18 + (x * 7 % 3):
                continue
            c = PCB
            if y == 7 or x == 4:
                c = PCB_L
            elif y == 24 or x == 27:
                c = PCB_D
            px(im, x, y, c)
    for x in range(9, 26):                                     # the traces
        if x % 4 != 1:
            px(im, x, 10, PCB_L)
    for y in range(11, 21):
        px(im, 25, y, PCB_L)
    for x in range(6, 12):
        px(im, x, 21, PCB_L)
    # the chip with its silver legs
    rect(im, 12, 12, 21, 19, CHIP)
    rect(im, 12, 12, 21, 12, (62, 68, 74))
    for x in range(13, 21, 2):
        px(im, x, 11, PIN)
        px(im, x, 20, PIN)
    px(im, 13, 13, (110, 118, 126))                             # the pin-1 dot
    # the gold edge contacts
    for x in range(7, 26, 2):
        rect(im, x, 23, x, 25, GOLD)
    # the yellow diode in the corner and the cut copper wire
    rect(im, 23, 8, 25, 9, DIODE)
    px(im, 23, 8, DIODE_L)
    for i, (x, y) in enumerate([(27, 13), (28, 12), (29, 12), (30, 11)]):
        px(im, x, y, COPPER if i % 2 else COPPER_D)
    px(im, 31, 10, (255, 210, 140))
    outline(im)
    return im


def scrap():
    im = Image.new('RGBA', (S, S), CLEAR)
    # a jagged, bent shard of a Nano plate: lighter towards the top-left, a panel seam, two bolts
    poly = [(5, 9), (12, 5), (20, 7), (27, 4), (26, 13), (29, 19), (22, 26), (13, 24), (8, 27), (6, 18)]

    def inside(x, y):
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

    for y in range(S):
        for x in range(S):
            if inside(x + 0.5, y + 0.5):
                t = (x + y) / 60.0
                c = mix(OLIVE_L, OLIVE_D, t)
                if (x * 5 + y * 7) % 19 == 0:
                    c = NANO
                px(im, x, y, c)
    for i in range(18):                                         # the bend: a darker crease across
        x, y = 8 + i, 20 - i // 2
        if im.getpixel((x, y))[3]:
            px(im, x, y, OLIVE_D)
        if im.getpixel((x, y + 1))[3]:
            px(im, x, y + 1, NANO_D)
    for bx, by in ((11, 10), (22, 17)):                         # bolts
        rect(im, bx, by, bx + 1, by + 1, BOLT)
        px(im, bx + 1, by + 1, NANO_D)
    for i, x in enumerate(range(13, 24)):                       # the broken light strip
        if x in (18, 19):
            continue
        px(im, x, 7 + (x - 13) // 5, GLOW if i % 3 else (214, 255, 216))
    outline(im)
    return im


def trophy_overlay():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the tag in the lower left corner, its hole and the string up to the item
    for y in range(22, 31):
        for x in range(1, 10):
            if x + (30 - y) < 3:                                 # cut corner
                continue
            c = TAG
            if y == 22 or x == 1:
                c = TAG_L
            elif y == 30 or x == 9:
                c = TAG_D
            px(im, x, y, c)
    px(im, 7, 24, OUTLINE)
    px(im, 7, 25, TAG_D)
    for x, y in ((3, 27), (4, 27), (5, 27), (3, 28), (5, 28), (3, 29), (4, 29), (5, 29)):
        px(im, x, y, TAG_D)                                     # a scratched-out mark on the tag
    for x, y in ((8, 23), (9, 22), (10, 21), (11, 20), (12, 19)):
        px(im, x, y, STRING)
    # the tag's own outline only (the scratches stay thin)
    src = im.copy()
    for y in range(S):
        for x in range(S):
            if src.getpixel((x, y))[3] or not (0 <= x <= 10 and 20 <= y <= 31):
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < S and 0 <= ny < S and src.getpixel((nx, ny))[3] and src.getpixel((nx, ny))[:3] != STRING:
                    px(im, x, y, OUTLINE)
                    break
    # scratches and rust specks in the middle (where every item's icon has pixels)
    for x, y in ((13, 12), (14, 13), (15, 14), (16, 15), (18, 11), (19, 12), (20, 13)):
        px(im, x, y, SCRATCH)
    for x, y in ((12, 17), (17, 18), (21, 16), (15, 10)):
        px(im, x, y, RUST)
    return im


def main():
    os.makedirs(os.path.join(TEX, 'entity'), exist_ok=True)
    out = {
        ('entity', 'lineman'): lineman(),
        ('entity', 'marauder'): marauder(),
        ('items', 'loot_shellFirmware'): firmware(),
        ('items', 'loot_nanoScrap'): scrap(),
        ('items', 'trophy_overlay'): trophy_overlay(),
    }
    for (folder, name), im in out.items():
        im.save(os.path.join(TEX, folder, name + '.png'))
    # the preview: the two skins and the three icons, x4, on grey
    z = 4
    sheet = Image.new('RGBA', (2 * 128 * z // 2 + 3 * S * z + 40, 64 * z // 2 + 20), (70, 72, 76, 255))
    x = 10
    for key in (('entity', 'lineman'), ('entity', 'marauder')):
        big = out[key].resize((128 * z // 2, 64 * z // 2), Image.NEAREST)
        sheet.paste(big, (x, 10), big)
        x += 128 * z // 2 + 5
    for key in (('items', 'loot_shellFirmware'), ('items', 'loot_nanoScrap'), ('items', 'trophy_overlay')):
        big = out[key].resize((S * z, S * z), Image.NEAREST)
        sheet.paste(big, (x, 10), big)
        x += S * z + 5
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
