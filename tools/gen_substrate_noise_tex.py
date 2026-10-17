# -*- coding: utf-8 -*-
"""Подложка (Si-14), the «noise» and the crab of phase 4 (Silicon Age, docs/plan-substrate-tech-f4.md §7.5-§7.11, package N).

Writes into src/main/resources/assets/siliconage/textures (the models' UV drawn 2 x 2, as tools/gen_substrate_mobs3_tex.py does):
  entity/phantom.png    - «Наводка» (ModelPhantomSC, 64 x 64 -> 128 x 128): pale blue-white «light» with brighter induction
                          lines running down it, two white eye slits in the hood, the coils' bars banded - the renderer tints it
                          and draws it see-through, full-bright;
  entity/leech.png      - «Токоед» (ModelLeechSC, 64 x 32 -> 128 x 64): braided cable segments - black rubber under a copper
                          braid, a steel plug for the head with a dark socket, the prongs copper (lit by the renderer anyway);
  entity/colossus.png   - «Кварцитовый колосс» (ModelColossusSC, 128 x 64 -> 256 x 128): quartzite blocks - grey-white with
                          lilac and rose veins, the edges of each face lit and shaded like cut stone, the spikes and the crystal
                          clear quartz (white-cyan facets), dark eye sockets with the eye bars cyan;
  entity/strider.png    - «Дуговой ходок» (ModelStriderSC, 64 x 32 -> 128 x 64): a transformer hub - dark green paint, cooling
                          fins, a yellow-black hazard band - a ceramic insulator core, steel legs with dark joints, copper
                          prongs, pale ceramic balls;
  entity/scrapper.png   - «Утилизатор» (ModelScrapperSC, 64 x 64 -> 128 x 128): a riveted shell of rusted scrap plates
                          (rust, grey patches, rivet rows), the hopper dark steel with a hazard rim, the junk pile in it (bits
                          of copper, steel, green boards), dark steel legs, rusty claws, amber eye lamps;
  entity/waferCrab.png  - «Подложник» (ModelWaferCrabSC, 64 x 32 -> 128 x 64): a small blue-grey crab, its wafer a silver
                          mirror with the etched dies' grid (the renderer adds the rainbow tint);
  items/loot_inductionThread  - «Индукционная нить»: a loose coil of glowing white-cyan thread on a small dark spool;
  items/loot_plasmaCondensate - «Плазменный конденсат»: a stoppered glass ampoule with a violet-blue plasma glow inside;
  items/loot_naturalWafer     - «Природная пластина»: a round wafer with the flat cut, its dies' grid and a rainbow sheen;
and a preview sheet next to this script (the six skins, the three icons x4).

Usage: python gen_substrate_noise_tex.py [project dir]
"""
import colorsys
import math
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'substrate_noise_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# skins: the model's UV pixels drawn 2 x 2
K = 2

# the Phantom: light
PH = (150, 196, 228)
PH_D = (104, 150, 190)
PH_L = (214, 238, 255)
PH_LINE = (240, 252, 255)
# the Leech: cable
RUBBER = (28, 30, 34)
RUBBER_L = (52, 56, 62)
COPPER = (196, 112, 52)
COPPER_D = (132, 70, 30)
COPPER_L = (238, 166, 96)
STEEL = (128, 134, 142)
STEEL_D = (82, 86, 94)
STEEL_L = (176, 182, 190)
# the Colossus: quartzite
QZ = (214, 210, 214)
QZ_D = (160, 154, 164)
QZ_L = (240, 238, 242)
VEIN_LILAC = (184, 160, 204)
VEIN_ROSE = (214, 170, 176)
CRYS = (226, 246, 252)
CRYS_D = (150, 204, 222)
CRYS_L = (255, 255, 255)
SOCKET = (52, 48, 58)
EYE = (96, 216, 232)
# the Strider: a transformer
PAINT = (58, 84, 66)
PAINT_D = (38, 56, 44)
PAINT_L = (86, 116, 92)
HAZARD = (226, 196, 48)
HAZ_D = (30, 30, 28)
CERAMIC = (222, 214, 196)
CERAMIC_D = (150, 120, 92)
JOINT = (34, 36, 40)
# the Scrapper: rust
RUST = (140, 74, 38)
RUST_D = (94, 48, 26)
RUST_L = (184, 108, 58)
PATCH = (118, 120, 118)
PATCH_D = (80, 82, 82)
RIVET = (198, 170, 128)
DARKSTEEL = (54, 58, 64)
DARKSTEEL_L = (86, 92, 100)
BOARD = (40, 120, 64)
AMBER = (255, 176, 64)
# the Wafer Crab
CRAB = (104, 118, 134)
CRAB_D = (70, 80, 94)
CRAB_L = (146, 160, 176)
MIRROR = (206, 212, 222)
MIRROR_D = (150, 158, 172)
MIRROR_L = (244, 248, 252)


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


