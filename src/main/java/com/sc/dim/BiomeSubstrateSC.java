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
 * Canyons (the Quartzite Ridges, Oxide Fields and the Rift come in Ф4 with ids of their own). Made by SubstrateSC.init
 * on a slot it checked is empty (super(id) writes the vanilla array); never added to BiomeManager (no Overworld
 * generation, no village / stronghold / spawn biome) and no mutation (id + 128 would pass 255).
 * <p>
 * Fixed: the constructor (id, kind), PLATEAUS / CANYONS, dictionaryTypes(kind); no rain (setDisableRain, rainfall 0);
 * empty spawn lists (only SpawnDirectorSC spawns here). The rest is W's: the map colours, the turquoise water, the
 * top / filler blocks (our generator doesn't read them - other mods may: the turf on the plateaus, Crystallite on
 * the canyons' floors) and a decorate that does nothing (if anyone calls it, vanilla's decorator would lay lakes,
 * clay and flowers here).
 */
public class BiomeSubstrateSC extends BiomeGenBase {

    public static final int PLATEAUS = 0, CANYONS = 1;
    /** The water's tint: turquoise (BlockLiquid multiplies the water texture with it). */
    public static final int WATER = 0x3FD3C8;
    /** The map colours (F3, map mods) and the never-used grass / foliage colours: pale teal, oxide violet. */
    private static final int COLOR_PLATEAUS = 0xA8C9C6, COLOR_CANYONS = 0x7A6A9A, GRASS = 0x8FB8A8;

    public final int kind;

    public BiomeSubstrateSC(int id, int kind) {
        super(id);
        this.kind = kind;
        setBiomeName(kind == CANYONS ? "Scribe Canyons" : "Silicon Plateaus");
        setColor(kind == CANYONS ? COLOR_CANYONS : COLOR_PLATEAUS);
        setDisableRain();
        setTemperatureRainfall(0.8F, 0F);
        waterColorMultiplier = WATER;
        spawnableMonsterList.clear();
        spawnableCreatureList.clear();
        spawnableWaterCreatureList.clear();
        spawnableCaveCreatureList.clear();
        Block rock = SubstrateBlocksSC.rock != null ? SubstrateBlocksSC.rock : Blocks.sandstone;   // the rock is registered in preInit, the biomes in init
        topBlock = rock;
        field_150604_aj = kind == CANYONS ? SubstrateBlocksSC.CRYSTALLITE : SubstrateBlocksSC.QUARTZ_TURF;
        fillerBlock = rock;
        field_76754_C = SubstrateBlocksSC.CRYSTALLITE;
    }

    /**
     * The BiomeDictionary types, explicit (ПК-6): other mods place hives, rubber trees and aura nodes by type - nothing
     * that invites them (no FOREST / PLAINS / JUNGLE / SWAMP / WATER / MAGICAL / SPOOKY...).
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
        return GRASS;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getBiomeFoliageColor(int x, int y, int z) {
        return GRASS;
    }
}
