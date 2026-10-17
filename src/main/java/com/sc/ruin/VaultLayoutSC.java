package com.sc.ruin;

import com.sc.dim.SubstrateTerrainSC;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §8.5, rule ПХ-1; proposal §3 «Хранилище | Под одним из 16 Узлов (1 на квадрат 4×4
 * Узла)»): where the Vaults are - pure maths of the seed and the Scribe grid. Written by the coordinator; package C may retune
 * the numbers (TODO(balance)), not the contract.
 * <ul>
 * <li>the crossings are grouped in blocks of BLOCK x BLOCK (block (bi, bj) - crossings bi·4 .. bi·4 + 3 on each axis); each
 * block holds one Vault, under the Dead Node of one of its crossings (by the hash) - but never under a crossing within
 * SPAWN_RING of the spawn's (0, 0): that block has none;</li>
 * <li>the Vault: a sealed bunker under the Node's plaza, its middle the crossing, HALF blocks each way (walls included), its
 * floor floorY (the plaza's floor - SubstrateTerrainSC.nodeFloorY, 24..34 - minus DEPTH, never below FLOOR_MIN), HEIGHT high
 * (floor and ceiling included); a shaft from the plaza's middle down, sealed by vault plating.</li>
 * </ul>
 */
public final class VaultLayoutSC {

    /** ПХ-1: crossings a block's side; the bunker's half size, height, its floor under the plaza; the spawn's ring. TODO(balance) */
    public static final int BLOCK = 4, HALF = 8, HEIGHT = 8, DEPTH = 18, FLOOR_MIN = 4, SPAWN_RING = 1;

    private static final long SALT = 0x7A017B10L;

    private VaultLayoutSC() {
    }

    /** ПХ-1: the crossing {i, j} of block (bi, bj) with its Vault, or null (pure). */
    public static int[] vaultOf(long seed, int bi, int bj) {
        long h = RuinLayoutSC.mix(seed ^ SALT, bi, bj);
        int i = bi * BLOCK + RuinLayoutSC.pick(h, BLOCK), j = bj * BLOCK + RuinLayoutSC.pick(RuinLayoutSC.mix(h, 1, 0), BLOCK);
        if (Math.abs(i) <= SPAWN_RING && Math.abs(j) <= SPAWN_RING) {
            return null;                                       // not under the spawn's own crossings
        }
        return new int[]{i, j};
    }

    /** ПХ-1: crossing (i, j) has a Vault under its Dead Node (pure). */
    public static boolean hasVault(long seed, int i, int j) {
        int[] v = vaultOf(seed, CampLayoutSC.floorDiv(i, BLOCK), CampLayoutSC.floorDiv(j, BLOCK));
        return v != null && v[0] == i && v[1] == j;
    }

    /** ПХ-1: the Vault's floor (its own block's Y) under crossing (i, j). */
    public static int floorY(long seed, int i, int j) {
        return Math.max(FLOOR_MIN, SubstrateTerrainSC.nodeFloorY(seed, i, j) - DEPTH);
    }

    /** The crossing {i, j} whose Vault's box, widened by `margin`, holds the column (x, z), or null (pure). */
    public static int[] vaultNear(long seed, int x, int z, int margin) {
        if (SubstrateTerrainSC.distanceToLine(x) > HALF + margin || SubstrateTerrainSC.distanceToLine(z) > HALF + margin) {
            return null;
        }
        int i = SubstrateTerrainSC.lineIndex(x), j = SubstrateTerrainSC.lineIndex(z);
        return hasVault(seed, i, j) ? new int[]{i, j} : null;
    }

    /** The point lies inside a Vault's bunker (floor to ceiling, walls included): the spawn director, the carver keep out (pure). */
    public static boolean inVault(long seed, int x, int y, int z) {
        int[] v = vaultNear(seed, x, z, 0);
        if (v == null) {
            return false;
        }
        int f = floorY(seed, v[0], v[1]);
        return y >= f && y < f + HEIGHT;
    }

    /**
     * The Pelengator's answer (ItemRuinFinderSC): the crossing {i, j} of the nearest Vault to (x, z) among the blocks within
     * `blocks` of the point's, or null (pure).
     */
    public static int[] nearest(long seed, int x, int z, int blocks) {
        int grid = SubstrateTerrainSC.GRID * BLOCK;
        int bi0 = CampLayoutSC.floorDiv(x, grid), bj0 = CampLayoutSC.floorDiv(z, grid);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int bi = bi0 - blocks; bi <= bi0 + blocks; bi++) {
            for (int bj = bj0 - blocks; bj <= bj0 + blocks; bj++) {
                int[] v = vaultOf(seed, bi, bj);
                if (v == null) {
                    continue;
                }
                double dx = v[0] * (double) SubstrateTerrainSC.GRID - x, dz = v[1] * (double) SubstrateTerrainSC.GRID - z;
                double d = dx * dx + dz * dz;
                if (d < bestD) {
                    bestD = d;
                    best = v;
                }
            }
        }
        return best;
    }
}
