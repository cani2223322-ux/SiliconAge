package com.sc.dim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.gen.NoiseGeneratorSimplex;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §3.2, rules ПМ-12..ПМ-16): the terrain of Ф4's biomes as pure maths - the chunk
 * generator, the self-test and the spawn director ask the same thing. Only a cell's inner square (SubstrateCellsSC.inner:
 * further than INNER_MARGIN from every Scribe line) ever changes; at its edge every shape meets the plateau of Ф1
 * (SubstrateTerrainSC.terraceY), so a canyon's walls, the camps and the neighbouring cells are those of Ф1.
 * <ul>
 * <li>the Quartzite Ridges: crests up to RIDGE_MAX, ramping over RIDGE_BLEND from the band, more caves (the carver), richer
 * veins (SubstrateOreGenSC x RIDGE_VEIN_MUL);</li>
 * <li>the Oxide Fields: glassy dunes DUNE_MIN..DUNE_MAX of all four oxide crusts, the fumaroles and the cracked RTGs
 * (SubstrateWildGenSC);</li>
 * <li>the Rift: past a rim RIFT_RIM wide a cliff down to a floor of Singular Glass (Y 1..RIFT_FLOOR over bedrock), the void
 * between; ISLANDS_MIN..MAX floating islands at ISLAND_MIN_Y..ISLAND_MAX_Y (Crystallite, oxide on top, the ion geysers); the
 * «puncture pillar» in the cell's middle - Singular Glass of radius PILLAR_RADIUS up to PILLAR_TOP round a glowing core.</li>
 * </ul>
 * Signatures and numbers fixed (the coordinator's; TODO(balance) the open ones); the bodies are W's.
 * <p>
 * The shapes, in a cell's own coordinates (u, v = |x - middle|, |z - middle| - 0..87 in the inner square; e = 88 - max(u, v) =
 * how far a column is inside it, 1 at its edge):
 * <ul>
 * <li>Ridges: a ridged simplex noise (1 - |n|, squared: sharp crests, wide valleys at about RIDGE_BASE), its crests lifted by a
 * slow «massif» noise and roughened by smaller spurs; blended from terraceY by a smoothstep over e = 1..RIDGE_BLEND. Top: Quartz
 * Turf on gentle ground under TURF_MAX, banded oxide on the slopes, bare Crystallite with oxide stripes on the steep faces;</li>
 * <li>Oxide Fields: dune crests across a wind from DUNE_WIND (long along them, short across), swelling by a slow noise; blended
 * over DUNE_BLEND. The dunes' body above DUNE_MIN - 2 is oxide, its colour by a patch noise (the four crusts in fringes like an
 * oil film - not bands by Y), the top a little turf dithered in near the band;</li>
 * <li>Rift: the void is a rounded square (a superellipse of exponent 4) whose edge wobbles 0..RIFT_WOBBLE inwards of the rim, so
 * the rim is never thinner than RIFT_RIM; the last RIFT_WALL blocks of rim before the void are banded oxide down to the floor
 * (the striped cliff); the rest of the rim is Ф1's plateau column (column() says false). Islands: lenses (a low dome on top, a
 * cone under) placed by the cell's hash, never touching each other, the rim or the pillar.</li>
 * </ul>
 * Never Blocks.stone; bedrock at Y 0; caches (noise per seed, islands per cell) are synchronised - the self-test may ask from
 * another thread than the chunk generator.
 */
public final class SubstrateWildTerrainSC {

    /** ПМ-12: the Ridges' crests, the ramp from the plateaus' band, the veins' multiplier. TODO(balance) */
    public static final int RIDGE_MAX = 140, RIDGE_BLEND = 32;
    public static final float RIDGE_VEIN_MUL = 1.5F;
    /** ПМ-13: the Oxide Fields' dunes. */
    public static final int DUNE_MIN = 64, DUNE_MAX = 80;
    /** ПМ-14: the Rift - its rim inside the band, its glass floor, the islands, the pillar. TODO(balance) */
    public static final int RIFT_RIM = 16, RIFT_FLOOR = 2, ISLAND_MIN_Y = 40, ISLAND_MAX_Y = 100, ISLANDS_MIN = 6, ISLANDS_MAX = 12;
    public static final int PILLAR_RADIUS = 2, PILLAR_TOP = 160;

