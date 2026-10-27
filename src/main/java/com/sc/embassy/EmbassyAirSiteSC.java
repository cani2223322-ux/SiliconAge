package com.sc.embassy;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.biome.BiomeGenMutated;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraftforge.common.BiomeDictionary;

/**
 * PACKAGE G (docs/plan-embassy-airfield-core.md §3.2, rule ЛП-1, docs/idea-embassy-airfield.md §4 «Выбор варианта»): which airfield a NEW
 * embassy gets - decided once, in MapGenEmbassySC.getStructureStart through EmbassyAirfieldSC.decideFor (embassy.airfield allowed it),
 * by the biome layers only (the site's WorldChunkManager, as EmbassySiteSC.biomeProblem: no chunk is loaded or made; the same answer on
 * every run for the same seed and the same set of mods). The embassy's site rules are not touched: the field must fit the land the
 * embassy already got.
 * <ol>
 * <li>FULL on the side by the seed (EmbassyAirfieldSC.sideOf): the field's level rectangles (Field.level) + 16 and each end's approach out
 * to FUNNEL_A + 150 (300 past the threshold) along the axis, FUNNEL_HALF + 5 across: no OCEAN, BEACH, SWAMP, MOUNTAIN type; rootHeight
 * under EmbassySiteSC.EDGE_ROOT; HILLS at most EDGE_PCT %; heightVariation under SHATTERED_VAR; in an approach FOREST at most
 * APPROACH_FOREST_PCT % (the review's: an approach may cross water and hold APPROACH_HILLS_PCT % of hills). An end whose approach fails - or whose funnel's chunks lie on disk already (EmbassySiteSC.chunkOnDisk, as
 * explored(): its growth could not be cleared) - has no ILS (its bit off: a visual approach only); both ends failing the biome rules -
 * no FULL on that side;</li>
 * <li>FULL on the other side (the same rules);</li>
 * <li>SHORT - inside today's level disc (always fits the land the embassy got): a runway 90 x 7, the helipad;</li>
 * <li>HELI - never needed by the rules above (SHORT always fits); kept for a later rule.</li>
 * </ol>
 * Fixed (the coordinator's): the class, choose's signature and answer. The body is package G's.
 * <p>
 * G's reading of the rules (as the site's rim rule, EmbassySiteSC.scan, which these numbers are): the land is read at the layers'
 * quarter resolution, one sample a 4 x 4 cell (its middle). A level rectangle's samples are those within LEVEL_MARGIN of any of the
 * field's level rectangles - but not inside the disc's rim (radius + LEVEL_OUT - EDGE_IN: the site's own rules judged it, and all of it
 * is cut to the floor); an approach's are those of its corridor (Field.approaches) off the level shape (Field.onShape: cut flat anyway).
 * Each sample (tally / count) is: water (OCEAN, BEACH, SWAMP), MOUNTAIN, a plateau (rootHeight of HIGH_ROOT - the savanna's and the
 * mesa's: Forge calls them PLAINS / SANDY), shattered (heightVariation of SHATTERED_VAR), hilly (HILLS, or rootHeight of EDGE_ROOT - the
 * site's rim count), FOREST; a vanilla mutation by its base biome's Forge types (EmbassySiteSC's), its own heights. One water, mountain,
 * plateau or shattered sample fails the part (levelProblem / approachProblem); hilly at most LEVEL_HILLS_PCT / APPROACH_HILLS_PCT
 * (EDGE_PCT) % of it, an approach's FOREST at most APPROACH_FOREST_PCT %. The decision itself is pure (decide, fullFits, fullIls) - the
 * self-test's on synthetic answers. Server thread (WorldChunkManager's IntCache), as EmbassySiteSC.
 */
public final class EmbassyAirSiteSC {

    /** An approach may hold this much FOREST (the trees over the glide path past a = +-FUNNEL_A). TODO(balance) */
    public static final int APPROACH_FOREST_PCT = 30;
    /** The approach's check reaches this far past the threshold (the neutral corridor's length). */
    public static final int APPROACH_OUT = EmbassyAirfieldSC.NEUTRAL_OUT;
    /** The level rectangles' check reaches this far past them (EmbassySiteSC.BAND_NEAR: the blend band's land must not stand as a wall). */
    public static final int LEVEL_MARGIN = EmbassySiteSC.BAND_NEAR;
    /**
     * Hilly samples a level rectangles' / an approach's tally may hold (%) and whether an approach may cross water (OCEAN, BEACH, SWAMP).
     * The level rectangles keep the site's rim rule (EmbassySiteSC.EDGE_PCT 10: more hills would stand as walls in the blend band); the
     * review's: an approach may cross water (it is no obstacle under a glide path) and hold APPROACH_HILLS_PCT 20 % of hills (the 1:20
     * plane rises over them). TODO(balance): G's measure (12 seeds x 12 sites by EmbassySiteSC.biomeProblem) - the plan's rules (10 %, no
     * water) FULL 37 %, both ILS ends 5 %; water allowed under the approaches - 49 %; and hills 20 % in both - 61 % (the plan's target
     * >= 60 %); these - the self-test's INFO line (SelfTestAirfieldSC.fullShare).
     */
    public static final int LEVEL_HILLS_PCT = EmbassySiteSC.EDGE_PCT, APPROACH_HILLS_PCT = 20;
    public static final boolean APPROACH_WATER = true;

