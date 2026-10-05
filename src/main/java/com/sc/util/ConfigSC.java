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
    /** Radiation: reactors and RTGs irradiate players nearby (off: no dose, no effects); every level multiplied by this. */
    public static boolean radiation = true;
    public static float radiationMultiplier = 1F;

    /** Balance (section "balance"), all 1 = as designed. Machines: work speed, EU a tick. */
    public static float machineSpeed = 1F, machineEnergy = 1F;
    /** Buffers: energy storages, portable batteries, suit pieces. */
    public static float storageCapacity = 1F, batteryCapacity = 1F, armorCapacity = 1F;
    /** EU a suit spends on each point of damage it absorbs. */
    public static float armorDamageCost = 1F;
    /** The lead suit: % of the radiation each piece stops (Защита свинцового костюма за одну часть, %). */
    public static int leadSuitPartProtection = 22;
    /** The field generator's upkeep; quarries' and the Exo rig's speed. */
    public static float fieldUpkeep = 1F, quarrySpeed = 1F;
    /** Wireless: transmitter range (XV stays unlimited), the loss over distance, the quantum pair's upkeep; translators load chunks. */
    public static float wirelessRange = 1F, wirelessLoss = 1F, quantumUpkeep = 1F;
    public static boolean quantumChunkLoading = true;
    /** The Exo blade's execute finishes off players too (off: a player takes the plain blow). */
    public static boolean bladeExecutePlayers = false;
    /** A drill stops drawing on the chestplate once its charge is below this %; an overheated chestplate never feeds it. */
    public static int drillArmorReserve = 15;
    /**
     * The Energy Converter: units of the other energies one EU is worth (RF, Mekanism J, Galacticraft gJ;
     * gJ 0 = Galacticraft's own rate when it is installed, else 16 / 2.44) and the conversion loss, %.
     */
    public static float converterRfPerEu = 4F, converterJPerEu = 10F, converterGjPerEu = 0F;
    public static int converterLoss = 5;

    /** A whole number scaled by a multiplier, at least `min`, capped to an int. */
    public static int scale(int base, float mul, int min) {
        return (int) Math.max(min, Math.min(Integer.MAX_VALUE, Math.round((double) base * mul)));
    }

    public static long scale(long base, float mul) {
        return Math.max(1L, Math.round(base * (double) mul));
    }

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
            radiation = config.getBoolean("enabled", "radiation", true,
                    "RTGs and reactors irradiate players nearby: a dose builds up and makes them ill (lead, suits and fields protect)");
            radiationMultiplier = config.getFloat("multiplier", "radiation", 1F, 0F, 10F,
                    "Every radiation level is multiplied by this (0.5 = half as strong)");
            String b = "balance";
            config.setCategoryComment(b, "Multipliers on the mod's balance (1 = as designed). On a server, give the clients the same file"
                    + " so their screens and tooltips show the same numbers.");
            machineSpeed = config.getFloat("machineSpeed", b, 1F, 0.1F, 10F,
                    "Machines work this many times faster (2 = recipes take half the time, at twice the EU a tick - the same EU an operation)");
            machineEnergy = config.getFloat("machineEnergy", b, 1F, 0.1F, 10F,
                    "Machines use this many times the EU (below ~0.7 water electrolysis + a fuel cell starts to give free energy)");
            storageCapacity = config.getFloat("storageCapacity", b, 1F, 0.1F, 10F, "Energy storages hold this many times the EU");
            batteryCapacity = config.getFloat("batteryCapacity", b, 1F, 0.1F, 10F, "Portable batteries hold this many times the EU");
            armorCapacity = config.getFloat("armorCapacity", b, 1F, 0.1F, 10F, "Suit pieces (Nano, Quantum, Exo) hold this many times the EU");
            armorDamageCost = config.getFloat("armorDamageCost", b, 1F, 0.1F, 10F,
                    "EU a suit spends on each point of damage it stops, times this (lower = stronger armour)");
            leadSuitPartProtection = config.getInt("leadSuitPartProtection", b, 22, 0, 25,
                    "Radiation each lead suit piece stops, % (4 pieces at 25 = all of it) / Защита от радиации за каждую часть свинцового костюма, %");
            fieldUpkeep = config.getFloat("fieldUpkeep", b, 1F, 0.1F, 10F, "The field generator's upkeep, times this");
            quarrySpeed = config.getFloat("quarrySpeed", b, 1F, 0.1F, 10F, "Quarries and the Exo Drilling Rig dig this many times faster");
            wirelessRange = config.getFloat("wirelessRange", b, 1F, 0.1F, 10F, "Wireless transmitters reach this many times as far (XV stays unlimited)");
            wirelessLoss = config.getFloat("wirelessLoss", b, 1F, 0F, 10F, "Wireless loss over distance, times this (0 = no loss)");
            quantumUpkeep = config.getFloat("quantumUpkeep", b, 1F, 0F, 10F, "The quantum translator pair's upkeep, times this");
            quantumChunkLoading = config.getBoolean("quantumChunkLoading", b, true,
                    "Quantum translators keep their chunks loaded (off: both ends must be loaded by players)");
            bladeExecutePlayers = config.getBoolean("bladeExecutePlayers", b, false,
                    "The Exo blade's execute (absolute blow below 20% health) works on players too (off: players take the plain blow)"
                    + " / Казнь клинка Экзо действует и на игроков (выкл.: по игроку обычный удар)");
            drillArmorReserve = config.getInt("drillArmorReserve", b, 15, 0, 90,
                    "A drill stops taking energy from the chestplate below this % of its charge (an overheated chestplate never feeds it)"
                    + " / Бур не берёт энергию с нагрудника, если его заряд ниже этого % (перегретый нагрудник не питает)");
            String cv = "converter";
            config.setCategoryComment(cv, "The Energy Converter: exchange rates and loss / Преобразователь энергии: курсы и потери");
            converterRfPerEu = config.getFloat("rfPerEu", cv, 4F, 0.1F, 1000F, "RF for one EU / RF за 1 EU");
            converterJPerEu = config.getFloat("jPerEu", cv, 10F, 0.1F, 1000F, "Mekanism joules (J) for one EU / Джоулей Mekanism за 1 EU");
            converterGjPerEu = config.getFloat("gjPerEu", cv, 0F, 0F, 1000F,
                    "Galacticraft gJ for one EU; 0 = Galacticraft's own rate (16 / 2.44 without it) / gJ Galacticraft за 1 EU; 0 - курс самого Galacticraft");
            converterLoss = config.getInt("lossPercent", cv, 5, 0, 50,
                    "Conversion loss, %; each Efficiency module takes 2 off (at most 2 count) / Потери преобразования, %; модуль КПД снимает 2 (до 2 шт.)");
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