    /** ПМ-12: the valleys' floor between the crests; turf only under TURF_MAX and on gentle ground. TODO(balance) */
    public static final int RIDGE_BASE = 78, TURF_MAX = 124;
    /** ПМ-13: the dunes' ramp from the band. */
    public static final int DUNE_BLEND = 24;
    /** ПМ-14: how far the void's edge wobbles inwards of the rim; the banded cliff's thickness; the islands' radii. TODO(balance) */
    public static final int RIFT_WOBBLE = 8, RIFT_WALL = 2, ISLAND_R_MIN = 5, ISLAND_R_MAX = 11;

    /** The cell's middle in its own coordinates; the inner square's half (u, v up to it). */
    private static final int MID = SubstrateCellsSC.CELL / 2, INNER_HALF = MID - SubstrateCellsSC.INNER_MARGIN - 1;
    /** The void's nominal half (the rim's inner edge) and the radius it always reaches (the wobble at its deepest). */
    private static final int VOID_HALF = INNER_HALF + 1 - RIFT_RIM, VOID_SAFE = VOID_HALF - RIFT_WOBBLE;
    /** The islands keep this far from the pillar's axis, from each other and from VOID_SAFE; tries for a cell's islands. */
    private static final int ISLAND_CLEAR = 10, ISLAND_GAP = 3, ISLAND_TRIES = 80;
    /** Slopes (the height difference over two blocks): gentle up to this (turf), steep from STEEP (bare striped rock). */
    private static final int GENTLE = 2, STEEP = 6;

    private static final double RIDGE_SCALE = 1D / 72D, SPUR_SCALE = 1D / 23D, MASS_SCALE = 1D / 190D;
    private static final double DUNE_WIND = Math.toRadians(30D), DUNE_ACROSS = 1D / 26D, DUNE_ALONG = 1D / 70D, SWELL_SCALE = 1D / 110D;
    private static final double PATCH_SCALE = 1D / 22D, PATCH_FINE = 1D / 7D, WOBBLE_SCALE = 1D / 18D;
    private static final long NOISE_SALT = 0x81D6E5F4L, ISLAND_SALT = 0x15A7D0C4L, DITHER_SALT = 0xD17E4L;

    /** The last seed's noises (a column is asked for often; building them is not cheap). */
    private static long noiseSeed;
    private static Noise noise;
    /** A cell's islands lately asked (the generator asks a column at a time): seed, then cell key -> islands. */
    private static final Map<Long, Island[]> ISLANDS = new HashMap<Long, Island[]>();
    private static long islandSeed;
    private static final int ISLANDS_CACHED = 256;

    private SubstrateWildTerrainSC() {
    }

    // ------------------------------------------------------------------ pure shapes

    /** ПМ-12: the Ridges' top solid block at (x, z) of a Ridges cell (the plateau's terrace at the inner square's edge). */
    public static int ridgeY(long seed, int x, int z) {
        int terrace = SubstrateTerrainSC.terraceY(seed, x, z);
        int e = inside(x, z);
        if (e <= 0) {
            return terrace;
        }
        Noise n = noise(seed);
        double crest = ridged(n.ridge.func_151605_a(x * RIDGE_SCALE, z * RIDGE_SCALE));
        double spur = ridged(n.spur.func_151605_a(x * SPUR_SCALE, z * SPUR_SCALE));
        double massif = 0.5D + 0.5D * n.mass.func_151605_a(x * MASS_SCALE, z * MASS_SCALE);
        double h = clamp01(crest * (0.5D + 0.5D * massif) + 0.2D * spur * (0.3D + 0.7D * crest));
        double raw = RIDGE_BASE + (RIDGE_MAX - RIDGE_BASE) * h;
        return Math.min(RIDGE_MAX, (int) Math.round(terrace + (raw - terrace) * ramp(e, RIDGE_BLEND)));
    }

    /** ПМ-13: the Oxide Fields' top at (x, z) of an Oxide cell. */
    public static int duneY(long seed, int x, int z) {
        int terrace = SubstrateTerrainSC.terraceY(seed, x, z);
        int e = inside(x, z);
        if (e <= 0) {
            return terrace;
        }
        Noise n = noise(seed);
        double c = Math.cos(DUNE_WIND), s = Math.sin(DUNE_WIND);
        double across = x * c + z * s, along = z * c - x * s;
        double crest = Math.pow(ridged(n.dune.func_151605_a(across * DUNE_ACROSS, along * DUNE_ALONG)), 1.5D);
        double swell = 0.5D + 0.5D * n.swell.func_151605_a(x * SWELL_SCALE, z * SWELL_SCALE);
        double ripple = n.patch.func_151605_a(x * PATCH_FINE, z * PATCH_FINE);
        double h = clamp01(crest * (0.45D + 0.55D * swell) + 0.06D * ripple);
        double raw = DUNE_MIN + (DUNE_MAX - DUNE_MIN) * h;
        int y = (int) Math.round(terrace + (raw - terrace) * ramp(e, DUNE_BLEND));
        return Math.max(DUNE_MIN, Math.min(DUNE_MAX, y));
    }

