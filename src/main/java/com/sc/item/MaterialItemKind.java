package com.sc.item;

/**
 * The 5-stage item kinds from 01_recipes.md §11.1 (IC2-style naming convention).
 * `prefix` is used both as the OreDict name prefix (e.g. "dustCopper") and as the
 * unlocalized-name suffix segment and texture name prefix, so all three stay in sync.
 */
public enum MaterialItemKind {

    CRUSHED_ORE("crushedOre"),
    PURIFIED_CRUSHED_ORE("purifiedCrushedOre"),
    DUST("dust"),
    DUST_TINY("dustTiny"),
    INGOT("ingot");

    public final String prefix;

    MaterialItemKind(String prefix) {
        this.prefix = prefix;
    }
}
