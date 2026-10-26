package com.sc.ruin;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.gen.SettlementLayoutSC;

/**
 * ПД-8 (docs/plan-ideas-2026-10-26.md, «Тоннели под плато», ★ A): where the copper busbar tunnels run - pure maths of the world seed
 * and the Scribe grid (the chunk generator, the self-test and the handbook ask the same thing), the coordinator's names and signatures,
 * ПД-8's bodies and TODO(balance) numbers.
 * <ul>
 * <li>a plateau cell (256 x 256 between the Scribe lines) has a tunnel along X and / or along Z with CHANCE_PCT each (never the spawn's
 * cell, never a Rift cell, never a cell with a settlement's site or a lithograph - their foundations and hall), straight from the canyon's
 * wall on one side to the opposite wall: WIDTH x HEIGHT inside (5 x 4), its floor at FLOOR_MIN..FLOOR_MAX (Y 22..28, by the cell's seed),
 * ramping (one block in RAMP) near each end to meet the canyon's floor there (SubstrateTerrainSC.canyonFloorY) - the entrance opens in the
 * canyon's wall;</li>
 * <li>two tunnels of one cell cross at a junction (JUNCTION x JUNCTION, a lamp column, a chest); a chest every CHEST_EVERY blocks in a
 * side niche (RuinLootSC's), a mimic (EntityMimicSC, disguised) at MIMIC_PCT of the niches;</li>
 * <li>a cell's start sits at the cell's middle chunk (the whole tunnel within 8 chunks of it - MapGenBase's reach: no cascade); a start
 * whose box was explored before is EMPTY (StructureRuinStartSC.explored - «только на ещё не открытой земле»).</li>
 * </ul>
 */
public final class TunnelLayoutSC {

    /** The tunnel's inside (across x up), its floor's band, the ramp's run a block of rise. TODO(balance) */
    public static final int WIDTH = 5, HEIGHT = 4, FLOOR_MIN = 22, FLOOR_MAX = 28, RAMP = 3;
    /** A tunnel along each axis of a cell with this chance (%); a junction's size; a chest's spacing; a mimic's share of the niches. TODO(balance) */
    public static final int CHANCE_PCT = 35, JUNCTION = 9, CHEST_EVERY = 48, MIMIC_PCT = 30;
    /** A cell a side (the Scribe grid). */
    public static final int CELL = SubstrateTerrainSC.GRID;
    /** ПД-8: the line's shift across from the cell's middle (+-), a segment's length at most, a camp this near shuts an end. TODO(balance) */
    public static final int SHIFT = 48, SEGMENT = 64, CAMP_GAP = 16;
    /** The inside's half width (5 wide: -2..+2), the walls at +-WALL. */
    public static final int HALF = WIDTH / 2, WALL = HALF + 1;

    private static final long SALT = 0x7E77E1L;

    private TunnelLayoutSC() {
    }

    /**
     * Pure: the cell (cx, cz) has a tunnel along X (`alongX`) or along Z - CHANCE_PCT by the cell's hash (each axis its own); never the
     * spawn's cell and its SPAWN_RING, a Rift cell, a cell with a settlement's site or a lithograph.
     */
    public static boolean has(long worldSeed, int cellX, int cellZ, boolean alongX) {
        if (Math.abs(cellX) <= SubstrateCellsSC.SPAWN_RING && Math.abs(cellZ) <= SubstrateCellsSC.SPAWN_RING) {
            return false;
        }
        if (pick(hash(worldSeed, cellX, cellZ, alongX ? 1 : 2), 100) >= CHANCE_PCT) {
            return false;
        }
        if (SubstrateCellsSC.pureKind(worldSeed, cellX, cellZ) == BiomeSubstrateSC.RIFT || RuinLayoutSC.lithoSite(worldSeed, cellX, cellZ) != null) {
            return false;
        }
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(worldSeed, cellX >> 1, cellZ >> 1);
        return s == null || (s.x >> 8) != cellX || (s.z >> 8) != cellZ;
    }

    /** Pure: the floor's Y in the cell's middle (FLOOR_MIN..FLOOR_MAX). */
    public static int floorY(long worldSeed, int cellX, int cellZ) {
        return FLOOR_MIN + pick(hash(worldSeed, cellX, cellZ, 3), FLOOR_MAX - FLOOR_MIN + 1);
    }

    /** Pure: the tunnel's line across - the cross coordinate (z of one along X, x of one along Z) of its middle (the cell's middle +-SHIFT). */
    public static int line(long worldSeed, int cellX, int cellZ, boolean alongX) {
        int base = (alongX ? cellZ : cellX) * CELL + CELL / 2;
        return base + pick(hash(worldSeed, cellX, cellZ, alongX ? 4 : 5), 2 * SHIFT + 1) - SHIFT;
    }

