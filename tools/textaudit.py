# -*- coding: utf-8 -*-
"""Silicon Age text audit (python tools/textaudit.py, needs Pillow): measures the screens' captions (both languages) with Minecraft 1.7.10's
own font widths (ascii.png + glyph_sizes.bin, as FontRenderer does) against the room each one has.
Reports what TextFitSC will draw smaller (<100%) and what it has to cut ("CUT", below 75%)."""
import io
import os
import re
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
JAR = os.path.join(os.path.expanduser("~"), ".gradle", "caches", "minecraft", "net", "minecraft", "minecraft",
                   "1.7.10", "minecraft-1.7.10.jar")
FONT = os.path.join(HERE, "build-font", "assets", "minecraft")
if not os.path.exists(FONT):          # the vanilla font, out of the Minecraft jar in the Gradle cache
    import zipfile
    with zipfile.ZipFile(JAR) as z:
        for n in ("assets/minecraft/textures/font/ascii.png", "assets/minecraft/font/glyph_sizes.bin"):
            z.extract(n, os.path.join(HERE, "build-font"))
LANG = os.path.join(HERE, "..", "src", "main", "resources", "assets", "siliconage", "lang") + os.sep

ASCII = (u"\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207"
         u"\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`"
         u"abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5"
         u"\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1"
         u"\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557"
         u"\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564"
         u"\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4"
         u"\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2"
         u"\u25a0\u0000")

