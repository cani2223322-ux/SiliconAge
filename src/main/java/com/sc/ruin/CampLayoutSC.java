package com.sc.ruin;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.dim.WorldProviderSubstrateSC;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §8.1, rule ПЗ-1; proposal §3 «Лагерь Отступников | Каньоны»): where the Renegades'
 * camps stand - pure maths of the seed and the Scribe grid (the chunk generator, the carver, the spawn director, the self-test
 * ask the same thing). Written by the coordinator; package C may retune the numbers (TODO(balance)), not the contract.
 * <ul>
 * <li>a camp lies on the floor of a canyon segment - the canyon along Scribe line `line` (axis AXIS_X: z = line·256, the canyon
 * runs along X; AXIS_Z: x = line·256, along Z) between crossings `seg` and `seg + 1` - with CHANCE_PCT a segment;</li>
 * <li>its middle on the canyon's centre line, the segment's middle +-JITTER along it (so NODE_CLEAR and more from both
 * crossings: a Dead Node's zone never meets it); never within MIN_SPAWN_DIST of the world's spawn point;</li>
 * <li>its box HALF_LEN along the canyon and HALF_ACROSS across it (the canyon is 9..15 wide - the box reaches into the walls'
 * foot); the Stolen Field's dome FIELD_RADIUS round the middle, the stolen panels beyond it on the floor, inside the box;</li>
 * <li>an Exo Executioner guards it in EXECUTIONER_PCT of the camps further than EXECUTIONER_MIN_DIST from the spawn;
 * MARAUDERS_MIN..MAX Marauders and the Warlord in every one.</li>
 * </ul>
 */
public final class CampLayoutSC {

    /** The canyon's direction: along X (the line z = line·256) or along Z (x = line·256). */
    public static final int AXIS_X = 0, AXIS_Z = 1;
    /** ПЗ-1: the share of canyon segments with a camp (%), its jitter along the canyon, its box, its dome. TODO(balance) */
    public static final int CHANCE_PCT = 25, JITTER = 48, HALF_LEN = 22, HALF_ACROSS = 8, FIELD_RADIUS = 10;
    /** ПЗ-1: the crossings' clearance (a Node's zone is 24; the box adds HALF_LEN) and the spawn's. */
    public static final int NODE_CLEAR = 128 - JITTER - HALF_LEN, MIN_SPAWN_DIST = 400;
    /** ПЗ-6: the residents - Marauders and the Warlord; an Executioner in EXECUTIONER_PCT of the far camps. TODO(balance) */
    public static final int MARAUDERS_MIN = 4, MARAUDERS_MAX = 6, EXECUTIONER_PCT = 30, EXECUTIONER_MIN_DIST = 1000;

    private static final long SALT = 0xCA3F0414L;

    private CampLayoutSC() {
    }

    /** One camp's place. */
    public static final class Site {
        public final int axis, line, seg, x, z, floorY;
        public final long seed;
        public final boolean executioner;
        public final int marauders;

        Site(int axis, int line, int seg, int x, int z, int floorY, long seed, boolean executioner, int marauders) {
            this.axis = axis;
            this.line = line;
            this.seg = seg;
            this.x = x;
            this.z = z;
            this.floorY = floorY;
            this.seed = seed;
            this.executioner = executioner;
            this.marauders = marauders;
        }

        /** The column lies in the camp's box widened by `margin`. */
        public boolean holds(int bx, int bz, int margin) {
            int along = axis == AXIS_X ? bx - x : bz - z, across = axis == AXIS_X ? bz - z : bx - x;
            return Math.abs(along) <= HALF_LEN + margin && Math.abs(across) <= HALF_ACROSS + margin;
        }

        /** The box {minX, minZ, maxX, maxZ}. */
        public int[] box() {
            int hx = axis == AXIS_X ? HALF_LEN : HALF_ACROSS, hz = axis == AXIS_X ? HALF_ACROSS : HALF_LEN;
            return new int[]{x - hx, z - hz, x + hx, z + hz};
        }
    }

    /** ПЗ-1: the camp of the canyon segment (axis, line, seg), or null (pure). */
    public static Site site(long seed, int axis, int line, int seg) {
        long h = mix(seed ^ SALT, axis * 0x10001L + line, seg);
        if (pick(h, 100) >= CHANCE_PCT) {
            return null;
        }
        int grid = SubstrateTerrainSC.GRID;
        int along = seg * grid + grid / 2 + pick(mix(h, 1, 0), 2 * JITTER + 1) - JITTER;
        int x = axis == AXIS_X ? along : line * grid, z = axis == AXIS_X ? line * grid : along;
        double dx = x - WorldProviderSubstrateSC.SPAWN_X, dz = z - WorldProviderSubstrateSC.SPAWN_Z;
        double fromSpawn = Math.sqrt(dx * dx + dz * dz);
        if (fromSpawn < MIN_SPAWN_DIST) {
            return null;
        }
        int floor = SubstrateTerrainSC.canyonFloorY(seed, x, z);
        boolean exe = fromSpawn >= EXECUTIONER_MIN_DIST && pick(mix(h, 2, 0), 100) < EXECUTIONER_PCT;
        int marauders = MARAUDERS_MIN + pick(mix(h, 3, 0), MARAUDERS_MAX - MARAUDERS_MIN + 1);
        return new Site(axis, line, seg, x, z, floor, mix(h, 4, 0), exe, marauders);
    }

    /** ПЗ-1: the camp whose middle column lies in chunk (cx, cz) - its structure start's chunk - or null (pure). */
    public static Site siteAtChunk(long seed, int cx, int cz) {
        int g = SubstrateTerrainSC.GRID >> 4;                  // 16 chunks a cell
        if (((cz % g) + g) % g == 0) {                         // a line along X passes z = cz·16 (the chunk's first row)
            Site s = site(seed, AXIS_X, cz / g, floorDiv(cx << 4, SubstrateTerrainSC.GRID));
            if (s != null && s.x >> 4 == cx && s.z >> 4 == cz) {
                return s;
            }
        }
        if (((cx % g) + g) % g == 0) {
            Site s = site(seed, AXIS_Z, cx / g, floorDiv(cz << 4, SubstrateTerrainSC.GRID));
            if (s != null && s.x >> 4 == cx && s.z >> 4 == cz) {
                return s;
            }
        }
        return null;
    }

    /** The camp whose box, widened by `margin`, holds the column (x, z), or null (pure). */
    public static Site siteNear(long seed, int x, int z, int margin) {
        if (SubstrateTerrainSC.distanceToLine(z) <= HALF_ACROSS + margin) {
            Site s = site(seed, AXIS_X, SubstrateTerrainSC.lineIndex(z), floorDiv(x, SubstrateTerrainSC.GRID));
            if (s != null && s.holds(x, z, margin)) {
                return s;
            }
        }
        if (SubstrateTerrainSC.distanceToLine(x) <= HALF_ACROSS + margin) {
            Site s = site(seed, AXIS_Z, SubstrateTerrainSC.lineIndex(x), floorDiv(z, SubstrateTerrainSC.GRID));
            if (s != null && s.holds(x, z, margin)) {
                return s;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    static int floorDiv(int a, int b) {
        int q = a / b;
        return a % b != 0 && (a < 0) != (b < 0) ? q - 1 : q;
    }

    static long mix(long seed, long a, long b) {
        return RuinLayoutSC.mix(seed, a, b);
    }

    static int pick(long h, int n) {
        return RuinLayoutSC.pick(h, n);
    }
}