    /** ПМ-14: the column (x, z) of a Rift cell is the void (nothing but the floor - and an island over it, if any). */
    public static boolean riftVoid(long seed, int x, int z) {
        return SubstrateCellsSC.inner(x, z) && !pillar(x, z) && voidEdge(seed, x, z) < 0D;
    }

    /** ПМ-14: {bottom, top} Y of the island over column (x, z) of a Rift cell, or null - none there. */
    public static int[] islandAt(long seed, int x, int z) {
        if (!SubstrateCellsSC.inner(x, z)) {
            return null;
        }
        for (Island is : islands(seed, x >> 8, z >> 8)) {
            int[] span = is.span(x, z);
            if (span != null) {
                return span;
            }
        }
        return null;
    }

    /** ПМ-14: the column is in the puncture pillar of its Rift cell (the cell's middle, PILLAR_RADIUS round). */
    public static boolean pillar(int x, int z) {
        int cx = (x >> 8) * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2, cz = (z >> 8) * SubstrateCellsSC.CELL + SubstrateCellsSC.CELL / 2;
        return (x - cx) * (x - cx) + (z - cz) * (z - cz) <= PILLAR_RADIUS * PILLAR_RADIUS;
    }

    /**
     * The top solid block's Y of column (x, z) in a cell of kind `kind` as generated (before the carver): Ф1's surface in the
     * belt and the band and on plateau cells; the Ridges' and the dunes' tops; in the Rift the pillar's top, an island's top, or
     * RIFT_FLOOR over the void, the terrace on the rim. Pure - the spawn director and the tests ask it.
     */
    public static int topY(int kind, long seed, int x, int z) {
        if (!SubstrateCellsSC.inner(x, z) || kind == BiomeSubstrateSC.PLATEAUS || kind == BiomeSubstrateSC.CANYONS) {
            return SubstrateTerrainSC.surfaceY(seed, x, z);
        }
        if (kind == BiomeSubstrateSC.RIDGES) {
            return ridgeY(seed, x, z);
        }
        if (kind == BiomeSubstrateSC.OXIDE) {
            return duneY(seed, x, z);
        }
        if (pillar(x, z)) {
            return PILLAR_TOP;
        }
        if (voidEdge(seed, x, z) < 0D) {
            int[] is = islandAt(seed, x, z);
            return is != null ? is[1] : RIFT_FLOOR;
        }
        return SubstrateTerrainSC.terraceY(seed, x, z);
    }

    /** ПМ-13: the oxide crust's colour (0..3 = violet, blue, gold, green) of an Oxide Fields column - patches, not bands by Y. */
    public static int oxidePatch(long seed, int x, int z) {
        Noise n = noise(seed);
        double v = n.patch.func_151605_a(x * PATCH_SCALE + 913D, z * PATCH_SCALE - 377D) + 0.35D * n.patch.func_151605_a(x * PATCH_FINE, z * PATCH_FINE);
        return (int) Math.floor((v + 2D) * 2.5D) & 3;
    }

    /** ПМ-14 (pure; the self-test): how many islands cell (cellX, cellZ) has if it is a Rift cell - ISLANDS_MIN..ISLANDS_MAX. */
    public static int islandCount(long seed, int cellX, int cellZ) {
        return islands(seed, cellX, cellZ).length;
    }

