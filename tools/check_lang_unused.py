# -*- coding: utf-8 -*-
"""СТ-4 (docs/audit-2026-10-20-nedorabotki.md): the reverse of check_settle_lang.py - which lang keys the lang files
have and the code never asks for (old screens' lines and the like).

Reads every string literal of src/main/java (and, read-only, ../SiliconAgeVehicles/src - the add-on may ask this mod's
keys; comments skipped) and counts a key of en_US.lang / ru_RU.lang as used when
  - it is a literal itself;
  - a literal is a prefix the code completes at run time: one ending in "." ("sc.battery.mode." + mode), one followed
    by "+", one that is no key itself (a base kept in a variable: key = exo ? "sc.quarrygui.exo.tab" : "sc.quarrygui.tab",
    then key + "short." + name), or a %s / %d pattern (String.format);
  - it is a literal + ".N" - a block of prose key.1, key.2, ... (BookContent.paras, Lang.trOr in a loop);
  - it is such a used key + a suffix literal (".short", ".hint", ".title" ...): tabs[i] + ".short";
  - the game asks it by its own scheme (tile.*.name, item.*.name, entity.*.name, fluid.*, key.*, itemGroup.*, death.*,
    container.*);
  - it is in EXCEPTIONS below (a key the code builds in a way the scan can't see - with the reason).
Prints the unused keys by group (the key up to its second dot) and their number. Read-only unless --apply:

    python -I tools/check_lang_unused.py              the report
    python -I tools/check_lang_unused.py --list       the unused keys only, one per line
    python -I tools/check_lang_unused.py --why KEY... why a key counts as used (or UNUSED)
    python -I tools/check_lang_unused.py --apply FILE removes the keys listed in FILE (one per line, # comments) from
                                                      both lang files, after a backup to
                                                      ../backups/<YYYY-MM-DD_HHMM>_before-lang-unused/

--apply removes only the keys the scan still finds unused (a listed key the code asks again is kept and named), keeps
CRLF, UTF-8 without BOM and the order of every other line (comments included). Never deletes a file.
"""
import io
import os
import re
import shutil
import sys
import time

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = [os.path.join(ROOT, "src", "main", "java"),
        os.path.join(os.path.dirname(ROOT), "SiliconAgeVehicles", "src")]       # the add-on: read only
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "siliconage", "lang")
LANGS = ("en_US", "ru_RU")
BACKUPS = os.path.join(os.path.dirname(ROOT), "backups")

# Asked by the game itself, by its own scheme (a block's / an item's / a mob's name, a fluid, a key binding ...).
GAME = [re.compile(p) for p in (r"^tile\..+\.name$", r"^item\..+\.name$", r"^entity\..+\.name$", r"^fluid\.",
                                r"^key\.", r"^itemGroup\.", r"^death\.", r"^container\.")]

# Keys the code builds so that no literal shows them (key, or a prefix ending in "." -> why). Add with care: each entry
# hides a key from this check for good.
EXCEPTIONS = {
}

KEY = re.compile(r"^[A-Za-z][A-Za-z0-9_\-]*(\.[A-Za-z0-9_\-%]+)+\.?$")
SUFFIX = re.compile(r"^\.[A-Za-z][A-Za-z0-9_\-]*(\.[A-Za-z0-9_\-]+)*$")
PARA = re.compile(r"^(.+)\.\d+$")


def load_keys(name):
    """The keys of a lang file, in order (a key twice: once)."""
    keys = []
    seen = set()
    with io.open(os.path.join(LANG, name + ".lang"), encoding="utf-8", newline="") as f:
        for line in f.read().split("\r\n"):
            if "=" in line and not line.lstrip().startswith("#"):
                k = line.split("=", 1)[0].strip()
                if k and k not in seen:
                    seen.add(k)
                    keys.append(k)
    return keys


def all_keys():
    keys = []
    seen = set()
    for name in LANGS:
        for k in load_keys(name):
            if k not in seen:
                seen.add(k)
                keys.append(k)
    return keys


