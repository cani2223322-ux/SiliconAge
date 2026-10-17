package com.sc.arena;

import com.sc.dim.SubstrateTerrainSC;
import com.sc.ruin.VaultLayoutSC;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §4.1, rules ПУ-8..ПУ-10; proposal §3 «Эпицентр Пробоя | Один на регион 4096×4096 |
 * Арена босса», §9 «огромная руина на пересечении главных Скрайб-линий»): where the Breakdown Epicentres are and what they are made
 * of - pure maths of the seed and the Scribe grid. Written by the coordinator: package A builds by it (MapGenArenaSC, the carve, the
 * pieces), package B fights in it (the coils' pedestals, the columns), the Arena Coordinator points by it, the self-test checks it.
 * A may retune the TODO(balance) numbers, not the contract.
 * <ul>
 * <li>a region is REGION x REGION crossings (4096 x 4096 blocks); its arena stands at one crossing - the region's «main lines» -
 * MARGIN..MARGIN + SPAN - 1 lines into it on each axis (so two arenas are 2304+ blocks apart), by the hash; the spawn's region
 * (0, 0) only SPAWN_REGION_FROM.. on both (2 km+ from the spawn); never a crossing with a Vault under it (VaultLayoutSC: the
 * bunker would sit under the pit) - TRIES picks, else the region has none;</li>
 * <li>the arena replaces that crossing's Dead Node (MapGenDeadNodeSC skips the crossing); its pit is carved in the chunk's arrays
 * (provideChunk - no setBlock, the light by the chunk's own skylight map) around the crossing: the floor's disk PIT_R at the
 * crossing's floor (SubstrateTerrainSC.nodeFloorY - the canyons run into it level), a plated wall ring to RIM_R, WALL_H high,
 * the terrain above it cut back to the sky; the box HALF each way - within the Ф1 strip (40 from the lines: the cells' new
 * biomes never reach it), 4 x 4 chunks round the crossing's corner;</li>
 * <li>inside: the Breakdown Console (CONSOLE), four coil pedestals on the diagonals (COIL_R), eight 2 x 2 columns COLUMN_H high
 * between the canyons' mouths and the diagonals (COLUMN_R) - the cover from the «сброс ядра» and the «Импульс глушения» -, lamps
 * on the wall.</li>
 * </ul>
 * Offsets ({dx, dz}) are from the crossing (x = i·256, z = j·256); the floor is floorY (its own block), the arena stands on
 * floorY + 1.
 */
public final class ArenaLayoutSC {

    /** ПУ-8: crossings a region's side; where in it the arena's crossing may be; the spawn's region; picks. TODO(balance) */
    public static final int REGION = 16, MARGIN = 4, SPAN = 8, SPAWN_REGION_FROM = 6, TRIES = 8;
    /** ПУ-9: the floor's disk, the wall ring (the box's half), the plated wall's height over the floor. */
    public static final int PIT_R = 28, RIM_R = 30, HALF = RIM_R, WALL_H = 12;
    /** ПУ-10: the columns (2 x 2, at 22.5° + k·45°), the coils' pedestals (the diagonals), the console, the lamps. TODO(balance) */
    public static final int COLUMNS = 8, COLUMN_R = 17, COLUMN_H = 7, COILS = 4, COIL_R = 22, LAMPS = 8, LAMP_R = 27;
    /** The console's offset (north-north-east of the middle, off the north canyon's mouth). */
    public static final int CONSOLE_DX = 12, CONSOLE_DZ = -21;

    private static final long SALT = 0xA7E4A5C1L;

    private ArenaLayoutSC() {
    }

    /** The region of crossing line `i`. */
    public static int regionOf(int i) {
        return floorDiv(i, REGION);
    }

    /** ПУ-8: the crossing {i, j} of region (ri, rj)'s arena, or null (pure). */
    public static int[] arenaOf(long seed, int ri, int rj) {
        boolean spawn = ri == 0 && rj == 0;
        int from = spawn ? SPAWN_REGION_FROM : MARGIN, span = MARGIN + SPAN - from;
        long h = mix(seed ^ SALT, ri, rj);
        for (int t = 0; t < TRIES; t++) {
            long ht = mix(h, t, 7);
            int i = ri * REGION + from + pick(ht, span), j = rj * REGION + from + pick(mix(ht, 1, 3), span);
            if (!VaultLayoutSC.hasVault(seed, i, j)) {
                return new int[]{i, j};
            }
        }
        return null;
    }

    /** ПУ-8: crossing (i, j) is its region's arena (pure). */
    public static boolean isArena(long seed, int i, int j) {
        int[] a = arenaOf(seed, regionOf(i), regionOf(j));
        return a != null && a[0] == i && a[1] == j;
    }

    /** The arena whose start chunk (its crossing's corner chunk: i·16, j·16) is (cx, cz), or null (pure; cheap first). */
    public static int[] arenaAtChunk(long seed, int cx, int cz) {
        int g = SubstrateTerrainSC.GRID >> 4;
        if (((cx % g) + g) % g != 0 || ((cz % g) + g) % g != 0) {
            return null;
        }
        int i = cx / g, j = cz / g;
        return isArena(seed, i, j) ? new int[]{i, j} : null;
    }

    /** The arena crossing {i, j} whose box, widened by `margin`, holds the column (x, z), or null (pure). */
    public static int[] arenaNear(long seed, int x, int z, int margin) {
        if (SubstrateTerrainSC.distanceToLine(x) > HALF + margin || SubstrateTerrainSC.distanceToLine(z) > HALF + margin) {
            return null;
        }
        int i = SubstrateTerrainSC.lineIndex(x), j = SubstrateTerrainSC.lineIndex(z);
        return isArena(seed, i, j) ? new int[]{i, j} : null;
    }

    /** ПУ-9: the arena's floor (its own block) - the crossing's floor, as a Dead Node's plaza. */
    public static int floorY(long seed, int i, int j) {
        return SubstrateTerrainSC.nodeFloorY(seed, i, j);
    }

    /** The crossing's world x (or z) of line `i`. */
    public static int centre(int i) {
        return i * SubstrateTerrainSC.GRID;
    }

    /** ПУ-9: the offset lies on the floor's disk. */
    public static boolean inPit(int dx, int dz) {
        return dx * dx + dz * dz <= PIT_R * PIT_R;
    }

    /** ПУ-9: the offset lies in the wall ring (out of the disk, within RIM_R). */
    public static boolean onRim(int dx, int dz) {
        int d2 = dx * dx + dz * dz;
        return d2 > PIT_R * PIT_R && d2 <= RIM_R * RIM_R;
    }

    /** ПУ-10: coil `k`'s pedestal (0..3: NE, SE, SW, NW) - the coil stands on it, at floorY + 2. */
    public static int[] coilSpot(int k) {
        int d = (int) Math.round(COIL_R / Math.sqrt(2D));
        int[][] s = {{d, -d}, {d, d}, {-d, d}, {-d, -d}};
        return s[((k % COILS) + COILS) % COILS].clone();
    }

    /** ПУ-10: column `k`'s north-west block (0..7, clockwise from 22.5° east of north); the column is 2 x 2. */
    public static int[] columnSpot(int k) {
        double a = Math.toRadians(22.5D + 45D * (((k % COLUMNS) + COLUMNS) % COLUMNS));
        return new int[]{(int) Math.round(Math.sin(a) * COLUMN_R) - 1, (int) Math.round(-Math.cos(a) * COLUMN_R) - 1};
    }

    /** ПУ-10: lamp `k` on the wall (0..7, as the columns' angles). */
    public static int[] lampSpot(int k) {
        double a = Math.toRadians(22.5D + 45D * (((k % LAMPS) + LAMPS) % LAMPS));
        return new int[]{(int) Math.round(Math.sin(a) * LAMP_R), (int) Math.round(-Math.cos(a) * LAMP_R)};
    }

    /** The offset is a column's block (any of the eight 2 x 2s) - the line-of-sight checks may ask it as well as the world. */
    public static boolean inColumn(int dx, int dz) {
        for (int k = 0; k < COLUMNS; k++) {
            int[] c = columnSpot(k);
            if (dx >= c[0] && dx <= c[0] + 1 && dz >= c[1] && dz <= c[1] + 1) {
                return true;
            }
        }
        return false;
    }

    /** The Arena Coordinator's answer: the crossing {i, j} of the nearest arena to (x, z) among the regions within `regions` (pure). */
    public static int[] nearest(long seed, int x, int z, int regions) {
        int span = SubstrateTerrainSC.GRID * REGION;
        int r0 = floorDiv(x, span), s0 = floorDiv(z, span);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int ri = r0 - regions; ri <= r0 + regions; ri++) {
            for (int rj = s0 - regions; rj <= s0 + regions; rj++) {
                int[] a = arenaOf(seed, ri, rj);
                if (a == null) {
                    continue;
                }
                double dx = centre(a[0]) - (double) x, dz = centre(a[1]) - (double) z, d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = a;
                }
            }
        }
        return best;
    }

    /** The arena record's key (ArenaDataSC) of region (ri, rj). */
    public static long key(int ri, int rj) {
        return ((long) ri << 32) ^ (rj & 0xFFFFFFFFL);
    }

    /** Floor division (Java 6 has no Math.floorDiv). */
    public static int floorDiv(int a, int b) {
        int q = a / b;
        return (a % b != 0 && (a < 0) != (b < 0)) ? q - 1 : q;
    }

    /** A well-mixed hash of the seed and two numbers (as RuinLayoutSC's). */
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
