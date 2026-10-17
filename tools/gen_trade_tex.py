# -*- coding: utf-8 -*-
"""Подложка (Si-14) phase 2, the «Терминал обмена» (Silicon Age, docs/plan-substrate-tech-f2.md §9, package T).

Writes into src/main/resources/assets/siliconage/textures:
  gui/trade.png            - the trade screen (GuiTradeSC), 256 x 256: the window 248 x 232 at (0, 0) in the mod's big
                             screens' steel (GuiBigSC's palette: the frame, the dark title bar with the dome's turquoise
                             accent, the divider over the inventory, the inventory's pockets); the holo screens are drawn
                             by the code (GuiHoloSC). The sprites below the window, at y 232:
                               ( 0, 232)  9 x 7  the deal's arrow, lit
                               ( 9, 232)  9 x 7  the same, dim (a deal not open to the player)
                               (18, 232) 16 x 16 the «Доверие» badge (the Elder's penance, a Stolen Cargo back)
                               (34, 232)  7 x 9  a padlock (a level not reached)
                               (41, 232)  9 x 7  a check mark (a contract handed in)
                               (50, 232)  4 x 12 the list's scroll knob
  items/creditChip         - «Кредит-чип»: a turquoise card with a gold contact pad, the dome's arc stamped on it and
                             a green diode; dark outline as every item of the mod;
  items/loot_stolenCargo   - «Краденый груз»: a dented steel crate with ribs, its turquoise settlement seal broken off
                             (half of it hanging on a wire) and the Marauders' blue insulating tape over a corner;
and a preview sheet next to this script (the window x2, the sprites and the icons x4).

Usage: python tools/gen_trade_tex.py [project dir]
"""
import os
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'trade_tex_preview.png')
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)
S = 32

# GuiBigSC's steel (keep in step with it)
FRAME = 0x1E2024
PANEL = 0xB9C1CC
HI_TOP = 0xECF0F6
HI_LEFT = 0xE2E8F0
LO_BOTTOM = 0x6E747E
LO_RIGHT = 0x767C86
TITLE_BAR = 0x2E3642
TITLE_LINE = 0xF0F2F6
SEP_DARK = 0x5A606A
POCKET_DARK = 0x343C48
POCKET_LIGHT = 0xECF1F7
POCKET_FILL = 0x707A88
# the settlement's dome (TileEntitySettlementCoreSC's shell 0x3CD2C8) and the holo cyan
DOME = (60, 210, 200)
DOME_D = (30, 120, 114)
DOME_DD = (18, 72, 70)
DOME_L = (150, 240, 232)
CYAN = (110, 230, 255)
CYAN_D = (36, 110, 150)
DIM = (70, 80, 92)
DIM_D = (44, 50, 58)
# items
GOLD = (224, 184, 80)
GOLD_D = (160, 120, 44)
GOLD_L = (250, 226, 150)
DIODE = (92, 255, 106)
STEEL = (138, 148, 158)
STEEL_D = (86, 96, 106)
STEEL_DD = (60, 66, 74)
STEEL_L = (184, 194, 204)
TAPE = (46, 95, 200)
TAPE_L = (90, 140, 230)
WIRE = (196, 112, 58)
WHITE = (240, 246, 250)
RED = (200, 60, 50)
LOCK_BODY = (214, 162, 60)
LOCK_D = (140, 98, 30)
GREEN = (90, 230, 110)
GREEN_D = (40, 140, 60)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255) if isinstance(c, int) else c


def px(im, x, y, c):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        c = rgb(c)
        im.putpixel((x, y), (c[0], c[1], c[2], 255))


def rect(im, x, y, w, h, c):
    """GuiBigSC.rect: w x h from (x, y)."""
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(im, xx, yy, c)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def outline(im):
    """A 1-pixel dark outline round everything drawn (the mod's items all have one)."""
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


# ---------------------------------------------------------------- the screen

W, H = 248, 232
INV_X, INV_Y, HOTBAR_Y = 44, 155, 213
SEPARATOR_Y = 141


def pocket(im, x, y):
    rect(im, x - 1, y - 1, 18, 18, POCKET_DARK)
    rect(im, x, y, 17, 17, POCKET_LIGHT)
    rect(im, x, y, 16, 16, POCKET_FILL)


