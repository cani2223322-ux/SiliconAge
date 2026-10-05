# -*- coding: utf-8 -*-
"""Ground / Space Bridge block textures (Silicon Age, docs/plan-ground-bridge.md, stage 1).

Writes into src/main/resources/assets/siliconage/textures/blocks (32 x 32, the mod's block size):
  bridgeCasing - the dark metal casing (ports' and modules' tops / bottoms);
  bridgeControllerFront / Side / Top - the controller: a green screen with the ring's schematic, side vents;
  bridgeCapacitorSide / Top - the Singularity Capacitor: blue cells behind glass;
  bridgeEnergyPort - the energy port (a gold socket, SV marks); bridgeGasPort - the gas port (a cyan valve);
  bridgeFocuser - the Singularity Focuser (magenta lens);
  bridgeNav / bridgeMass / bridgeCooler / bridgeShield + bridgeModuleTop - the modules (an icon on a plate);
  bridgeBeacon / bridgeBeaconTop - the Receiver Beacon; bridgeAnchor / bridgeAnchorTop - the Interdimensional Anchor;
  gravityCoilOn - the gravity coil glowing (an open portal);
  bridgeVortexG_0..8 (3 x 3 tiles of one green swirl) and bridgeVortexS_0..24 (5 x 5 tiles of a blue-white swirl
  with stars), each animated (16 frames, .mcmeta).
and a preview sheet (x4) next to this script.

Usage: python gen_bridge_tex.py [project dir] [--fx]
  --fx: only textures/fx/vortex_spiral, vortex_stars, vortex_glow (256 / 256 / 128 px, the one-disc vortex).
"""
import json
import math
import os
import random
import sys

from PIL import Image

PROJECT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith('--') else os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'blocks')
PREVIEW = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bridge_tex_preview.png')
S = 32
FRAMES = 16

BASE = (44, 52, 50)          # dark metal, a little green
OUTLINE = (12, 15, 14)
HI = (84, 96, 92)
LO = (26, 31, 30)
MID = (56, 66, 63)
RIVET = (120, 132, 128)
GREEN = (60, 220, 110)
BLUE = (80, 160, 255)
CYAN = (110, 220, 255)
GOLD = (240, 196, 80)
MAGENTA = (220, 80, 240)


def clamp(v):
    return max(0, min(255, int(round(v))))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def scale(c, k):
    return tuple(clamp(v * k) for v in c)


def new(c=BASE):
    return Image.new('RGBA', (S, S), c + (255,))


