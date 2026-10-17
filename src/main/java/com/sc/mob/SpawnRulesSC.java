package com.sc.mob;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.5): SpawnDirectorSC's limits as pure decisions (rules ПС-1, ПС-2) - the
 * self-test checks them without a world. Signatures fixed; M may add more.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.9, rules ПС-7, ПС-8): the census counts each native by its ICensusSC answer (a
 * swarm once, the mimics and the repair drones never); within NEAR_RADIUS of a player at most NEAR_MAX natives, of them at
 * most QUANTUM_NEAR_MAX of the Quantum tier and above; at most ELITE_MAX elites within ELITE_RADIUS; about WORLD_MAX in the
 * whole dimension. The kinds by the place of the spot - the plateaus, the canyons, a Dead Node's zone - and their weights,
 * by MobEntitiesSC pool id (the arrays are as long as the pool; the settlements' Tuner and guard, the repair drone and the
 * mimic weigh 0: those never come from here on their own). The Ф1 signatures stay as they were (the Ф1 self-test asks them).
 */
public final class SpawnRulesSC {

    /** Ticks between two tries for one player; the radius counted; the caps; how far from any player a group appears. */
    public static final int PERIOD = 200, NEAR_RADIUS = 64, NEAR_MAX = 8, WORLD_MAX = 40, MIN_DIST = 24, MAX_DIST = 48;
    /** Ф3, ПС-7: of the NEAR_MAX at most this many of the Quantum tier and above; at most ELITE_MAX elites within ELITE_RADIUS. */
    public static final int QUANTUM_NEAR_MAX = 2, ELITE_RADIUS = 128, ELITE_MAX = 1;
    /** Ф3, ПС-8: the places of a spot (placeOf). */
    public static final int PLACE_PLATEAU = 0, PLACE_CANYON = 1, PLACE_NODE = 2;
    /**
     * The weights by pool id (Lineman, Marauder, -, -, Trooper, Jammer, Hollow Shell, -, Executioner, nanite swarm, sentry
     * drone, -) on the plateaus, in the canyons and in a Dead Node's zone. TODO(balance)
     */
    public static final int[] PLATEAU_WEIGHTS = {60, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            CANYON_WEIGHTS = {30, 70, 0, 0, 30, 15, 0, 0, 3, 5, 0, 0},
            NODE_WEIGHTS = {0, 10, 0, 0, 0, 0, 70, 0, 0, 10, 20, 0};
    /**
     * Group sizes by pool id: Linemen walk in pairs, Marauders 2..4; Ф3 - Troopers 1..2, a Jammer alone (an escort aside),
     * Hollow Shells 2..3, a patrol's repair drones 2, the Executioner alone, a swarm of 6..10 particles (one census place),
     * sentry drones 1..2.
     */
    public static final int[] GROUP_MIN = {2, 2, 0, 0, 1, 1, 2, 2, 1, 6, 1, 0}, GROUP_MAX = {2, 4, 0, 0, 2, 1, 3, 2, 1, 10, 2, 0};
    /** The group's members appear within this many blocks of the first one; tries for each member's spot; tries for the first spot. */
    public static final int GROUP_SPREAD = 3, MEMBER_TRIES = 4, SPOT_TRIES = 6;
    /** Ф3, ПС-8: the Executioner never closer than this to the world's spawn point (blocks, horizontal). TODO(balance) */
    public static final int EXECUTIONER_MIN_DIST = 1000;
    /** Ф3: a Jammer brings a Trooper (if the caps allow); a Shell patrol brings two repair drones. TODO(balance) */
    public static final float JAMMER_ESCORT_CHANCE = 0.5F, DRONE_PATROL_CHANCE = 0.3F;

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

    /**
     * Ф3, ПС-7: as maySpawn, and a group of the Quantum tier (`quantum`) also within QUANTUM_NEAR_MAX - `nearQuantum` of them
     * around already.
     */
    public static boolean maySpawn(int near, int nearQuantum, int inWorld, int group, boolean quantum, boolean peaceful) {
        return maySpawn(near, inWorld, group, peaceful) && (!quantum || nearQuantum + group <= QUANTUM_NEAR_MAX);
    }

    /** Ф3: the largest group from `want` down to `min` that both counts allow, 0 if none. */
    public static int fitGroup(int near, int nearQuantum, int inWorld, int want, int min, boolean quantum, boolean peaceful) {
        for (int g = want; g >= Math.max(1, min); g--) {
            if (maySpawn(near, nearQuantum, inWorld, g, quantum, peaceful)) {
                return g;
            }
        }
        return 0;
    }

    /** Ф3, ПС-7: one more elite may appear where `elitesNear` are within ELITE_RADIUS. */
    public static boolean eliteAllowed(int elitesNear) {
        return elitesNear < ELITE_MAX;
    }

    /** Ф3: the kind (pool id) counts as the Quantum tier and above in the census (Trooper, Jammer, Hollow Shell, Executioner). */
    public static boolean quantumKind(int kind) {
        return kind == MobEntitiesSC.ID_TROOPER || kind == MobEntitiesSC.ID_JAMMER || kind == MobEntitiesSC.ID_HOLLOW_SHELL
                || kind == MobEntitiesSC.ID_EXECUTIONER;
    }

    /** Ф3: the kind is an elite of its own (takes the elite place whatever it rolls: the Executioner). */
    public static boolean eliteKind(int kind) {
        return kind == MobEntitiesSC.ID_EXECUTIONER;
    }

    /** Ф3: the kind wears the mod's armour - it may roll an elite modifier (not the Executioner: an elite of its kind). */
    public static boolean mayRollElite(int kind) {
        return kind == MobEntitiesSC.ID_LINEMAN || kind == MobEntitiesSC.ID_MARAUDER || kind == MobEntitiesSC.ID_TROOPER
                || kind == MobEntitiesSC.ID_JAMMER || kind == MobEntitiesSC.ID_HOLLOW_SHELL;
    }

    /** Ф3, ПС-8: a place's table (PLATEAU_WEIGHTS for an unknown place). */
    public static int[] weights(int place) {
        return place == PLACE_NODE ? NODE_WEIGHTS : place == PLACE_CANYON ? CANYON_WEIGHTS : PLATEAU_WEIGHTS;
    }

    /**
     * Ф3, ПС-7, ПС-8 (pure): the weights for one spot - a copy of the place's table without the kinds that couldn't come
     * there now: the Executioner closer than EXECUTIONER_MIN_DIST to the world's spawn or with no room for an elite; the
     * Quantum tier with no room under QUANTUM_NEAR_MAX (the roll takes another kind instead of nothing).
     */
    public static int[] weightsAt(int place, double fromSpawn, boolean quantumRoom, boolean eliteRoom) {
        int[] w = weights(place).clone();
        for (int k = 0; k < w.length; k++) {
            if (quantumKind(k) && !quantumRoom || eliteKind(k) && (!eliteRoom || fromSpawn < EXECUTIONER_MIN_DIST)) {
                w[k] = 0;
            }
        }
        return w;
    }

    /** Ф3, ПС-8 (pure): the place of a column - in a Dead Node's zone, else the canyons or the plateaus by its biome. */
    public static int placeOf(boolean inNodeZone, boolean canyonBiome) {
        return inNodeZone ? PLACE_NODE : canyonBiome ? PLACE_CANYON : PLACE_PLATEAU;
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
