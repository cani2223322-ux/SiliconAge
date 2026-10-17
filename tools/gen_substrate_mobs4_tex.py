# -*- coding: utf-8 -*-
"""Подложка (Si-14), the armoured creatures of phase 4 (Silicon Age, docs/plan-substrate-tech-f4.md §7.1-§7.4, package M).

Writes into src/main/resources/assets/siliconage/textures:
  entity/overheated.png   - «Перегретый»: soot-black scorched plating cracked with molten orange (ModelBiped's 64 x 32 layout
                            drawn at 128 x 64, as the Ф1-Ф3 skins); the joints glow, two slit eyes burn yellow-white. The
                            renderer draws this skin again additively in orange, so the cracks glow through the armour's gaps;
  entity/warlord.png      - «Вожак лагеря»: a dark crimson undersuit with gold piping and, on the hat layer's UV, its crest
                            (32, 0: crimson bristles on a black ridge) and its cape (46, 0: crimson cloth, a darker lining,
                            the hem tattered - the alpha test cuts the holes);
  entity/sentinel.png     - «Сингулярный часовой»: a void-black undersuit with thin white seams and a white visor line (the
                            Singular scheme H «Пустота»: #0C0C0C / #EBEBEB);
  entity/eclipse.png      - «Затмение»: black plating veined with crimson (the scheme C «Багровая»: #280E14 / #FF465A), a
                            crimson visor;
  items/loot_boiledLoop   - «Выкипевший контур»: a coil of a helium loop's copper tubing, scorched black, split open where it
                            boiled out, a wisp of frost-white vapour;
  items/loot_singularScrap- «Лом Сингулярной брони»: a jagged shard of violet-black Singular plating with its glowing violet
                            line broken off;
and a preview sheet next to this script (the four skins x2, the two icons x4).

Usage: python gen_substrate_mobs4_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'substrate_mobs4_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# skins: the model's UV pixels drawn 2 x 2
K = 2

JOINT = (26, 24, 28)
# the Overheated: soot and molten metal
SOOT = (34, 28, 26)
SOOT_D = (20, 17, 16)
SOOT_L = (58, 46, 40)
EMBER = (196, 70, 24)
MOLTEN = (255, 128, 40)
MOLTEN_L = (255, 214, 120)
# the Warlord: dark crimson and gold
WCRIMSON = (88, 18, 26)
WCRIMSON_D = (54, 10, 16)
WCRIMSON_L = (134, 34, 40)
GOLD = (214, 166, 60)
GOLD_D = (150, 110, 36)
CAPE = (150, 22, 32)
CAPE_D = (98, 12, 20)
CAPE_L = (196, 44, 52)
BRISTLE = (204, 30, 40)
BRISTLE_L = (255, 90, 90)
# the Sentinel: the scheme H «Пустота»
VOID = (12, 12, 12)
VOID_D = (4, 4, 6)
VOID_L = (30, 30, 34)
SEAM = (235, 235, 235)
SEAM_D = (150, 150, 156)
# «Затмение»: the scheme C «Багровая»
ECL = (40, 14, 20)
ECL_D = (20, 6, 10)
ECL_L = (64, 24, 32)
VEIN = (255, 70, 90)
VEIN_D = (170, 36, 52)
VEIN_L = (255, 180, 186)
# items
CU = (196, 112, 64)
CU_D = (128, 66, 36)
CU_L = (236, 168, 110)
SCORCH = (46, 34, 30)
FROST = (230, 242, 255)
FROST_D = (170, 196, 222)
S_BASE = (34, 22, 48)
S_DARK = (20, 12, 30)
S_FACET = (60, 40, 86)
S_GLOW = (190, 110, 255)
S_GLOW_L = (236, 210, 255)


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
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }


HEAD = faces(0, 0, 8, 8, 8)
BODY = faces(16, 16, 8, 12, 4)
ARM = faces(40, 16, 4, 12, 4)
LEG = faces(0, 16, 4, 12, 4)


def paint(im, face, fn):
    """fn(x, y, w, h) -> colour or None for each REAL pixel of a face (x, y within it, w, h its real size)."""
    u, v, w, h = face
    for y in range(h * K):
        for x in range(w * K):
            px(im, u * K + x, v * K + y, fn(x, y, w * K, h * K))


def suit_fn(base, dark, light, joint, pipe, joint_rows=(), joint_col=None):
    """An undersuit: shaded plating, a darker hem, lit top-left edges, the joints' bands at `joint_rows` (real px)."""
    def fn(x, y, w, h):
        if any(r0 <= y <= r1 for r0, r1 in joint_rows):
            return joint_col(x, y) if joint_col else (joint if (x + y) % 3 else mix(joint, (0, 0, 0), 0.4))
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