def px(im, x, y, c, a=255):
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.putpixel((x, y), tuple(c) + (a,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(im, x, y, c)


def bevel(im, x0, y0, x1, y1, hi, lo, fill=None):
    if fill is not None:
        rect(im, x0, y0, x1, y1, fill)
    rect(im, x0, y0, x1, y0, hi)
    rect(im, x0, y0, x0, y1, hi)
    rect(im, x0, y1, x1, y1, lo)
    rect(im, x1, y0, x1, y1, lo)


def noise(im, x0, y0, x1, y1, seed):
    r = random.Random(seed)
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if r.random() < 0.18:
                c = im.getpixel((x, y))[:3]
                px(im, x, y, scale(c, 1.08 if r.random() < 0.5 else 0.92))


def casing(seed=1):
    im = new()
    noise(im, 0, 0, S - 1, S - 1, seed)
    rect(im, 0, 0, S - 1, 0, OUTLINE)
    rect(im, 0, S - 1, S - 1, S - 1, OUTLINE)
    rect(im, 0, 0, 0, S - 1, OUTLINE)
    rect(im, S - 1, 0, S - 1, S - 1, OUTLINE)
    bevel(im, 1, 1, S - 2, S - 2, HI, LO)
    for (x, y) in ((3, 3), (S - 4, 3), (3, S - 4), (S - 4, S - 4)):
        px(im, x, y, RIVET)
        px(im, x + 1, y + 1, LO)
    return im


def glow_rect(im, x0, y0, x1, y1, c, strength=0.35):
    for y in range(y0 - 1, y1 + 2):
        for x in range(x0 - 1, x1 + 2):
            if 0 <= x < S and 0 <= y < S and not (x0 <= x <= x1 and y0 <= y <= y1):
                old = im.getpixel((x, y))[:3]
                px(im, x, y, mix(old, c, strength))
    rect(im, x0, y0, x1, y1, c)


def controller_front():
    im = casing(2)
    bevel(im, 4, 4, 27, 22, LO, HI, (8, 14, 10))
    # the ring schematic: 5 x 5 squares, the inside green
    for v in range(5):
        for u in range(5):
            if u in (0, 4) or v in (0, 4):
                rect(im, 7 + u * 4, 6 + v * 3, 9 + u * 4, 7 + v * 3, (110, 104, 150))
    rect(im, 11, 9, 21, 16, scale(GREEN, 0.8))
    rect(im, 14, 11, 18, 14, GREEN)
    px(im, 16, 20, GREEN)
    # buttons under the screen
    for i, c in enumerate((GREEN, (240, 200, 80), (230, 80, 70))):
        bevel(im, 6 + i * 7, 25, 10 + i * 7, 28, HI, LO, scale(c, 0.85))
    return im


def controller_side():
    im = casing(3)
    for y in range(6, 26, 4):
        rect(im, 6, y, 25, y + 1, LO)
        rect(im, 6, y, 25, y, OUTLINE)
    glow_rect(im, 14, 28, 17, 28, GREEN, 0.3)
    return im


def controller_top():
    im = casing(4)
    bevel(im, 8, 8, 23, 23, LO, HI, MID)
    rect(im, 12, 12, 19, 19, (20, 30, 24))
    rect(im, 14, 14, 17, 17, scale(GREEN, 0.7))
    return im


def capacitor_side():
    im = casing(5)
    for i in range(3):
        x0 = 4 + i * 8
        bevel(im, x0, 4, x0 + 6, 27, OUTLINE, HI, (10, 16, 28))
        for y in range(6, 26):
            t = (y - 6) / 19.0
            px(im, x0 + 2, y, mix(BLUE, (180, 220, 255), 1 - t))
            px(im, x0 + 3, y, mix(BLUE, (210, 235, 255), 1 - t))
            px(im, x0 + 4, y, scale(BLUE, 0.7))
        px(im, x0 + 2, 6, (240, 250, 255))
    return im


def capacitor_top():
    im = casing(6)
    bevel(im, 6, 6, 25, 25, LO, HI, (12, 20, 34))
    for (x, y) in ((10, 10), (19, 10), (10, 19), (19, 19)):
        glow_rect(im, x, y, x + 2, y + 2, BLUE, 0.4)
    rect(im, 15, 6, 16, 25, scale(GOLD, 0.6))
    rect(im, 6, 15, 25, 16, scale(GOLD, 0.6))
    return im


def energy_port():
    im = casing(7)
    bevel(im, 8, 8, 23, 23, LO, HI, (40, 36, 24))
    bevel(im, 11, 11, 20, 20, scale(GOLD, 1.1), scale(GOLD, 0.5), GOLD)
    rect(im, 13, 13, 18, 18, (60, 46, 14))
    rect(im, 15, 12, 16, 19, (255, 230, 120))
    rect(im, 12, 15, 19, 16, (255, 230, 120))
    for i in range(4):
        glow_rect(im, 4 + i * 7, 27, 6 + i * 7, 27, GOLD, 0.25)
    return im


def gas_port():
    im = casing(8)
    for r in range(10, 0, -1):
        c = mix((20, 40, 50), CYAN, (10 - r) / 14.0)
        for a in range(0, 360, 4):
            px(im, int(round(16 + r * math.cos(math.radians(a)))), int(round(16 + r * math.sin(math.radians(a)))), c)
    rect(im, 14, 6, 17, 25, scale(CYAN, 0.6))
    rect(im, 6, 14, 25, 17, scale(CYAN, 0.6))
    rect(im, 13, 13, 18, 18, CYAN)
    rect(im, 15, 15, 16, 16, (230, 250, 255))
    return im


def focuser():
    im = casing(9)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - 15.5, y - 15.5)
            if d < 11:
                t = d / 11.0
                c = mix((255, 200, 255), MAGENTA, t)
                c = mix(c, (60, 10, 70), max(0, t - 0.6) * 2)
                px(im, x, y, c)
            elif d < 12.5:
                px(im, x, y, OUTLINE)
    for i in range(4):
        a = math.radians(45 + 90 * i)
        for r in range(12, 15):
            px(im, int(round(15.5 + r * math.cos(a))), int(round(15.5 + r * math.sin(a))), MAGENTA)
    px(im, 12, 11, (255, 255, 255))
    px(im, 13, 11, (255, 240, 255))
    return im


def module_base(seed, color):
    im = casing(seed)
    bevel(im, 5, 5, 26, 26, LO, HI, (18, 22, 21))
    glow_rect(im, 5, 28, 26, 28, color, 0.2)
    return im


def nav():
    im = module_base(10, GREEN)
    for a in range(0, 360, 3):
        px(im, int(round(15.5 + 8 * math.cos(math.radians(a)))), int(round(15.5 + 8 * math.sin(math.radians(a)))), scale(GREEN, 0.7))
    rect(im, 15, 7, 16, 24, scale(GREEN, 0.4))
    rect(im, 7, 15, 24, 16, scale(GREEN, 0.4))
    for i in range(7):
        px(im, 16 + i, 16 - i, GREEN)
    px(im, 21, 11, (220, 255, 220))
    return im


def mass():
    im = module_base(11, (240, 160, 60))
    for i in range(3):
        y = 9 + i * 6
        rect(im, 10 - i, y, 21 + i, y + 3, scale((240, 160, 60), 0.6 + i * 0.15))
    rect(im, 8, 25, 23, 25, (240, 160, 60))
    return im


def cooler():
    im = module_base(12, CYAN)
    for k in range(3):
        a = math.radians(k * 60)
        for r in range(-8, 9):
            px(im, int(round(15.5 + r * math.cos(a))), int(round(15.5 + r * math.sin(a))), CYAN)
    rect(im, 14, 14, 17, 17, (230, 250, 255))
    return im


def shield():
    im = module_base(13, (230, 80, 70))
    for y in range(8, 25):
        w = 8 if y < 17 else max(0, 8 - (y - 16))
        for x in range(16 - w, 16 + w):
            px(im, x, y, (150, 50, 46) if (x + y) % 5 else (230, 80, 70))
    for y in range(8, 25):
        w = 8 if y < 17 else max(0, 8 - (y - 16))
        if w > 0:
            px(im, 16 - w, y, (255, 140, 120))
            px(im, 15 + w, y, (120, 30, 30))
    return im


def module_top():
    im = casing(14)
    bevel(im, 9, 9, 22, 22, LO, HI, MID)
    rect(im, 13, 13, 18, 18, (24, 30, 28))
    return im


def beacon(top=False):
    im = casing(15 if not top else 16)
    if top:
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - 15.5, y - 15.5)
                if d < 6:
                    px(im, x, y, mix((200, 255, 210), GREEN, d / 6))
                elif d < 7:
                    px(im, x, y, OUTLINE)
        return im
    rect(im, 13, 4, 18, 27, (24, 30, 28))
    for y in range(5, 27, 3):
        glow_rect(im, 14, y, 17, y, GREEN if y % 2 else scale(GREEN, 0.6), 0.25)
    return im


