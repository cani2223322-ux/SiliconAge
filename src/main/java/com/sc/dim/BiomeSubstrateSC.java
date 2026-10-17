package com.sc.dim;

import java.util.Random;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.2): the Substrate's biomes - Ф1: the Silicon Plateaus and the Scribe
 * Canyons; Ф4 (docs/plan-substrate-tech-f4.md §3): the Quartzite Ridges, the Oxide Fields and the Rift with ids of their
 * own. Made by SubstrateSC.init on a slot it checked is empty (super(id) writes the vanilla array); never added to
 * BiomeManager (no Overworld generation, no village / stronghold / spawn biome) and no mutation (id + 128 would pass 255).
 * <p>
 * Fixed: the constructor (id, kind), the kinds (PLATEAUS .. RIFT, NAMES, KEYS), dictionaryTypes(kind); no rain (setDisableRain, rainfall 0);
 * empty spawn lists (only SpawnDirectorSC spawns here). The rest is W's: the map colours, the turquoise water, the
 * top / filler blocks (our generator doesn't read them - other mods may: the turf on the plateaus, Crystallite on
 * the canyons' floors) and a decorate that does nothing (if anyone calls it, vanilla's decorator would lay lakes,
 * clay and flowers here).
 */
public class BiomeSubstrateSC extends BiomeGenBase {

    public static final int PLATEAUS = 0, CANYONS = 1;
    /**
     * Ф4 (docs/plan-substrate-tech-f4.md §3, the coordinator's): the Quartzite Ridges, the Oxide Fields, the Rift - whole plateau
     * cells chosen by SubstrateCellsSC (the canyon belts stay canyons); KINDS - how many there are. Append only (the self-test and
     * SpawnRulesSC's places count on the order).
     */
    public static final int RIDGES = 2, OXIDE = 3, RIFT = 4, KINDS = 5;
    /** The biomes' names in F3 and the logs (Latin; the handbook's names are lang keys sc.dim.biome.<key>), by kind. */
    public static final String[] NAMES = {"Silicon Plateaus", "Scribe Canyons", "Quartzite Ridges", "Oxide Fields", "The Rift"};
    /** The lang keys' tails (sc.dim.biome.<key>), by kind. */
    public static final String[] KEYS = {"plateaus", "canyons", "ridges", "oxide", "rift"};
    /** The water's tint: turquoise (BlockLiquid multiplies the water texture with it). */
    public static final int WATER = 0x3FD3C8;
    /** The map colours (F3, map mods) and the never-used grass / foliage colours: pale teal, oxide violet. */
    private static final int COLOR_PLATEAUS = 0xA8C9C6, COLOR_CANYONS = 0x7A6A9A, GRASS = 0x8FB8A8;
    /** Ф4's map colours: quartzite white-grey, the oxide's gold-violet sheen, the Rift's dark violet (W may retune). */
    private static final int[] COLORS = {COLOR_PLATEAUS, COLOR_CANYONS, 0xD8D8E0, 0xB89A5A, 0x2A1440};
    /**
     * Ф4 (W): the top block's meta by kind, for other mods that read topBlock (our generator doesn't): the turf on the plateaus and
     * the Ridges' gentle ground, Crystallite on the canyons' floors and the Rift's (its rim is a plateau, its islands' tops oxide -
     * Crystallite tells another mod «bare rock»), the gold crust on the dunes.
     */
    private static final int[] TOP_META = {SubstrateBlocksSC.QUARTZ_TURF, SubstrateBlocksSC.CRYSTALLITE, SubstrateBlocksSC.QUARTZ_TURF,
            SubstrateBlocksSC.OXIDE_GOLD, SubstrateBlocksSC.CRYSTALLITE};
    /** Ф4: the (never used) grass / foliage colours by kind - the turf's teal, a canyon's violet, quartz grey, oxide gold, Rift violet. */
    private static final int[] GRASS_BY_KIND = {GRASS, GRASS, 0xB8C4C0, 0xA89060, 0x6A4A8A};

    public final int kind;

    public BiomeSubstrateSC(int id, int kind) {
        super(id);
        this.kind = kind;
        int k = kind >= 0 && kind < KINDS ? kind : PLATEAUS;
        setBiomeName(NAMES[k]);
        setColor(COLORS[k]);
        setDisableRain();
        setTemperatureRainfall(0.8F, 0F);
        waterColorMultiplier = WATER;
        spawnableMonsterList.clear();
        spawnableCreatureList.clear();
        spawnableWaterCreatureList.clear();
        spawnableCaveCreatureList.clear();
        Block rock = SubstrateBlocksSC.rock != null ? SubstrateBlocksSC.rock : Blocks.sandstone;   // the rock is registered in preInit, the biomes in init
        topBlock = rock;
        field_150604_aj = TOP_META[k];
        fillerBlock = rock;
        field_76754_C = SubstrateBlocksSC.CRYSTALLITE;
    }

    /**
     * The BiomeDictionary types, explicit (ПК-6): other mods place hives, rubber trees and aura nodes by type - nothing
     * that invites them (no FOREST / PLAINS / JUNGLE / SWAMP / WATER / MAGICAL / SPOOKY...). Ф4: the same three for the
     * Ridges, the Oxide Fields and the Rift (MOUNTAIN / HILLS would call other mods' mountain generators here).
     */
    public static BiomeDictionary.Type[] dictionaryTypes(int kind) {
        return kind == CANYONS
                ? new BiomeDictionary.Type[]{BiomeDictionary.Type.WASTELAND, BiomeDictionary.Type.DRY, BiomeDictionary.Type.SPARSE}
                : new BiomeDictionary.Type[]{BiomeDictionary.Type.WASTELAND, BiomeDictionary.Type.DRY, BiomeDictionary.Type.SPARSE};
    }

    /** Nothing: the Substrate's chunk provider decorates itself (ores, traces, druses in its populate). */
    @Override
    public void decorate(World world, Random rand, int x, int z) {
    }

    @Override
    public float getSpawningChance() {
        return 0F;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeGrassColor(int x, int y, int z) {
        return GRASS_BY_KIND[kind >= 0 && kind < KINDS ? kind : PLATEAUS];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        return GRASS_BY_KIND[kind >= 0 && kind < KINDS ? kind : PLATEAUS];
    }
}
