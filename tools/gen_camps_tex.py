# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 4, package C: the Renegades' camps, the Vaults and their relics (Silicon Age,
docs/plan-substrate-tech-f4.md §8).

The camp's blocks are the mod's own machines stolen and patched by the Renegades: the mod's textures are read and
daubed over - crimson paint (the Renegades' colour, TileEntityStolenFieldSC.SHELL_RGB), rust, welds, scrap patches and
their claw mark. The Vault's blocks are the Lithographers' dark steel (gen_ruins_tex.py's palette), heavier.

Writes into src/main/resources/assets/siliconage/textures:
  blocks/camp_side          - the stolen casing (the HV machine casing, crimson over its orange, a welded patch) - the panel's
                              and the pad's sides;
  blocks/camp_stolenField   - «Краденый генератор поля»: the Field Generator's face, its frame crimson, the shard's glow red,
                              a scrap plate bolted over a corner, the claw mark;
  blocks/camp_stolenSolar   - «Краденая солнечная панель»: the Nano panel's cells, two cracked, one taped over, the frame red;
  blocks/camp_stolenPad     - «Краденая зарядная плита»: the HV charge pad's top, scorched, a crimson ring round its coil;
  blocks/camp_campSafe      - «Сейф Отступников»: a heavy riveted door, a crimson band, the dial and a key slot;
  blocks/camp_campSafeOpen  - the same door torn open: the dark inside, the bolts drawn;
  blocks/ruin_vaultPlating  - «Плита Хранилища»: thick dark steel, a deep bevel, four heavy bolts, a faint violet seam;
  blocks/ruin_vaultLamp     - «Лампа Хранилища»: the plating round a recessed pale-violet lamp behind a grille;
  blocks/ruin_campTarp      - «Навес Отступников»: crimson-brown canvas, stitched patches, a strap and its folds;
  items/relic_campKey       - «Ключ лагеря»: a crude key cut from a crimson bar, a tag on a wire;
  items/relic_keyFragment   - «Фрагмент Ключа Пробоя»: a quarter of a dark disc with glowing violet traces;
  items/relic_sealedCrumb   - «Опечатанная Крупица»: the Singularity Crumb in a glass capsule with a crimson seal;
  items/ruinFinder          - «Пеленгатор руин»: a handheld finder - a round screen with a needle, an antenna, a grip;
and a preview sheet next to this script (camps_tex_preview.png).

Usage: python tools/gen_camps_tex.py [project dir]
"""
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'camps_tex_preview.png')
S = 32
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)

# the Renegades' crimson (TileEntityStolenFieldSC.SHELL_RGB 0xC02838) and its shades
RED = (192, 40, 56)
RED_L = (226, 74, 82)
RED_D = (118, 22, 34)
RED_DK = (66, 14, 22)
RUST = (142, 86, 50)
RUST_D = (98, 58, 36)
SCRAP = (110, 112, 118)
SCRAP_L = (150, 152, 158)
SCRAP_D = (64, 66, 72)
WELD = (232, 210, 150)
SOOT = (26, 25, 28)
# the Lithographers' steel (gen_ruins_tex.py), and the Vault's darker one
STEEL = (86, 90, 104)
STEEL_HI = (134, 140, 156)
STEEL_MID = (104, 109, 124)
STEEL_LO = (60, 62, 74)
STEEL_DK = (34, 35, 43)
VAULT = (58, 60, 72)
VAULT_HI = (104, 108, 124)
VAULT_LO = (40, 41, 50)
VAULT_DK = (22, 22, 28)
VIOLET = (132, 86, 200)
VIOLET_L = (214, 186, 255)
VIOLET_D = (70, 40, 110)
CANVAS = (128, 52, 50)
CANVAS_L = (156, 72, 64)
CANVAS_D = (88, 34, 36)
STITCH = (206, 170, 120)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c[:3])


def px(im, x, y, c, a=255):
    w, h = im.size
    x, y = int(round(x)), int(round(y))
    if c is not None and 0 <= x < w and 0 <= y < h:
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def get(im, x, y):
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[:3]


def alpha(im, x, y):
    w, h = im.size
    return im.getpixel((max(0, min(w - 1, x)), max(0, min(h - 1, y))))[3]


def noise_grid(seed, cells=4, size=S):
    """A smooth size x size value noise (-1..1) that tiles (gen_ruins_tex.py's)."""
    r = random.Random(seed)
    g = [[r.uniform(-1, 1) for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    step = size / float(cells)
    for y in range(size):
        for x in range(size):
            fx, fy = x / step, y / step
            x0, y0 = int(fx) % cells, int(fy) % cells
            x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] + (g[y0][x1] - g[y0][x0]) * tx
            b = g[y1][x0] + (g[y1][x1] - g[y1][x0]) * tx
            out[y][x] = a + (b - a) * ty
    return out


def grain(im, seed, k=0.06, x0=0, y0=0, x1=S - 1, y1=S - 1):
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if alpha(im, x, y):
                px(im, x, y, scale(get(im, x, y), 1 + r.uniform(-k, k)))


def bevel(im, base, hi, mid, lo, dk, width=3):
    """The mod's casing frame: an outer line, a lit top-left bevel, a shaded bottom-right one, `width` wide."""
    for y in range(S):
        for x in range(S):
            if x == 0 or y == 0:
                c = mid
            elif x == S - 1 or y == S - 1:
                c = dk
            elif x < width and y < S - x or y < width and x < S - y:
                c = hi if min(x, y) == 1 else mix(hi, base, 0.4)
            elif x >= S - width or y >= S - width:
                c = lo if max(x, y) == S - 2 else mix(lo, base, 0.3)
            else:
                continue
            px(im, x, y, c)


def rivet(im, x, y, hi=STEEL_HI, mid=STEEL_MID, lo=STEEL_LO, dk=STEEL_DK):
    px(im, x, y, hi)
    px(im, x + 1, y, mid)
    px(im, x, y + 1, lo)
    px(im, x + 1, y + 1, dk)


def streak(im, x, y, length, seed, col=RUST, dark=RUST_D):
    r = random.Random(seed)
    for k in range(length):
        t = 1 - k / float(length)
        px(im, x, y + k, mix(get(im, x, y + k), col if k < 2 else dark, 0.75 * t + 0.15))
        if r.random() < 0.35 * t:
            px(im, x + r.choice((-1, 1)), y + k, mix(get(im, x + 1, y + k), dark, 0.4 * t))


def load(rel):
    """One of the mod's own textures (32 x 32) - the stolen machines start from them."""
    p = os.path.join(TEX, rel)
    im = Image.open(p).convert('RGBA')
    if im.size != (S, S):
        im = im.resize((S, S), Image.NEAREST)
    return im


def orange(c):
    """The mod's casing orange (its frames) - the Renegades paint it over."""
    r, g, b = c
    return r > 60 and r > g * 1.25 and g > b * 1.2


def daub(im, seed, amount=0.85, frame_only=True, width=4):
    """Crimson paint brushed over the casing's orange: thick where the noise is high, chipped to the orange elsewhere."""
    n = noise_grid(seed, 4)
    m = noise_grid(seed + 3, 8)
    for y in range(S):
        for x in range(S):
            c = get(im, x, y)
            edge = x < width or y < width or x >= S - width or y >= S - width
            if frame_only and not edge or not orange(c):
                continue
            v = n[y][x] * 0.5 + m[y][x] * 0.5 + amount - 0.5
            if v > 0:
                lum = (c[0] * 0.5 + c[1] * 0.35 + c[2] * 0.15) / 150.0
                px(im, x, y, scale(mix(RED_D, RED, 0.6), max(0.45, min(1.35, lum))))


def claw(im, x0, y0, n=3, length=7, col=RED_L, dark=RED_D):
    """The Renegades' claw mark: `n` slanted strokes."""
    for k in range(n):
        for t in range(length):
            x, y = x0 + k * 2 + t // 2, y0 + t
            px(im, x, y, col if 1 <= t < length - 1 else dark)
            px(im, x + 1, y, mix(get(im, x + 1, y), dark, 0.5))


def patch(im, x0, y0, x1, y1, seed):
    """A scrap plate bolted over the casing: grey steel, a weld line round it, rivets at its corners."""
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = mix(SCRAP, SCRAP_L, 0.3) if y == y0 or x == x0 else (SCRAP_D if y == y1 or x == x1 else SCRAP)
            px(im, x, y, scale(c, 1 + r.uniform(-0.06, 0.06)))
    for x in range(x0 - 1, x1 + 2):
        for y in (y0 - 1, y1 + 1):
            if r.random() < 0.7:
                px(im, x, y, mix(get(im, x, y), WELD, 0.55))
    for y in range(y0, y1 + 1):
        for x in (x0 - 1, x1 + 1):
            if r.random() < 0.7:
                px(im, x, y, mix(get(im, x, y), WELD, 0.55))
    for (x, y) in ((x0 + 1, y0 + 1), (x1 - 2, y0 + 1), (x0 + 1, y1 - 2), (x1 - 2, y1 - 2)):
        rivet(im, x, y, SCRAP_L, SCRAP, SCRAP_D, SOOT)


def scorch(im, cx, cy, rad, k=0.6):
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d < rad:
                px(im, x, y, mix(get(im, x, y), SOOT, k * (1 - d / rad) ** 1.5))


def outline(im):
    src = im.copy()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            if src.getpixel((x, y))[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and src.getpixel((nx, ny))[3] != 0:
                    px(im, x, y, OUTLINE)
                    break


# ---------------------------------------------------------------- the camp's blocks

def camp_side():
    im = load('blocks/machineCasingHV.png')
    daub(im, 11, 0.75, frame_only=False)
    patch(im, 17, 18, 26, 26, 12)
    claw(im, 6, 8)
    streak(im, 9, 23, 6, 13)
    streak(im, 24, 4, 5, 14)
    grain(im, 15, 0.04)
    return im


def stolen_field():
    im = load('blocks/machineFieldGeneratorFront.png')
    daub(im, 21, 0.95)
    # the shard's blue glow turned crimson (the Renegades rewired it): blue-dominant pixels to red
    for y in range(S):
        for x in range(S):
            c = get(im, x, y)
            if c[2] > c[0] + 30 and c[2] > 90:
                lum = (c[0] + c[1] + c[2]) / 3.0 / 255.0
                px(im, x, y, mix(RED_D, (255, 150, 150), min(1.0, lum * 1.15)))
    patch(im, 21, 3, 28, 9, 22)
    claw(im, 4, 21, 3, 6)
    streak(im, 5, 28, 3, 23)
    grain(im, 24, 0.04)
    return im


def stolen_solar():
    im = load('blocks/machineSolarPanelNanoFront.png')
    # the panel's frame (its lighter outer ring) painted crimson in patches
    n = noise_grid(31, 4)
    for y in range(S):
        for x in range(S):
            if (x < 2 or y < 2 or x >= S - 2 or y >= S - 2) and n[y][x] > -0.3:
                c = get(im, x, y)
                px(im, x, y, scale(RED, 0.55 + (c[0] + c[1] + c[2]) / 765.0 * 0.7))
    # two cracked cells: a dark star with light lips
    for (cx, cy, seed) in ((10, 9, 32), (22, 21, 33)):
        r = random.Random(seed)
        for a in range(5):
            ang = r.uniform(0, math.pi * 2)
            for t in range(r.randint(3, 6)):
                x, y = int(round(cx + math.cos(ang) * t)), int(round(cy + math.sin(ang) * t))
                px(im, x, y, (16, 30, 30))
                px(im, x, y + 1, mix(get(im, x, y + 1), (198, 247, 242), 0.3))
        px(im, cx, cy, (8, 14, 14))
    # one cell taped over: a grey strip with a crimson stripe
    for y in range(18, 23):
        for x in range(4, 13):
            c = (120, 116, 104) if y not in (18, 22) else (92, 88, 78)
            if y == 20:
                c = RED_D
            px(im, x, y, c)
    grain(im, 34, 0.04)
    return im


def stolen_pad():
    im = load('blocks/chargePadHVTop.png')
    daub(im, 41, 0.9)
    scorch(im, 16, 16, 12, 0.45)
    # a crimson ring round the coil
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 15.5, y - 15.5)
            if 9.2 < d < 10.6:
                px(im, x, y, RED if (x + y) % 5 else RED_D)
    claw(im, 23, 23, 2, 5)
    grain(im, 42, 0.04)
    return im


def safe_door(open_):
    im = Image.new('RGBA', (S, S))
    n = noise_grid(51, 4)
    for y in range(S):
        for x in range(S):
            px(im, x, y, scale(SCRAP_D, 1 + n[y][x] * 0.08))
    bevel(im, SCRAP_D, SCRAP_L, SCRAP, (48, 50, 56), SOOT, 3)
    if open_:
        # the door torn off its hinges: the dark inside, a shelf, the drawn bolts sticking out of the frame
        for y in range(4, S - 4):
            for x in range(4, S - 4):
                d = min(x - 4, y - 4, S - 5 - x, S - 5 - y)
                px(im, x, y, mix((10, 10, 12), (36, 34, 38), max(0.0, 1 - d / 5.0) * 0.8))
        for x in range(5, S - 5):
            px(im, x, 17, (70, 72, 78))
            px(im, x, 18, (40, 40, 46))
        for (x, y) in ((9, 15), (10, 15), (20, 14), (21, 15), (14, 24), (15, 24)):
            px(im, x, y, (196, 120, 60) if (x + y) % 2 else (150, 150, 160))   # what's left inside: a glint
        for y in (8, 15, 22):
            for x in range(1, 5):
                px(im, x, y, SCRAP_L if x < 4 else SCRAP)
                px(im, x, y + 1, SOOT)
        for (x, y) in ((27, 6), (27, 24)):
            for k in range(3):
                px(im, x + k, y, (70, 72, 78))
                px(im, x + k, y + 1, SOOT)
        claw(im, 22, 25, 2, 4)
        grain(im, 53, 0.05)
        return im
    # the door: a crimson band, hinge bars on the left, the dial, the key slot, rivets
    for y in range(11, 15):
        for x in range(3, S - 3):
            px(im, x, y, RED if y in (12, 13) else RED_D)
    for y in (6, 24):
        for x in range(3, 9):
            px(im, x, y, SCRAP_L)
            px(im, x, y + 1, SCRAP)
            px(im, x, y + 2, SOOT)
    cx, cy = 20.5, 21.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d < 5.6:
                lit = 0.25 + 0.5 * max(0.0, (cx - x + cy - y) / 8.0)
                c = mix((80, 82, 90), (170, 172, 180), lit)
                if d > 4.6:
                    c = (40, 40, 46)
                elif abs(math.atan2(y - cy, x - cx) * 6 / math.pi % 1) < 0.18 and d > 3.2:
                    c = (210, 210, 216)                       # the dial's marks
                px(im, x, y, c)
    px(im, int(cx), int(cy), (40, 40, 46))
    px(im, int(cx), int(cy) - 1, (40, 40, 46))
    for y in range(4, 9):                                   # the key slot over the dial, its rim lit red
        px(im, 20, y, (8, 8, 10))
        px(im, 21, y, (8, 8, 10))
        px(im, 19, y, RED_L if y in (4, 8) else RED_D)
        px(im, 22, y, RED_D)
    for (x, y) in ((4, 4), (26, 4), (4, 26), (26, 26), (12, 4), (12, 26)):
        rivet(im, x, y, SCRAP_L, SCRAP, (48, 50, 56), SOOT)
    streak(im, 27, 15, 6, 54)
    streak(im, 6, 15, 4, 55)
    claw(im, 8, 17, 3, 6)
    grain(im, 56, 0.05)
    return im


# ---------------------------------------------------------------- the Vault's blocks and the tarp

def vault_plating():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(61, 4)
    for y in range(S):
        for x in range(S):
            px(im, x, y, scale(VAULT, 1 + n[y][x] * 0.06))
    bevel(im, VAULT, VAULT_HI, mix(VAULT, VAULT_HI, 0.5), VAULT_LO, VAULT_DK, 4)
    # an inner bevel: the plate's raised middle
    for y in range(7, S - 7):
        for x in range(7, S - 7):
            if x == 7 or y == 7:
                px(im, x, y, mix(VAULT, VAULT_HI, 0.55))
            elif x == S - 8 or y == S - 8:
                px(im, x, y, VAULT_DK)
    # a faint violet seam across the middle (the Lithographers' work)
    for x in range(8, S - 8):
        px(im, x, 15, mix(get(im, x, 15), VIOLET_D, 0.55))
        px(im, x, 16, mix(get(im, x, 16), VIOLET, 0.35 if x % 3 else 0.6))
    # four heavy bolts: a dark ring, a lit head
    for (bx, by) in ((5, 5), (25, 5), (5, 25), (25, 25)):
        for y in range(by - 1, by + 3):
            for x in range(bx - 1, bx + 3):
                d = math.hypot(x - bx - 0.5, y - by - 0.5)
                if d < 1.0:
                    px(im, x, y, VAULT_HI if x <= bx and y <= by else STEEL_MID)
                elif d < 2.1:
                    px(im, x, y, VAULT_DK)
    grain(im, 62, 0.04, 1, 1, S - 2, S - 2)
    return im


def vault_lamp():
    im = vault_plating()
    cx, cy = 15.5, 15.5
    for y in range(8, S - 8):
        for x in range(8, S - 8):
            d = max(abs(x - cx), abs(y - cy))
            t = max(0.0, 1 - d / 8.0)
            c = mix(VIOLET, (246, 238, 255), t ** 0.8)
            if x in (8, S - 9) or y in (8, S - 9):
                c = VAULT_DK
            elif (x - 8) % 4 == 0 or (y - 8) % 4 == 0:
                c = mix(STEEL_MID, VAULT_HI, 0.5)                 # the grille over the glass
            px(im, x, y, c)
    return im


def camp_tarp():
    im = Image.new('RGBA', (S, S))
    n = noise_grid(71, 4)
    m = noise_grid(72, 8)
    for y in range(S):
        for x in range(S):
            fold = math.sin((x + n[y][x] * 3) * 0.55) * 0.12          # the canvas sags in folds
            c = mix(CANVAS, CANVAS_L, 0.5 + fold * 2 + m[y][x] * 0.15) if fold > 0 else mix(CANVAS, CANVAS_D, -fold * 3)
            px(im, x, y, c)
    # stitched patches: a darker and a lighter piece sewn on
    for (x0, y0, x1, y1, col) in ((3, 4, 12, 11, (110, 60, 44)), (18, 18, 28, 27, (150, 96, 70))):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px(im, x, y, mix(col, get(im, x, y), 0.25))
        for x in range(x0, x1 + 1, 2):
            px(im, x, y0, STITCH)
            px(im, x, y1, STITCH)
        for y in range(y0, y1 + 1, 2):
            px(im, x0, y, STITCH)
            px(im, x1, y, STITCH)
    # a strap across with its buckle
    for x in range(S):
        px(im, x, 14, (62, 46, 34))
        px(im, x, 15, (82, 62, 44))
        px(im, x, 16, (52, 38, 28))
    for y in range(13, 18):
        px(im, 21, y, (170, 160, 130))
        px(im, 23, y, (170, 160, 130))
    for x in range(21, 24):
        px(im, x, 13, (170, 160, 130))
        px(im, x, 17, (170, 160, 130))
    grain(im, 73, 0.06)
    return im


# ---------------------------------------------------------------- the items

def camp_key():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the bow: a ring cut from a crimson bar
    cx, cy = 10.5, 10.5
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if 3.0 <= d <= 6.6:
                lit = 0.35 + 0.4 * max(0.0, (cx - x + cy - y) / 9.0)
                px(im, x, y, mix(RED_D, RED_L, lit))
    # the shank down to the bottom right (a bar 3 wide along the diagonal, lit on its upper side), its bits
    for y in range(S):
        for x in range(S):
            along, across = (x + y) / 2.0 - 15.0, (x - y) / 1.414
            if 0 <= along <= 13 and abs(across) <= 1.6:
                px(im, x, y, SCRAP_L if across > 0.7 else (SCRAP if across > -0.7 else SCRAP_D))
    for (x, y, w, h) in ((22, 26, 2, 3), (26, 22, 3, 2)):
        for yy in range(y, y + h):
            for xx in range(x, x + w):
                px(im, xx, yy, SCRAP if xx + yy < x + y + w else SCRAP_D)
    # the tag on a wire: a small plate with a claw mark
    for y in range(3, 6):
        px(im, 17, y, (170, 160, 130))
    for y in range(6, 12):
        for x in range(15, 21):
            px(im, x, y, (196, 180, 120) if x == 15 or y == 6 else (164, 148, 96))
    for k in range(2):
        px(im, 17 + k * 2, 8, RED_D)
        px(im, 17 + k * 2, 9, RED_D)
    outline(im)
    return im


def key_fragment():
    im = Image.new('RGBA', (S, S), CLEAR)
    cx, cy = 4.0, 27.0                                   # a quarter of a disc - its corner the disc's middle
    r = random.Random(81)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - cx, y - cy)
            if d < 23 and x >= 4 and y <= 27:
                lit = 0.35 + 0.35 * max(0.0, (y - x) / 30.0)
                c = mix((22, 16, 32), (62, 48, 84), lit)
                if d > 21:
                    c = (84, 66, 112)                          # the rim
                px(im, x, y, c)
    # the broken edges: a jagged cut along both straight sides
    for t in range(4, 28):
        if r.random() < 0.5:
            px(im, 4, t, CLEAR, 0)
        if r.random() < 0.5:
            px(im, t, 27, CLEAR, 0)
    # the glowing traces: two arcs and spokes in violet
    for rad in (9, 15):
        for a in range(0, 90, 2):
            ang = math.radians(a)
            x, y = int(round(cx + math.cos(ang) * rad)), int(round(cy - math.sin(ang) * rad))
            if 5 <= x and y <= 26:
                px(im, x, y, VIOLET_L if a % 10 < 4 else VIOLET)
    for a in (20, 55):
        ang = math.radians(a)
        for t in range(5, 21):
            x, y = int(round(cx + math.cos(ang) * t)), int(round(cy - math.sin(ang) * t))
            if 5 <= x and y <= 26:
                px(im, x, y, VIOLET)
    outline(im)
    return im


def sealed_crumb():
    im = Image.new('RGBA', (S, S), CLEAR)
    crumb = load('items/singularCrumb.png')
    # the capsule: a rounded glass cylinder, faint blue-grey, its ends steel caps
    for y in range(5, 27):
        for x in range(9, 23):
            t = (x - 9) / 13.0
            glass = mix((70, 90, 110), (160, 186, 206), max(0.0, 0.7 - abs(t - 0.3) * 1.4))
            px(im, x, y, glass, 120)
    # the Crumb inside: the mod's own icon cut to its shards and fitted in (12 x 18 at most)
    box = crumb.getbbox()
    shard = crumb.crop(box) if box else crumb
    k = min(12.0 / shard.size[0], 18.0 / shard.size[1])
    shard = shard.resize((max(1, int(shard.size[0] * k)), max(1, int(shard.size[1] * k))), Image.NEAREST)
    ox, oy = 16 - shard.size[0] // 2, 16 - shard.size[1] // 2
    for y in range(shard.size[1]):
        for x in range(shard.size[0]):
            p = shard.getpixel((x, y))
            if p[3] > 0:
                px(im, x + ox, y + oy, p[:3])
    for y in (3, 4, 26, 27):
        for x in range(9, 23):
            px(im, x, y, SCRAP_L if y in (3, 26) else SCRAP_D)
    # the crimson seal: a band over the cap's joint and its wax blob on the side
    for x in range(9, 23):
        px(im, x, 7, RED_D)
        px(im, x, 8, RED)
    for y in range(6, 11):
        for x in range(18, 23):
            if math.hypot(x - 20, y - 8) < 2.6:
                px(im, x, y, RED_L if x + y < 28 else RED_D)
    # the glass's highlight
    for y in range(10, 25):
        if get(im, 11, y) != OUTLINE and alpha(im, 11, y) < 255:
            px(im, 11, y, (230, 240, 250), 170)
    outline(im)
    return im


def ruin_finder():
    im = Image.new('RGBA', (S, S), CLEAR)
    # the grip and the body: the mod's orange casing colours, a dark front
    for y in range(18, 30):
        for x in range(12, 20):
            px(im, x, y, (126, 80, 37) if x in (12, 19) else (183, 118, 56))
    for y in range(4, 22):
        for x in range(5, 27):
            d = math.hypot(x - 15.5, y - 12.5)
            if d < 9.6:
                px(im, x, y, (224, 144, 68) if d > 8.4 else (54, 59, 66))
    # the round screen with its rings and the needle (towards the top right)
    for y in range(5, 21):
        for x in range(6, 26):
            d = math.hypot(x - 15.5, y - 12.5)
            if d < 7.2:
                c = mix((14, 60, 56), (30, 120, 112), max(0.0, 1 - d / 7.2))
                if abs(d - 4.5) < 0.5:
                    c = (60, 160, 150)
                px(im, x, y, c)
    for t in range(7):
        px(im, 15 + t * 0.85, 12 - t * 0.7, (198, 247, 242))
    px(im, 15, 12, (246, 156, 73))
    px(im, 16, 13, (246, 156, 73))
    # the antenna and its tip
    for t in range(6):
        px(im, 25 + t // 2, 6 - t, (150, 152, 158))
    px(im, 28, 0, (226, 74, 82))
    px(im, 28, 1, (192, 40, 56))
    outline(im)
    return im


def save(rel, im):
    p = os.path.join(TEX, rel)
    im.save(p)
    return im


def main():
    blocks = [('blocks/camp_side.png', camp_side()), ('blocks/camp_stolenField.png', stolen_field()),
              ('blocks/camp_stolenSolar.png', stolen_solar()), ('blocks/camp_stolenPad.png', stolen_pad()),
              ('blocks/camp_campSafe.png', safe_door(False)), ('blocks/camp_campSafeOpen.png', safe_door(True)),
              ('blocks/ruin_vaultPlating.png', vault_plating()), ('blocks/ruin_vaultLamp.png', vault_lamp()),
              ('blocks/ruin_campTarp.png', camp_tarp())]
    items = [('items/relic_campKey.png', camp_key()), ('items/relic_keyFragment.png', key_fragment()),
             ('items/relic_sealedCrumb.png', sealed_crumb()), ('items/ruinFinder.png', ruin_finder())]
    for rel, im in blocks + items:
        save(rel, im)
    # the preview: every texture x6 on the mod's dark grey
    k = 6
    cols = 7
    all_ = blocks + items
    rows = (len(all_) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * (S * k + 8) + 8, rows * (S * k + 8) + 8), (64, 64, 68, 255))
    for i, (rel, im) in enumerate(all_):
        big = im.resize((S * k, S * k), Image.NEAREST)
        sheet.paste(big, (8 + (i % cols) * (S * k + 8), 8 + (i // cols) * (S * k + 8)), big)
    sheet.save(PREVIEW)
    print('wrote %d textures and %s' % (len(all_), PREVIEW))


if __name__ == '__main__':
    main()
