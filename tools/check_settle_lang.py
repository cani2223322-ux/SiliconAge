# -*- coding: utf-8 -*-
"""Package D, Подложка Ф2 (docs/plan-substrate-tech-f2.md §10.1): which lang keys the code asks for and the lang files
lack - the integrator's check after merging the packages' reports.

Scans src/main/java for string literals that look like the mod's lang keys (sc.*, tile.siliconage.*,
item.siliconage.*, entity.SiliconAge.*), keeps those under the given prefixes (default: Ф2's namespaces) and prints
every one missing from en_US.lang or ru_RU.lang, with the file that uses it. A literal ending in "." is a prefix the
code completes at run time (sc.settle.kind. + key) - listed apart, not as missing. Read-only; run with python -I:

    python -I tools/check_settle_lang.py [prefix ...]
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src", "main", "java")
LANG = os.path.join(ROOT, "src", "main", "resources", "assets", "siliconage", "lang")
PREFIXES = ["sc.settle.", "sc.trade.", "sc.contract.", "sc.capsule.", "sc.map.", "sc.death.", "sc.bridge.target.",
            "sc.bridge.place.settlement", "sc.tooltip.settlementCore", "sc.tooltip.showcase", "sc.tooltip.dock.",
            "sc.tooltip.creditChip", "sc.tooltip.returnCapsule", "sc.tooltip.settlementMap", "sc.tooltip.loot.stolenCargo",
            "sc.manual.world.", "sc.nei.obtain."]
LITERAL = re.compile(r'"((?:sc|tile\.siliconage|item\.siliconage|entity\.SiliconAge)\.[A-Za-z0-9_.]+)"')


def load(name):
    keys = {}
    with io.open(os.path.join(LANG, name + ".lang"), encoding="utf-8", newline="") as f:
        for line in f.read().split("\r\n"):
            if "=" in line and not line.startswith("#"):
                keys.setdefault(line.split("=", 1)[0], line.split("=", 1)[1])
    return keys


def main(prefixes):
    en, ru = load("en_US"), load("ru_RU")
    used = {}
    for top, _, files in os.walk(JAVA):
        for fn in files:
            if fn.endswith(".java"):
                path = os.path.join(top, fn)
                with io.open(path, encoding="utf-8") as f:
                    for key in LITERAL.findall(f.read()):
                        if any(key.startswith(p) for p in prefixes):
                            used.setdefault(key, set()).add(os.path.relpath(path, JAVA).replace(os.sep, "/"))
    missing, partial = [], []
    for key in sorted(used):
        if key.endswith("."):
            if not any(k.startswith(key) for k in en):
                partial.append(key)
            continue
        # a paragraph block's base (key.1, key.2 ...: BookContent.paras) or a prefix the code completes / a test's prefix list
        if any(k.startswith(key + ".") for k in en) and any(k.startswith(key + ".") for k in ru):
            continue
        if all(u.startswith("com/sc/debug/") for u in used[key]) and any(k.startswith(key) for k in en):
            continue                                    # a self-test's prefix list ("sc.nei.obtain.settle" for settlement, settlerules)
        lack = [n for n, m in (("en_US", en), ("ru_RU", ru)) if key not in m]
        if lack:
            missing.append((key, lack))
    print("%d keys under %d prefixes used in the code, %d missing, %d prefixes with no key at all" % (len(used), len(prefixes), len(missing), len(partial)))
    for key, lack in missing:
        print("MISSING %s (%s) - %s" % (key, ", ".join(lack), ", ".join(sorted(used[key]))))
    for key in partial:
        print("PREFIX %s* - no key starts so (%s)" % (key, ", ".join(sorted(used[key]))))
    only = sorted(set(en) ^ set(ru))
    for key in only:
        print("ONE FILE ONLY %s" % key)
    return 1 if missing or only else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or PREFIXES))