def anchor(top=False):
    im = casing(17 if not top else 18)
    c = (150, 110, 255)
    if top:
        for a in range(0, 360, 2):
            for r in (5, 9):
                px(im, int(round(15.5 + r * math.cos(math.radians(a)))), int(round(15.5 + r * math.sin(math.radians(a)))), c)
        rect(im, 14, 14, 17, 17, (240, 230, 255))
        return im
    # an anchor shape
    rect(im, 15, 6, 16, 24, c)
    rect(im, 11, 9, 20, 10, c)
    for i in range(6):
        px(im, 9 + i, 24 - i // 2, c)
        px(im, 22 - i, 24 - i // 2, c)
    rect(im, 9, 21, 10, 24, c)
    rect(im, 21, 21, 22, 24, c)
    px(im, 15, 5, (240, 230, 255))
    px(im, 16, 5, (240, 230, 255))
    return im


def coil_on(src):
    """The gravity coil, glowing: its stripes lit violet-white."""
    im = src.copy().convert('RGBA')
    out = Image.new('RGBA', im.size)
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = im.getpixel((x, y))
            lum = (r + g + b) / 3.0
            if r > g + 40 and r > b + 20:          # the red stripes
                c = mix((190, 120, 255), (255, 240, 255), min(1, (r - 120) / 120.0))
            else:
                c = mix((r, g, b), (150, 110, 230), 0.25 if lum > 30 else 0.1)
            out.putpixel((x, y), c + (a,))
    return out


# ------------------------------------------------------------------ the vortex

def swirl(n, frame, space):
    """One frame of an n x n block swirl (n*S pixels), RGBA."""
    size = n * S
    im = Image.new('RGBA', (size, size))
    c = (size - 1) / 2.0
    rmax = size / 2.0
    t = frame / float(FRAMES)
    rnd = random.Random(7)
    stars = [(rnd.random() * size, rnd.random() * size, rnd.random()) for _ in range(n * n * 3)] if space else []
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy) / rmax
            th = math.atan2(dy, dx)
            arms = 3 if not space else 4
            v = math.sin(arms * th + 9.0 * r - t * 2 * math.pi)
            v = (v + 1) / 2
            core = max(0.0, 1 - r * 1.6)
            edge = max(0.0, min(1.0, (1.15 - r) * 3))
            if space:
                base = mix((10, 20, 70), (90, 150, 255), v * (1 - r * 0.5))
                col = mix(base, (240, 248, 255), core ** 1.5)
            else:
                base = mix((8, 60, 30), (70, 230, 120), v * (1 - r * 0.4))
                col = mix(base, (220, 255, 230), core ** 1.5)
            a = int(150 + 90 * edge * (0.6 + 0.4 * v))
            im.putpixel((x, y), col + (max(90, min(240, a)),))
    for (sx, sy, ph) in stars:
        # stars spiral in with the swirl
        dx, dy = sx - c, sy - c
        ang = -t * 2 * math.pi * 0.25
        rx = c + dx * math.cos(ang) - dy * math.sin(ang)
        ry = c + dx * math.sin(ang) + dy * math.cos(ang)
        tw = 0.5 + 0.5 * math.sin((t + ph) * 2 * math.pi)
        ix, iy = int(rx), int(ry)
        if 0 <= ix < size and 0 <= iy < size:
            old = im.getpixel((ix, iy))
            im.putpixel((ix, iy), mix(old[:3], (255, 255, 255), 0.6 + 0.4 * tw) + (240,))
    return im


def write_vortex(prefix, n, space):
    frames = [swirl(n, f, space) for f in range(FRAMES)]
    for row in range(n):
        for col in range(n):
            strip = Image.new('RGBA', (S, S * FRAMES))
            for f, fr in enumerate(frames):
                strip.paste(fr.crop((col * S, row * S, col * S + S, row * S + S)), (0, f * S))
            name = '%s_%d' % (prefix, row * n + col)
            strip.save(os.path.join(OUT, name + '.png'))
            with open(os.path.join(OUT, name + '.png.mcmeta'), 'w') as fh:
                json.dump({'animation': {'frametime': 2}}, fh)
    return frames[0]


def main():
    tex = {
        'bridgeCasing': casing(1),
        'bridgeControllerFront': controller_front(),
        'bridgeControllerSide': controller_side(),
        'bridgeControllerTop': controller_top(),
        'bridgeCapacitorSide': capacitor_side(),
        'bridgeCapacitorTop': capacitor_top(),
        'bridgeEnergyPort': energy_port(),
        'bridgeGasPort': gas_port(),
        'bridgeFocuser': focuser(),
        'bridgeNav': nav(),
        'bridgeMass': mass(),
        'bridgeCooler': cooler(),
        'bridgeShield': shield(),
        'bridgeModuleTop': module_top(),
        'bridgeBeacon': beacon(),
        'bridgeBeaconTop': beacon(True),
        'bridgeAnchor': anchor(),
        'bridgeAnchorTop': anchor(True),
    }
    coil = Image.open(os.path.join(OUT, 'gravityCoil.png'))
    tex['gravityCoilOn'] = coil_on(coil)
    for name, im in tex.items():
        im.save(os.path.join(OUT, name + '.png'))
    g = write_vortex('bridgeVortexG', 3, False)
    sp = write_vortex('bridgeVortexS', 5, True)
    # preview: the blocks x4 in a row, the two swirls
    names = sorted(tex.keys())
    sheet = Image.new('RGBA', (len(names) * 132 + 8, 132 + 8 + 5 * S * 2 + 8), (30, 30, 34, 255))
    for i, n in enumerate(names):
        sheet.paste(tex[n].resize((128, 128), Image.NEAREST), (8 + i * 132, 8))
    sheet.paste(g.resize((3 * S * 2, 3 * S * 2), Image.NEAREST), (8, 148), g.resize((3 * S * 2, 3 * S * 2), Image.NEAREST))
    sheet.paste(sp.resize((5 * S * 2, 5 * S * 2), Image.NEAREST), (8 + 3 * S * 2 + 16, 148), sp.resize((5 * S * 2, 5 * S * 2), Image.NEAREST))
    sheet.save(PREVIEW)
    print('wrote %d block textures, %d vortex tiles, preview %s' % (len(tex), 9 + 25, PREVIEW))


# ------------------------------------------------------------------ the vortex as one disc (fx, 256 px)
# The portal is drawn as ONE disc by the centre cell's renderer (client/BridgeVortexRendererSC): these are greyscale
# (white + alpha) pictures it tints and turns, so the colour by kind / stability is the renderer's.

FX = os.path.join(PROJECT, 'src', 'main', 'resources', 'assets', 'siliconage', 'textures', 'fx')
FS = 256


def smooth(e0, e1, x):
    t = max(0.0, min(1.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def fx_spiral():
    """A seamless radial spiral: 3 logarithmic arms + 7 thin streaks, white, the alpha its brightness, fading to
    nothing at the rim (r = 1 is the texture's inscribed circle)."""
    im = Image.new('RGBA', (FS, FS), (255, 255, 255, 0))
    c = (FS - 1) / 2.0
    pix = im.load()
    for y in range(FS):
        for x in range(FS):
            dx, dy = (x - c) / c, (y - c) / c
            r = math.hypot(dx, dy)
            if r >= 1.0:
                continue
            th = math.atan2(dy, dx)
            lr = math.log(max(r, 0.02))
            arms = 0.5 + 0.5 * math.cos(3 * th + 4.2 * lr)
            streak = 0.5 + 0.5 * math.cos(7 * th + 7.5 * lr + 1.3)
            v = 0.62 * arms ** 1.6 + 0.38 * streak ** 3
            v *= 0.55 + 0.45 * smooth(0.0, 0.35, r)          # the very centre is a calm eye
            v *= 1 - smooth(0.78, 1.0, r)                     # fade out at the rim
            a = int(max(0, min(255, v * 255)))
            g = int(200 + 55 * v)
            pix[x, y] = (g, g, g, a)
    return im


def fx_stars():
    """The Space bridge's starfield: dots of different size and colour inside the inscribed circle, transparent."""
    im = Image.new('RGBA', (FS, FS), (255, 255, 255, 0))
    c = (FS - 1) / 2.0
    pix = im.load()
    rnd = random.Random(1609)
    for _ in range(170):
        r = math.sqrt(rnd.random()) * 0.92 * c
        th = rnd.random() * 2 * math.pi
        sx, sy = c + r * math.cos(th), c + r * math.sin(th)
        big = rnd.random()
        rad = 0.7 + (2.0 if big > 0.93 else 0.9 if big > 0.7 else 0.0)
        col = rnd.choice([(255, 255, 255), (200, 220, 255), (255, 235, 210), (220, 200, 255)])
        for y in range(int(sy - rad - 2), int(sy + rad + 3)):
            for x in range(int(sx - rad - 2), int(sx + rad + 3)):
                if not (0 <= x < FS and 0 <= y < FS):
                    continue
                d = math.hypot(x - sx, y - sy)
                a = max(0.0, 1 - d / (rad + 1.2)) ** 2
                if a <= 0:
                    continue
                old = pix[x, y]
                na = min(255, old[3] + int(a * 255))
                pix[x, y] = col + (na,)
    return im


def fx_glow(size=128):
    """A soft round glow (the halo, the dark core, the birth / collapse flash): white, gaussian alpha."""
    im = Image.new('RGBA', (size, size), (255, 255, 255, 0))
    c = (size - 1) / 2.0
    pix = im.load()
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / c
            a = math.exp(-r * r * 4.5) * (1 - smooth(0.85, 1.0, r))
            pix[x, y] = (255, 255, 255, int(255 * a))
    return im


def main_fx():
    if not os.path.isdir(FX):
        os.makedirs(FX)
    out = {'vortex_spiral': fx_spiral(), 'vortex_stars': fx_stars(), 'vortex_glow': fx_glow()}
    for name, im in out.items():
        im.save(os.path.join(FX, name + '.png'))
        with open(os.path.join(FX, name + '.png.mcmeta'), 'w') as fh:
            json.dump({'texture': {'blur': True, 'clamp': True}}, fh)
    sheet = Image.new('RGBA', (3 * (FS + 8) + 8, FS + 16), (20, 24, 30, 255))
    for i, name in enumerate(sorted(out)):
        im = out[name] if out[name].size[0] == FS else out[name].resize((FS, FS), Image.BILINEAR)
        sheet.paste(im, (8 + i * (FS + 8), 8), im)
    sheet.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'bridge_fx_preview.png'))
    print('wrote %d fx textures into %s' % (len(out), FX))


if __name__ == '__main__':
    if '--fx' in sys.argv:          # only the vortex disc's pictures (textures/fx), the block textures untouched
        main_fx()
    else:
        main()
