package com.sc.mob;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.5): SpawnDirectorSC's limits as pure decisions (rules ПС-1, ПС-2) - the
 * self-test checks them without a world. Signatures fixed; M may add more.
 */
public final class SpawnRulesSC {

    /** Ticks between two tries for one player; the radius counted; the caps; how far from any player a group appears. */
    public static final int PERIOD = 200, NEAR_RADIUS = 64, NEAR_MAX = 8, WORLD_MAX = 40, MIN_DIST = 24, MAX_DIST = 48;
    /** Biome weights (Lineman / Marauder) on the plateaus and in the canyons. TODO(balance) */
    public static final int[] PLATEAU_WEIGHTS = {60, 40}, CANYON_WEIGHTS = {30, 70};
    /** Group sizes by kind (the weights' order = MobEntitiesSC pool ids): Linemen walk in pairs, Marauders 2..4. */
    public static final int[] GROUP_MIN = {2, 2}, GROUP_MAX = {2, 4};
    /** The group's members appear within this many blocks of the first one; tries for each member's spot; tries for the first spot. */
    public static final int GROUP_SPREAD = 3, MEMBER_TRIES = 4, SPOT_TRIES = 6;

    private SpawnRulesSC() {
    }

    /** A group of `group` may spawn near a player with `near` natives around and `inWorld` in the whole dimension (Peaceful: never). */
    public static boolean maySpawn(int near, int inWorld, int group, boolean peaceful) {
        return !peaceful && group > 0 && near + group <= NEAR_MAX && inWorld + group <= WORLD_MAX;
    }

    /** The largest group from `want` down to `min` that maySpawn allows, 0 if none. */
    public static int fitGroup(int near, int inWorld, int want, int min, boolean peaceful) {
        for (int g = want; g >= Math.max(1, min); g--) {
            if (maySpawn(near, inWorld, g, peaceful)) {
                return g;
            }
        }
        return 0;
    }

    /** A weighted pick: `roll` in 0 .. sum(weights) - 1. @return the index, -1 for no positive weight */
    public static int pick(int[] weights, int roll) {
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            if (roll < weights[i]) {
                return i;
            }
            roll -= weights[i];
        }
        return -1;
    }

    public static int sum(int[] weights) {
        int s = 0;
        for (int w : weights) {
            s += Math.max(0, w);
        }
        return s;
    }
}