def cracks(im, face, seed, colours, count=3):
    """Branching cracks across a face: random walks from its edges in the crack colours (centre bright, rims darker)."""
    u, v, w, h = face
    rnd = random.Random(seed)
    W, H = w * K, h * K
    for _ in range(count):
        x, y = rnd.randrange(W), rnd.choice((0, H - 1)) if rnd.random() < 0.5 else rnd.randrange(H)
        dx, dy = rnd.choice((-1, 0, 1)), rnd.choice((-1, 1))
        for step in range(rnd.randint(6, 16)):
            c = colours[0] if step % 5 else colours[1]
            px(im, u * K + x, v * K + y, c)
            if rnd.random() < 0.25:
                px(im, u * K + x + 1, v * K + y, colours[2])
            x += dx if rnd.random() < 0.6 else rnd.choice((-1, 0, 1))
            y += dy if rnd.random() < 0.7 else 0
            x = max(0, min(W - 1, x))
            y = max(0, min(H - 1, y))


def overheated():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def molten_joint(x, y):
        return MOLTEN if (x + y) % 4 else MOLTEN_L

    seed = 41
    for part, rows in ((HEAD, ()), (BODY, ((19, 20),)), (ARM, ((10, 13), (20, 23))), (LEG, ((10, 13), (20, 23)))):
        for name, f in part.items():
            r = rows if name not in ('top', 'bottom') else ()
            paint(im, f, suit_fn(SOOT, SOOT_D, SOOT_L, EMBER, None, r, molten_joint))
            if name not in ('top', 'bottom'):
                seed += 7
                cracks(im, f, seed, (MOLTEN, MOLTEN_L, EMBER), 2 if part is not BODY else 3)
    u, v, w, h = HEAD['front']                                          # the slit eyes
    x0, y0 = u * K, v * K
    rect(im, x0 + 2, y0 + 6, x0 + 13, y0 + 8, SOOT_D)
    rect(im, x0 + 3, y0 + 7, x0 + 6, y0 + 7, MOLTEN_L)
    rect(im, x0 + 9, y0 + 7, x0 + 12, y0 + 7, MOLTEN_L)
    rect(im, x0 + 5, y0 + 11, x0 + 10, y0 + 12, EMBER)                  # a vent where the mouth was
    for gx in range(6, 10, 2):
        px(im, x0 + gx, y0 + 11, MOLTEN)
    return im


