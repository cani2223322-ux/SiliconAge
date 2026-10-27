# -*- coding: utf-8 -*-
"""«Лётное поле посольства» (Silicon Age, docs/plan-embassy-airfield-core.md §2.4, package P).

Writes into src/main/resources/assets/siliconage/textures/blocks the looks of the airfield (32 x 32, as the mod's machines):
  embassyAirfield_runway       - the runway's paving: dark asphalt, its aggregate's grain (no joints: a runway is one surface);
  embassyAirfield_shoulder     - the shoulder: a lighter, worn asphalt, coarser grain, a fine crack;
  embassyAirfield_apron        - the apron's concrete: a light slab, its joint along two edges (a block a slab), a few stains;
  embassyAirfield_markWhite    - white paint (the runway's and the helipad's marks): a little worn, the asphalt showing through;
  embassyAirfield_markYellow   - yellow paint (the taxiway's line, the stands): worn, the concrete showing through;
  embassyAirfield_lightEdge / _lightThreshold / _lightEnd - the runway's lamps flush in its asphalt: a steel ring with four bolts,
                                 the lens (clear-white, green, red) with its glare;
  embassyAirfield_lightTaxi    - the taxiway's lamp flush in the concrete: the blue lens;
  showcase2_towerConsole_front / _side / _top - «Пульт КДП»: the controller's side - the radar scope (rings, the sweep, three
                                 blips) and the flight strips over a row of keys; the sides - a vented steel panel, the
                                 Tuners' turquoise stripe; the top - the desk: keys, the microphone, a small screen;
  showcase2_serviceColumn_front / _side / _top - «Колонка обслуживания»: its display (the turquoise figures), the hydrogen
                                 (blue) and oxygen (white) hoses on their hooks, the hazard stripes at its foot; the sides -
                                 the casing, the turquoise stripe, vents; the top - the cap with its green status lamp;
  showcase2_airfieldBeacon_side / _top - «Светомаяк»: a glass lamp on a steel base - white one half, green the other, the
                                 lamp's bulb between; the top - the round lens split white and green in its ring;
  showcase2_aaPost_front / _side / _top - «Зенитный пост Наладчиков»: the twin barrels' muzzles in an armoured mantlet, the
                                 sight's turquoise eye between; the sides - riveted armour plates, the barrels along the top;
                                 the top - the turret's plates, its hatch and the radar dish.
Every texture is opaque. A preview sheet (x4) goes next to this script.

Usage: python tools/gen_airfield_tex.py [project dir]
"""
import math
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..')
BLOCKS = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
PREVIEW = os.path.join(HERE, 'airfield_tex_preview.png')

S = 32

# palettes (the settlements' steel, the dome's turquoise - «Бирюза Наладчиков» 0x48F0DC - as gen_embassy_tex.py)
OUTLINE = (11, 13, 10)
STEEL = (120, 140, 160)
STEEL_D = (84, 100, 118)
STEEL_L = (170, 186, 204)
CASING = (44, 40, 52)
CASING_L = (70, 66, 82)
CASING_D = (26, 24, 32)
TURQ = (72, 240, 220)
TURQ_L = (176, 255, 244)
TURQ_M = (44, 178, 166)
TURQ_D = (20, 98, 92)
WHITE = (236, 255, 252)
SCREEN = (10, 26, 28)
SCREEN_L = (18, 44, 46)
KEY = (58, 62, 72)
KEY_L = (96, 102, 116)
RED = (210, 64, 54)
AMBER = (232, 176, 52)
GREEN = (80, 230, 120)
# the airfield's own
ASPHALT = (58, 60, 64)
ASPHALT_D = (44, 46, 50)
ASPHALT_L = (88, 90, 94)
SHOULDER = (82, 84, 86)
SHOULDER_D = (66, 68, 70)
SHOULDER_L = (106, 108, 108)
CONCRETE = (150, 150, 144)
CONCRETE_D = (120, 120, 114)
CONCRETE_L = (172, 172, 166)
PAINT_W = (232, 234, 228)
PAINT_W_D = (206, 208, 204)
PAINT_Y = (226, 180, 40)
PAINT_Y_D = (196, 152, 30)
RADAR = (60, 220, 100)
RADAR_D = (18, 70, 34)
RADAR_BG = (8, 30, 16)
H2 = (70, 130, 230)
O2 = (226, 232, 236)
HAZARD = (226, 186, 40)
GLASS = (36, 52, 60)
LAMP_W = (255, 250, 226)
LAMP_G = (110, 255, 150)
ARMOUR = (98, 106, 94)
ARMOUR_D = (66, 72, 64)
ARMOUR_L = (134, 142, 128)
BORE = (16, 16, 18)