def noise(x, y, seed=0):
    """A small repeatable hash noise in 0..1."""
    n = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


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


def paint(im, face, fn):
    """fn(x, y, w, h) -> colour or None for each REAL pixel of a face (x, y within it, w, h its real size)."""
    u, v, w, h = face
    for y in range(h * K):
        for x in range(w * K):
            px(im, u * K + x, v * K + y, fn(x, y, w * K, h * K))


def box(im, u, v, w, h, d, fn_by_face):
    for name, f in faces(u, v, w, h, d).items():
        paint(im, f, fn_by_face(name))


def shaded(base, dark, light, speck=None, seed=0):
    """A face lit on its top-left edges, shaded on its bottom-right, a few specks."""
    def fn(x, y, w, h):
        if x == 0 or y == 0:
            return light
        if x == w - 1 or y == h - 1:
            return dark
        if speck is not None and noise(x, y, seed) < 0.06:
            return speck
        return base
    return fn


# ---- the Phantom

def phantom():
    im = Image.new('RGBA', (64 * K, 64 * K), CLEAR)

    def light(name, lines=True):
        def fn(x, y, w, h):
            t = y / float(max(1, h - 1))
            c = mix(PH_L, PH, t)                                        # brighter at the top
            if lines and x % 5 == 2 and (y + x) % 7 != 0:
                return PH_LINE                                          # the induction lines running down
            if noise(x, y, 3) < 0.08:
                return PH_D
            return c
        return fn

    box(im, 0, 0, 6, 6, 6, lambda n: light(n, n != 'front'))           # the hood
    u, v, w, h = faces(0, 0, 6, 6, 6)['front']
    x0, y0 = u * K, v * K
    rect(im, x0, y0, x0 + 11, y0 + 11, PH_D)                            # a dark face under the hood
    for gx in (2, 7):                                                   # two white eye slits
        rect(im, x0 + gx, y0 + 5, x0 + gx + 2, y0 + 6, PH_LINE)
    box(im, 24, 0, 8, 9, 4, lambda n: light(n))                         # the torso
    box(im, 48, 0, 2, 9, 2, lambda n: light(n, False))                  # the wisp arms
    box(im, 0, 13, 6, 4, 3, lambda n: light(n))                         # the tail
    box(im, 18, 13, 4, 4, 2, lambda n: light(n))
    box(im, 30, 13, 2, 4, 1, lambda n: light(n, False))

    def bars(name):
        return lambda x, y, w, h: PH_LINE if (x // 2) % 2 == 0 else PH_L
    box(im, 0, 24, 10, 1, 1, bars)                                      # the coils' bars
    box(im, 0, 28, 1, 1, 8, bars)
    return im


# ---- the Leech

def leech():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def braid(name):
        def fn(x, y, w, h):
            if name in ('front', 'back'):
                return RUBBER if (x + y) % 3 else RUBBER_L              # the cut ends: rubber round a copper core
            # the copper braid crossing over the black rubber
            if (x + y) % 6 in (0, 1) or (x - y) % 6 in (0, 1):
                return COPPER_L if (x + y) % 12 < 2 else COPPER
            return RUBBER if (x * 3 + y) % 11 else RUBBER_L
        return fn
    box(im, 0, 0, 3, 3, 5, braid)

    def plug(name):
        def fn(x, y, w, h):
            if name == 'front':
                if 2 <= x <= w - 3 and 2 <= y <= h - 3:
                    return (16, 16, 18)                                 # the socket
                return STEEL_D
            if y in (h // 3, 2 * h // 3):
                return STEEL_D                                          # the plug's grooves
            if x == 0 or y == 0:
                return STEEL_L
            return STEEL
        return fn
    box(im, 0, 10, 4, 4, 5, plug)
    box(im, 20, 10, 1, 1, 2, lambda n: (lambda x, y, w, h: COPPER_L if (x + y) % 2 else COPPER))
    return im


# ---- the Colossus

def quartz(seed, veins=True):
    def fn(x, y, w, h):
        edge = min(x, y, w - 1 - x, h - 1 - y)
        if edge == 0:
            return QZ_L if x == 0 or y == 0 else QZ_D                   # cut stone: lit and shaded edges
        if veins:
            vx = math.sin((x + seed * 7) * 0.35 + y * 0.18) * 3 + (y * 0.6)
            if abs((x + vx) % 17 - 8) < 0.7:
                return VEIN_LILAC
            if abs((y * 1.3 - x * 0.4 + seed * 5) % 23 - 11) < 0.6:
                return VEIN_ROSE
        n = noise(x, y, seed)
        if n < 0.08:
            return QZ_D
        if n > 0.94:
            return QZ_L
        return mix(QZ, QZ_D, 0.15 * (y / float(max(1, h))))
    return fn


def crystal(seed):
    def fn(x, y, w, h):
        facet = ((x * 2 + y + seed) // 3) % 3
        c = (CRYS, CRYS_D, CRYS_L)[facet]
        if x == 0:
            return CRYS_L
        if x == w - 1:
            return CRYS_D
        return c
    return fn


def colossus():
    im = Image.new('RGBA', (128 * K, 64 * K), CLEAR)
    box(im, 0, 0, 16, 14, 10, lambda n: quartz(1))                      # the torso
    box(im, 52, 0, 8, 6, 7, lambda n: quartz(2))                        # the head
    u, v, w, h = faces(52, 0, 8, 6, 7)['front']
    x0, y0 = u * K, v * K
    rect(im, x0 + 1, y0 + 2, x0 + 14, y0 + 6, SOCKET)                   # the brow's shadow over the eyes
    rect(im, x0 + 4, y0 + 8, x0 + 11, y0 + 9, QZ_D)                     # a crack of a mouth
    box(im, 82, 0, 4, 8, 4, lambda n: crystal(1))                       # the shoulder spikes
    box(im, 98, 0, 3, 10, 3, lambda n: crystal(2))                      # the back spike
    box(im, 110, 0, 3, 5, 3, lambda n: crystal(3))                      # the crystal in its fist
    box(im, 122, 0, 2, 1, 1, lambda n: (lambda x, y, w, h: EYE))       # the eye bars
    box(im, 0, 24, 6, 12, 6, lambda n: quartz(4))                       # the legs
    box(im, 24, 24, 6, 18, 6, lambda n: quartz(5))                      # the arms
    box(im, 0, 48, 7, 5, 7, lambda n: quartz(6, False))                 # the fists
    return im


# ---- the Strider

def strider():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)

    def hub(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                if (x // 3) % 2 == 0 and 2 < y < h - 3:
                    return PAINT_D                                      # the cooling fins seen from above
                return PAINT if min(x, y, w - 1 - x, h - 1 - y) else PAINT_D
            if 3 <= y <= 5:                                             # the hazard band round it
                return HAZARD if ((x + y) // 3) % 2 == 0 else HAZ_D
            if x % 4 == 0:
                return PAINT_D                                          # fins
            return PAINT_L if y == 0 else PAINT
        return fn
    box(im, 0, 0, 10, 5, 10, hub)

    def core(name):
        def fn(x, y, w, h):
            if name in ('top', 'bottom'):
                return CERAMIC
            return CERAMIC_D if y % 2 == 1 else CERAMIC                 # the insulator's rings
        return fn
    box(im, 0, 16, 4, 3, 4, core)

    def steel(joint_rows):
        def fn(x, y, w, h):
            if any(r0 <= y <= r1 for r0, r1 in joint_rows):
                return JOINT
            if x == 0:
                return STEEL_L
            if x == w - 1:
                return STEEL_D
            return STEEL if (x + y) % 9 else STEEL_D
        return fn
    box(im, 40, 0, 2, 14, 2, lambda n: steel(((0, 1), (26, 27))))       # thighs (real px)
    box(im, 48, 0, 1, 16, 1, lambda n: steel(((0, 1), (30, 31))))       # shins: a dark foot
    box(im, 52, 0, 1, 6, 1, lambda n: (lambda x, y, w, h: COPPER_L if x == 0 else COPPER))
    box(im, 56, 0, 2, 2, 2, lambda n: (lambda x, y, w, h: CERAMIC if (x + y) % 3 else (255, 255, 255)))
    return im


# ---- the Scrapper

def scrap(seed):
    def fn(x, y, w, h):
        plate = ((x // 9) + (y // 7) * 3 + seed) % 5
        if x % 9 == 0 or y % 7 == 0:
            return RUST_D                                               # the seams between the plates
        if (x % 9 == 2 or x % 9 == 6) and y % 7 == 2:
            return RIVET                                                # rivets
        if plate == 3:
            return PATCH if noise(x, y, seed) > 0.15 else PATCH_D       # a grey patch welded on
        n = noise(x, y, seed + 9)
        if n < 0.12:
            return RUST_D
        if n > 0.9:
            return RUST_L
        return RUST
    return fn


def scrapper():
    im = Image.new('RGBA', (64 * K, 64 * K), CLEAR)
    box(im, 0, 0, 16, 7, 14, lambda n: scrap(1))                        # the shell

    def hopper(name):
        def fn(x, y, w, h):
            if name == 'top':
                if min(x, y, w - 1 - x, h - 1 - y) <= 1:
                    return HAZARD if ((x + y) // 2) % 2 == 0 else HAZ_D  # the hazard rim: the weak spot
                return (18, 18, 20)                                     # the dark mouth
            if y == 0:
                return DARKSTEEL_L
            return DARKSTEEL if (x + y * 2) % 13 else DARKSTEEL_L
        return fn
    box(im, 0, 21, 10, 6, 9, hopper)

    def junk(name):
        def fn(x, y, w, h):
            n = noise(x, y, 77)
            if n < 0.25:
                return COPPER
            if n < 0.45:
                return STEEL
            if n < 0.6:
                return BOARD
            if n < 0.7:
                return (200, 190, 80)
            return DARKSTEEL
        return fn
    box(im, 0, 36, 8, 1, 7, junk)
    box(im, 58, 21, 1, 3, 1, lambda n: (lambda x, y, w, h: DARKSTEEL))   # the eye stalks
    box(im, 54, 29, 2, 2, 2, lambda n: (lambda x, y, w, h: AMBER))       # the eyes (lit anyway)

    def leg(name):
        return lambda x, y, w, h: DARKSTEEL_L if y == 0 else (JOINT if x % 8 == 0 else DARKSTEEL)
    box(im, 38, 21, 8, 2, 2, leg)
    box(im, 38, 25, 6, 2, 2, leg)
    box(im, 38, 29, 2, 2, 6, lambda n: scrap(2))                        # the claw arms
    box(im, 30, 37, 3, 3, 4, lambda n: scrap(3))                        # the pincers
    return im


# ---- the Wafer Crab

def wafer_crab():
    im = Image.new('RGBA', (64 * K, 32 * K), CLEAR)
    box(im, 0, 0, 8, 3, 7, lambda n: shaded(CRAB, CRAB_D, CRAB_L, CRAB_D, 5))

    def wafer(name):
        def fn(x, y, w, h):
            if name != 'top':
                return MIRROR_D                                         # the wafer's thin edge
            if x % 4 == 0 or y % 4 == 0:
                return MIRROR_D                                         # the dies' grid
            if (x + y) % 9 == 0:
                return MIRROR_L
            return MIRROR
        return fn
    box(im, 0, 10, 10, 1, 10, wafer)
    box(im, 30, 0, 4, 1, 1, lambda n: (lambda x, y, w, h: CRAB_D if x % 3 == 0 else CRAB))
    box(im, 40, 0, 2, 2, 3, lambda n: shaded(CRAB_L, CRAB_D, (190, 200, 214)))
    box(im, 50, 0, 1, 2, 1, lambda n: (lambda x, y, w, h: (20, 20, 24) if y < 2 else CRAB_D))
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


def induction_thread():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the spool: a small dark cylinder in the middle
    rect(im, 12, 11, 19, 20, (40, 44, 52))
    rect(im, 11, 10, 20, 11, (70, 76, 88))
    rect(im, 11, 20, 20, 21, (70, 76, 88))
    rect(im, 13, 12, 13, 19, (60, 66, 78))
    # the thread wound round it and trailing off in loops
    for i in range(240):
        t = i / 240.0
        a = t * math.pi * 7
        r = 3.5 + t * 9.5
        x = 15.5 + math.cos(a) * r
        y = 15.5 + math.sin(a) * r * 0.75
        c = mix((150, 230, 255), (250, 255, 255), 0.5 + 0.5 * math.sin(a * 2))
        px(im, int(round(x)), int(round(y)), c)
    for (x, y) in ((6, 9), (25, 22), (9, 25)):                          # glints
        px(im, x, y, (255, 255, 255))
    outline(im)
    return im


def plasma_condensate():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx = 15.5
    for y in range(5, 29):
        half = 6.5 if y > 9 else 3.5
        if y > 24:
            half = 6.5 - (y - 24) * 1.3                                 # the rounded bottom
        for x in range(S):
            d = abs(x + 0.5 - cx)
            if d > half:
                continue
            if y <= 9:
                c = (150, 120, 70) if y <= 7 else (110, 86, 50)         # the stopper and its neck
                if d > half - 1:
                    c = (90, 70, 40)
            else:
                glow = max(0.0, 1.0 - math.hypot(x + 0.5 - cx, y - 18.5) / 8.0)
                c = mix((70, 40, 150), (230, 210, 255), glow)           # the plasma: violet, white at its heart
                if d > half - 1:
                    c = (170, 190, 220)                                 # the glass wall
                if x + 0.5 < cx - 3 and 12 <= y <= 20:
                    c = mix(c, (255, 255, 255), 0.45)                   # the glass's highlight
            px(im, x, y, c)
    for (x, y) in ((14, 16), (17, 19), (15, 21)):
        px(im, x, y, (255, 255, 255))
    outline(im)
    return im


def natural_wafer():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy, r = 15.5, 15.5, 13.0
    for y in range(S):
        for x in range(S):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d > r or dy > 11.0:
                continue                                                # round, with the flat cut at the bottom
            hue = (math.atan2(dy, dx) / (2 * math.pi) + d / 40.0) % 1.0
            rr, gg, bb = colorsys.hsv_to_rgb(hue, 0.35, 0.95)
            c = (clamp(rr * 255), clamp(gg * 255), clamp(bb * 255))
            if (x % 5 == 0 or y % 5 == 0) and d < r - 1.5:
                c = mix(c, (110, 116, 130), 0.55)                       # the dies' grid
            if d > r - 1.2 or dy > 10.0:
                c = (150, 156, 170)                                     # the rim
            px(im, x, y, c)
    for i in range(5):                                                  # a highlight across
        px(im, 8 + i, 7 + i // 2, (255, 255, 255))
    outline(im)
    return im


def main():
    for folder in ('entity', 'items'):
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
    skins = [('phantom', phantom()), ('leech', leech()), ('colossus', colossus()), ('strider', strider()),
             ('scrapper', scrapper()), ('waferCrab', wafer_crab())]
    icons = [('loot_inductionThread', induction_thread()), ('loot_plasmaCondensate', plasma_condensate()),
             ('loot_naturalWafer', natural_wafer())]
    for name, im in skins:
        im.save(os.path.join(TEX, 'entity', name + '.png'))
    for name, im in icons:
        im.save(os.path.join(TEX, 'items', name + '.png'))
    # the preview: the skins at their own size on two rows, the icons x4, on grey
    col_w = 256 + 10
    w = 3 * col_w + 10
    sheet = Image.new('RGBA', (w + 3 * (S * 4 + 5) + 10, 2 * (128 + 10) + 10), (70, 72, 76, 255))
    for i, (name, im) in enumerate(skins):
        sheet.paste(im, (10 + (i % 3) * col_w, 10 + (i // 3) * 138), im)
    for i, (name, im) in enumerate(icons):
        big = im.resize((S * 4, S * 4), Image.NEAREST)
        sheet.paste(big, (w + i * (S * 4 + 5), 10), big)
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