    /**
     * ПМ-14 for populate (SubstrateWildGenSC): the islands of cell (cellX, cellZ) if it is a Rift cell - {x, z, y, radius, depth}
     * each (y: the top's rim; the dome rises up to 2 over it, the cone reaches depth under it) - and their hashes in `hashes`
     * (same order; may be null).
     */
    static int[][] islandList(long seed, int cellX, int cellZ, List<Long> hashes) {
        Island[] all = islands(seed, cellX, cellZ);
        int[][] out = new int[all.length][];
        for (int i = 0; i < all.length; i++) {
            out[i] = new int[]{all[i].x, all[i].z, all[i].y, all[i].r, all[i].depth};
            if (hashes != null) {
                hashes.add(Long.valueOf(all[i].h));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the chunk's arrays

    /**
     * ChunkProviderSubstrateSC.provideChunk (the core's call) for every column of a chunk of a cell of kind `kind` (RIDGES, OXIDE
     * or RIFT): the column world (wx, wz) into the arrays at `col` (index col + y; Block[] / byte[] as the chunk's). @return true
     * - laid here; false - Ф1's column is laid instead (the band along the lines, the Rift's rim away from its cliff)
     */
    public static boolean column(int kind, long seed, int wx, int wz, int col, Block[] blocks, byte[] metas) {
        if (SubstrateBlocksSC.rock == null || !SubstrateCellsSC.inner(wx, wz)) {
            return false;
        }
        switch (kind) {
            case BiomeSubstrateSC.RIDGES:
                ridgeColumn(seed, wx, wz, col, blocks, metas);
                return true;
            case BiomeSubstrateSC.OXIDE:
                duneColumn(seed, wx, wz, col, blocks, metas);
                return true;
            case BiomeSubstrateSC.RIFT:
                return riftColumn(seed, wx, wz, col, blocks, metas);
            default:
                return false;
        }
    }

    /** ПМ-12: a Ridges column - turf on gentle ground, banded oxide on slopes, Crystallite with oxide stripes on steep faces. */
    private static void ridgeColumn(long seed, int x, int z, int col, Block[] blocks, byte[] metas) {
        int top = ridgeY(seed, x, z);
        int slope = Math.max(Math.abs(ridgeY(seed, x + 1, z) - ridgeY(seed, x - 1, z)), Math.abs(ridgeY(seed, x, z + 1) - ridgeY(seed, x, z - 1)));
        int crustFrom = Math.max(SubstrateTerrainSC.OXIDE_FROM, top - SubstrateTerrainSC.crustDepth(seed, x, z));
        Block rock = SubstrateBlocksSC.rock;
        blocks[col] = Blocks.bedrock;
        for (int y = 1; y <= top; y++) {
            int meta;
            if (y == top) {
                meta = slope <= GENTLE && top < TURF_MAX ? SubstrateBlocksSC.QUARTZ_TURF
                        : slope >= STEEP ? SubstrateBlocksSC.CRYSTALLITE : band(y);
            } else if (y >= crustFrom) {
                meta = slope >= STEEP && (y / SubstrateTerrainSC.BAND & 1) == 1 ? SubstrateBlocksSC.CRYSTALLITE : band(y);
            } else {
                meta = SubstrateBlocksSC.CRYSTALLITE;
            }
            blocks[col + y] = rock;
            metas[col + y] = (byte) meta;
        }
    }

    /** ПМ-13: an Oxide Fields column - the dune's body of oxide by the patch noise, turf dithered in near the band. */
    private static void duneColumn(long seed, int x, int z, int col, Block[] blocks, byte[] metas) {
        int top = duneY(seed, x, z);
        int patch = oxidePatch(seed, x, z);
        double t = ramp(inside(x, z), DUNE_BLEND);
        boolean turf = t < 0.2D + 0.4D * (SubstrateTerrainSC.pick(SubstrateTerrainSC.mix(seed ^ DITHER_SALT, x, z), 1000) / 1000D);
        int crustFrom = Math.max(SubstrateTerrainSC.OXIDE_FROM, Math.min(top - SubstrateTerrainSC.crustDepth(seed, x, z), DUNE_MIN - 2));
        Block rock = SubstrateBlocksSC.rock;
        blocks[col] = Blocks.bedrock;
        for (int y = 1; y <= top; y++) {
            int meta;
            if (y == top && turf) {
                meta = SubstrateBlocksSC.QUARTZ_TURF;
            } else if (y >= crustFrom) {
                meta = SubstrateBlocksSC.OXIDE_VIOLET + (patch + (top - y) / 4 & 3);    // the patch on top, other films deeper
            } else {
                meta = SubstrateBlocksSC.CRYSTALLITE;
            }
            blocks[col + y] = rock;
            metas[col + y] = (byte) meta;
        }
    }

    /** ПМ-14: a Rift column - the pillar, the void (the floor, an island over it), the banded cliff; the rest of the rim: Ф1's. */
    private static boolean riftColumn(long seed, int x, int z, int col, Block[] blocks, byte[] metas) {
        Block glass = SubstrateWildBlocksSC.glass, rock = SubstrateBlocksSC.rock;
        if (glass == null) {
            return false;
        }
        if (pillar(x, z)) {                                  // the core's column gets its glowing meta in populate (glow)
            blocks[col] = Blocks.bedrock;
            for (int y = 1; y <= PILLAR_TOP; y++) {
                blocks[col + y] = glass;
                metas[col + y] = (byte) SubstrateWildBlocksSC.GLASS;
            }
            return true;
        }
        double edge = voidEdge(seed, x, z);
        if (edge < 0D) {
            blocks[col] = Blocks.bedrock;
            for (int y = 1; y <= RIFT_FLOOR; y++) {
                blocks[col + y] = glass;
                metas[col + y] = (byte) SubstrateWildBlocksSC.GLASS;
            }
            int[] is = islandAt(seed, x, z);
            if (is != null) {
                int patch = oxidePatch(seed, x, z), skin = is[1] - is[0] >= 3 ? 2 : 1;
                for (int y = is[0]; y <= is[1]; y++) {
                    blocks[col + y] = rock;
                    metas[col + y] = (byte) (y > is[1] - skin ? SubstrateBlocksSC.OXIDE_VIOLET + patch : SubstrateBlocksSC.CRYSTALLITE);
                }
            }
            return true;
        }
        if (edge > RIFT_WALL) {
            return false;                                    // the rim: the plateau of Ф1, as the band beside it
        }
        int top = SubstrateTerrainSC.terraceY(seed, x, z);  // the cliff: oxide bands from the floor up, as a canyon's wall
        blocks[col] = Blocks.bedrock;
        for (int y = 1; y <= top; y++) {
            blocks[col + y] = rock;
            metas[col + y] = (byte) (y == top ? SubstrateBlocksSC.QUARTZ_TURF : y > RIFT_FLOOR ? band(y) : SubstrateBlocksSC.CRYSTALLITE);
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    /** How far the column lies inside its cell's inner square: 1 at its edge, up to 88 in the middle; 0 or less outside. */
    static int inside(int x, int z) {
        int u = Math.abs((x & 255) - MID), v = Math.abs((z & 255) - MID);
        return INNER_HALF + 1 - Math.max(u, v);
    }

    /**
     * The Rift's void edge at a column: the column's superellipse distance from the cell's middle minus the void's radius there
     * (VOID_HALF less a wobble of 0..RIFT_WOBBLE) - below 0 the void, 0..RIFT_WALL the cliff, more the rim. The superellipse
     * distance is never below max(u, v), so the void never reaches the rim's RIFT_RIM blocks.
     */
    private static double voidEdge(long seed, int x, int z) {
        double u = Math.abs((x & 255) - MID), v = Math.abs((z & 255) - MID);
        double norm = Math.sqrt(Math.sqrt(u * u * u * u + v * v * v * v));
        double wobble = RIFT_WOBBLE * clamp01(0.5D + 0.5D * noise(seed).wobble.func_151605_a(x * WOBBLE_SCALE, z * WOBBLE_SCALE));
        return norm - (VOID_HALF - wobble);
    }

    /** The oxide colour band at height y as a meta (the crust's and the walls' banding of Ф1). */
    private static int band(int y) {
        return SubstrateBlocksSC.OXIDE_VIOLET + SubstrateTerrainSC.oxideBand(y);
    }

    /** A ridged noise: 1 on the noise's zero line, squared - sharp crests, wide troughs. */
    private static double ridged(double n) {
        double r = 1D - Math.min(1D, Math.abs(n));
        return r * r;
    }

    /** A smoothstep from 0 at e = 1 (the inner square's edge) to 1 at e = blend. */
    private static double ramp(int e, int blend) {
        double t = clamp01((e - 1) / (double) (blend - 1));
        return t * t * (3D - 2D * t);
    }

    private static double clamp01(double v) {
        return v < 0D ? 0D : v > 1D ? 1D : v;
    }

    private static synchronized Noise noise(long seed) {
        if (noise == null || noiseSeed != seed) {
            noise = new Noise(new Random(seed ^ NOISE_SALT));
            noiseSeed = seed;
        }
        return noise;
    }

    /** The cell's islands (pure, cached): placed by its hash, a radius and a height each, never meeting (the class comment). */
    private static Island[] islands(long seed, int cellX, int cellZ) {
        Long k = Long.valueOf(SubstrateCellsSC.key(cellX, cellZ));
        synchronized (ISLANDS) {
            if (islandSeed != seed || ISLANDS.size() > ISLANDS_CACHED) {
                ISLANDS.clear();
                islandSeed = seed;
            }
            Island[] got = ISLANDS.get(k);
            if (got != null) {
                return got;
            }
        }
        Random r = new Random(SubstrateTerrainSC.mix(seed ^ ISLAND_SALT, cellX, cellZ));
        int want = ISLANDS_MIN + r.nextInt(ISLANDS_MAX - ISLANDS_MIN + 1);
        int mx = cellX * SubstrateCellsSC.CELL + MID, mz = cellZ * SubstrateCellsSC.CELL + MID;
        List<Island> made = new ArrayList<Island>();
        for (int t = 0; t < 2 * ISLAND_TRIES && made.size() < (t < ISLAND_TRIES ? want : ISLANDS_MIN); t++) {
            int rad = t < ISLAND_TRIES ? ISLAND_R_MIN + r.nextInt(ISLAND_R_MAX - ISLAND_R_MIN + 1) : ISLAND_R_MIN;   // short of the least: small ones
            double reach = VOID_SAFE - ISLAND_GAP - Island.LOBE - rad, from = ISLAND_CLEAR + rad + Island.LOBE;
            double a = r.nextDouble() * Math.PI * 2D, d = from + r.nextDouble() * Math.max(0D, reach - from);
            int ix = mx + (int) Math.round(Math.cos(a) * d), iz = mz + (int) Math.round(Math.sin(a) * d);
            double u = Math.abs(ix - mx), v = Math.abs(iz - mz);
            if (Math.sqrt(Math.sqrt(u * u * u * u + v * v * v * v)) > reach) {
                continue;                                    // its lobes would touch the cliff
            }
            boolean clear = true;
            for (Island o : made) {
                double ddx = ix - o.x, ddz = iz - o.z, gap = rad + o.r + 2 * Island.LOBE + ISLAND_GAP;
                if (ddx * ddx + ddz * ddz < gap * gap) {
                    clear = false;
                    break;
                }
            }
            if (!clear) {
                continue;
            }
            int depth = 3 + rad * 3 / 4;
            int lo = ISLAND_MIN_Y + depth + 1, hi = ISLAND_MAX_Y - Island.DOME - 1;
            made.add(new Island(ix, iz, lo + r.nextInt(hi - lo + 1), rad, depth, r.nextLong()));
        }
        Island[] out = made.toArray(new Island[made.size()]);
        synchronized (ISLANDS) {
            if (islandSeed == seed) {
                ISLANDS.put(k, out);
            }
        }
        return out;
    }

    /** One floating island: a lens with a few lobes round its edge. */
    private static final class Island {
        /** The edge's lobes reach this much past the radius; the dome rises this much over the rim's Y. */
        static final double LOBE = 1.5D;
        static final int DOME = 2;
        final int x, z, y, r, depth, lobes;
        final double phase;
        final long h;

        Island(int x, int z, int y, int r, int depth, long h) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.r = r;
            this.depth = depth;
            this.h = h;
            this.lobes = 3 + SubstrateTerrainSC.pick(h, 3);
            this.phase = SubstrateTerrainSC.pick(h >>> 8, 628) / 100D;
        }

        /** {bottom, top} at column (cx, cz), or null outside this island. */
        int[] span(int cx, int cz) {
            double dx = cx - x, dz = cz - z, d2 = dx * dx + dz * dz, outer = r + LOBE + 0.5D;
            if (d2 >= outer * outer) {
                return null;
            }
            double rr = r + LOBE * Math.min(1D, r / 8D) * Math.sin(Math.atan2(dz, dx) * lobes + phase), d = Math.sqrt(d2);   // small ones rounder
            if (d >= rr) {
                return null;
            }
            double t = d / rr;
            int top = y + (int) Math.round(DOME * (1D - t * t));
            int bottom = y - (int) Math.round(depth * Math.pow(1D - t, 0.75D));
            return new int[]{Math.min(bottom, top), top};
        }
    }

    /** The noises of one seed (simplex, each about -1..1). */
    private static final class Noise {
        final NoiseGeneratorSimplex ridge, spur, mass, dune, swell, patch, wobble;

        Noise(Random r) {
            ridge = new NoiseGeneratorSimplex(r);
            spur = new NoiseGeneratorSimplex(r);
            mass = new NoiseGeneratorSimplex(r);
            dune = new NoiseGeneratorSimplex(r);
            swell = new NoiseGeneratorSimplex(r);
            patch = new NoiseGeneratorSimplex(r);
            wobble = new NoiseGeneratorSimplex(r);
        }
    }
}
