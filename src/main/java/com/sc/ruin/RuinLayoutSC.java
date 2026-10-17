package com.sc.ruin;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.settle.gen.SettlementLayoutSC;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.1, §8.4, rules ПУ-1, ПУ-4): where the ruins stand - pure maths of the world
 * seed and the Scribe grid, no World, so the chunk generator, SpawnDirectorSC (Shell patrols at the Dead Nodes), the caves'
 * carver and the self-test ask the same thing.
 * <ul>
 * <li>a Dead Node at every crossing of two Scribe lines (x = i·256, z = j·256): its zone is NODE_HALF blocks round the
 * crossing on both axes - the canyon belt's own width, so a Node never meets a settlement (they keep 35+ blocks off the
 * belt);</li>
 * <li>an abandoned lithograph in a plateau cell (256 x 256 between the lines, «сетка 16 x 16 чанков») with LITHO_CHANCE_PCT,
 * never in the spawn cell (0, 0) and never in a cell a settlement's site takes (SettlementLayoutSC.site of its region): the
 * hall HALL x HALL x HALL_H underground, its middle LITHO_JITTER at most from the cell's middle (so it stays 80+ blocks from
 * every canyon), its entrance on the surface over it.</li>
 * </ul>
 * A lithograph's hall: the box middle +-HALL_HALF on X and Z, from floorY (the floor's own block, FLOOR_MIN..FLOOR_MAX - the
 * metallization layer) up to floorY + HALL_H - 1 (the ceiling's block); at least ROCK_OVER blocks of rock between the ceiling
 * and the lowest terrace over the hall. The entrance: a shaft at (middle x, middle z + SHAFT_DZ) - the first row inside the
 * hall's north wall, its ladder on that wall - up to a HUT x HUT hut on the surface (its north wall in line with the hall's).
 * <p>
 * Signatures fixed (the coordinator's: nodeNear / nodeAtChunk have their bodies already); the lithographs' bodies are X's.
 */
public final class RuinLayoutSC {

    /** ПУ-1: a Dead Node's zone round its crossing, either axis (= SubstrateTerrainSC.CANYON_BELT). */
    public static final int NODE_HALF = SubstrateTerrainSC.CANYON_BELT;
    /** ПУ-4: a lithograph in a free plateau cell (%), its jitter from the cell's middle, the hall (25 x 25 x 9). TODO(balance) */
    public static final int LITHO_CHANCE_PCT = 30, LITHO_JITTER = 48, HALL = 25, HALL_H = 9;
    /** ПУ-5: the hall's half size (25 = 2 x 12 + 1); its floor's Y; the rock left over its ceiling at least. */
    public static final int HALL_HALF = HALL / 2, FLOOR_MIN = 30, FLOOR_MAX = 40, ROCK_OVER = 12;
    /** The entrance: the shaft's column (Z from the hall's middle: the row inside the north wall), the surface hut's size. */
    public static final int SHAFT_DZ = 1 - HALL_HALF, HUT = 5;

    /** A plateau cell a side (= the Scribe grid). */
    private static final int CELL = SubstrateTerrainSC.GRID;
    private static final long LITHO_SALT = 0x117A0614L, NODE_SALT = 0xDEAD70DEL;

    private RuinLayoutSC() {
    }

    /** ПУ-1: the crossing {i, j} whose Dead Node zone holds the column (x, z), or null (pure). */
    public static int[] nodeNear(int x, int z) {
        if (SubstrateTerrainSC.distanceToLine(x) > NODE_HALF || SubstrateTerrainSC.distanceToLine(z) > NODE_HALF) {
            return null;
        }
        return new int[]{SubstrateTerrainSC.lineIndex(x), SubstrateTerrainSC.lineIndex(z)};
    }

    /** ПУ-1: the crossing {i, j} whose centre column lies in chunk (cx, cz) - the chunk its structure start belongs to - or null. */
    public static int[] nodeAtChunk(int cx, int cz) {
        int g = SubstrateTerrainSC.GRID >> 4;                  // 16 chunks a cell
        if (((cx % g) + g) % g != 0 || ((cz % g) + g) % g != 0) {
            return null;
        }
        return new int[]{cx / g, cz / g};
    }

    /** A Dead Node's own seed (its pieces' layout, its chests) - crossing (i, j) (pure). */
    public static long nodeSeed(long worldSeed, int i, int j) {
        return mix(worldSeed ^ NODE_SALT, i, j);
    }

    /** One lithograph's place: its cell, the hall's middle column and its floor (Y), its own seed (loot, residents). */
    public static final class LithoSite {
        public final int cellX, cellZ, x, z, floorY;
        public final long seed;

        public LithoSite(int cellX, int cellZ, int x, int z, int floorY, long seed) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.x = x;
            this.z = z;
            this.floorY = floorY;
            this.seed = seed;
        }

        /** The hall's ceiling block (Y). */
        public int ceilingY() {
            return floorY + HALL_H - 1;
        }

        /** The shaft's column. */
        public int shaftX() {
            return x;
        }

        public int shaftZ() {
            return z + SHAFT_DZ;
        }

        /** The column (bx, bz) lies in the hall's box widened by `margin`. */
        public boolean hallHolds(int bx, int bz, int margin) {
            return Math.abs(bx - x) <= HALL_HALF + margin && Math.abs(bz - z) <= HALL_HALF + margin;
        }

        /** The hut's box {minX, minZ, maxX, maxZ}: HUT x HUT, its north wall in line with the hall's, the shaft in its north row. */
        public int[] hutBox() {
            int h = HUT / 2;
            return new int[]{x - h, z - HALL_HALF, x + h, z - HALL_HALF + HUT - 1};
        }
    }

    /** ПУ-4: the lithograph of plateau cell (cellX, cellZ) (cell n spans n·256 .. n·256 + 255), or null (pure). X's body. */
    public static LithoSite lithoSite(long worldSeed, int cellX, int cellZ) {
        long h = cellHash(worldSeed, cellX, cellZ);
        if (h == 0L || settlementCell(worldSeed, cellX, cellZ)) {
            return null;                                       // ПУ-4: not the spawn's cell, not a settlement's
        }
        int x = middle(h, cellX, 1), z = middle(h, cellZ, 2);
        int floor = FLOOR_MIN + pick(mix(h, 3, 0), FLOOR_MAX - FLOOR_MIN + 1);
        // ПУ-5: ROCK_OVER blocks of rock at least between the ceiling and the lowest terrace over the hall (the plateaus are
        // 64..74, so with the floor at 30..40 it always holds - kept honest should the numbers ever change)
        int low = SubstrateTerrainSC.PLATEAU_MAX;
        for (int i = -HALL_HALF; i <= HALL_HALF; i += 4) {
            for (int k = -HALL_HALF; k <= HALL_HALF; k += 4) {
                low = Math.min(low, SubstrateTerrainSC.terraceY(worldSeed, x + i, z + k));
            }
        }
        floor = Math.max(1, Math.min(floor, low - ROCK_OVER - HALL_H));
        return new LithoSite(cellX, cellZ, x, z, floor, mix(h, 4, 0));
    }

    /** ПУ-4: the lithograph whose start chunk (its hall's middle column) is (cx, cz), or null (pure). X's body. */
    public static LithoSite lithoAtChunk(long worldSeed, int cx, int cz) {
        int cellX = cx >> 4, cellZ = cz >> 4;                   // 16 chunks a cell
        long h = cellHash(worldSeed, cellX, cellZ);
        if (h == 0L || middle(h, cellX, 1) >> 4 != cx || middle(h, cellZ, 2) >> 4 != cz) {
            return null;                                       // cheap first: asked for every chunk round a new one
        }
        return lithoSite(worldSeed, cellX, cellZ);
    }

    /** ПУ-5: the point lies in a lithograph's hall (SpawnDirectorSC spawns nothing there; the carver cuts no via through it). X's body. */
    public static boolean inLitho(long worldSeed, int x, int y, int z) {
        LithoSite s = lithoNear(worldSeed, x, z, 0);
        return s != null && y >= s.floorY && y <= s.ceilingY();
    }

    /**
     * The lithograph whose hall's box, widened by `margin`, holds the column (x, z), or null (pure). Only the column's own
     * cell can have one (a hall keeps 68+ blocks off its cell's edges).
     */
    public static LithoSite lithoNear(long worldSeed, int x, int z, int margin) {
        return middleNear(worldSeed, x, z, margin) == null ? null : lithoSite(worldSeed, x >> 8, z >> 8);
    }

    /**
     * The cheap half of lithoNear: the hall's middle column {x, z} of the column's cell if its chance came up and the hall's
     * box widened by `margin` holds the column - a candidate only (a settlement's cell has none: lithoSite says). For the
     * generator's structure map, which has a start only where lithoSite has a site (pure).
     */
    public static int[] middleNear(long worldSeed, int x, int z, int margin) {
        int cellX = x >> 8, cellZ = z >> 8;                    // a cell = 256
        long h = cellHash(worldSeed, cellX, cellZ);
        if (h == 0L) {
            return null;
        }
        int mx = middle(h, cellX, 1), mz = middle(h, cellZ, 2);
        return Math.abs(x - mx) > HALL_HALF + margin || Math.abs(z - mz) > HALL_HALF + margin ? null : new int[]{mx, mz};
    }

    // ------------------------------------------------------------------ helpers

    /** The cell's hash if its chance came up (and it isn't the spawn's cell), else 0 - the settlement check is lithoSite's. */
    private static long cellHash(long worldSeed, int cellX, int cellZ) {
        if (cellX == 0 && cellZ == 0) {
            return 0L;                                         // the world's spawn (128, 128) stands in cell (0, 0)
        }
        long h = mix(worldSeed ^ LITHO_SALT, cellX, cellZ);
        return pick(h, 100) < LITHO_CHANCE_PCT ? h | 1L : 0L;
    }

    /** The hall's middle on one axis: the cell's middle +-LITHO_JITTER. */
    private static int middle(long h, int cell, int salt) {
        return cell * CELL + CELL / 2 + pick(mix(h, salt, 7), 2 * LITHO_JITTER + 1) - LITHO_JITTER;
    }

    /** A settlement's site (its region's, if any) has its middle in this cell. */
    private static boolean settlementCell(long worldSeed, int cellX, int cellZ) {
        SettlementLayoutSC.Site s = SettlementLayoutSC.site(worldSeed, cellX >> 1, cellZ >> 1);   // a region = 2 x 2 cells
        return s != null && s.x >> 8 == cellX && s.z >> 8 == cellZ;
    }

    /** A well-mixed hash of the seed and two numbers (SplitMix64's finaliser, as SubstrateTerrainSC's). */
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
