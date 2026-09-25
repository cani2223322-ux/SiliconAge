package com.sc.worldgen;

import java.util.Random;

import com.sc.init.ModBlocks;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;

import cpw.mods.fml.common.IWorldGenerator;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.common.BiomeDictionary;

/**
 * Worldgen for all 16 ores (§10) plus Limestone (§17.1), per step 2. Y-range/vein-size come
 * from ConfigSC (i.e. from the .cfg, tunable without a rebuild); veinsPerChunk isn't specified
 * anywhere in the design doc, so it's a TODO-defaulted config value too (see ConfigSC).
 */
public class OreGenSC implements IWorldGenerator {

    // TODO(design doc): Limestone's veins-per-chunk/size aren't given a number in §17.1
    // beyond "крупные пласты" ("large seams") - defaulted here to be deliberately common,
    // consistent with it gating the very first step of the whole silicon chain.
    private static final int LIMESTONE_MIN_Y = 40;
    private static final int LIMESTONE_MAX_Y = 90;
    private static final int LIMESTONE_VEIN_SIZE = 20;
    private static final int LIMESTONE_VEINS_PER_CHUNK = 6;

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world, IChunkProvider chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.dimensionId != 0) {
            return; // all 16 ores + Limestone are Overworld-only per §10/§17.1
        }

        int blockX = chunkX * 16;
        int blockZ = chunkZ * 16;
        BiomeGenBase biome = world.getBiomeGenForCoords(blockX + 8, blockZ + 8);

        for (OreEntry ore : OreEntry.values()) {
            if (matchesBiome(biome, ore.biomes)) {
                generateOre(world, random, blockX, blockZ, ore);
            }
        }

        generateLimestone(world, random, blockX, blockZ);
    }

    private void generateOre(World world, Random random, int blockX, int blockZ, OreEntry ore) {
        ConfigSC.OreGenSettings settings = ConfigSC.settingsFor(ore);
        if (settings.maxY <= settings.minY || settings.veinsPerChunk <= 0) {
            return;
        }
        WorldGenMinable generator = new WorldGenMinable(ModBlocks.oreSC, ore.meta(), settings.veinSize, Blocks.stone);
        for (int i = 0; i < settings.veinsPerChunk; i++) {
            int x = blockX + random.nextInt(16);
            int y = settings.minY + random.nextInt(settings.maxY - settings.minY);
            int z = blockZ + random.nextInt(16);
            generator.generate(world, random, x, y, z);
        }
    }

    private void generateLimestone(World world, Random random, int blockX, int blockZ) {
        Block target = ModBlocks.limestoneSC;
        WorldGenMinable generator = new WorldGenMinable(target, 0, LIMESTONE_VEIN_SIZE, Blocks.stone);
        for (int i = 0; i < LIMESTONE_VEINS_PER_CHUNK; i++) {
            int x = blockX + random.nextInt(16);
            int y = LIMESTONE_MIN_Y + random.nextInt(LIMESTONE_MAX_Y - LIMESTONE_MIN_Y);
            int z = blockZ + random.nextInt(16);
            generator.generate(world, random, x, y, z);
        }
    }

    private boolean matchesBiome(BiomeGenBase biome, BiomeDictionary.Type[] requiredTypes) {
        if (requiredTypes == null || requiredTypes.length == 0) {
            return true; // "Любой" (any biome), per §10
        }
        for (BiomeDictionary.Type type : requiredTypes) {
            if (BiomeDictionary.isBiomeOfType(biome, type)) {
                return true;
            }
        }
        return false;
    }
}