def literals(text):
    """(literal, the next code character after it) for every string literal of a Java source; comments skipped."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
        elif c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
        elif c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            i = j + 1
        elif c == '"':
            j = i + 1
            buf = []
            while j < n and text[j] != '"':
                if text[j] == "\\" and j + 1 < n:
                    buf.append(text[j:j + 2])
                    j += 2
                else:
                    buf.append(text[j])
                    j += 1
            k = j + 1
            while k < n and text[k] in " \t\r\n":
                k += 1
            out.append(("".join(buf), text[k] if k < n else ""))
            i = j + 1
        else:
            i += 1
    return out


class Code(object):
    """What the sources ask: full literals, prefixes the code completes, String.format patterns, suffix literals."""

    def __init__(self, keys):
        self.full, self.prefixes, self.patterns, self.suffixes = set(), set(), [], set()
        for base in JAVA:
            if not os.path.isdir(base):
                continue
            for top, _, files in os.walk(base):
                for fn in files:
                    if fn.endswith(".java"):
                        with io.open(os.path.join(top, fn), encoding="utf-8", errors="replace") as f:
                            for lit, nxt in literals(f.read()):
                                self.add(lit, nxt)
        known = set(keys)
        self.prefixes |= set(l for l in self.full if l not in known)    # no key itself: a base the code completes

    def add(self, lit, nxt):
        if SUFFIX.match(lit):
            self.suffixes.add(lit)
            return
        if not KEY.match(lit.replace("%s", "x").replace("%d", "0")):
            return
        self.full.add(lit)
        if lit.endswith(".") or nxt == "+":
            self.prefixes.add(lit)
        if "%s" in lit or "%d" in lit:
            parts = re.split(r"(%s|%d)", lit)
            rx = "".join("[^=]+" if p in ("%s", "%d") else re.escape(p) for p in parts)
            self.patterns.append(re.compile("^" + rx + "$"))

    def why(self, key, suffix=True):
        """Why the key counts as used, or None."""
        if key in self.full:
            return "a literal"
        for g in GAME:
            if g.search(key):
                return "asked by the game (%s)" % g.pattern
        for e in EXCEPTIONS:
            if key == e or (e.endswith(".") and key.startswith(e)):
                return "EXCEPTIONS: " + EXCEPTIONS[e]
        m = PARA.match(key)
        if m and m.group(1) in self.full:
            return "a paragraph of the literal base \"%s\"" % m.group(1)      # key.1, key.2 ... (paras)
        for i in range(len(key) - 1, 0, -1):
            if key[:i] in self.prefixes:
                return "completes the literal prefix \"%s\"" % key[:i]
        for p in self.patterns:
            if p.match(key):
                return "matches the format literal " + p.pattern
        if suffix:
            for s in self.suffixes:
                if key.endswith(s) and len(key) > len(s):
                    w = self.why(key[:-len(s)], False)
                    if w:
                        return "\"%s\" + the suffix literal \"%s\" (%s)" % (key[:-len(s)], s, w)
        return None


def unused_keys():
    keys = all_keys()
    code = Code(keys)
    return [k for k in keys if code.why(k) is None]


def group(key):
    return ".".join(key.split(".")[:2])


def report():
    unused = unused_keys()
    groups = {}
    for k in unused:
        groups.setdefault(group(k), []).append(k)
    for g in sorted(groups):
        print("%s (%d)" % (g, len(groups[g])))
        for k in groups[g]:
            print("    " + k)
    print("unused: %d keys in %d groups%s" % (len(unused), len(groups),
                                            " (exceptions: %d)" % len(EXCEPTIONS) if EXCEPTIONS else ""))
    return 0


def apply(listfile):
    wanted = []
    with io.open(listfile, encoding="utf-8") as f:
        for line in f:
            line = line.split("#", 1)[0].strip()
            if line:
                wanted.append(line)
    keys = set(all_keys())
    unused = set(unused_keys())
    drop = set(k for k in wanted if k in unused)
    gone = [k for k in wanted if k not in keys]
    kept = [k for k in wanted if k in keys and k not in unused]
    if gone:
        print("not in the lang files (removed already?): %d" % len(gone))
    if kept:
        print("kept - the code asks them: " + ", ".join(kept))
    if not drop:
        print("nothing to remove")
        return 1 if kept else 0
    texts = {}
    for name in LANGS:
        with io.open(os.path.join(LANG, name + ".lang"), "rb") as f:
            raw = f.read()
        if raw.startswith(b"\xef\xbb\xbf") or b"\r\n" not in raw:
            print("%s: not CRLF / UTF-8 without BOM - nothing changed" % name)
            return 1
        texts[name] = raw.decode("utf-8")
    backup = os.path.join(BACKUPS, time.strftime("%Y-%m-%d_%H%M") + "_before-lang-unused")
    if not os.path.isdir(backup):
        os.makedirs(backup)
    for name in LANGS:
        shutil.copy2(os.path.join(LANG, name + ".lang"), os.path.join(backup, name + ".lang"))
    print("backup: " + backup)
    for name in LANGS:
        lines = texts[name].split("\r\n")
        out = [l for l in lines
               if not ("=" in l and not l.lstrip().startswith("#") and l.split("=", 1)[0].strip() in drop)]
        with io.open(os.path.join(LANG, name + ".lang"), "wb") as f:
            f.write("\r\n".join(out).encode("utf-8"))
        print("%s: %d lines removed" % (name, len(lines) - len(out)))
    return 0


def main(argv):
    if "--apply" in argv:
        i = argv.index("--apply")
        if i + 1 >= len(argv):
            print("--apply needs the file with the keys")
            return 2
        return apply(argv[i + 1])
    if "--why" in argv:
        keys = all_keys()
        code = Code(keys)
        for key in argv[argv.index("--why") + 1:]:
            print("%s: %s" % (key, (code.why(key) or "UNUSED") if key in keys else "not in the lang files"))
        return 0
    if "--list" in argv:
        for k in unused_keys():
            print(k)
        return 0
    return report()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
