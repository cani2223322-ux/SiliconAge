# -*- coding: utf-8 -*-
"""Shared helpers of the 2026-10-26 ideas' texture scripts (docs/plan-ideas-2026-10-26.md: ПД-4..ПД-7, ОМ-2..ОМ-7).

The mod's pixel style (gen_trade_tex.py's): 32 x 32 items with a 1-pixel dark outline, blocks as framed steel panels with a light top-left
bevel, a dark bottom-right one, corner bolts and a few diagonal scratches. Each gen_*_tex.py imports this module from its own folder and
writes into src/main/resources/assets/siliconage/textures; the preview sheet goes to the folder in SC_PREVIEW_DIR (else next to the
scripts).
"""
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..')
TEX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures')
PREVIEW_DIR = os.environ.get('SC_PREVIEW_DIR', HERE)
CLEAR = (0, 0, 0, 0)
OUTLINE = (11, 13, 10)
S = 32

# GuiGaugeSC.tierColor (keep in step with it): LV .. SV
TIER_COLORS = [0x6E6E6E, 0xC0661A, 0xB8900E, 0x7A3CB8, 0x178C84, 0x2F7FD0, 0x2A1C3E, 0xB81E32]
TIERS = ['LV', 'MV', 'HV', 'EV', 'IV', 'QV', 'XV', 'SV']

GOLD = (224, 184, 80)
GOLD_D = (160, 120, 44)
GOLD_L = (250, 226, 150)
STEEL = (138, 148, 158)
STEEL_D = (86, 96, 106)
STEEL_DD = (60, 66, 74)
STEEL_L = (184, 194, 204)
WHITE = (240, 246, 250)
CYAN = (110, 230, 255)
CYAN_D = (36, 110, 150)
DOME = (60, 210, 200)
DOME_D = (30, 120, 114)
RED = (210, 56, 46)
RED_D = (130, 30, 26)
GREEN = (90, 230, 110)
GREEN_D = (40, 140, 60)
ORANGE = (255, 160, 60)
VIOLET = (190, 120, 255)
VIOLET_D = (98, 52, 160)
VIOLET_L = (230, 200, 255)
LEAD = (104, 110, 122)
LEAD_D = (70, 74, 84)
LEAD_L = (150, 156, 168)


def rgb(c):
    if isinstance(c, int):
        return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
    return tuple(c[:3])


def new(w=S, h=S):
    return Image.new('RGBA', (w, h), CLEAR)


def px(im, x, y, c, a=255):
    w, h = im.size
    if c is not None and 0 <= x < w and 0 <= y < h:
        c = rgb(c)
        im.putpixel((x, y), (c[0], c[1], c[2], a))


def rect(im, x, y, w, h, c, a=255):
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            px(im, xx, yy, c, a)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    a, b = rgb(a), rgb(b)
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(c, k):
    """k > 0 lighter (towards white), k < 0 darker (towards black)."""
    return mix(c, (255, 255, 255), k) if k >= 0 else mix(c, (0, 0, 0), -k)


def line(im, x0, y0, x1, y1, c, a=255):
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        px(im, x0, y0, c, a)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def disc(im, cx, cy, r, c, a=255):
    for y in range(int(cy - r - 1), int(cy + r + 2)):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                px(im, x, y, c, a)


def ring(im, cx, cy, r0, r1, c, a=255):
    for y in range(int(cy - r1 - 1), int(cy + r1 + 2)):
        for x in range(int(cx - r1 - 1), int(cx + r1 + 2)):
            d = (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2
            if r0 * r0 <= d <= r1 * r1:
                px(im, x, y, c, a)


def noise(x, y, seed=0):
    """A fixed pseudo-random 0..1 by pixel (the same texture every run)."""
    n = (x * 374761393 + y * 668265263 + seed * 1442695041) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


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


def panel(im, base, x=0, y=0, w=S, h=S, bolts=True, scratches=True, seed=1):
    """A block's steel panel: the frame, the bevel, a faint grain, the bolts, the scratches."""
    dark, light = shade(base, -0.45), shade(base, 0.35)
    for yy in range(y, y + h):
        for xx in range(x, x + w):
            k = (noise(xx, yy, seed) - 0.5) * 0.08
            px(im, xx, yy, shade(base, k))
    rect(im, x, y, w, 1, dark)
    rect(im, x, y, 1, h, dark)
    rect(im, x, y + h - 1, w, 1, dark)
    rect(im, x + w - 1, y, 1, h, dark)
    rect(im, x + 1, y + 1, w - 2, 1, light)
    rect(im, x + 1, y + 1, 1, h - 2, light)
    rect(im, x + 1, y + h - 2, w - 2, 1, shade(base, -0.25))
    rect(im, x + w - 2, y + 1, 1, h - 2, shade(base, -0.25))
    if bolts:
        for bx, by in ((x + 3, y + 3), (x + w - 4, y + 3), (x + 3, y + h - 4), (x + w - 4, y + h - 4)):
            px(im, bx, by, shade(base, 0.55))
            px(im, bx + 1, by + 1, shade(base, -0.5))
    if scratches:
        for k in range(3):
            sx, sy = x + 8 + k * 7, y + 22 - k * 5
            for i in range(3):
                px(im, sx + i, sy - i, shade(base, 0.18))


def lamp(im, x, y, c, on=True):
    """A 2 x 2 indicator lamp with its glint."""
    rect(im, x - 1, y - 1, 4, 4, OUTLINE)
    rect(im, x, y, 2, 2, c if on else shade(c, -0.6))
    if on:
        px(im, x, y, shade(c, 0.6))


def save(folder, name, im):
    d = os.path.join(TEX, folder)
    os.makedirs(d, exist_ok=True)
    im.save(os.path.join(d, name + '.png'))


def preview(name, images, scale=4):
    """The images side by side on grey, x scale, into PREVIEW_DIR/<name>_preview.png."""
    w = sum(i.size[0] * scale + 6 for i in images) + 6
    h = max(i.size[1] for i in images) * scale + 12
    sheet = Image.new('RGBA', (w, h), (70, 72, 76, 255))
    x = 6
    for i in images:
        big = i.resize((i.size[0] * scale, i.size[1] * scale), Image.NEAREST)
        sheet.paste(big, (x, 6), big)
        x += big.size[0] + 6
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    sheet.save(os.path.join(PREVIEW_DIR, name + '_preview.png'))