def window(im):
    x = y = 0
    rect(im, x, y, W, H, FRAME)
    rect(im, x + 1, y + 1, W - 2, H - 2, PANEL)
    rect(im, x + 1, y + 1, W - 3, 1, HI_TOP)
    rect(im, x + 1, y + 1, 1, H - 3, HI_LEFT)
    rect(im, x + 2, y + H - 2, W - 3, 1, LO_BOTTOM)
    rect(im, x + W - 2, y + 2, 1, H - 3, LO_RIGHT)
    # the title bar, inset, with the dome's accent on its left end
    rect(im, x + 3, y + 3, W - 6, 1, LO_BOTTOM)
    rect(im, x + 3, y + 3, 1, 13, LO_BOTTOM)
    rect(im, x + 4, y + 4, W - 8, 11, TITLE_BAR)
    rect(im, x + 4, y + 15, W - 8, 1, TITLE_LINE)
    rect(im, x + 4, y + 4, 2, 11, DOME)
    rect(im, x + 6, y + 4, 1, 11, DOME_D)
    # faint vertical brushing on the steel (every 6th column a hair lighter): only where nothing else sits
    for xx in range(2, W - 2, 6):
        for yy in range(17, SEPARATOR_Y - 1):
            if im.getpixel((xx, yy))[:3] == rgb(PANEL):
                px(im, xx, yy, mix(PANEL, HI_LEFT, 0.15))
    # the divider over the inventory
    rect(im, x + 7, y + SEPARATOR_Y, W - 14, 1, SEP_DARK)
    rect(im, x + 7, y + SEPARATOR_Y + 1, W - 14, 1, TITLE_LINE)
    for r in range(3):
        for c in range(9):
            pocket(im, x + INV_X + c * 18, y + INV_Y + r * 18)
    for c in range(9):
        pocket(im, x + INV_X + c * 18, y + HOTBAR_Y)
    # two rivets in the corners of the inventory's bay
    for rx, ry in ((14, 150), (W - 16, 150), (14, H - 10), (W - 16, H - 10)):
        rect(im, rx, ry, 2, 2, STEEL_D)
        px(im, rx, ry, HI_TOP)


ARROW = ["....##...",
         ".....##..",
         "########.",
         "#########",
         "########.",
         ".....##..",
         "....##..."]

BADGE = ["......####......",
         "....##DDDD##....",
         "...#DDLLLLDD#...",
         "..#DLLLLLLLLD#..",
         ".#DLLLLWWLLLLD#.",
         ".#DLLLLWWLLLLD#.",
         "#DLLLLLWWLLLLLD#",
         "#DLLWWWWWWWWLLD#",
         "#DLLWWWWWWWWLLD#",
         "#DLLLLLWWLLLLLD#",
         ".#DLLLLWWLLLLD#.",
         ".#DDLLLWWLLLDD#.",
         "..#DDLLLLLLDD#..",
         "...##DDDDDD##...",
         ".....##DD##.....",
         ".......##......."]

LOCK = [".###...",
        "#...#..",
        "#...#..",
        "#####..",
        "#BBBBB#",
        "#BB.BB#",
        "#BB.BB#",
        "#BBBBB#",
        "#######"]

CHECK = ["........#",
         ".......##",
         "#.....##.",
         "##...##..",
         ".##.##...",
         "..###....",
         "...#....."]


def sprite(im, ox, oy, rows, colours):
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in colours:
                px(im, ox + x, oy + y, colours[ch])


def screen():
    im = Image.new('RGBA', (256, 256), CLEAR)
    window(im)
    sy = 232
    sprite(im, 0, sy, ARROW, {'#': CYAN})
    for y in range(4, 7):                                       # the lit arrow's lower half a shade darker
        for x in range(9):
            if ARROW[y][x] == '#':
                px(im, x, sy + y, mix(CYAN, CYAN_D, 0.45))
    sprite(im, 9, sy, ARROW, {'#': DIM})
    sprite(im, 18, sy, BADGE, {'#': DOME_DD, 'D': DOME_D, 'L': DOME, 'W': WHITE})
    for x, y in ((5, 3), (4, 4), (6, 3)):                       # the badge's glint
        px(im, 18 + x, sy + y, DOME_L)
    sprite(im, 34, sy, LOCK, {'#': LOCK_D, 'B': LOCK_BODY})
    px(im, 34 + 3, sy + 5, OUTLINE)
    sprite(im, 41, sy, CHECK, {'#': GREEN})
    for y in range(7):                                          # its shadow side
        for x in range(9):
            if CHECK[y][x] == '#' and (x + 1 >= 9 or CHECK[y][x + 1] != '#') and y > 2:
                px(im, 41 + x, sy + y, GREEN_D)
    rect(im, 50, sy, 4, 12, CYAN_D)                             # the knob: framed, lit in the middle
    rect(im, 51, sy + 1, 2, 10, CYAN)
    rect(im, 51, sy + 5, 2, 2, (220, 250, 255))
    return im


# ---------------------------------------------------------------- items