img = Image.open(os.path.join(FONT, "textures", "font", "ascii.png")).convert("RGBA")
cell = img.size[0] // 16
CHAR_W = []
for k in range(256):
    col, row = k % 16, k // 16
    w = 0
    for x in range(cell - 1, -1, -1):
        if any(img.getpixel((col * cell + x, row * cell + y))[3] > 0 for y in range(cell)):
            w = x + 1
            break
    CHAR_W.append(w * 8 // cell + 1)
CHAR_W[32] = 4
GLYPH = open(os.path.join(FONT, "font", "glyph_sizes.bin"), "rb").read()


def char_w(c):
    if c == u" ":
        return 4
    i = ASCII.find(c)
    if ord(c) > 0 and i >= 0:
        return CHAR_W[i]
    g = GLYPH[ord(c)] if ord(c) < len(GLYPH) else 0
    if g:
        j, k = g >> 4, g & 15
        if k > 7:
            k, j = 15, 0
        return (k + 1 - j) // 2 + 1
    return 0


def width(s):
    s = re.sub(u"\u00a7.", "", s)
    return sum(char_w(c) for c in s)


def load(f):
    d = {}
    for line in io.open(LANG + f, encoding="utf-8"):
        line = line.rstrip("\n")
        if "=" in line and not line.startswith("#"):
            k, v = line.split("=", 1)
            d[k] = v
    return d


def fmt(v, args):
    v = re.sub(r"%(\d+\$)?[\d.]*[dfs]", "%s", v)
    try:
        return v % tuple(args) if args else v.replace("%%", "%")
    except TypeError:
        return v


RESULTS = []


def check(lang, where, text, room):
    w = width(text)
    if w > room:
        ratio = room / float(w)
        RESULTS.append((lang, where, text, w, room, "CUT" if ratio < 0.75 else "%d%%" % int(ratio * 100)))


def audit(lang):
    L = load(lang)
    t = lambda k, *a: fmt(L.get(k, k), a)
    on, off = t("sc.fieldgui.on"), t("sc.fieldgui.off")
    # ---- quarry: tabs (icon + short name in 55 px), buttons (caption room = width - 6), labels
    for k in ("quarry", "area", "map", "output", "functions", "upgrades"):
        check(lang, "quarry tab", t("sc.quarrygui.tabshort." + k), (248 - 16 - 6) // 4 - 4 - 17)
    for i in range(28):
        room = (80 if i == 1 else 114) - 6
        name = t("sc.quarrygui.flag.%d" % i) if i != 1 else t("sc.quarrygui.flag.fortune", "III")
        check(lang, "quarry function", name + ": " + off, room)
        if i < 12 or i >= 18:
            check(lang, "quarry function (no module)", t("sc.quarrygui.flag.%d" % i), room)
    check(lang, "quarry function", t("sc.quarrygui.flag.24.active"), 108)
    check(lang, "quarry page", t("sc.quarrygui.page", 1, 2), 48)
    for k, n in (("sc.quarrygui.power", 3),):
        for i in range(n):
            check(lang, "quarry power (Quarry tab)", t(k, t(k + ".%d" % i)), 114 - 6)
            check(lang, "quarry power (Functions tab)", t(k, t(k + ".%d" % i)), 114 - 6)
    for i in range(3):
        check(lang, "quarry redstone", t("sc.fieldgui.redstone", t("sc.fieldgui.redstone.%d" % i)), 114 - 6)
        check(lang, "quarry shape", t("sc.quarrygui.shape", t("sc.quarrygui.shape.%d" % i)), 112 - 6)
    for i in range(4):
        check(lang, "quarry replace", t("sc.quarrygui.replace", t("sc.quarrygui.replace.%d" % i)), 112 - 6)
        check(lang, "quarry show", t("sc.quarrygui.show", t("sc.quarrygui.show.%d" % i)), 112 - 6)
        check(lang, "quarry filter", t("sc.quarrygui.filter", t("sc.quarrygui.filter.%d" % i)), 114 - 6)
    for i in range(7):
        check(lang, "quarry output side", t("sc.quarrygui.out", t("sc.quarrygui.side.%d" % i)), 72 - 6)
        check(lang, "quarry tank side", t("sc.quarrygui.side.%d" % i), 56 - 6)
    for k in ("dash", "plane", "ores"):
        check(lang, "quarry area look", t("sc.quarrygui." + k, off), 112 - 6)
    check(lang, "quarry brightness", t("sc.quarrygui.bright", 100), 112 - 6)
    check(lang, "quarry colour target", t("sc.quarrygui.target.plane"), 92 - 6)
    check(lang, "quarry start", t("sc.quarrygui.start"), 72 - 6)
    check(lang, "quarry pause", t("sc.quarrygui.pause"), 72 - 6)
    check(lang, "quarry reset", t("sc.quarrygui.reset"), 72 - 6)
    check(lang, "quarry xp", t("sc.quarrygui.xp", 99999), 80 - 6)
    check(lang, "quarry scan", t("sc.quarrygui.scan"), 74 - 6)
    for k, a in (("sizex", (128, 128)), ("sizez", (128, 128)), ("offx", (-64,)), ("offz", (-64,)), ("bottom", (255,))):
        check(lang, "quarry area label", t("sc.quarrygui." + k, *a), 114)
    room = 222 - 12
    for s in ("paused", "running", "no_power", "no_head", "buffer_full", "done", "no_area", "redstone", "blocked_by_field"):
        check(lang, "quarry status", t("sc.quarry.status." + s), room)
    check(lang, "quarry layer", t("sc.quarrygui.layer", 255, 1, 100), room)
    check(lang, "quarry cost", t("sc.quarrygui.cost", 99999, "86.4"), room)
    check(lang, "quarry left", t("sc.quarrygui.left", "1048576", 99, 59), room)
    check(lang, "quarry pump", t("sc.quarrygui.pump", 16000, 16000, "Lava"), 232)
    check(lang, "quarry slot labels", t("sc.quarrygui.slot.scanner"), 60)
    check(lang, "quarry modules title", t("sc.quarrygui.modules"), 62)
    check(lang, "quarry filter label", t("sc.quarrygui.filterlabel"), 150)
    check(lang, "quarry functions count", t("sc.quarrygui.fncount", 20, 20), 248 - 190)
    # ---- field generator
    for k in ("field", "functions", "access", "map", "upgrades", "zone"):
        check(lang, "field tab", t("sc.fieldgui.tab.%s.short" % k), (248 - 16 - 6) // 4 - 4 - 17)
    for i in range(3):
        check(lang, "quarry fluid filter", t("sc.quarrygui.ff", t("sc.quarrygui.ff.%d" % i)), 114 - 6)
    for i in range(2):
        check(lang, "quarry fluid filter rest", t("sc.quarrygui.ffact.%d" % i), 72 - 6)
    for i in range(4):
        check(lang, "quarry tank full", t("sc.quarrygui.tankfull", t("sc.quarrygui.tankfull.%d" % i)), 158 - 6)
    for i in range(8):
        check(lang, "quarry tank side", t("sc.quarrygui.tank.sidebtn", t("sc.quarrygui.sideshort.%d" % i)), 44 - 6)
    for k in ("clear", "tofilter", "pin", "unpin"):
        check(lang, "quarry tank button", t("sc.quarrygui.tank." + k), 44 - 6)
    check(lang, "quarry tank button", t("sc.quarrygui.tank.auto", off), 44 - 6)
    check(lang, "quarry tank button", t("sc.quarrygui.ff.hand"), 38 - 6)
    check(lang, "quarry tank button", t("sc.quarrygui.ff.clear"), 70 - 6)
    check(lang, "quarry wash feed", t("sc.quarrygui.wash.feed"), 40 - 6)
    check(lang, "quarry fluid vein", t("sc.quarrygui.fvein", off), 62 - 6)
    check(lang, "quarry fluid vein", t("sc.quarrygui.fvein.range", 64), 62 - 6)
    for k in ("take", "keep"):
        check(lang, "quarry fluid vein", t("sc.quarrygui.fvein." + k), 62 - 6)
    check(lang, "quarry fluid vein", t("sc.quarrygui.fvein.count", 256), 42)
    check(lang, "quarry tank label", t("sc.quarrygui.tank.label.locked", 4, "EV"), 45)
    check(lang, "quarry tank label", t("sc.quarrygui.tank.label.empty", 4), 45)
    check(lang, "quarry tank title", t("sc.quarrygui.tanks.title", 4, 4, 144000), 248 - 30 - 8)
    check(lang, "quarry tab", t("sc.quarrygui.tabshort.tanks"), (248 - 16 - 6) // 4 - 4 - 17)
    check(lang, "quarry pump line", t("sc.quarrygui.pump2", 576000, 4, 4), 232)
    for flag in (64, 512):
        check(lang, "field charge switch", t("sc.fieldgui.flag.%d" % flag) + ": " + off, 114 - 6)
    for i in range(4):
        check(lang, "field charge mode", t("sc.fieldgui.charge.mode", t("sc.fieldgui.charge.mode.%d" % i)), 232 - 6)
    check(lang, "field charge reserve", t("sc.fieldgui.charge.reserve", 90, 999999), 248 - 90)
    for k, args in (("rate", (163840,)), ("boosters", (4, 4)), ("now", (655360, 16)), ("off", ()), ("title", ())):
        check(lang, "field charge text", t("sc.fieldgui.charge." + k, *args), 232)
    for k in ("charge", "functions"):
        check(lang, "field page button", t("sc.fieldgui.page." + k), 110 - 6)
    for i in range(3):
        check(lang, "field redstone", t("sc.fieldgui.redstone", t("sc.fieldgui.redstone.%d" % i)), 114 - 6)
        check(lang, "field filter", t("sc.fieldgui.filter", t("sc.fieldgui.filter.%d" % i)), 232 - 6)
    for i in range(5):
        check(lang, "field colour", t("sc.fieldgui.color", t("sc.fieldgui.color.%d" % i)), 114 - 6)
    for flag in (1, 2, 4, 8, 16, 32, 64, 128, 256):
        check(lang, "field switch", t("sc.fieldgui.flag.%d" % flag) + ": " + off, (114 if flag in (4, 8, 256) else 232) - 6)
    check(lang, "field upkeep", t("sc.gui.field.upkeep", 99999), 222 - 12)
    # ---- field generator: the Zone tab
    check(lang, "zone radius", t("sc.fieldzone.radius", 256, 256), 114)
    for k, args in (("height", (256,)), ("height.auto", ()), ("height.world", ()), ("offx", ("-32",)), ("offy", ("-32",)), ("offz", ("-32",))):
        check(lang, "zone label", t("sc.fieldzone." + k, *args), 114)
    for m in ("union", "box", "prism", "dome", "cylinder"):
        check(lang, "zone shape", t("sc.fieldgui.shape", t("sc.field.mode." + m)), 114 - 6)
    for i in range(3):
        check(lang, "zone anchor", t("sc.fieldzone.anchor", t("sc.fieldzone.anchor.%d" % i)), 114 - 6)
        check(lang, "zone colour target", t("sc.fieldzone.target", t("sc.fieldzone.target.%d" % i)), 96 - 6)
        check(lang, "zone animation", t("sc.fieldzone.anim", t("sc.fieldzone.anim.%d" % i)), 114 - 6)
    check(lang, "zone preview", t("sc.fieldzone.preview", off), 114 - 6)
    check(lang, "zone apply", t("sc.fieldzone.apply"), 56 - 6)
    check(lang, "zone cancel", t("sc.fieldzone.cancel"), 56 - 6)
    for i in range(4):
        check(lang, "zone outline", t("sc.fieldzone.outline", t("sc.fieldzone.outline.%d" % i)), 114 - 6)
    check(lang, "zone brightness", t("sc.fieldzone.bright", 100), 114 - 6)
    for flag in (256, 1024, 2048, 4096):
        check(lang, "zone switch", t("sc.fieldgui.flag.%d" % flag) + ": " + off, 114 - 6)
    check(lang, "zone readout", t("sc.fieldzone.info", "16 777 216", 99999) + " " + t("sc.fieldzone.pending"), 232)
    check(lang, "field upgrades count", t("sc.fieldgui.upgrades.count", 16, 16, 160000), 232)
    check(lang, "field input", t("sc.fieldgui.upgrades.input", "XV", 32768), 232)
    # ---- the large screens (GuiBigSC): upgrade row, tank labels, generator and storage readings
    check(lang, "big upgrades effect", t("sc.gui.big.upgrades.effect", "10.54", "12.34"), 248 - 8 - 124)
    check(lang, "big upgrades count", t("sc.gui.big.upgrades.count", 4, 4), 248 - 8 - 124)
    check(lang, "big upgrades label", t("sc.gui.big.upgrades"), 49 - 1 - 8 - 2)
    for k in ("in", "out"):
        check(lang, "big tank label", t("sc.gui.holo.tank." + k, 2), 31 - 7)
    for k in ("water", "coolant", "fuel"):
        check(lang, "big gen tank label", t("sc.gui.holo.gen.tank." + k), 31 - 7)
    for k in ("passive", "fluid_fuel", "fusion", "solid", "wind", "water", "thermo", "dual_fluid", "rtg", "exo", "creative"):
        check(lang, "big gen caption", t("sc.gui.holo.gen." + k), 206 - 107)
    check(lang, "big gen row", t("sc.gui.holo.gen.rated", 32768), 206 - 107)
    check(lang, "big gen row", t("sc.gui.holo.gen.out", "XV", 32768), 206 - 107)
    check(lang, "big gen row", t("sc.gui.holo.gen.buffer", 100), 206 - 107)
    check(lang, "big gen now", t("sc.gui.gen.now", 32768), 88)
    check(lang, "big storage", t("sc.storage.gui.stored", "2000000000"), 206 - 42)
    check(lang, "big storage", t("sc.storage.gui.capacity", "2000000000", 100), 206 - 42)
    # ---- the machines' holo screen: its number rows (no tanks: 71 px)
    check(lang, "machine holo row", t("sc.gui.holo.progress", "100%"), 71)
    check(lang, "machine holo row", t("sc.gui.holo.energy", 1900), 71)
    check(lang, "machine holo row", t("sc.gui.holo.input", "EV", 2048), 71)
    # ---- generators, machines, storages
    for k, v in L.items():
        if k.startswith("sc.generator.") and "." not in k[len("sc.generator."):]:
            check(lang, "generator title", v, 176 - 6 - 20 - 12)
        if k.startswith("sc.status.generator."):
            check(lang, "generator status", v, 152 - 4 - 8)
        if k.startswith("sc.machine.") and k.count(".") == 2:
            check(lang, "machine title", v, 176 - 6 - 20 - 12)
        if k.startswith("sc.status.machine."):
            check(lang, "machine status (holo screen)", v, 58)
        if k.startswith("sc.gui.holo.process."):
            check(lang, "machine process caption", v, 130 - 72 - 2)
    for k, a in (("sc.gui.gen.height", (192,)), ("sc.gui.gen.free", (100,)), ("sc.gui.gen.flow", (4,)),
                 ("sc.gui.gen.pairs", (3,)), ("sc.gui.gen.dt", (1050,)), ("sc.gui.gen.capsules", (2,))):
        check(lang, "generator info", t(k, *a), 152 - 4 - 68)
    for k in ("sc.gui.gen.day", "sc.gui.gen.night", "sc.gui.gen.rain", "sc.gui.gen.nosky"):
        check(lang, "solar info", t(k), 152 - 100)
    check(lang, "generator now", t("sc.gui.gen.now", 163840), 152 - 4 - 8)
    check(lang, "creative tier", t("sc.gui.gen.creativetier", "XV", 32768), 100 - 6)
    check(lang, "storage capacity", t("sc.storage.gui.capacity", "2000000000", 100), 152 - 12)
    check(lang, "storage flow", t("sc.storage.gui.flow", "+32768"), 130 - 4 - 8)
    for k, v in L.items():
        if k.startswith("tile.siliconage.energyStorage.") or k.startswith("tile.siliconage.chargePad."):
            check(lang, "storage title", v, 176 - 6 - 20 - 12)
    # ---- armour screen (K): function buttons 170 px, key buttons 90 px
    for k, v in L.items():
        if k.startswith("sc.armorgui.glow."):
            check(lang, "armour light colour", t("sc.armorgui.glow", v), 180 - 6)
        if re.match(r"sc\.(armorfn|bladefn|drillfn)\.[a-z_]+$", k):
            check(lang, "armour screen function", v + ": " + off, 170 - 6)
    # ---- tube filter buttons
    for k, v in L.items():
        if k.startswith("sc.filter.btn."):
            check(lang, "filter button", v, 66 - 6)


for lang in ("ru_RU.lang", "en_US.lang"):
    audit(lang)
cut = [r for r in RESULTS if r[5] == "CUT"]
print("%d too wide, %d of them cut" % (len(RESULTS), len(cut)))
for r in sorted(RESULTS, key=lambda r: (r[5] != "CUT", r[0], r[1])):
    print("%-11s %-4s %-32s %3d>%3d  %s" % (r[0][:5], r[5], r[1][:32], r[3], r[4], r[2]))
