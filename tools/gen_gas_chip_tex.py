# -*- coding: utf-8 -*-
"""БР-4: icons of the four life-support armour chips (Silicon Age, docs/plan-audit-2026-10-20-star.md).

The chips used to borrow the old chips' pictures (util/ChipType). This draws their own, in the old chips' style:
the frame (green-grey board, dark screen, gold pins) is the colours most of the existing chipSensor / chipPower /
chipDefense / chipMobility / chipUtility agree on, the screen's inside is drawn anew in the old screens' steps, and
a new symbol goes on the 12 x 12 screen, shaded like the old ones (lit top-left, dark bottom-right, a drop shadow
on the screen):
  chipCryoLoop    - a frosty helium-blue coil;
  chipCryoTank    - a gas cylinder with a snowflake;
  chipOxygenRegen - an O2 bubble inside a round arrow;
  chipRecuperator - two round arrows chasing each other round a drop of helium.
Variants: A and B - two different symbols on the old board; C - the A symbols on a frost-blue board (the
life-support line told apart by its board colour). The size is 32 x 32, like the old chips and every item of the
mod. ItemArmorChipSC takes the icon by ChipType.textureName, so the file names are the only link to the code.

Writes:
  textures/items/chipCryoLoop.png ... chipRecuperator.png  - the chosen variant (A unless --variant B|C);
  textures/items/<name>_t1..t3.png                         - only with --tiers: the chosen variant with tier
                                                             dots (I / II / III) in the board's corner (not used
                                                             by the code yet - 12 icons need getIconFromDamage
                                                             by meta % 3);
  tools/gas_chips_preview.png       - the old borrowed icons, then A / B / C (x8, the 1:1 icon in the corner);
  tools/gas_chips_tier_preview.png  - variant A with the tier dots (x8).

Usage: python gen_gas_chip_tex.py [project dir] [--variant=A|B|C] [--tiers]
"""
import colorsys
import os
import sys

from PIL import Image, ImageDraw

ARGS = [a for a in sys.argv[1:] if not a.startswith('--')]
FLAGS = [a for a in sys.argv[1:] if a.startswith('--')]
VARIANT = 'A'
for _f in FLAGS:
    if _f.startswith('--variant='):
        VARIANT = _f.split('=', 1)[1].upper()
HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = ARGS[0] if ARGS else os.path.join(HERE, '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'items')
PREVIEW = os.path.join(HERE, 'gas_chips_preview.png')
PREVIEW_TIERS = os.path.join(HERE, 'gas_chips_tier_preview.png')
S = 32
OLD = ['chipSensor', 'chipPower', 'chipDefense', 'chipMobility', 'chipUtility']
# what each gas chip borrowed before (ChipType), for the preview sheet
BORROWED = {'chipCryoLoop': 'chipDefense', 'chipCryoTank': 'chipUtility',
            'chipOxygenRegen': 'chipSensor', 'chipRecuperator': 'chipPower'}
NAMES = ['chipCryoLoop', 'chipCryoTank', 'chipOxygenRegen', 'chipRecuperator']
SX, SY = 10, 10                                         # the screen's top-left corner (12 x 12)

# symbol colours: (lit, base, shaded) - the old chips use the same three steps
FAMILY = {
    'B': ((230, 251, 255), (143, 227, 255), (88, 172, 214)),     # helium (ArmorGasSC: 0x8FE3FF)
    'W': ((255, 255, 255), (229, 236, 242), (176, 188, 202)),    # oxygen white / frost
    'S': ((214, 222, 232), (164, 174, 188), (110, 120, 136)),    # steel
    'G': ((196, 255, 206), (118, 228, 142), (64, 168, 96)),      # green - "comes back"
    'D': ((120, 186, 226), (78, 140, 190), (56, 104, 150)),      # the inside of a bubble
    'Y': ((255, 255, 197), (255, 241, 169), (218, 205, 143)),    # the power chip's pale gold
}


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def darker(c, t):
    return mix(c, (0, 0, 0), t)


def load(name):
    return Image.open(os.path.join(OUT, name + '.png')).convert('RGBA')


# the screen's inside, lit top-left to dark bottom-right (the steps the old chips' screens are made of)
SCREEN = [(62, 62, 70), (54, 54, 60), (48, 48, 55), (42, 42, 48), (36, 36, 41), (26, 26, 30)]


def blank_frame():
    """The old chips' frame with an empty screen: the board, the pins and the screen's rim are the colours most of
    the five old chips agree on; the screen's inside (where the old symbols were) is drawn anew in the same steps."""
    olds = [load(n) for n in OLD]
    im = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    for y in range(S):
        for x in range(S):
            votes = {}
            for o in olds:
                p = o.getpixel((x, y))
                votes[p] = votes.get(p, 0) + 1
            best = max(votes.items(), key=lambda kv: (kv[1], -sum(kv[0][:3])))[0]
            im.putpixel((x, y), best)
    for j in range(1, 11):
        for i in range(1, 11):
            t = (i + j - 2) / 18.0 * (len(SCREEN) - 1)
            noise = ((i * 37 + j * 91 + i * j * 13) % 7) / 7.0 - 0.5          # fixed, the same on every run
            k = max(0, min(len(SCREEN) - 1, int(round(t + noise * 0.9))))
            im.putpixel((SX + i, SY + j), SCREEN[k] + (255,))
    return im


def frost_frame(frame):
    """Variant C: the board's green-grey turned frost-blue (the gold pins and the neutral screen stay)."""
    im = frame.copy()
    for y in range(S):
        for x in range(S):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            if s < 0.08 or not (0.25 < h < 0.55):         # neutral screen pixels, gold pins
                continue
            nr, ng, nb = colorsys.hsv_to_rgb(0.555, min(1.0, s * 1.5), v)
            im.putpixel((x, y), (clamp(nr * 255), clamp(ng * 255), clamp(nb * 255), a))
    return im


# --- the symbols: 12 x 12 maps on the screen (row 0 / column 0 are its lit rim, row 11 / column 11 its dark rim -
# --- a symbol may run over them, as the old power bolt does). '.' = screen; an upper-case letter = that FAMILY,
# --- shaded by its neighbours; a lower-case letter = that family's lit colour as is (glints); '*' = pure white.
SYMBOLS = {
    'chipCryoLoop': {
        'A': [  # a frosty helium coil, four runs, brass ends (like the helium loop module it is made from)
            '............',
            'YBBbBBBbBB..',
            'YBBBBBBBBBB.',
            '.........BB.',
            '.BBBbBBBBBB.',
            '.BBBBBBBBB..',
            '.BB.........',
            '.BBBBBBbBBB.',
            '..BBBBBBBBB.',
            '.........BB.',
            'YBBbBBBBBBB.',
            'YBBBBBBBBB..',
        ],
        'B': [  # a U-tube with steel ends round a frost snowflake
            '............',
            'SS.......SS.',
            'BB.......BB.',
            'BB...W...BB.',
            'BB.W.W.W.BB.',
            'BB..WWW..BB.',
            'BB.WW*WW.BB.',
            'BB..WWW..BB.',
            'BBB.W.W.BBB.',
            '.BBB...BBB..',
            '..BBBBBBB...',
            '...BBBBB....',
        ],
    },
    'chipCryoTank': {
        'A': [  # a steel gas cylinder with a valve and a helium-blue snowflake
            '............',
            '....SSS.....',
            '.....S......',
            '..SSSSSSS...',
            '.SSSSSSSSS..',
            '.SSBSBSBSS..',
            '.SSSBBBSSS..',
            '.SSBBbBBSS..',
            '.SSSBBBSSS..',
            '.SSBSBSBSS..',
            '.SSSSSSSSS..',
            '..SSSSSSS...',
        ],
        'B': [  # a helium cell (steel caps, blue glass) with a white snowflake
            '............',
            '...SSSSS....',
            '..BBBBBBB...',
            '..BBBBBBB...',
            '..BWBWBWB...',
            '..BBWWWBB...',
            '..BWW*WWB...',
            '..BBWWWBB...',
            '..BWBWBWB...',
            '..BBBBBBB...',
            '...SSSSS....',
            '............',
        ],
    },
    'chipOxygenRegen': {
        'A': [  # an O2 bubble inside a round arrow
            '...GGGG.....',
            '..GGGGG..GG.',
            '.GG.....GGG.',
            'GG..WWW.GGG.',
            'GG.W*DDW....',
            'GG.WDDDW....',
            'GG.WDDDW....',
            'GG..WWW.....',
            '.GG.....G...',
            '..GGGGGGG...',
            '...GGGGG....',
            '............',
        ],
        'B': [  # a bubble with the energy bolt in it (oxygen made from EU)
            '............',
            '....WWWW....',
            '..WW....WW..',
            '..W*...YY...',
            '.W....YY..W.',
            '.W...YYYY.W.',
            '.W....YY..W.',
            '.W...YY...W.',
            '..W..Y...W..',
            '..WW....WW..',
            '....WWWW....',
            '............',
        ],
    },
    'chipRecuperator': {
        'A': [  # two round arrows chasing each other round a helium drop
            '...GGGG..G..',
            '..GGGGG.GG..',
            '.GG..B.GGG..',
            'GG...B......',
            'GG..BBB.....',
            '...BbBBB....',
            '...BBBBB..GG',
            '....BBB...GG',
            '..GGG....GG.',
            '..GG.GGGGG..',
            '..G..GGGG...',
            '............',
        ],
        'B': [  # vapour rises off a helium drop and an arrow brings it back
            '............',
            '....GGGGG...',
            '...GGGGGGG..',
            '..GG.....GG.',
            '.GGGG....GG.',
            '..GG.....GG.',
            '..........G.',
            '.....B...W..',
            '....BBB.....',
            '...BbBBB.W..',
            '...BBBBB....',
            '....BBB.....',
        ],
    },
}


NEIGHBOURS = [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)]