    /** A sample's facts (flags): water (OCEAN, BEACH, SWAMP), MOUNTAIN, a plateau, shattered land, hilly, FOREST. */
    public static final int F_WATER = 1, F_MOUNTAIN = 2, F_HIGH = 4, F_SHATTERED = 8, F_HILLY = 16, F_FOREST = 32;
    /** A tally's counts (count): the samples, then one count a flag in F_*'s order. */
    public static final int T_SAMPLES = 0, T_WATER = 1, T_MOUNTAIN = 2, T_HIGH = 3, T_SHATTERED = 4, T_HILLY = 5, T_FOREST = 6, T_SIZE = 7;
    /** The ends (Field.approaches / funnels index; EmbassyAirfieldSC.ILS_LOW / ILS_HIGH): the -a end's, the +a end's. */
    public static final int END_LOW = 0, END_HIGH = 1;

    private static final BiomeDictionary.Type[] WATER = {BiomeDictionary.Type.OCEAN, BiomeDictionary.Type.BEACH, BiomeDictionary.Type.SWAMP};

    private EmbassyAirSiteSC() {
    }

    /**
     * {kind (EmbassyAirfieldSC.HELI / SHORT / FULL), side (+1 / -1), ils bits (ILS_LOW | ILS_HIGH; FULL only)} for a new embassy at
     * `site` of server world `w` (null: none fits - the record gets NONE). Reads the biome layers only (and the region files' headers
     * for the funnels: chunkOnDisk - no file is made). Server.
     */
    public static int[] choose(World w, EmbassySiteSC.Site site) {
        return choose(w == null ? null : w.getWorldChunkManager(), w instanceof WorldServer ? (WorldServer) w : null, site);
    }

    /**
     * choose on the biome layers `m` (null: SHORT) of server world `ws` (its region files for the funnels; null - none on disk: the
     * self-test's measure on other seeds' layers, SelfTestAirfieldSC.fullShare).
     */
    public static int[] choose(WorldChunkManager m, WorldServer ws, EmbassySiteSC.Site site) {
        if (site == null) {
            return null;
        }
        int first = EmbassyAirfieldSC.sideOf(site.seed);
        if (m == null) {
            return new int[]{EmbassyAirfieldSC.SHORT, first, 0};
        }
        for (int k = 0; k < 2; k++) {
            EmbassyAirfieldSC.Field f = fullField(site, k == 0 ? first : -first);
            if (levelProblem(levelTally(m, f)) != null) {
                continue;                                       // the field's own ground doesn't fit: no FULL on this side
            }
            boolean low = approachProblem(approachTally(m, f, END_LOW)) == null;
            boolean high = approachProblem(approachTally(m, f, END_HIGH)) == null;
            if (!fullFits(true, low, high)) {
                continue;                                       // both approaches bad: no FULL on this side
            }
            boolean lowDisk = low && ws != null && funnelOnDisk(ws, f, END_LOW);
            boolean highDisk = high && ws != null && funnelOnDisk(ws, f, END_HIGH);
            return new int[]{EmbassyAirfieldSC.FULL, f.side, fullIls(low, high, lowDisk, highDisk)};
        }
        return new int[]{EmbassyAirfieldSC.SHORT, first, 0};
    }

    /** The FULL field `site` would have on `side` (both ILS ends: the check's) - the same frame the plan gives (EmbassyLayoutSC.gateDir). Pure. */
    public static EmbassyAirfieldSC.Field fullField(EmbassySiteSC.Site site, int side) {
        return EmbassyAirfieldSC.Field.of(site.x, site.z, site.radius, site.floorY, EmbassyLayoutSC.gateDir(site.seed), EmbassyAirfieldSC.FULL,
                side, EmbassyAirfieldSC.ILS_LOW | EmbassyAirfieldSC.ILS_HIGH, EmbassyAirfieldSC.VERSION);
    }

    // ================================================================== the decision (pure: the self-test's)

