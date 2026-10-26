# -*- coding: utf-8 -*-
"""ОМ-3 «Гафний из циркония» (Silicon Age, docs/plan-ideas-2026-10-26.md): blocks/fluids/zrcl4_still / zrcl4_flow (16 x 256, the
mcmeta's frametime 2) - the TiCl4's animated frames recoloured to ZrCl4's pale yellow-green (ModFluids.COLORS 0xD8E0B0): each pixel's
brightness kept, the hue the new one.

Usage: python tools/gen_zrcl4_tex.py [project dir]   (the preview: SC_PREVIEW_DIR or tools/)
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ideas_texlib import TEX, preview, clamp

SRC, DST = 'ticl4', 'zrcl4'
FROM, TO = (0xE8, 0xE4, 0xC8), (0xD8, 0xE0, 0xB0)


def recolour(im):
    out = Image.new('RGBA', im.size)
    src = im.convert('RGBA')
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = src.getpixel((x, y))
            k = (r + g + b) / float(sum(FROM))                  # brightness against the old colour
            out.putpixel((x, y), (clamp(TO[0] * k), clamp(TO[1] * k), clamp(TO[2] * k), a))
    return out


def main():
    d = os.path.join(TEX, 'blocks', 'fluids')
    shown = []
    for kind in ('still', 'flow'):
        src = Image.open(os.path.join(d, '%s_%s.png' % (SRC, kind)))
        out = recolour(src)
        out.save(os.path.join(d, '%s_%s.png' % (DST, kind)))
        with open(os.path.join(d, '%s_%s.png.mcmeta' % (DST, kind)), 'w') as f:
            f.write('{"animation": {"frametime": 2}}')
        shown.append(out.crop((0, 0, 16, 16)))
    preview('zrcl4_tex', shown, 6)


if __name__ == '__main__':
    main()