def credit_chip():
    im = Image.new('RGBA', (S, S), CLEAR)
    x0, y0, x1, y1 = 3, 8, 28, 24                               # the card, its corners cut
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            corner = min(x - x0, x1 - x) + min(y - y0, y1 - y)
            if corner < 2:
                continue
            t = ((x - x0) + (y - y0)) / float((x1 - x0) + (y1 - y0))
            c = mix(DOME_L, DOME_D, 0.25 + t * 0.6)
            if y == y0 or x == x0 or (corner == 2 and (x < 16 and y < 16)):
                c = DOME_L
            elif y == y1 or x == x1:
                c = DOME_DD
            px(im, x, y, c)
    # the gold contact pad: a 7 x 6 block cut into six pads
    rect(im, 6, 12, 8, 7, GOLD_D)
    rect(im, 7, 13, 6, 5, GOLD)
    for x in (9, 10):
        for y in range(13, 18):
            px(im, x, y, GOLD_D)
    for x in range(7, 13):
        px(im, x, 15, GOLD_D)
    px(im, 7, 13, GOLD_L)
    px(im, 11, 13, GOLD_L)
    # the dome's arc stamped on the right, over a floor line
    for x in range(17, 26):
        px(im, x, 20, DOME_DD)
    for x, y in ((17, 19), (18, 17), (19, 16), (20, 15), (21, 15), (22, 15), (23, 16), (24, 17), (25, 19)):
        px(im, x, y, DOME_DD)
        px(im, x, y - 1, DOME_L)
    px(im, 21, 18, DOME_L)                                      # the core under the dome
    # the diode and the traces from the pad
    rect(im, 24, 10, 2, 2, DIODE)
    px(im, 24, 10, (214, 255, 216))
    for x in range(14, 17):
        px(im, x, 14, GOLD_D)
    for x in range(14, 18):
        px(im, x, 17, GOLD_D)
    outline(im)
    return im


def stolen_cargo():
    im = Image.new('RGBA', (S, S), CLEAR)
    x0, y0, x1, y1 = 4, 10, 27, 27                              # the crate's front; a top face above it
    for y in range(6, 10):                                      # the lid, seen from a little above
        d = 9 - y
        for x in range(x0 + d, x1 + 1 + min(d, 2)):
            px(im, x, y, STEEL_L if y == 6 else mix(STEEL_L, STEEL, 0.5))
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            c = mix(STEEL, STEEL_D, (y - y0) / float(y1 - y0) * 0.6)
            if x == x0 or y == y0:
                c = STEEL_L
            elif x == x1 or y == y1:
                c = STEEL_DD
            px(im, x, y, c)
    for y in (14, 21):                                          # the ribs
        for x in range(x0 + 1, x1):
            px(im, x, y, STEEL_DD)
            px(im, x, y + 1, STEEL_L)
    for x, y in ((24, 25), (25, 24), (26, 23), (25, 25), (26, 24), (26, 25)):
        px(im, x, y, STEEL_DD)                                  # a dent at the bottom right corner
    for x in (x0 + 2, x1 - 2):                                  # bolts
        for y in (y0 + 2, y1 - 2):
            px(im, x, y, STEEL_DD)
    # the settlement's seal: a turquoise plate with the dome's arc - its right half torn off
    rect(im, 12, 15, 5, 6, DOME_D)
    rect(im, 13, 16, 3, 4, DOME)
    px(im, 13, 16, DOME_L)
    for x, y in ((17, 15), (17, 17), (17, 19)):                 # the jagged tear
        px(im, x, y, DOME_D)
    # the other half hangs on its wire under the crate
    for x, y in ((19, 18), (19, 19), (20, 20), (20, 21), (20, 22), (21, 23)):
        px(im, x, y, WIRE)
    rect(im, 20, 24, 3, 3, DOME_D)
    px(im, 21, 25, DOME)
    # the Marauders' blue tape across the top left corner
    for i in range(9):
        x, y = x0 - 1 + i, y0 + 7 - i
        for k in range(3):
            if y + k >= 6:
                px(im, x, y + k, TAPE if k else TAPE_L)
    # a scratched red mark (whoever took it struck the address out)
    for x, y in ((8, 23), (9, 24), (10, 23), (9, 22), (8, 25), (10, 25)):
        px(im, x, y, RED)
    outline(im)
    return im


def main():
    out = {
        ('gui', 'trade'): screen(),
        ('items', 'creditChip'): credit_chip(),
        ('items', 'loot_stolenCargo'): stolen_cargo(),
    }
    for (folder, name), im in out.items():
        os.makedirs(os.path.join(TEX, folder), exist_ok=True)
        im.save(os.path.join(TEX, folder, name + '.png'))
    # the preview: the window x2, the two icons x4, on grey
    z = 2
    gui = out[('gui', 'trade')]
    sheet = Image.new('RGBA', (256 * z + 2 * S * 4 + 40, 256 * z + 20), (70, 72, 76, 255))
    big = gui.resize((256 * z, 256 * z), Image.NEAREST)
    sheet.paste(big, (10, 10), big)
    x = 256 * z + 20
    for key in (('items', 'creditChip'), ('items', 'loot_stolenCargo')):
        icon = out[key].resize((S * 4, S * 4), Image.NEAREST)
        sheet.paste(icon, (x, 10), icon)
        x += S * 4 + 5
    sheet.save(PREVIEW)


if __name__ == '__main__':
    main()