    /**
     * The variant by the checks' answers (pure - choose's order on synthetic answers): `firstSide` the seed's side; index 0 that side,
     * 1 the other: `levelOk[s]` - its level rectangles fit; `approachOk[s][END_LOW / END_HIGH]` - that end's approach fits;
     * `onDisk[s][end]` - that end's funnel chunks are on disk. FULL on the first side that fits (fullFits; its ILS bits - fullIls), else
     * SHORT on the first side. {kind, side, ils}.
     */
    public static int[] decide(int firstSide, boolean[] levelOk, boolean[][] approachOk, boolean[][] onDisk) {
        int first = firstSide < 0 ? -1 : 1;
        for (int s = 0; s < 2; s++) {
            if (fullFits(levelOk[s], approachOk[s][END_LOW], approachOk[s][END_HIGH])) {
                return new int[]{EmbassyAirfieldSC.FULL, s == 0 ? first : -first,
                        fullIls(approachOk[s][END_LOW], approachOk[s][END_HIGH], onDisk[s][END_LOW], onDisk[s][END_HIGH])};
            }
        }
        return new int[]{EmbassyAirfieldSC.SHORT, first, 0};
    }

    /** FULL stands on a side: its level rectangles fit and at least one end's approach does. Pure. */
    public static boolean fullFits(boolean levelOk, boolean lowOk, boolean highOk) {
        return levelOk && (lowOk || highOk);
    }

    /** A FULL field's ILS bits: an end whose approach fits and whose funnel's chunks are not on disk yet (its growth can be cleared). Pure. */
    public static int fullIls(boolean lowOk, boolean highOk, boolean lowOnDisk, boolean highOnDisk) {
        return (lowOk && !lowOnDisk ? EmbassyAirfieldSC.ILS_LOW : 0) | (highOk && !highOnDisk ? EmbassyAirfieldSC.ILS_HIGH : 0);
    }

    /** One sample's facts (F_*) from its biome's Forge types and heights (see the class comment). Pure. */
    public static int flags(boolean water, boolean mountain, boolean hills, boolean forest, float rootHeight, float heightVariation) {
        int f = water ? F_WATER : 0;
        f |= mountain ? F_MOUNTAIN : 0;
        f |= rootHeight >= EmbassySiteSC.HIGH_ROOT ? F_HIGH : 0;
        f |= heightVariation >= EmbassySiteSC.SHATTERED_VAR ? F_SHATTERED : 0;
        f |= hills || rootHeight >= EmbassySiteSC.EDGE_ROOT ? F_HILLY : 0;
        f |= forest ? F_FOREST : 0;
        return f;
    }

    /** A new tally (T_SIZE counts, all 0). */
    public static int[] tally() {
        return new int[T_SIZE];
    }

    /** One sample of facts `flags` into tally `t`. Pure. */
    public static void count(int[] t, int flags) {
        t[T_SAMPLES]++;
        for (int i = 0; i < T_SIZE - 1; i++) {
            t[T_WATER + i] += (flags & (1 << i)) != 0 ? 1 : 0;
        }
    }

    /** What breaks the rules of a level rectangles' tally (null: it fits) - see the class comment. Pure. */
    public static String levelProblem(int[] t) {
        return problem(t, true, LEVEL_HILLS_PCT, -1);
    }

    /** What breaks the rules of an approach's tally (null: it fits): the level rules and FOREST at most APPROACH_FOREST_PCT %. Pure. */
    public static String approachProblem(int[] t) {
        return problem(t, !APPROACH_WATER, APPROACH_HILLS_PCT, APPROACH_FOREST_PCT);
    }

    /** A tally against the rules: no water (`noWater`), mountain, plateau or shattered sample; hilly at most `hillsPct` %, FOREST `forestPct` % (-1: any). */
    private static String problem(int[] t, boolean noWater, int hillsPct, int forestPct) {
        int n = t[T_SAMPLES];
        if (n <= 0) {
            return "no biome read";
        }
        if (noWater && t[T_WATER] > 0) {
            return "water (ocean / beach / swamp) x" + t[T_WATER];
        }
        if (t[T_MOUNTAIN] > 0) {
            return "mountains x" + t[T_MOUNTAIN];
        }
        if (t[T_HIGH] > 0) {
            return "plateau x" + t[T_HIGH];
        }
        if (t[T_SHATTERED] > 0) {
            return "shattered land x" + t[T_SHATTERED];
        }
        if (t[T_HILLY] * 100 > hillsPct * n) {
            return "hills " + t[T_HILLY] * 100 / n + "% > " + hillsPct + "%";
        }
        if (forestPct >= 0 && t[T_FOREST] * 100 > forestPct * n) {
            return "forest " + t[T_FOREST] * 100 / n + "% > " + forestPct + "%";
        }
        return null;
    }

