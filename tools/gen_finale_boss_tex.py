# -*- coding: utf-8 -*-
"""Подложка (Si-14), phase 5: the Breakdown Warden and its Containment Coils (Silicon Age, docs/plan-substrate-tech-f5.md §5, package B).

Writes into src/main/resources/assets/siliconage/textures (the models' UV drawn 2 x 2, as tools/gen_substrate_noise_tex.py does):
  entity/breakdownWarden.png - «Смотритель Пробоя» (ModelBreakdownWardenSC, 128 x 64 -> 256 x 128): the Singular suit in the
                               Lithographers' colours (SingularScheme L: the near-black plates 0x1C1A24 with pale gold seams
                               0xF0E2B4), the chestplate-reactor's front - the core's rings and the violet-white scars of the
                               Breakdown running out from it, a heat sink of fins on its back, a small helmet with a gold visor
                               slit, four armoured arms (the upper pair's gold bands, the lower pair's copper-wound forearms and
                               steel claws), the hips' plate with cable sockets, the skirt's cables (black rubber under a copper
                               braid, steel plugs), heavy boots with gold toe caps;
  entity/containmentCoil.png - «Катушка удержания» (ModelContainmentCoilSC, 64 x 32 -> 128 x 64): the dark plated base with its
                               gold rim, the steel post's ceramic insulator rings, three copper coil rings (the windings seen from
                               above and the side), the glass core (pale blue - the renderer lights it), the emitter cap;
and a preview sheet next to this script (both skins x2).

Usage: python gen_finale_boss_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'finale_boss_preview.png')
CLEAR = (0, 0, 0, 0)

# skins: the model's UV pixels drawn 2 x 2
K = 2

# «Литограф» (SingularScheme.L): the plates and the light
PLATE = (28, 26, 36)
PLATE_D = (16, 15, 22)
PLATE_M = (40, 37, 52)
PLATE_L = (60, 56, 76)
GOLD = (240, 226, 180)
GOLD_D = (168, 150, 104)
GOLD_M = (206, 190, 140)
# the Breakdown's scars and the reactor
SCAR = (196, 150, 255)
SCAR_L = (236, 220, 255)
CORE = (255, 248, 226)
# cables, steel
RUBBER = (24, 25, 30)
RUBBER_L = (46, 48, 56)
COPPER = (196, 112, 52)
COPPER_D = (132, 70, 30)
COPPER_L = (238, 166, 96)
STEEL = (128, 134, 142)
STEEL_D = (82, 86, 94)
STEEL_L = (176, 182, 190)
# the coil
CERAMIC = (222, 214, 196)
CERAMIC_D = (150, 140, 120)
GLASS = (170, 214, 236)
GLASS_L = (226, 244, 255)
GLASS_D = (110, 150, 176)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def px(im, x, y, c):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], 255))


def noise(x, y, seed=0):
    """A small repeatable hash noise in 0..1."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


# ---------------------------------------------------------------- the models' boxes

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


def box(im, u, v, w, h, d, fn_by_face):
    for name, f in faces(u, v, w, h, d).items():
        paint(im, f, fn_by_face(name))


def plate(seed, seam=GOLD_D, panel=7, rivets=True):
    """A Singular plate: near-black, its edges lit pale gold (the seams), fine panel lines, a rivet now and then."""
    def fn(x, y, w, h):
        edge = min(x, y, w - 1 - x, h - 1 - y)
        if edge == 0:
            return seam if (x + y) % 5 else GOLD_M                       # the seam, a brighter glint now and then
        if edge == 1:
            return PLATE_L if x == 1 or y == 1 else PLATE_D
        if panel and (y % (panel * K) == panel * K - 1):
            return PLATE_D                                              # the panels' joints
        if rivets and edge == 2 and (x + y) % 6 == 0:
            return GOLD_D
        n = noise(x, y, seed)
        if n < 0.07:
            return PLATE_M
        if n > 0.96:
            return PLATE_L
        return mix(PLATE, PLATE_D, 0.25 * (y / float(max(1, h))))
    return fn


def scar(fn, seed, density=0.7):
    """A plate crossed by the Breakdown's violet-white scars (thin jagged lines)."""
    def g(x, y, w, h):
        for s in range(2):
            ph = seed * 3.1 + s * 2.3
            cx = w * (0.3 + 0.4 * noise(s, seed, 7))
            line = cx + math.sin(y * 0.35 + ph) * 2.2 + (y - h / 2.0) * (0.5 if s else -0.6)
            if abs(x - line) < density and noise(x, y, seed + s) > 0.25:
                return SCAR_L if abs(x - line) < 0.35 else SCAR
        return fn(x, y, w, h)
    return g


