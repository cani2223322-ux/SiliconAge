package com.sc.util;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import net.minecraftforge.common.config.Configuration;

/**
 * Forge Configuration wrapper. Step 2 requirement: "Вынеси диапазоны в Configuration (.cfg),
 * чтобы их можно было тюнить без пересборки" - every ore's Y-range and vein size from
 * OreEntry's built-in defaults is re-read from the .cfg here, so a server owner can retune
 * generation without touching code.
 */
public final class ConfigSC {

    private static final Map<OreEntry, OreGenSettings> ORE_SETTINGS = new EnumMap<OreEntry, OreGenSettings>(OreEntry.class);

    /** Sounds: working machines / generators / quarries play their loops; the volume of every mod sound (0..1). */
    public static boolean machineSounds = true;
    public static float soundVolume = 1F;

    private ConfigSC() {
    }

    public static void load(File configFile) {
        Configuration config = new Configuration(configFile);
        try {
            config.load();
            for (OreEntry ore : OreEntry.values()) {
                String category = "worldgen.ore." + ore.oreName;
                int minY = config.getInt("minY", category, ore.defaultMinY, 0, 255,
                        "Minimum generation height for " + ore.oreName);
                int maxY = config.getInt("maxY", category, ore.defaultMaxY, 0, 255,
                        "Maximum generation height for " + ore.oreName);
                // Capped at 32: WorldGenMinable's reach grows with vein size, and past ~40 it
                // writes into not-yet-generated neighbour chunks (cascading worldgen).
                int veinSize = config.getInt("veinSize", category, ore.defaultVeinSize, 1, 32,
                        "Blocks per vein for " + ore.oreName);
                int veinsPerChunk = config.getInt("veinsPerChunk", category, 4, 0, 64,
                        "Attempted veins per chunk for " + ore.oreName + " (TODO: not specified in design doc, defaulted)");
                ORE_SETTINGS.put(ore, new OreGenSettings(minY, maxY, veinSize, veinsPerChunk));
            }
            machineSounds = config.getBoolean("machines", "sounds", true,
                    "Working machines, generators and quarries make their sound");
            soundVolume = config.getFloat("volume", "sounds", 1F, 0F, 1F,
                    "Volume of every Silicon Age sound (0 = silent)");
        } finally {
            if (config.hasChanged()) {
                config.save();
            }
        }
    }

    public static OreGenSettings settingsFor(OreEntry ore) {
        OreGenSettings settings = ORE_SETTINGS.get(ore);
        if (settings == null) {
            // load() wasn't called (e.g. in a unit test) - fall back to the doc's defaults.
            settings = new OreGenSettings(ore.defaultMinY, ore.defaultMaxY, ore.defaultVeinSize, 4);
        }
        return settings;
    }

    public static final class OreGenSettings {
        public final int minY;
        public final int maxY;
        public final int veinSize;
        public final int veinsPerChunk;

        public OreGenSettings(int minY, int maxY, int veinSize, int veinsPerChunk) {
            this.minY = minY;
            this.maxY = maxY;
            this.veinSize = veinSize;
            this.veinsPerChunk = veinsPerChunk;
        }
    }
}
