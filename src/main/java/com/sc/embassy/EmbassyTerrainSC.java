package com.sc.embassy;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.world.ChunkCoordIntPair;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.4, rule ПЕ-6): an embassy's land levelled IN A CHUNK'S OWN ARRAYS - the blocks and
 * metas ChunkProviderGenerate hands ChunkProviderEvent.ReplaceBiomeBlocks before its surface pass (EmbassyWorldGenSC), so nothing
 * outside the chunk is read or set and nothing cascades. The arrays' layout is Chunk's own (its constructor's, checked in the
 * bytecode): index (x * 16 + z) * height + y, height = blocks.length / 256. At that point an Overworld column is only stone, water
 * under the sea level and air (null).
 * <ul>
 * <li>the column's natural height - its top solid block in the array (naturalTop);</li>
 * <li>its new top - EmbassyLayoutSC.targetY(natural, floor, levelPast): on the level ground the floor, over the BLEND band eased back to
 * the natural height, past it untouched;</li>
 * <li>under the new top: stone (air and water there too); over it: air (stone and water there too) - only in the band, water under
 * the sea level (SEA) stays, so its shore is the land's own;</li>
 * <li>the band's new tops are remembered a while (rememberBand / bandTarget: transient, by chunk) for the ground piece
 * (ComponentEmbGroundSC), which fills the caves vanilla cuts after this pass - the land's own height can't be read back later.</li>
 * </ul>
 * Pure apart from the band's memory (levelColumn and levelChunk change only the arrays they are given) - the self-test calls them on
 * synthetic arrays. Server thread.
 */
public final class EmbassyTerrainSC {

    /** Vanilla's sea level: the array's water is under it; the band keeps it there. */
    public static final int SEA = 63;
    /** The chunks whose band tops are remembered at most (an embassy's band touches about a hundred). */
    public static final int BAND_CHUNKS = 2048;

    /** Chunk (cx, cz) -> its 256 columns' band tops + 1 ((x * 16 + z); 0: not in a band, or not known). Least recently used goes. */
    private static final Map<Long, byte[]> BAND = new LinkedHashMap<Long, byte[]>(256, 0.75F, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
            return size() > BAND_CHUNKS;
        }
    };

    private EmbassyTerrainSC() {
    }

    /** The arrays' index of (x, y, z) of the chunk (0..15, 0..height-1, 0..15). */
    public static int index(int x, int z, int y, int height) {
        return (x * 16 + z) * height + y;
    }

    /** Ground of the array: neither air nor a liquid. */
    public static boolean solid(Block b) {
        if (b == null) {
            return false;
        }
        Material m = b.getMaterial();
        return m != Material.air && !m.isLiquid();
    }

    /** The top solid block's y of the column starting at `base` (its y = 0), -1 none. */
    public static int naturalTop(Block[] blocks, int base, int height) {
        for (int y = height - 1; y >= 0; y--) {
            if (solid(blocks[base + y])) {
                return y;
            }
        }
        return -1;
    }

    /**
     * ПЕ-6: one column of the arrays levelled - `past` how far past the level ground it lies (EmbassyLayoutSC.levelPast; 0 or less: on
     * it). Under the new top stone, over it air (see the class comment); `metas` may be null (Forge's old event without them).
     * Pure. @return the column's new top, -1 past the band (untouched)
     */
    public static int levelColumn(Block[] blocks, byte[] metas, int base, int height, int floorY, double past) {
        if (past >= EmbassyLayoutSC.BLEND || height < 2) {
            return -1;
        }
        int natural = naturalTop(blocks, base, height);
        int target = EmbassyLayoutSC.targetY(natural < 0 ? floorY : natural, floorY, past);
        target = Math.max(1, Math.min(height - 2, target));
        boolean band = past > 0D;
        for (int y = 1; y < height; y++) {
            int i = base + y;
            Block b = blocks[i];
            if (y <= target) {
                if (!solid(b)) {
                    blocks[i] = Blocks.stone;
                    if (metas != null) {
                        metas[i] = 0;
                    }
                }
            } else if (b != null && (solid(b) || !band || y >= SEA)) {
                blocks[i] = null;                                // air (the band's water under the sea level stays)
                if (metas != null) {
                    metas[i] = 0;
                }
            }
        }
        return target;
    }

    /**
     * ПЕ-6: chunk (cx, cz)'s columns under one embassy (its core's column, the level ground's radius, the floor) levelled; `bandOut`
     * (256, or null) gets each band column's new top + 1 at (x * 16 + z) (0 elsewhere). Pure. @return the columns levelled or eased
     */
    public static int levelChunk(Block[] blocks, byte[] metas, int cx, int cz, int coreX, int coreZ, int levelRadius, int floorY,
            byte[] bandOut) {
        if (blocks == null || blocks.length < 512) {
            return 0;
        }
        int h = blocks.length / 256, n = 0;
        byte[] m = metas != null && metas.length == blocks.length ? metas : null;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double past = EmbassyLayoutSC.levelPast((cx << 4) + x - coreX, (cz << 4) + z - coreZ, levelRadius);
                int t = levelColumn(blocks, m, index(x, z, 0, h), h, floorY, past);
                if (t < 0) {
                    continue;
                }
                n++;
                if (past > 0D && bandOut != null && bandOut.length >= 256) {
                    bandOut[x * 16 + z] = (byte) (Math.min(254, t) + 1);
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ the band's tops, for the ground piece (transient)

    /** Chunk (cx, cz)'s band tops (levelChunk's bandOut) kept for its populate windows; nothing kept for an all-zero one. */
    public static void rememberBand(int cx, int cz, byte[] band) {
        if (band == null || band.length < 256) {
            return;
        }
        for (int i = 0; i < 256; i++) {
            if (band[i] != 0) {
                BAND.put(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(cx, cz)), band);
                return;
            }
        }
    }

    /** The band column (x, z)'s new top as levelled in its chunk's arrays this run, -1 not known (another run, or not in a band). */
    public static int bandTarget(int x, int z) {
        byte[] b = BAND.get(Long.valueOf(ChunkCoordIntPair.chunkXZ2Int(x >> 4, z >> 4)));
        int v = b == null ? 0 : b[(x & 15) * 16 + (z & 15)] & 0xFF;
        return v == 0 ? -1 : v - 1;
    }

    /** The Overworld unloads, the server stops: the band's tops go. */
    public static void clearBand() {
        BAND.clear();
    }
}