def warlord():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    for name, f in HEAD.items():
        paint(im, f, suit_fn(WCRIMSON_D, JOINT, WCRIMSON, JOINT, None))
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 2, y0 + 6, x0 + 13, y0 + 8, JOINT)                    # the visor, a gold rim
    rect(im, x0 + 3, y0 + 7, x0 + 12, y0 + 7, (255, 70, 60))
    rect(im, x0 + 2, y0 + 5, x0 + 13, y0 + 5, GOLD_D)
    for name, f in BODY.items():
        paint(im, f, suit_fn(WCRIMSON, WCRIMSON_D, WCRIMSON_L, JOINT, GOLD if name in ('front', 'back') else None, ((19, 20),)))
    for name, f in ARM.items():
        paint(im, f, suit_fn(WCRIMSON, WCRIMSON_D, WCRIMSON_L, JOINT, None, ((10, 13), (20, 23)) if name != 'top' else ()))
    for name, f in LEG.items():
        paint(im, f, suit_fn(WCRIMSON, WCRIMSON_D, WCRIMSON_L, JOINT, GOLD if name == 'front' else None,
                             ((10, 13), (20, 23)) if name != 'top' else ()))

    # the crest: box 1 x 3 x 6 at (32, 0) - bristles standing up from a black ridge
    def crest(name):
        def fn(x, y, w, h):
            if name == 'bottom':
                return JOINT
            if name == 'top':
                return BRISTLE_L if x % 2 == 0 else BRISTLE
            if y >= h - 2:
                return JOINT                                            # the ridge it sits on
            return BRISTLE_L if (x + 2 * y) % 5 == 0 else (BRISTLE if x % 2 == 0 else mix(BRISTLE, WCRIMSON_D, 0.4))
        return fn

    for name, f in faces(32, 0, 1, 3, 6).items():
        paint(im, f, crest(name))

    # the cape: box 8 x 12 x 1 at (46, 0) - the outer side (back) crimson with a gold hem line, the lining darker, the hem torn
    rnd = random.Random(13)
    torn = [rnd.randint(0, 5) for _ in range(16)]

    def cape(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom', 'left', 'right'):
                return CAPE_D
            if y >= h - torn[min(len(torn) - 1, x)]:
                return None                                             # the tattered hem: holes
            if name == 'front':
                return mix(CAPE_D, JOINT, 0.3)                          # the lining, towards the body
            if y in (1, 2):
                return GOLD if x % 3 else GOLD_D
            fold = (x // 3) % 2
            return CAPE_L if fold == 0 and (y + x) % 7 == 0 else (CAPE if fold == 0 else mix(CAPE, CAPE_D, 0.5))
        return fn

    for name, f in faces(46, 0, 8, 12, 1).items():
        paint(im, f, cape(name))
    return im


def seam_suit(base, dark, light, seam, seam_d, visor):
    """A Singular-styled undersuit: dark plating, thin bright seams along the parts' edges and across the joints."""
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def fn_for(rows):
        def fn(x, y, w, h):
            if any(r0 <= y <= r1 for r0, r1 in rows):
                return seam if (x // 2) % 2 == 0 else seam_d
            if x == 1 and y % 6 != 5:
                return seam_d
            if x == 0 or y == 0:
                return light
            if x == w - 1 or y == h - 1:
                return dark
            if (x * 5 + y * 3) % 23 == 0:
                return light
            return base
        return fn

    for part, rows in ((HEAD, ()), (BODY, ((19, 19),)), (ARM, ((11, 11), (21, 21))), (LEG, ((11, 11), (21, 21)))):
        for name, f in part.items():
            paint(im, f, fn_for(rows if name not in ('top', 'bottom') else ()))
    u, v, w, h = HEAD['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 2, y0 + 6, x0 + 13, y0 + 8, dark)
    rect(im, x0 + 3, y0 + 7, x0 + 12, y0 + 7, visor)
    return im


def sentinel():
    im = seam_suit(VOID, VOID_D, VOID_L, SEAM, SEAM_D, SEAM)
    u, v, w, h = BODY['front']                                          # a white ring sigil on the chest
    cx, cy = u * K + 8, v * K + 9
    for a in range(0, 360, 15):
        px(im, int(round(cx + math.cos(math.radians(a)) * 3.5)), int(round(cy + math.sin(math.radians(a)) * 3.5)), SEAM)
    return im


def eclipse():
    im = seam_suit(ECL, ECL_D, ECL_L, VEIN_D, ECL_D, VEIN_L)
    seed = 77
    for part in (BODY, ARM, LEG, HEAD):                                 # crimson veins running through the plating
        for name, f in part.items():
            if name in ('top', 'bottom'):
                continue
            seed += 5
            cracks(im, f, seed, (VEIN, VEIN_L, VEIN_D), 2)
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


def boiled_loop():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy = 15.5, 17.0
    # a coil of tubing: two turns of a thick ring (an ellipse, the far half darker), scorched in patches, split at the right
    for turn, (rx, ry, dy) in enumerate(((11.0, 7.0, -2.5), (9.5, 6.0, 2.5))):
        for y in range(S):
            for x in range(S):
                ex, ey = (x + 0.5 - cx) / rx, (y + 0.5 - (cy + dy)) / ry
                d = math.sqrt(ex * ex + ey * ey)
                if abs(d - 1.0) > 0.17:
                    continue
                a = math.degrees(math.atan2(ey, ex)) % 360
                if 340 <= a or a <= 8:
                    continue                                            # the split where it boiled out
                far = ey < 0
                t = 0.5 + 0.5 * (d - 1.0) / 0.17
                c = mix(CU_L, CU_D, t)
                if far:
                    c = mix(c, CU_D, 0.45)
                if (int(a) // 23 + turn) % 3 == 0 or (x * 3 + y * 5) % 19 == 0:
                    c = mix(c, SCORCH, 0.65)                            # scorched black
                px(im, x, y, c)
    for (x, y) in ((26, 15), (27, 16), (26, 19), (27, 20)):           # the torn lips of the split
        px(im, x, y, CU_L)
    for i, (x, y) in enumerate(((28, 13), (29, 11), (28, 9), (29, 7), (30, 5), (28, 18), (29, 17))):
        px(im, x, y, FROST if i % 2 == 0 else FROST_D)                  # a wisp of vapour
    for bx, by in ((6, 10), (23, 25)):                                  # the fittings
        rect(im, bx, by, bx + 1, by + 1, mix(CU_D, SCORCH, 0.4))
    outline(im)
    return im


def singular_scrap():
    im = Image.new('RGBA', (S, S), CLEAR)
    poly = [(5, 9), (12, 4), (22, 5), (28, 10), (27, 17), (29, 25), (20, 28), (13, 25), (6, 27), (4, 18)]
    for y in range(S):
        for x in range(S):
            if inside(poly, x + 0.5, y + 0.5):
                facet = ((x + 2 * y) // 6) % 3
                c = (S_DARK, S_BASE, S_FACET)[facet]
                if (x * 3 + y) % 17 == 0:
                    c = mix(c, S_GLOW, 0.25)
                px(im, x, y, c)
    for i in range(20):                                                 # the glowing line, broken off at one end
        x, y = 6 + i, 20 - (i * 2) // 3
        if (x, y) in ((15, 14), (16, 14)):
            continue
        if im.getpixel((x, y))[3]:
            px(im, x, y, S_GLOW if i % 4 else S_GLOW_L)
            if im.getpixel((x, y + 1))[3]:
                px(im, x, y + 1, mix(S_GLOW, S_BASE, 0.5))
    for x, y in ((18, 7), (18, 8), (19, 9), (19, 10), (20, 11)):        # a crack
        px(im, x, y, (6, 4, 10))
    for bx, by in ((9, 9), (23, 22)):
        rect(im, bx, by, bx + 1, by + 1, S_FACET)
    outline(im)
    return im


def main():
    for folder in ('entity', 'items'):
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
    skins = [('overheated', overheated()), ('warlord', warlord()), ('sentinel', sentinel()), ('eclipse', eclipse())]
    icons = [('loot_boiledLoop', boiled_loop()), ('loot_singularScrap', singular_scrap())]
    for name, im in skins:
        im.save(os.path.join(TEX, 'entity', name + '.png'))
    for name, im in icons:
        im.save(os.path.join(TEX, 'items', name + '.png'))
    # the preview: the four skins x2 on two rows, the two icons x4, on grey
    z = 2
    w = 2 * 128 * z + 30
    sheet = Image.new('RGBA', (w + 2 * S * 4 + 20, 2 * 64 * z + 30), (70, 72, 76, 255))
    for i, (name, im) in enumerate(skins):
        big = im.resize((128 * z, 64 * z), Image.NEAREST)
        sheet.paste(big, (10 + (i % 2) * (128 * z + 10), 10 + (i // 2) * (64 * z + 10)), big)
    for i, (name, im) in enumerate(icons):
        big = im.resize((S * 4, S * 4), Image.NEAREST)
        sheet.paste(big, (w + i * (S * 4 + 5), 10), big)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
