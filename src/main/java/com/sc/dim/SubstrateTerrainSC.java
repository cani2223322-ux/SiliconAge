package com.sc.dim;

import java.util.Random;

import net.minecraft.world.gen.NoiseGeneratorPerlin;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.3): the Substrate's terrain as pure maths - no World, so the chunk
 * generator, the chunk manager, the provider's spawn point and the self-test all ask the same thing. The Scribe
 * lines run along every GRID-th block of X and of Z; the canyon belt (the Scribe Canyons biome) is CANYON_BELT
 * blocks either side of a line; plateaus between, terraces Y PLATEAU_MIN..PLATEAU_MAX.
 * <p>
 * The shapes:
 * <ul>
 * <li>plateaus: terraces of TERRACE_STEP blocks from a NoiseGeneratorPerlin of the world seed; just below each
 * terrace's edge a one-block lip, so a step of 2 can be walked up (and mobs path over it);</li>
 * <li>canyons: a straight cut 2h + 1 wide (h = canyonHalfWidth, 4..7) along every line, vertical walls. The floor's
 * height is set at every crossing of two lines (a «node», CANYON_FLOOR_MIN..CANYON_FLOOR_MAX) and runs evenly from
 * one node to the next, so the canyons always meet level; the floor is CANYON_DEPTH_MIN..CANYON_DEPTH_MAX below any
 * terrace;</li>
 * <li>a crossing: a square «plaza» of half-size max(hx, hz) + CROSSING_EXTRA at the node's floor (the Dead Nodes
 * stand there from Ф3).</li>
 * </ul>
 * Lines along Z (x = n·GRID) take their width from canyonHalfWidth(seed, n), lines along X (z = n·GRID) from
 * canyonHalfWidth(~seed, n) - so the two lines of one number differ.
 * <p>
 * Signatures fixed (the self-test, the chunk manager); the bodies are W's.
 */
public final class SubstrateTerrainSC {

    public static final int GRID = 256, CANYON_BELT = 24;
    public static final int PLATEAU_MIN = 64, PLATEAU_MAX = 74, TERRACE_STEP = 2;
    /** A canyon itself: 9..15 wide, 30..50 deep below the plateau around it. */
    public static final int CANYON_WIDTH_MIN = 9, CANYON_WIDTH_MAX = 15, CANYON_DEPTH_MIN = 30, CANYON_DEPTH_MAX = 50;
    /** The layers (§3.3 of docs/plan-substrate.md). */
    public static final int BASE_TOP = 20, METAL_TOP = 40, OXIDE_FROM = 60;

    /** A canyon's floor: deep enough under every terrace (64..74) that the depth is always 30..50 (24..34). */
    public static final int CANYON_FLOOR_MIN = PLATEAU_MAX - CANYON_DEPTH_MAX, CANYON_FLOOR_MAX = PLATEAU_MIN - CANYON_DEPTH_MIN;
    /** A crossing's plaza reaches this much further than the wider of its two canyons (19..25 across). */
    public static final int CROSSING_EXTRA = 5;
    /** The oxide crust under the top block: 2..6 blocks (never below OXIDE_FROM). */
    public static final int CRUST_MIN = 2, CRUST_MAX = 6;
    /** The oxide bands by Y (the crust and the canyon walls): 4 colours, 3 blocks each. */
    public static final int BAND = 3;

    /** The terrace noise: 3 octaves, the biggest terraces some 150 blocks across. */
    private static final int OCTAVES = 3;
    private static final double TERRACE_SCALE = 1D / 40D, TERRACE_SPAN = 4.0D, LIP = 0.8D;
    private static final double CRUST_SCALE = 1D / 12D, CRUST_OFFSET = 4096D;
    private static final long NOISE_SALT = 0x5115C0DE14L;

    /** The last seed's noise: a column is asked for often (256 per chunk), building the noise is not cheap. */
    private static long noiseSeed;
    private static NoiseGeneratorPerlin noise;

    private SubstrateTerrainSC() {
    }

    /** Blocks from `coord` to the nearest Scribe line on that axis (0 on the line). */
    public static int distanceToLine(int coord) {
        int m = ((coord % GRID) + GRID) % GRID;                // Java 6: no Math.floorMod
        return Math.min(m, GRID - m);
    }

    /** The number of the Scribe line nearest to `coord` (line n lies at n·GRID). */
    public static int lineIndex(int coord) {
        return floorDiv(coord + GRID / 2, GRID);
    }

    /** The column is in a canyon belt (the Scribe Canyons biome). */
    public static boolean inCanyonBelt(int x, int z) {
        return distanceToLine(x) <= CANYON_BELT || distanceToLine(z) <= CANYON_BELT;
    }

    /** BiomeSubstrateSC.PLATEAUS / CANYONS of the column. */
    public static int biomeKind(int x, int z) {
        return inCanyonBelt(x, z) ? BiomeSubstrateSC.CANYONS : BiomeSubstrateSC.PLATEAUS;
    }

    /** Half the width of the canyon along line number `line` (lines are numbered by coord / GRID), so the width is 2h + 1. */
    public static int canyonHalfWidth(long seed, int line) {
        int lo = (CANYON_WIDTH_MIN - 1) / 2, hi = (CANYON_WIDTH_MAX - 1) / 2;
        return lo + pick(mix(seed, line, 0x77D7L), hi - lo + 1);
    }

