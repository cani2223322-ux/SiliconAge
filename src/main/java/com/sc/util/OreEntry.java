package com.sc.util;

import net.minecraftforge.common.BiomeDictionary;

/**
 * The 16 ore types from design doc 01_recipes.md §10 (final resolved count - see the doc's
 * "4-я проверка" note: 15 confirmed + Spodumene candidate = 16, which is also the hard cap
 * for BlockOreSC's metadata, 0-15).
 *
 * Y-range/biome/vein-size/tool are all the doc's TODO-filled defaults ("по аналогии"), wired
 * through ConfigSC so they can be tuned without a rebuild, per the step 2 requirement.
 */
public enum OreEntry {

    QUARTZITE("quartzite", 10, 60, 6, HarvestTool.IRON, null),
    CHALCOPYRITE("chalcopyrite", 20, 70, 8, HarvestTool.STONE, null),
    CASSITERITE("cassiterite", 20, 70, 6, HarvestTool.STONE, null),
    GALENA("galena", 10, 50, 6, HarvestTool.IRON, null),
    SPHALERITE("sphalerite", 10, 50, 6, HarvestTool.IRON, null),
    BAUXITE("bauxite", 20, 60, 8, HarvestTool.STONE, new BiomeDictionary.Type[]{BiomeDictionary.Type.JUNGLE, BiomeDictionary.Type.SAVANNA, BiomeDictionary.Type.PLAINS}),
    ILMENITE("ilmenite", 5, 40, 4, HarvestTool.IRON, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN}),
    WOLFRAMITE("wolframite", 5, 30, 3, HarvestTool.IRON, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN}),
    ARGYRODITE("argyrodite", 5, 25, 3, HarvestTool.DIAMOND, new BiomeDictionary.Type[]{BiomeDictionary.Type.MESA, BiomeDictionary.Type.HILLS}),
    TANTALITE("tantalite", 5, 20, 2, HarvestTool.DIAMOND, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN}),
    BADDELEYITE("baddeleyite", 5, 20, 2, HarvestTool.DIAMOND, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN}),
    SPERRYLITE("sperrylite", 5, 16, 2, HarvestTool.DIAMOND, new BiomeDictionary.Type[]{BiomeDictionary.Type.MESA, BiomeDictionary.Type.HILLS}),
    MONAZITE("monazite", 5, 30, 4, HarvestTool.IRON, new BiomeDictionary.Type[]{BiomeDictionary.Type.BEACH, BiomeDictionary.Type.RIVER}),
    HALITE("halite", 10, 50, 6, HarvestTool.IRON, new BiomeDictionary.Type[]{BiomeDictionary.Type.DESERT, BiomeDictionary.Type.MESA}),
    MAGNESITE("magnesite", 20, 60, 6, HarvestTool.IRON, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN}),
    SPODUMENE("spodumene", 30, 70, 3, HarvestTool.DIAMOND, new BiomeDictionary.Type[]{BiomeDictionary.Type.HILLS, BiomeDictionary.Type.MOUNTAIN});

    /** Vanilla pickaxe harvest levels (Block.setHarvestLevel("pickaxe", level)). */
    public enum HarvestTool {
        STONE(1), IRON(2), DIAMOND(3);

        public final int level;

        HarvestTool(int level) {
            this.level = level;
        }
    }

    public final String oreName;
    public final int defaultMinY;
    public final int defaultMaxY;
    public final int defaultVeinSize;
    public final HarvestTool tool;
    /** null means "any biome" (§10: "Любой"). */
    public final BiomeDictionary.Type[] biomes;

    OreEntry(String oreName, int defaultMinY, int defaultMaxY, int defaultVeinSize, HarvestTool tool, BiomeDictionary.Type[] biomes) {
        this.oreName = oreName;
        this.defaultMinY = defaultMinY;
        this.defaultMaxY = defaultMaxY;
        this.defaultVeinSize = defaultVeinSize;
        this.tool = tool;
        this.biomes = biomes;
    }

    /** Metadata slot on BlockOreSC == ordinal(); kept as an explicit accessor for readability at call sites. */
    public int meta() {
        return ordinal();
    }

    public static OreEntry byMeta(int meta) {
        OreEntry[] values = values();
        return values[meta >= 0 && meta < values.length ? meta : 0];
    }
}