    // ================================================================== the land (the biome layers - no chunk loaded)

    /** One biome's facts (F_*): a vanilla mutation (id + 128) by its base biome's Forge types (Forge guesses the mutations' own), its own heights. */
    public static int flagsOf(BiomeGenBase g) {
        BiomeGenBase t = typed(g);
        boolean water = false;
        for (BiomeDictionary.Type w : WATER) {
            water |= BiomeDictionary.isBiomeOfType(t, w);
        }
        return flags(water, BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.MOUNTAIN), BiomeDictionary.isBiomeOfType(t,
                BiomeDictionary.Type.HILLS), BiomeDictionary.isBiomeOfType(t, BiomeDictionary.Type.FOREST), g.rootHeight, g.heightVariation);
    }

    /** The tally of field `f`'s level rectangles + LEVEL_MARGIN (not inside the disc's rim - see the class comment). Server thread. */
    public static int[] levelTally(WorldChunkManager m, EmbassyAirfieldSC.Field f) {
        int[][] boxes = new int[f.level.length][];
        for (int i = 0; i < boxes.length; i++) {
            boxes[i] = f.world(EmbassyAirfieldSC.grow(f.level[i], LEVEL_MARGIN));
        }
        return read(m, f, boxes, f.radius + EmbassyLayoutSC.LEVEL_OUT - EmbassySiteSC.EDGE_IN, false);
    }

    /** The tally of field `f`'s approach `end` (END_LOW / END_HIGH: Field.approaches), off its level shape. Server thread. */
    public static int[] approachTally(WorldChunkManager m, EmbassyAirfieldSC.Field f, int end) {
        return read(m, f, new int[][]{f.world(f.approaches[end])}, 0, true);
    }

    /**
     * The samples of the world boxes `boxes` ({minX, minZ, maxX, maxZ}) - one read of their union - into a tally: past `rim` from the
     * core (0: every one), off the level shape (`offShape`). Server thread.
     */
    private static int[] read(WorldChunkManager m, EmbassyAirfieldSC.Field f, int[][] boxes, int rim, boolean offShape) {
        int[] t = tally();
        if (boxes.length == 0) {
            return t;
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] b : boxes) {
            minX = Math.min(minX, b[0]);
            minZ = Math.min(minZ, b[1]);
            maxX = Math.max(maxX, b[2]);
            maxZ = Math.max(maxZ, b[3]);
        }
        int q0x = minX >> 2, q0z = minZ >> 2, nx = (maxX >> 2) - q0x + 1, nz = (maxZ >> 2) - q0z + 1;
        BiomeGenBase[] b = m.getBiomesForGeneration(null, q0x, q0z, nx, nz);
        long rim2 = (long) rim * rim;
        for (int j = 0; j < nz; j++) {
            for (int i = 0; i < nx; i++) {
                BiomeGenBase g = b[i + j * nx];
                int x = ((q0x + i) << 2) + 2, z = ((q0z + j) << 2) + 2;     // the cell's middle
                if (g == null || !inAny(boxes, x, z)) {
                    continue;
                }
                long dx = x - f.coreX, dz = z - f.coreZ;
                if (rim > 0 && dx * dx + dz * dz < rim2 || offShape && f.onShape(x, z)) {
                    continue;
                }
                count(t, flagsOf(g));
            }
        }
        return t;
    }

    /** Some chunk of field `f`'s funnel `end` (Field.funnels) is loaded or saved (EmbassySiteSC.chunkOnDisk: no region file is made). Server. */
    public static boolean funnelOnDisk(WorldServer w, EmbassyAirfieldSC.Field f, int end) {
        if (end < 0 || end >= f.funnels.length) {
            return false;
        }
        int[] b = f.world(f.funnels[end]);
        for (int cx = b[0] >> 4; cx <= b[2] >> 4; cx++) {
            for (int cz = b[1] >> 4; cz <= b[3] >> 4; cz++) {
                if (EmbassySiteSC.chunkOnDisk(w, cx, cz)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inAny(int[][] boxes, int x, int z) {
        for (int[] b : boxes) {
            if (x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3]) {
                return true;
            }
        }
        return false;
    }

    /** The biome whose Forge types count: a vanilla mutation's (id + 128) base (EmbassySiteSC's rule). */
    private static BiomeGenBase typed(BiomeGenBase g) {
        if (g instanceof BiomeGenMutated && g.biomeID >= 128) {
            BiomeGenBase base = BiomeGenBase.getBiomeGenArray()[g.biomeID - 128];
            return base != null ? base : g;
        }
        return g;
    }
}