def occupied(m, x, y):
    return 0 <= x < 12 and 0 <= y < 12 and m[y][x] != '.'


def draw_symbol(frame, m):
    im = frame.copy()
    # the drop shadow first: the screen pixel right of / under a symbol pixel goes darker
    for y in range(12):
        for x in range(12):
            if m[y][x] != '.':
                continue
            if occupied(m, x - 1, y) or occupied(m, x, y - 1) or occupied(m, x - 1, y - 1):
                p = im.getpixel((SX + x, SY + y))
                c = darker(p[:3], 0.22)
                im.putpixel((SX + x, SY + y), c + (255,))
    for y in range(12):
        for x in range(12):
            ch = m[y][x]
            if ch == '.':
                continue
            if ch == '*':
                c = (255, 255, 255)
            elif ch.islower():
                c = FAMILY[ch.upper()][0]
            else:
                lit, base, shade = FAMILY[ch]
                # which way the pixel faces: the sum of the directions to its empty neighbours; the light comes
                # from the top-left, as on the old chips (a thin stroke faces both ways and keeps its base colour)
                nx = ny = 0.0
                for dx, dy in NEIGHBOURS:
                    if not occupied(m, x + dx, y + dy):
                        w = 1.0 if dx == 0 or dy == 0 else 0.5
                        nx += dx * w
                        ny += dy * w
                face = -(nx + ny)
                c = lit if face >= 1 else shade if face <= -1 else base
            im.putpixel((SX + x, SY + y), c + (255,))
    return im


TIER_LED = ((236, 252, 255), (143, 227, 255), (70, 140, 176))


def with_tier(icon, tier):
    """Tier I / II / III: 1-3 lit 2 x 2 dots on the board's bottom-right corner, right to left."""
    im = icon.copy()
    for i in range(tier):
        x0 = 24 - i * 3
        im.putpixel((x0, 24), TIER_LED[0] + (255,))
        im.putpixel((x0 + 1, 24), TIER_LED[1] + (255,))
        im.putpixel((x0, 25), TIER_LED[1] + (255,))
        im.putpixel((x0 + 1, 25), TIER_LED[2] + (255,))
    return im


def build(variant, frame, cool):
    out = {}
    for name in NAMES:
        m = SYMBOLS[name]['A' if variant == 'C' else variant]
        for row in m:
            assert len(row) == 12, (name, variant, row)
        assert len(m) == 12, (name, variant)
        out[name] = draw_symbol(cool if variant == 'C' else frame, m)
    return out


def sheet(rows, captions, title, z=8):
    cell = S * z
    pad = 16
    head = 26
    w = pad + len(NAMES) * (cell + pad) + 150
    h = head + len(rows) * (cell + pad + 22) + pad
    im = Image.new('RGBA', (w, h), (60, 60, 60, 255))
    d = ImageDraw.Draw(im)
    d.text((pad, 8), title, fill=(255, 255, 255, 255))
    for r, (label, icons) in enumerate(rows):
        y = head + r * (cell + pad + 22)
        for c, icon in enumerate(icons):
            x = pad + c * (cell + pad)
            d.text((x, y), captions[c], fill=(220, 220, 220, 255))
            big = icon.resize((cell, cell), Image.NEAREST)
            im.alpha_composite(big, (x, y + 16))
            im.alpha_composite(icon, (x + cell - S, y + 16 + cell - S))      # 1:1 in the corner
        d.text((pad + len(NAMES) * (cell + pad), y + 16 + cell // 2), label, fill=(255, 255, 255, 255))
    return im


def main():
    variant = VARIANT
    if variant not in ('A', 'B', 'C'):
        sys.exit('--variant= takes A, B or C, not ' + variant)
    frame = blank_frame()
    cool = frost_frame(frame)
    sets = dict((v, build(v, frame, cool)) for v in ('A', 'B', 'C'))
    chosen = sets[variant]
    for name in NAMES:
        chosen[name].save(os.path.join(OUT, name + '.png'))
        if '--tiers' in FLAGS:
            for t in (1, 2, 3):
                with_tier(chosen[name], t).save(os.path.join(OUT, '%s_t%d.png' % (name, t)))
    rows = [('old (borrowed)', [load(BORROWED[n]) for n in NAMES])]
    rows += [('variant ' + v, [sets[v][n] for n in NAMES]) for v in ('A', 'B', 'C')]
    sheet(rows, NAMES, 'BR-4 gas chips: A, B - symbols on the old board; C - A on a frost-blue board').save(PREVIEW)
    trows = [('tier ' + 'I' * t, [with_tier(sets['A'][n], t) for n in NAMES]) for t in (1, 2, 3)]
    sheet(trows, NAMES, 'BR-4 gas chips, variant A with tier dots (meta % 3 = 0 / 1 / 2)').save(PREVIEW_TIERS)
    print('wrote variant %s: %s%s' % (variant, ', '.join(NAMES), ' (+ tiers)' if '--tiers' in FLAGS else ''))


if __name__ == '__main__':
    main()