def new_block(c):
    return Image.new('RGBA', (S, S), c + (255,))


def put(im, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), c + (255,))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(im, x, y, c)


def frame(im, light, dark, mid, width=2):
    """A bevelled frame round the face: lit top / left, dark bottom / right, the corners' rivets (gen_embassy_tex.frame)."""
    for i in range(S):
        for k in range(width):
            put(im, i, k, light if k == 0 else mid)
            put(im, k, i, light if k == 0 else mid)
            put(im, i, S - 1 - k, dark)
            put(im, S - 1 - k, i, dark if k == 0 else mid)
    put(im, 0, 0, OUTLINE)
    for (x, y) in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        put(im, x, y, STEEL_L)
        put(im, x + 1, y + 1, STEEL_D)


def noise(x, y, seed):
    """A small deterministic hash 0..255 (the same picture on every run)."""
    h = (x * 374761393 + y * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFF


def disc(im, cx, cy, r0, r1, c):
    """The ring r0 <= d < r1 round (cx, cy) in colour c (a function of (x, y, d) if callable)."""
    for y in range(S):
        for x in range(S):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if r0 <= d < r1:
                put(im, x, y, c(x, y, d) if callable(c) else c)


# ------------------------------------------------------------------ the paving and the paint

def asphalt_px(x, y, seed=1):
    n = noise(x, y, seed)
    if n < 36:
        return ASPHALT_D
    if n > 232:
        return ASPHALT_L
    return mix(ASPHALT, ASPHALT_D, (n % 17) / 60.0)


def runway():
    """Dark asphalt: the aggregate's grain, no joints (the runway reads as one surface)."""
    im = new_block(ASPHALT)
    for y in range(S):
        for x in range(S):
            put(im, x, y, asphalt_px(x, y))
    return im


def shoulder():
    """The shoulder: lighter, worn, a coarser grain (2 x 2), a fine crack across."""
    im = new_block(SHOULDER)
    for y in range(S):
        for x in range(S):
            n = noise(x // 2, y // 2, 2)
            c = SHOULDER_D if n < 50 else SHOULDER_L if n > 220 else mix(SHOULDER, SHOULDER_D, (noise(x, y, 3) % 13) / 40.0)
            put(im, x, y, c)
    cx = 9
    for y in range(4, 27):                                      # a hairline crack, wandering
        cx += (noise(3, y, 4) % 3) - 1
        put(im, cx, y, SHOULDER_D if y % 5 else ASPHALT_D)
    return im


def concrete_px(x, y, seed=5):
    n = noise(x, y, seed)
    c = mix(CONCRETE, CONCRETE_L, (n % 23) / 70.0) if n > 128 else mix(CONCRETE, CONCRETE_D, (n % 19) / 80.0)
    return c


def apron():
    """A concrete slab a block: fine grain, its joint along the top and left edges (the grid between slabs), two stains."""
    im = new_block(CONCRETE)
    for y in range(S):
        for x in range(S):
            put(im, x, y, concrete_px(x, y))
    for i in range(S):
        put(im, i, 0, CONCRETE_D)
        put(im, 0, i, CONCRETE_D)
        put(im, i, 1, mix(CONCRETE_D, CONCRETE, 0.5))
    for (sx, sy, r) in ((21, 12, 4.2), (8, 24, 2.6)):         # oil stains
        disc(im, sx, sy, 0, r, lambda x, y, d: mix(concrete_px(x, y), CONCRETE_D, 0.45 * (1 - d / r) + 0.1))
    return im


def mark_white():
    """White paint over asphalt: a little worn - the asphalt's dark grain shows in a few specks."""
    im = new_block(PAINT_W)
    for y in range(S):
        for x in range(S):
            n = noise(x, y, 7)
            put(im, x, y, asphalt_px(x, y) if n < 5 else PAINT_W_D if n < 40 else PAINT_W)
    return im


def mark_yellow():
    """Yellow paint over concrete: worn - the concrete shows in specks."""
    im = new_block(PAINT_Y)
    for y in range(S):
        for x in range(S):
            n = noise(x, y, 8)
            put(im, x, y, concrete_px(x, y) if n < 6 else PAINT_Y_D if n < 44 else PAINT_Y)
    return im


def lamp(base, lens, lens_d):
    """A lamp flush in the paving: a steel ring with four bolts, the lens with its glare and a rim of shade."""
    im = base()
    c = (S - 1) / 2.0
    disc(im, c, c, 8.0, 9.2, OUTLINE)
    disc(im, c, c, 6.0, 8.0, lambda x, y, d: STEEL_L if (x - c) + (y - c) < -3 else STEEL_D if (x - c) + (y - c) > 3 else STEEL)
    disc(im, c, c, 0.0, 6.0, lambda x, y, d: mix(lens, lens_d, d / 6.0))
    disc(im, c - 1.6, c - 1.6, 0.0, 2.0, lambda x, y, d: mix(WHITE, lens, d / 2.0))   # the glare
    for (bx, by) in ((c, c - 7), (c, c + 7), (c - 7, c), (c + 7, c)):
        put(im, int(round(bx)), int(round(by)), OUTLINE)
    return im


def light_edge():
    return lamp(runway, LAMP_W, (226, 214, 160))


def light_threshold():
    return lamp(runway, LAMP_G, (40, 170, 80))


def light_end():
    return lamp(runway, (255, 110, 96), (170, 40, 32))


def light_taxi():
    return lamp(apron, (130, 170, 255), (40, 76, 190))


# ------------------------------------------------------------------ the tower's desk

def tower_front():
    """The controller's side: the radar scope (rings, the sweep, three blips) on the left, the flight strips on the right, keys."""
    im = new_block(CASING)
    rect(im, 3, 3, 16, 17, RADAR_BG)
    cx, cy = 9.5, 10.0
    for y in range(3, 18):
        for x in range(3, 17):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            ang = math.atan2(y - cy, x - cx)
            if 6.0 <= d < 6.9 or 3.4 <= d < 3.9:
                put(im, x, y, RADAR_D if d < 4 else RADAR_D)    # the range rings
            if d < 6.0 and -0.9 < ang < -0.1:
                put(im, x, y, mix(RADAR, RADAR_D, (-ang - 0.1) / 0.8))   # the sweep's fading wedge
    for k in range(7):                                          # the sweep's line
        put(im, int(round(cx + k * 0.92)), int(round(cy - k * 0.39)), RADAR)
    for (bx, by) in ((6, 7), (13, 13), (11, 5)):                # the blips: the aircraft
        put(im, bx, by, (200, 255, 210))
        put(im, bx + 1, by, RADAR)
    for y in range(3, 18):
        put(im, 3, y, SCREEN_L)
        put(im, 16, y, OUTLINE)
    rect(im, 18, 3, 28, 17, SCREEN)                             # the flight strips: white, two amber, one turquoise (the next)
    strips = (PAINT_W, AMBER, TURQ, AMBER, PAINT_W)
    for i, y in enumerate(range(4, 17, 3)):
        for x in range(19, 28):
            put(im, x, y, strips[i] if x > 19 else OUTLINE)
            put(im, x, y + 1, mix(strips[i], SCREEN, 0.55) if x > 19 else OUTLINE)
        put(im, 21 + i % 3 * 2, y, OUTLINE)                     # a callsign's mark on each
    for y in range(3, 18):
        put(im, 18, y, SCREEN_L)
        put(im, 28, y, OUTLINE)
    for y in (21, 24):                                          # the keys
        for x in range(4, 28, 3):
            put(im, x, y, KEY_L)
            put(im, x + 1, y, KEY)
            put(im, x, y + 1, KEY)
            put(im, x + 1, y + 1, OUTLINE)
    put(im, 7, 21, RADAR)
    put(im, 22, 24, RED)
    for x in range(3, 29):                                      # the desk's lit edge
        put(im, x, 27, TURQ_M if x % 4 else TURQ_L)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def tower_side():
    """A vented steel panel with the Tuners' turquoise stripe (the Dispatchers' desk's kin)."""
    im = new_block(STEEL_D)
    for y in range(3, S - 3):
        for x in range(3, S - 3):
            put(im, x, y, STEEL if (x + y) % 9 else mix(STEEL, STEEL_L, 0.4))
    for y in range(6, 18, 3):
        for x in range(7, S - 7):
            put(im, x, y, OUTLINE)
            put(im, x, y + 1, STEEL_L)
    for x in range(3, S - 3):
        put(im, x, 22, TURQ)
        put(im, x, 23, TURQ_M)
    put(im, 24, 26, RADAR)                                      # the desk's power lamp
    frame(im, STEEL_L, OUTLINE, STEEL)
    return im


def tower_top():
    """The desk: the keyboard, a microphone on its stalk, a small screen with the wind's arrow."""
    im = new_block(CASING_L)
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            put(im, x, y, CASING if (x * 7 + y * 3) % 13 else CASING_L)
    for y in range(18, 27, 3):
        for x in range(5, 23, 3):
            put(im, x, y, KEY_L)
            put(im, x + 1, y, KEY)
            put(im, x, y + 1, KEY)
            put(im, x + 1, y + 1, OUTLINE)
    rect(im, 6, 5, 17, 12, SCREEN)                              # the small screen: the wind's arrow
    for k in range(6):
        put(im, 8 + k, 10 - k // 2, TURQ_L)
    put(im, 13, 7, TURQ_L)
    put(im, 12, 6, TURQ)
    put(im, 13, 8, TURQ)
    for x in range(6, 18):
        put(im, x, 13, OUTLINE)
    for k in range(9):                                          # the microphone: its stalk, its head
        put(im, 25 - k // 3, 24 - k, STEEL_L if k % 2 else STEEL)
    disc(im, 22.5, 14.5, 0, 2.2, lambda x, y, d: OUTLINE if d > 1.4 else KEY_L)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


# ------------------------------------------------------------------ the service column

def hazard_band(im, y0, y1):
    for y in range(y0, y1 + 1):
        for x in range(2, S - 2):
            put(im, x, y, HAZARD if ((x + y) // 3) % 2 == 0 else OUTLINE)


def column_front():
    """The stand's side: the display (the figures in turquoise), the hydrogen (blue) and oxygen (white) hoses, hazard stripes."""
    im = new_block(CASING)
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            put(im, x, y, CASING if (x + 2 * y) % 11 else CASING_L)
    rect(im, 6, 4, 25, 11, SCREEN)                              # the display: two lines of figures
    digits = ((8, 6), (9, 6), (11, 6), (12, 6), (14, 6), (16, 6), (17, 6), (19, 6), (20, 6), (22, 6))
    for (x, y) in digits:
        put(im, x, y, TURQ_L)
        put(im, x, y + 1, TURQ)
    for x in range(8, 18):
        put(im, x, 9, TURQ_M if x % 2 else TURQ_D)
    put(im, 22, 9, GREEN)
    for y in range(4, 12):
        put(im, 6, y, SCREEN_L)
        put(im, 25, y, OUTLINE)
    for (hx, col) in ((9, H2), (21, O2)):                       # the holsters, the nozzles, the hoses' loops
        rect(im, hx - 2, 14, hx + 2, 18, STEEL_D)
        rect(im, hx - 1, 15, hx + 1, 17, col)
        put(im, hx, 13, STEEL_L)
        for k in range(8):
            ang = math.pi * k / 7.0
            put(im, int(round(hx + 3.2 * math.cos(ang))), int(round(22 + 2.6 * math.sin(ang))), col)
        put(im, hx - 3, 19, col)
        put(im, hx - 3, 20, col)
        put(im, hx - 3, 21, col)
    hazard_band(im, 26, 28)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def column_side():
    """The casing: a turquoise stripe up its middle, vents, hazard stripes at the foot."""
    im = new_block(CASING)
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            put(im, x, y, CASING if (x + 2 * y) % 11 else CASING_L)
    for y in range(3, 25):
        put(im, 15, y, TURQ)
        put(im, 16, y, TURQ_M)
    for y in range(6, 20, 3):
        for x in range(5, 12):
            put(im, x, y, OUTLINE)
            put(im, x, y + 1, CASING_L)
        for x in range(20, 27):
            put(im, x, y, OUTLINE)
            put(im, x, y + 1, CASING_L)
    hazard_band(im, 26, 28)
    frame(im, CASING_L, OUTLINE, CASING_D)
    return im


def column_top():
    """The cap: steel, riveted, the status lamp (green) in its middle."""
    im = new_block(STEEL_D)
    for y in range(2, S - 2):
        for x in range(2, S - 2):
            put(im, x, y, mix(STEEL_D, STEEL, 0.5 + 0.5 * ((S - x) + (S - y)) / (2.0 * S)))
    c = (S - 1) / 2.0
    disc(im, c, c, 4.0, 5.0, OUTLINE)
    disc(im, c, c, 0.0, 4.0, lambda x, y, d: mix((200, 255, 210), GREEN, d / 4.0))
    for (x, y) in ((6, 6), (25, 6), (6, 25), (25, 25)):
        put(im, x, y, OUTLINE)
    frame(im, STEEL_L, OUTLINE, STEEL)
    return im


# ------------------------------------------------------------------ the beacon

def beacon_side():
    """A glass lamp on a steel base: white the left half, green the right, the bulb between, the cap on top."""
    im = new_block(STEEL_D)
    for y in range(S):
        for x in range(S):
            if y >= 22:                                         # the base
                put(im, x, y, STEEL_L if y == 22 else STEEL if (x + y) % 7 else STEEL_D)
            elif y <= 3:                                        # the cap
                put(im, x, y, STEEL_L if y == 0 else STEEL)
            else:                                               # the glass, each half its colour, brighter in the middle
                t = abs(y - 12.5) / 9.0
                half = LAMP_W if x < S // 2 else LAMP_G
                edge = mix(half, GLASS, 0.15 + 0.6 * t + 0.25 * abs(x - 15.5) / 16.0)
                put(im, x, y, edge)
    for y in range(4, 22):                                      # the glass's frame bars
        for x in (0, 1, S - 2, S - 1, 15, 16):
            put(im, x, y, STEEL_D if x in (1, S - 2) else STEEL)
    disc(im, 15.5, 12.5, 0, 2.6, lambda x, y, d: mix(WHITE, (255, 236, 170), d / 2.6))   # the bulb
    for x in range(S):
        put(im, x, 4, OUTLINE)
        put(im, x, 21, OUTLINE)
    return im


def beacon_top():
    """The lens from above: a round lens split white and green in its steel ring, the bulb's glare."""
    im = new_block(STEEL)
    c = (S - 1) / 2.0
    for y in range(S):
        for x in range(S):
            put(im, x, y, STEEL if (x + y) % 6 else STEEL_D)
    disc(im, c, c, 12.0, 13.5, OUTLINE)
    disc(im, c, c, 0.0, 12.0, lambda x, y, d: mix(LAMP_W if x < c else LAMP_G, GLASS, d / 18.0))
    for i in range(S):
        if abs(i - c) < 12.0:
            put(im, int(c), i, STEEL_D)                         # the bar between the halves
            put(im, int(c) + 1, i, STEEL)
    disc(im, c, c, 0.0, 2.5, lambda x, y, d: mix(WHITE, (255, 236, 170), d / 2.5))
    frame(im, STEEL_L, OUTLINE, STEEL)
    return im


# ------------------------------------------------------------------ the air defence gun

def armour_px(x, y):
    n = noise(x, y, 11)
    return ARMOUR_D if n < 30 else ARMOUR_L if n > 236 else ARMOUR


def aa_front():
    """The mantlet: armour, the twin barrels' muzzles (bores, bright rims), the sight's turquoise eye between, rivets."""
    im = new_block(ARMOUR)
    for y in range(S):
        for x in range(S):
            put(im, x, y, armour_px(x, y))
    rect(im, 4, 6, 27, 19, ARMOUR_D)                            # the mantlet's plate
    rect(im, 5, 7, 26, 18, ARMOUR)
    for (mx, my) in ((9.5, 12.5), (21.5, 12.5)):
        disc(im, mx, my, 3.4, 4.4, STEEL_L)
        disc(im, mx, my, 2.4, 3.4, STEEL_D)
        disc(im, mx, my, 0.0, 2.4, BORE)
    disc(im, 15.5, 12.5, 0.0, 1.6, lambda x, y, d: TURQ_L if d < 0.8 else TURQ)
    for x in range(6, 26, 4):                                   # rivets along the plate
        put(im, x, 8, OUTLINE)
        put(im, x, 17, OUTLINE)
    for y in range(22, 29, 3):                                  # the base's plates
        for x in range(2, S - 2):
            put(im, x, y, ARMOUR_D)
    frame(im, ARMOUR_L, OUTLINE, ARMOUR_D)
    return im


def aa_side():
    """Armour plates with rivets, the two barrels along the top, the turret's ring below."""
    im = new_block(ARMOUR)
    for y in range(S):
        for x in range(S):
            put(im, x, y, armour_px(x, y))
    for (by, c) in ((6, STEEL_L), (7, STEEL), (8, STEEL_D), (10, STEEL_L), (11, STEEL), (12, STEEL_D)):
        for x in range(3, S - 3):
            put(im, x, by, c)                                   # the barrels
    for x in range(5, S - 4, 6):                                # their cooling rings
        for by in (6, 7, 8, 10, 11, 12):
            put(im, x, by, OUTLINE if by in (8, 12) else STEEL_D)
    for x in range(2, S - 2):                                   # the plates' seams
        put(im, x, 15, OUTLINE)
        put(im, x, 23, ARMOUR_D)
    for y in range(16, 29):
        put(im, 15, y, ARMOUR_D)
    for (x, y) in ((5, 18), (12, 18), (19, 18), (26, 18), (5, 26), (26, 26)):
        put(im, x, y, OUTLINE)
        put(im, x - 1, y - 1, ARMOUR_L)
    frame(im, ARMOUR_L, OUTLINE, ARMOUR_D)
    return im


def aa_top():
    """The turret from above: its plates, the hatch, the radar dish, the barrels' roots."""
    im = new_block(ARMOUR)
    for y in range(S):
        for x in range(S):
            put(im, x, y, armour_px(x, y))
    c = (S - 1) / 2.0
    disc(im, c, c, 12.0, 13.0, ARMOUR_D)                        # the turret's ring
    disc(im, 11.0, 18.0, 4.0, 5.0, OUTLINE)                     # the hatch
    disc(im, 11.0, 18.0, 0.0, 4.0, lambda x, y, d: ARMOUR_L if x + y < 28 else ARMOUR)
    put(im, 11, 18, STEEL_L)
    disc(im, 21.0, 19.0, 0.0, 3.6, lambda x, y, d: STEEL_L if d < 1.2 else STEEL if d < 2.6 else STEEL_D)   # the radar dish
    put(im, 21, 19, TURQ)
    for (bx) in (12, 19):                                       # the barrels' roots towards the front
        rect(im, bx, 2, bx + 1, 9, STEEL_D)
        put(im, bx, 2, BORE)
        put(im, bx + 1, 2, BORE)
    frame(im, ARMOUR_L, OUTLINE, ARMOUR_D)
    return im


def main():
    blocks = [('embassyAirfield_runway', runway()), ('embassyAirfield_shoulder', shoulder()), ('embassyAirfield_apron', apron()),
              ('embassyAirfield_markWhite', mark_white()), ('embassyAirfield_markYellow', mark_yellow()),
              ('embassyAirfield_lightEdge', light_edge()), ('embassyAirfield_lightThreshold', light_threshold()),
              ('embassyAirfield_lightEnd', light_end()), ('embassyAirfield_lightTaxi', light_taxi()),
              ('showcase2_towerConsole_front', tower_front()), ('showcase2_towerConsole_side', tower_side()),
              ('showcase2_towerConsole_top', tower_top()),
              ('showcase2_serviceColumn_front', column_front()), ('showcase2_serviceColumn_side', column_side()),
              ('showcase2_serviceColumn_top', column_top()),
              ('showcase2_airfieldBeacon_side', beacon_side()), ('showcase2_airfieldBeacon_top', beacon_top()),
              ('showcase2_aaPost_front', aa_front()), ('showcase2_aaPost_side', aa_side()), ('showcase2_aaPost_top', aa_top())]
    os.makedirs(BLOCKS, exist_ok=True)
    for name, im in blocks:
        assert all(im.getpixel((x, y))[3] == 255 for x in range(S) for y in range(S)), name + ' must be opaque'
        im.save(os.path.join(BLOCKS, name + '.png'))
    bz = 4 * S
    per_row = 10
    rows = (len(blocks) + per_row - 1) // per_row
    sheet = Image.new('RGBA', (per_row * (bz + 8) + 8, rows * (bz + 8) + 8), (70, 72, 76, 255))
    for i, (name, im) in enumerate(blocks):
        big = im.resize((bz, bz), Image.NEAREST)
        sheet.paste(big, (8 + (i % per_row) * (bz + 8), 8 + (i // per_row) * (bz + 8)), big)
    sheet.save(PREVIEW)
    print('wrote %d block textures into %s' % (len(blocks), BLOCKS))


if __name__ == '__main__':
    main()
