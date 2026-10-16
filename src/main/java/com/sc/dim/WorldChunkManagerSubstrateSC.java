package com.sc.dim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.WorldChunkManager;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.2): which biome lies where - the Scribe grid (a canyon belt along every
 * 256th line of X and Z, plateaus between: SubstrateTerrainSC.biomeKind, pure maths the self-test can ask). Fixed:
 * the constructor (World), rain 0 everywhere.
 * <p>
 * Extends WorldChunkManager through its protected constructor (no GenLayers, no WorldType) and overrides everything
 * that would read the base class's genBiomes / biomeIndexLayer / biomeCache, as WorldChunkManagerHell does. The
 * «generation» methods (getBiomesForGeneration, areBiomesViable, findBiomePosition) work in vanilla's 4-block cells,
 * so other mods' structure checks get the same answers as from a vanilla manager.
 */
public class WorldChunkManagerSubstrateSC extends WorldChunkManager {

    public WorldChunkManagerSubstrateSC(World world) {
        super();
    }

    /** The biome of a kind (a plain vanilla one only if the Substrate's never got made - it can't be loaded then). */
    private static BiomeGenBase biome(int kind) {
        BiomeGenBase b = kind == BiomeSubstrateSC.CANYONS ? SubstrateSC.canyons() : SubstrateSC.plateaus();
        return b != null ? b : BiomeGenBase.desert;
    }

    private static BiomeGenBase at(int x, int z) {
        return biome(SubstrateTerrainSC.biomeKind(x, z));
    }

    /** Where a player could start: the plateaus (WorldServer.createSpawnPosition and the like). */
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public List getBiomesToSpawnIn() {
        List l = new ArrayList();
        l.add(biome(BiomeSubstrateSC.PLATEAUS));
        return l;
    }

    @Override
    public BiomeGenBase getBiomeGenAt(int x, int z) {
        return at(x, z);
    }

    @Override
    public float[] getRainfall(float[] out, int x, int z, int w, int d) {
        if (out == null || out.length < w * d) {
            out = new float[w * d];
        }
        Arrays.fill(out, 0, w * d, 0F);
        return out;
    }

    @Override
    public float getTemperatureAtHeight(float temperature, int y) {
        return temperature;
    }

    /** In 4-block cells (as vanilla's genBiomes): cell (x, z) is blocks 4x..4x+3, asked at its middle. */
    @Override
    public BiomeGenBase[] getBiomesForGeneration(BiomeGenBase[] out, int x, int z, int w, int d) {
        if (out == null || out.length < w * d) {
            out = new BiomeGenBase[w * d];
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                out[i + j * w] = at((x + i) * 4 + 2, (z + j) * 4 + 2);
            }
        }
        return out;
    }

    @Override
    public BiomeGenBase[] loadBlockGeneratorData(BiomeGenBase[] out, int x, int z, int w, int d) {
        return getBiomeGenAt(out, x, z, w, d, true);
    }

    /** Per block, index i + j * w (dx + dz * width) - as a chunk's biome array is filled. */
    @Override
    public BiomeGenBase[] getBiomeGenAt(BiomeGenBase[] out, int x, int z, int w, int d, boolean cache) {
        if (out == null || out.length < w * d) {
            out = new BiomeGenBase[w * d];
        }
        for (int j = 0; j < d; j++) {
            for (int i = 0; i < w; i++) {
                out[i + j * w] = at(x + i, z + j);
            }
        }
        return out;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean areBiomesViable(int x, int z, int radius, List allowed) {
        int x1 = x - radius >> 2, z1 = z - radius >> 2, x2 = x + radius >> 2, z2 = z + radius >> 2;
        for (int cx = x1; cx <= x2; cx++) {
            for (int cz = z1; cz <= z2; cz++) {
                if (!allowed.contains(at(cx * 4 + 2, cz * 4 + 2))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A random 4-block cell within `range` whose biome is allowed (vanilla's pick: each match replaces with 1 / n), or null. */
    @Override
    @SuppressWarnings("rawtypes")
    public ChunkPosition findBiomePosition(int x, int z, int range, List allowed, Random random) {
        int x1 = x - range >> 2, z1 = z - range >> 2, x2 = x + range >> 2, z2 = z + range >> 2;
        ChunkPosition found = null;
        int n = 0;
        for (int cz = z1; cz <= z2; cz++) {
            for (int cx = x1; cx <= x2; cx++) {
                if (allowed.contains(at(cx * 4 + 2, cz * 4 + 2)) && (found == null || random.nextInt(n + 1) == 0)) {
                    found = new ChunkPosition(cx << 2, 0, cz << 2);
                    n++;
                }
            }
        }
        return found;
    }

    @Override
    public void cleanupCache() {
    }
}