    /**
     * Pure: the tunnel's two ends along its axis {from, to} - each a block past the canyon's wall (the line's canyonHalfWidth + 1 from the
     * Scribe line: the entrance opens in the wall).
     */
    public static int[] ends(long worldSeed, int cellX, int cellZ, boolean alongX) {
        int c = alongX ? cellX : cellZ;
        long s = alongX ? worldSeed : ~worldSeed;
        return new int[]{c * CELL + SubstrateTerrainSC.canyonHalfWidth(s, c) + 1, (c + 1) * CELL - SubstrateTerrainSC.canyonHalfWidth(s, c + 1) - 1};
    }

    /** Pure: the canyon's floor at an end (the column just outside it, in the canyon), -1 none. */
    public static int endFloor(long worldSeed, int cellX, int cellZ, boolean alongX, boolean far) {
        int[] e = ends(worldSeed, cellX, cellZ, alongX);
        int t = far ? e[1] + 1 : e[0] - 1, across = line(worldSeed, cellX, cellZ, alongX);
        return alongX ? SubstrateTerrainSC.canyonFloorY(worldSeed, t, across) : SubstrateTerrainSC.canyonFloorY(worldSeed, across, t);
    }

    /**
     * Pure: the floor's Y (the block one stands on) at `t` along the tunnel - floorY in its body, ramping one block in RAMP from each end's
     * canyon floor (an end with no canyon floor: the body's).
     */
    public static int floorAt(long worldSeed, int cellX, int cellZ, boolean alongX, int t) {
        int f = floorY(worldSeed, cellX, cellZ);
        int[] e = ends(worldSeed, cellX, cellZ, alongX);
        int out = f;
        int a = endFloor(worldSeed, cellX, cellZ, alongX, false), b = endFloor(worldSeed, cellX, cellZ, alongX, true);
        if (a > 0) {
            out = ramp(f, a, t - e[0], out);
        }
        if (b > 0) {
            out = ramp(f, b, e[1] - t, out);
        }
        return out;
    }

    /** The ramp from `end` (at distance 0) to `body`: one block in RAMP; past it - `otherwise`. */
    private static int ramp(int body, int end, int dist, int otherwise) {
        int rise = Math.abs(end - body);
        if (dist < 0 || dist >= rise * RAMP) {
            return otherwise;
        }
        int step = dist / RAMP;
        return end > body ? end - step : end + step;
    }

    /** Pure: the end `far` of the tunnel is shut (a camp within CAMP_GAP of it - no opening there). */
    public static boolean endShut(long worldSeed, int cellX, int cellZ, boolean alongX, boolean far) {
        int[] e = ends(worldSeed, cellX, cellZ, alongX);
        int t = far ? e[1] : e[0], across = line(worldSeed, cellX, cellZ, alongX);
        return alongX ? CampLayoutSC.siteNear(worldSeed, t, across, CAMP_GAP) != null : CampLayoutSC.siteNear(worldSeed, across, t, CAMP_GAP) != null;
    }

    /** Pure: the junction's middle {x, z} of a cell with both tunnels, null without both. */
    public static int[] junction(long worldSeed, int cellX, int cellZ) {
        if (!has(worldSeed, cellX, cellZ, true) || !has(worldSeed, cellX, cellZ, false)) {
            return null;
        }
        return new int[]{line(worldSeed, cellX, cellZ, false), line(worldSeed, cellX, cellZ, true)};
    }

    /** A cell's own hash for `k`. */
    static long hash(long seed, int cellX, int cellZ, int k) {
        long h = seed ^ SALT ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL) ^ ((long) k << 48);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static int pick(long h, int n) {
        return (int) ((h & Long.MAX_VALUE) % n);
    }

    /** Pure: the chunk whose start holds cell (cellX, cellZ)'s tunnels - {cx, cz}: the cell's middle chunk. */
    public static int[] startChunk(int cellX, int cellZ) {
        int g = CELL >> 4;
        return new int[]{cellX * g + g / 2, cellZ * g + g / 2};
    }

    /** Pure: the cell whose start chunk is (cx, cz) - {cellX, cellZ} - or null (not a cell's middle chunk). */
    public static int[] cellAtChunk(int cx, int cz) {
        int g = CELL >> 4;
        if (((cx - g / 2) % g + g) % g != 0 || ((cz - g / 2) % g + g) % g != 0) {
            return null;
        }
        return new int[]{CampLayoutSC.floorDiv(cx, g), CampLayoutSC.floorDiv(cz, g)};
    }
}