def rubber_braid(name):
    def fn(x, y, w, h):
        if name in ('top', 'bottom'):
            return COPPER_D
        if (x + y) % 6 in (0, 1) or (x - y) % 6 in (0, 1):
            return COPPER_L if (x + y) % 12 < 2 else COPPER        # the copper braid over the rubber
        return RUBBER if (x * 3 + y) % 11 else RUBBER_L
    return fn


def steel(seed):
    def fn(x, y, w, h):
        if x == 0 or y == 0:
            return STEEL_L
        if x == w - 1 or y == h - 1:
            return STEEL_D
        return STEEL if noise(x, y, seed) > 0.1 else STEEL_D
    return fn


# ---------------------------------------------------------------- the Warden

def warden():
    im = Image.new('RGBA', (128 * K, 64 * K), CLEAR)
    # the helmet: plates, a dark visor band with the gold slit (the visor box itself is lit by the renderer)
    def helmet(name):
        base = plate(1, panel=0)
        if name != 'front':
            return base

        def fn(x, y, w, h):
            if 3 <= y <= 6 and 1 <= x <= w - 2:
                return GOLD if y in (4, 5) and 3 <= x <= w - 4 else PLATE_D
            if y >= h - 3 and (x // 2) % 2 == 0:
                return PLATE_M                                          # the breathing grille
            return base(x, y, w, h)
        return fn
    box(im, 0, 0, 6, 6, 6, helmet)
    box(im, 24, 0, 4, 1, 1, lambda n: (lambda x, y, w, h: GOLD))

    # the chestplate-reactor: the core's rings on its front and the scars running out from it
    def chest(name):
        base = scar(plate(2), 2) if name in ('left', 'right', 'back') else plate(2)
        if name != 'front':
            return base
        cx, cy = 7.0 * K, 5.0 * K                                       # the core's middle on the front (UV 5..9 x 3..7)

        def fn(x, y, w, h):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r = math.hypot(dx, dy)
            if r < 2.4 * K:
                return CORE if r < 1.6 * K else GOLD                    # (under the core's box: seen at its edges)
            if abs(r - 3.2 * K) < 0.7:
                return GOLD                                             # the inner ring
            if abs(r - 4.6 * K) < 0.6:
                return GOLD_D if (int(math.degrees(math.atan2(dy, dx))) // 20) % 2 else GOLD_M
            ang = math.atan2(dy, dx)
            for k in range(5):                                          # the Breakdown's scars out of the reactor
                a = k * 1.256 + 0.4
                da = (ang - a + math.pi) % (2 * math.pi) - math.pi
                if r > 4.6 * K and abs(da * r - math.sin(r * 0.5 + k) * 1.2) < 0.8:
                    return SCAR_L if r < 6.5 * K else SCAR
            return base(x, y, w, h)
        return fn
    box(im, 0, 12, 14, 12, 8, chest)

    def core(name):
        def fn(x, y, w, h):
            if name == 'front':
                r = math.hypot(x + 0.5 - w / 2.0, y + 0.5 - h / 2.0)
                return CORE if r < w * 0.28 else GOLD if r < w * 0.42 else GOLD_D
            return GOLD_D
        return fn
    box(im, 44, 12, 4, 4, 1, core)

    def sink(name):
        def fn(x, y, w, h):
            if name in ('back', 'front'):
                if (x // 2) % 2 == 0:
                    return PLATE_L if y % 4 else PLATE_M                # the fins
                return SCAR if x == w // 2 and y % 3 != 0 else PLATE_D  # a violet glow between them
            return plate(3, panel=0)(x, y, w, h)
        return fn
    box(im, 0, 44, 6, 8, 3, sink)

    def pauldron(name):
        return scar(plate(4, panel=0), 4, 0.6) if name in ('top', 'front') else plate(4, panel=0)
    box(im, 56, 0, 5, 3, 6, pauldron)

    def upper_arm(name):
        base = plate(5)

        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return PLATE_M
            if 9 * K <= y <= 9 * K + 1 or 3 * K <= y <= 3 * K + 1:
                return GOLD_M                                           # the gold bands
            return base(x, y, w, h)
        return fn
    box(im, 80, 0, 3, 12, 3, upper_arm)

    def hand(name):
        def fn(x, y, w, h):
            if name == 'bottom':
                return PLATE_D
            if y <= 1 and x % 3 == 1:
                return GOLD                                             # the knuckles
            return plate(6, panel=0, rivets=False)(x, y, w, h)
        return fn
    box(im, 92, 0, 4, 3, 4, hand)

    def lower_arm(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return STEEL_D
            if 4 * K <= y <= 8 * K:                                     # the forearm wound with copper wire
                return COPPER_L if y % 2 == 0 and x % 3 != 2 else COPPER if y % 2 == 0 else COPPER_D
            return steel(7)(x, y, w, h)
        return fn
    box(im, 80, 16, 2, 10, 2, lower_arm)
    box(im, 88, 16, 3, 2, 3, lambda n: steel(8))

    def hips(name):
        base = plate(9, panel=0)

        def fn(x, y, w, h):
            if name in ('front', 'back', 'left', 'right') and y >= h - 2 and x % 4 in (1, 2):
                return COPPER_D if x % 4 == 1 else COPPER               # the cable sockets along its lower rim
            return base(x, y, w, h)
        return fn
    box(im, 0, 32, 10, 3, 6, hips)
    box(im, 32, 32, 4, 11, 4, lambda n: plate(10))

    def boot(name):
        base = plate(11, panel=0)

        def fn(x, y, w, h):
            if name == 'front' or name == 'top' and y < 3:
                return GOLD_M if (x + y) % 4 else GOLD                  # the gold toe cap
            return base(x, y, w, h)
        return fn
    box(im, 48, 32, 5, 2, 5, boot)
    box(im, 68, 32, 1, 9, 1, rubber_braid)
    box(im, 72, 32, 2, 1, 2, lambda n: (lambda x, y, w, h: COPPER_L if n == 'bottom' and (x + y) % 2 == 0 else STEEL))
    return im


# ---------------------------------------------------------------- the coil

def coil():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    box(im, 0, 0, 10, 2, 10, lambda n: plate(21, panel=0))             # the base

    def post(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return STEEL_D
            if y % 6 in (0, 1, 2):
                return CERAMIC if y % 6 != 2 else CERAMIC_D             # the insulator rings
            return steel(22)(x, y, w, h)
        return fn
    box(im, 40, 0, 4, 10, 4, post)

    def ring(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                cx, cy = w / 2.0, h / 2.0
                r = max(abs(x + 0.5 - cx), abs(y + 0.5 - cy))
                if r < 2.2 * K:
                    return None if name == 'top' else COPPER_D          # (the core passes through it)
                return COPPER_L if int(r) % 2 == 0 else COPPER          # the windings round and round
            return COPPER_L if y == 0 else COPPER if (x // 2) % 2 else COPPER_D
        return fn
    box(im, 0, 12, 8, 1, 8, ring)

    def glass(name):
        def fn(x, y, w, h):
            if x == 0 or (y + x) % 7 == 0:
                return GLASS_L
            if x == w - 1:
                return GLASS_D
            return GLASS
        return fn
    box(im, 32, 12, 2, 10, 2, glass)

    def cap(name):
        def fn(x, y, w, h):
            if name == 'top':
                r = math.hypot(x + 0.5 - w / 2.0, y + 0.5 - h / 2.0)
                return GLASS_L if r < 1.6 else STEEL if r < w / 2.0 - 1 else GOLD_D
            return GOLD_D if y == 0 else steel(23)(x, y, w, h)
        return fn
    box(im, 40, 14, 4, 2, 4, cap)
    return im


def main():
    os.makedirs(os.path.join(TEX, 'entity'), exist_ok=True)
    skins = [('breakdownWarden', warden()), ('containmentCoil', coil())]
    for name, im in skins:
        im.save(os.path.join(TEX, 'entity', name + '.png'))
    # the preview: both skins x2 on grey
    big = [im.resize((im.size[0] * 2, im.size[1] * 2), Image.NEAREST) for _, im in skins]
    sheet = Image.new('RGBA', (big[0].size[0] + big[1].size[0] + 30, max(b.size[1] for b in big) + 20), (70, 72, 76, 255))
    sheet.paste(big[0], (10, 10), big[0])
    sheet.paste(big[1], (big[0].size[0] + 20, 10), big[1])
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
