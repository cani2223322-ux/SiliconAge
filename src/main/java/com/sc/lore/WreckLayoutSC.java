package com.sc.lore;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.dim.SubstrateCellsSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.gen.SettlementLayoutSC;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §6.2, rules ПЛ-3, ПЛ-4; proposal §3 «Обломки моста Литографов | Редко | Кольца из
 * Выгоревших катушек (дроп — Лом), около 30 блоков. Журналы»): where the Lithographers' bridge wrecks lie - pure maths of the seed and
 * the Scribe grid. Written by the coordinator; package J may retune the TODO(balance) numbers, not the contract.
 * <ul>
 * <li>a plateau cell (256 x 256, its pure kind PLATEAUS - so the ground is always Ф1's terraces, legacy or not), never the spawn's
 * cell (0, 0) and never a cell with a settlement's site, holds a wreck on CHANCE_PCT;</li>
 * <li>its middle stands OFFSET from the cell's middle towards one of its four corners (by the hash) - 52 blocks off the Scribe lines,
 * clear of the canyon belt, the arenas' and the Dead Nodes' boxes, a lithograph's entrance hut and the cell's middle;</li>
 * <li>the wreck: a ring of burnt coils (ruinSC meta BURNT_COIL - package J appends it) of radius RING_MIN..RING_MAX, tilted (one
 * half sunk into the terrace, the other raised up to TILT_MAX), broken in places, with burnt panels and a husk scattered round it,
 * HALF the box each way; one chest under the ring's raised side (WreckLootSC - a Journal №9 / №10, scrap, a component).</li>
 * </ul>
 */
public final class WreckLayoutSC {

    /** ПЛ-3: a free plateau cell's chance, the offset from the middle, the ring, its tilt, the box's half. TODO(balance) */
    public static final int CHANCE_PCT = 20, OFFSET = 76, RING_MIN = 9, RING_MAX = 12, TILT_MAX = 6, HALF = 14;

    private static final long SALT = 0x0B21D6E5L;

    private WreckLayoutSC() {
    }

    /** One wreck: its cell, the middle column and the terrace's top there, the ring's radius, its tilt axis (0: X, 1: Z) and lean, its seed. */
    public static final class Site {
        public final int cellX, cellZ, x, z, y, radius, axis, lean;
        public final long seed;

        public Site(int cellX, int cellZ, int x, int z, int y, int radius, int axis, int lean, long seed) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.z = z;
            this.y = y;
            this.radius = radius;
            this.axis = axis;
            this.lean = lean;
            this.seed = seed;
        }

        /** The column (bx, bz) lies in the wreck's box widened by `margin`. */
        public boolean holds(int bx, int bz, int margin) {
            return Math.abs(bx - x) <= HALF + margin && Math.abs(bz - z) <= HALF + margin;
        }
    }

    /** ПЛ-3: the wreck of plateau cell (cellX, cellZ), or null (pure). */
    public static Site site(long seed, int cellX, int cellZ) {
        if (cellX == 0 && cellZ == 0) {
            return null;
        }
        long h = mix(mix(seed ^ SALT, cellX, 1), cellZ, 2);     // not mix(seed, cellX, cellZ): that is the same for (-a, -b) when a, b are odd
        if (pick(h, 100) >= CHANCE_PCT || SubstrateCellsSC.pureKind(seed, cellX, cellZ) != BiomeSubstrateSC.PLATEAUS || settlementCell(seed, cellX, cellZ)) {
            return null;
        }
        int corner = pick(mix(h, 1, 0), 4);
        int x = cellX * SubstrateTerrainSC.GRID + SubstrateTerrainSC.GRID / 2 + (corner % 2 == 0 ? OFFSET : -OFFSET);
        int z = cellZ * SubstrateTerrainSC.GRID + SubstrateTerrainSC.GRID / 2 + (corner / 2 == 0 ? OFFSET : -OFFSET);
        int radius = RING_MIN + pick(mix(h, 2, 0), RING_MAX - RING_MIN + 1);
        int axis = pick(mix(h, 3, 0), 2), lean = 2 + pick(mix(h, 4, 0), TILT_MAX - 1);
        return new Site(cellX, cellZ, x, z, SubstrateTerrainSC.terraceY(seed, x, z), radius, axis, lean, mix(h, 5, 0));
    }

    /** The wreck whose start chunk (its middle column's) is (cx, cz), or null (pure; cheap first). */
    public static Site siteAtChunk(long seed, int cx, int cz) {
        Site s = site(seed, cx >> 4, cz >> 4);
        return s != null && s.x >> 4 == cx && s.z >> 4 == cz ? s : null;
    }

    /** The wreck whose box, widened by `margin`, holds the column (x, z), or null (pure; only the column's own cell can). */
    public static Site siteNear(long seed, int x, int z, int margin) {
        int lx = Math.abs((x & (SubstrateTerrainSC.GRID - 1)) - SubstrateTerrainSC.GRID / 2), lz = Math.abs((z & (SubstrateTerrainSC.GRID - 1)) - SubstrateTerrainSC.GRID / 2);
        if (Math.abs(lx - OFFSET) > HALF + margin || Math.abs(lz - OFFSET) > HALF + margin) {
            return null;                                       // cheap first: asked for many columns while a chunk is made
        }
        Site s = site(seed, x >> 8, z >> 8);
        return s != null && s.holds(x, z, margin) ? s : null;
    }

    /** A settlement's site (its region's) has its middle in this cell (as RuinLayoutSC's). */
    private static boolean settlementCell(long seed, int cellX, int cellZ) {
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(seed, cellX >> 1, cellZ >> 1);
        return s != null && s.x >> 8 == cellX && s.z >> 8 == cellZ;
    }

    static long mix(long seed, long a, long b) {
        long h = seed ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    static int pick(long h, int n) {
        return (int) ((h >>> 1) % n);
    }
}