    /** The canyons' floor at the crossing of line `i` (x = i·GRID) and line `j` (z = j·GRID): CANYON_FLOOR_MIN..MAX. */
    public static int nodeFloorY(long seed, int i, int j) {
        return CANYON_FLOOR_MIN + pick(mix(seed ^ 0xF100A5L, i, j), CANYON_FLOOR_MAX - CANYON_FLOOR_MIN + 1);
    }

    /** The plateau's terrace at the column as if no canyon were cut there: PLATEAU_MIN..PLATEAU_MAX. */
    public static int terraceY(long seed, int x, int z) {
        double n = noise(seed).func_151601_a(x * TERRACE_SCALE, z * TERRACE_SCALE);
        double t = (n / TERRACE_SPAN + 1D) * 0.5D;
        if (t <= 0D) {
            return PLATEAU_MIN;
        }
        if (t >= 1D) {
            return PLATEAU_MAX;
        }
        double s = t * ((PLATEAU_MAX - PLATEAU_MIN) / TERRACE_STEP);
        int level = (int) Math.floor(s);
        int y = PLATEAU_MIN + level * TERRACE_STEP;
        return s - level > LIP && y < PLATEAU_MAX ? y + 1 : y;     // the lip just below the next terrace
    }

    /**
     * How far outside the nearest canyon the column lies: 0 or less inside one (a canyon or a crossing's plaza), 1 = the
     * first block of a wall, and so on (far from every line: just something large).
     */
    public static int canyonEdgeDistance(long seed, int x, int z) {
        int dx = distanceToLine(x), dz = distanceToLine(z);
        if (dx > CANYON_BELT && dz > CANYON_BELT) {
            return Math.min(dx, dz) - (CANYON_WIDTH_MAX - 1) / 2;
        }
        int hx = canyonHalfWidth(seed, lineIndex(x)), hz = canyonHalfWidth(~seed, lineIndex(z));
        int plaza = Math.max(dx, dz) - (Math.max(hx, hz) + CROSSING_EXTRA);
        return Math.min(plaza, Math.min(dx - hx, dz - hz));
    }

    /** The canyon's floor at the column (its top solid block), or -1 when the column is no canyon. */
    public static int canyonFloorY(long seed, int x, int z) {
        int dx = distanceToLine(x), dz = distanceToLine(z);
        if (dx > CANYON_BELT && dz > CANYON_BELT) {
            return -1;
        }
        int i = lineIndex(x), j = lineIndex(z);
        int hx = canyonHalfWidth(seed, i), hz = canyonHalfWidth(~seed, j);
        if (Math.max(dx, dz) <= Math.max(hx, hz) + CROSSING_EXTRA) {
            return nodeFloorY(seed, i, j);                       // the crossing's plaza
        }
        if (dx <= hx) {                                          // a canyon along Z: from node (i, n) to node (i, n + 1)
            int n = floorDiv(z, GRID);
            return lerp(nodeFloorY(seed, i, n), nodeFloorY(seed, i, n + 1), z - n * GRID);
        }
        if (dz <= hz) {                                          // a canyon along X
            int n = floorDiv(x, GRID);
            return lerp(nodeFloorY(seed, n, j), nodeFloorY(seed, n + 1, j), x - n * GRID);
        }
        return -1;
    }

    /** The top solid block's Y of the column (a plateau terrace, or a canyon's floor). */
    public static int surfaceY(long seed, int x, int z) {
        int floor = canyonFloorY(seed, x, z);
        return floor >= 0 ? floor : terraceY(seed, x, z);
    }

    /** The oxide crust's thickness under the column's top block: CRUST_MIN..CRUST_MAX, smooth across the land. */
    public static int crustDepth(long seed, int x, int z) {
        double n = noise(seed).func_151601_a(x * CRUST_SCALE + CRUST_OFFSET, z * CRUST_SCALE - CRUST_OFFSET);
        int d = (int) Math.round((CRUST_MIN + CRUST_MAX) * 0.5D + n);
        return Math.max(CRUST_MIN, Math.min(CRUST_MAX, d));
    }

    /** The oxide colour at height `y`: 0..3 = violet, blue, gold, green (+ SubstrateBlocksSC.OXIDE_VIOLET = the meta). */
    public static int oxideBand(int y) {
        return floorDiv(y, BAND) & 3;
    }

    // ------------------------------------------------------------------ helpers

    private static synchronized NoiseGeneratorPerlin noise(long seed) {
        if (noise == null || noiseSeed != seed) {
            noise = new NoiseGeneratorPerlin(new Random(seed ^ NOISE_SALT), OCTAVES);
            noiseSeed = seed;
        }
        return noise;
    }

    /** a..b over one GRID, rounded: a canyon's floor `t` blocks past the node of floor a towards the node of floor b. */
    private static int lerp(int a, int b, int t) {
        return a + (int) Math.round((b - a) * (double) t / GRID);
    }

    /** Java 6: no Math.floorDiv. */
    static int floorDiv(int a, int b) {
        int q = a / b;
        return a % b != 0 && (a < 0) != (b < 0) ? q - 1 : q;
    }

    /** A well-mixed hash of the seed and two numbers (SplitMix64's finaliser). */
    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** 0..n-1 from a hash. */
    static int pick(long h, int n) {
        return (int) ((h >>> 1) % n);
    }
}
