package com.sc.mob;

import com.sc.dim.BiomeSubstrateSC;
import com.sc.hazard.StormRulesSC;

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
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §7.12, rules ПС-41, ПС-42): three more places - the Quartzite Ridges, the Oxide Fields, the
 * Rift (placeOf(node, biome kind); the Ф3 overload stays); every table as long as the pool (22); in an EMP storm the swarm x3
 * and the Arc Strider everywhere but at a Node (stormWeights); «Затмение» never by the tables - its own roll at a Rift spot
 * (eclipseChancePct); the Warlord and the Sentinel never from here (their camps and Vaults place them). The census: the
 * Overheated, the Colossus, the Strider and the three rare ones count as the Quantum tier, the rare ones as elites. The
 * peaceful branch (the Wafer Crab): its own period and caps (peacefulMaySpawn), never in the hostiles' census.
 */
public final class SpawnRulesSC {

    /** Ticks between two tries for one player; the radius counted; the caps; how far from any player a group appears. */
    public static final int PERIOD = 200, NEAR_RADIUS = 64, NEAR_MAX = 8, WORLD_MAX = 40, MIN_DIST = 24, MAX_DIST = 48;
    /** Ф3, ПС-7: of the NEAR_MAX at most this many of the Quantum tier and above; at most ELITE_MAX elites within ELITE_RADIUS. */
    public static final int QUANTUM_NEAR_MAX = 2, ELITE_RADIUS = 128, ELITE_MAX = 1;
    /** Ф3, ПС-8: the places of a spot (placeOf). Ф4 (ПС-41): the Quartzite Ridges, the Oxide Fields, the Rift - appended. */
    public static final int PLACE_PLATEAU = 0, PLACE_CANYON = 1, PLACE_NODE = 2, PLACE_RIDGES = 3, PLACE_OXIDE = 4, PLACE_RIFT = 5;
    /** Ф4: how many places there are (the self-test walks them: weights(0 .. PLACES - 1)). */
    public static final int PLACES = 6;
    /**
     * The weights by pool id (Lineman, Marauder, -, -, Trooper, Jammer, Hollow Shell, -, Executioner, nanite swarm, sentry
     * drone, -; Ф4: Overheated, -, -, -, Phantom, Leech, Colossus, -, Scrapper, -) on the plateaus, in the canyons and in a
     * Dead Node's zone; Ф4 (§7.12) on the Ridges, on the Oxide Fields and in the Rift. The Warlord (13), the Sentinel (14) and
     * «Затмение» (15) weigh 0 everywhere, the Arc Strider (19) too - it comes only with a storm (stormWeights); the Wafer Crab
     * (21) has the peaceful branch. TODO(balance)
     */
    public static final int[] PLATEAU_WEIGHTS = {60, 40, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 6, 0, 3, 0},
            CANYON_WEIGHTS = {30, 70, 0, 0, 30, 15, 0, 0, 3, 5, 0, 0, 0, 0, 0, 0, 8, 10, 0, 0, 3, 0},
            NODE_WEIGHTS = {0, 10, 0, 0, 0, 0, 70, 0, 0, 10, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            RIDGES_WEIGHTS = {15, 25, 0, 0, 30, 10, 0, 0, 0, 15, 0, 0, 0, 0, 0, 0, 8, 10, 12, 0, 0, 0},
            OXIDE_WEIGHTS = {0, 10, 0, 0, 10, 20, 0, 0, 0, 0, 0, 0, 40, 0, 0, 0, 0, 0, 0, 0, 4, 0},
            RIFT_WEIGHTS = {0, 0, 0, 0, 0, 0, 20, 0, 15, 10, 0, 0, 10, 0, 0, 0, 10, 0, 0, 0, 0, 0};
    /**
     * Group sizes by pool id: Linemen walk in pairs, Marauders 2..4; Ф3 - Troopers 1..2, a Jammer alone (an escort aside),
     * Hollow Shells 2..3, a patrol's repair drones 2, the Executioner alone, a swarm of 6..10 particles (one census place),
     * sentry drones 1..2. Ф4 (§7.12): the Overheated 1..2, Phantoms 1..2, a Leech, a Colossus, a Strider, a Scrapper and
     * «Затмение» alone, the Wafer Crabs 1..3 (the peaceful branch); the Warlord and the Sentinel never from here (0).
     */
    public static final int[] GROUP_MIN = {2, 2, 0, 0, 1, 1, 2, 2, 1, 6, 1, 0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1},
            GROUP_MAX = {2, 4, 0, 0, 2, 1, 3, 2, 1, 10, 2, 0, 2, 0, 0, 1, 2, 1, 1, 1, 1, 3};
    /** The group's members appear within this many blocks of the first one; tries for each member's spot; tries for the first spot. */
    public static final int GROUP_SPREAD = 3, MEMBER_TRIES = 4, SPOT_TRIES = 6;
    /** Ф3, ПС-8: the Executioner never closer than this to the world's spawn point (blocks, horizontal). TODO(balance) */
    public static final int EXECUTIONER_MIN_DIST = 1000;
    /** Ф3: a Jammer brings a Trooper (if the caps allow); a Shell patrol brings two repair drones. TODO(balance) */
    public static final float JAMMER_ESCORT_CHANCE = 0.5F, DRONE_PATROL_CHANCE = 0.3F;
    /** Ф4 (§7.12, ПФ-6): in an EMP storm the swarm's weight x STORM_SWARM_MUL and the Arc Strider at STORM_STRIDER_WEIGHT but at a Node. */
    public static final int STORM_SWARM_MUL = StormRulesSC.SWARM_MUL, STORM_STRIDER_WEIGHT = 12;
    /**
     * Ф4 (§7.4, ПС-34): «Затмение»'s own roll - ECLIPSE_PCT % of the director's tries at a Rift spot (x
     * StormRulesSC.EMISSION_ECLIPSE_MUL while the Singular Emission runs), only while its record (RareRegistrySC.ECLIPSE) isn't
     * ALIVE and ECLIPSE_RESPAWN_TICKS (3 game days) went by since it died. TODO(balance)
     */
    public static final int ECLIPSE_PCT = 2;
    public static final long ECLIPSE_RESPAWN_TICKS = 72000L;
    /**
     * Ф4 (§7.12, ПС-41): the peaceful branch - a try every PEACEFUL_PERIOD ticks a player, at most PEACEFUL_NEAR_MAX within
     * NEAR_RADIUS and PEACEFUL_WORLD_MAX in the dimension, only on the plateaus and the Ridges. TODO(balance)
     */
    public static final int PEACEFUL_PERIOD = 400, PEACEFUL_NEAR_MAX = 4, PEACEFUL_WORLD_MAX = 12;

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

    /**
     * Ф3: the kind (pool id) counts as the Quantum tier and above in the census (Trooper, Jammer, Hollow Shell, Executioner). Ф4
     * (§7.12): the Overheated (burnt-out Quantum), the three rare ones, the Colossus and the Strider.
     */
    public static boolean quantumKind(int kind) {
        return kind == MobEntitiesSC.ID_TROOPER || kind == MobEntitiesSC.ID_JAMMER || kind == MobEntitiesSC.ID_HOLLOW_SHELL
                || kind == MobEntitiesSC.ID_EXECUTIONER || kind == MobEntitiesSC.ID_OVERHEATED || rareKind(kind)
                || kind == MobEntitiesSC.ID_COLOSSUS || kind == MobEntitiesSC.ID_STRIDER;
    }

    /** Ф3: the kind is an elite of its own (takes the elite place whatever it rolls: the Executioner; Ф4 - the three rare ones). */
    public static boolean eliteKind(int kind) {
        return kind == MobEntitiesSC.ID_EXECUTIONER || rareKind(kind);
    }

    /** Ф4 (ПС-30): one of the rare ones - the camps' Warlord, the Vaults' Sentinel, «Затмение» (RareRegistrySC keeps them). */
    public static boolean rareKind(int kind) {
        return kind == MobEntitiesSC.ID_WARLORD || kind == MobEntitiesSC.ID_SENTINEL || kind == MobEntitiesSC.ID_ECLIPSE;
    }

    /**
     * Ф3: the kind wears the mod's armour - it may roll an elite modifier (not the Executioner: an elite of its kind). Ф4: the
     * Overheated too (the rare ones are elites of their kind).
     */
    public static boolean mayRollElite(int kind) {
        return kind == MobEntitiesSC.ID_LINEMAN || kind == MobEntitiesSC.ID_MARAUDER || kind == MobEntitiesSC.ID_TROOPER
                || kind == MobEntitiesSC.ID_JAMMER || kind == MobEntitiesSC.ID_HOLLOW_SHELL || kind == MobEntitiesSC.ID_OVERHEATED;
    }

    /** Ф3, ПС-8: a place's table (PLATEAU_WEIGHTS for an unknown place). Ф4: the Ridges', the Oxide Fields', the Rift's. */
    public static int[] weights(int place) {
        switch (place) {
            case PLACE_NODE:
                return NODE_WEIGHTS;
            case PLACE_CANYON:
                return CANYON_WEIGHTS;
            case PLACE_RIDGES:
                return RIDGES_WEIGHTS;
            case PLACE_OXIDE:
                return OXIDE_WEIGHTS;
            case PLACE_RIFT:
                return RIFT_WEIGHTS;
            default:
                return PLATEAU_WEIGHTS;
        }
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

    /**
     * Ф4 (§7.12, ПФ-6; pure): `w` (a copy) as an EMP storm changes it at `place` - the swarm x STORM_SWARM_MUL, the Arc Strider
     * at STORM_STRIDER_WEIGHT everywhere but in a Dead Node's zone. No storm: the copy as it was.
     */
    public static int[] stormWeights(int[] w, int place, boolean storm) {
        int[] out = w.clone();
        if (!storm) {
            return out;
        }
        if (MobEntitiesSC.ID_NANITE < out.length) {
            out[MobEntitiesSC.ID_NANITE] *= STORM_SWARM_MUL;
        }
        if (place != PLACE_NODE && MobEntitiesSC.ID_STRIDER < out.length) {
            out[MobEntitiesSC.ID_STRIDER] = Math.max(out[MobEntitiesSC.ID_STRIDER], STORM_STRIDER_WEIGHT);
        }
        return out;
    }

    /**
     * Ф4 (ПС-41; pure): weightsAt, then the storm (stormWeights) - the Strider is of the Quantum tier, so a full Quantum cap
     * leaves it out as well.
     */
    public static int[] weightsAt(int place, double fromSpawn, boolean quantumRoom, boolean eliteRoom, boolean storm) {
        int[] w = stormWeights(weightsAt(place, fromSpawn, quantumRoom, eliteRoom), place, storm);
        for (int k = 0; k < w.length; k++) {
            if (quantumKind(k) && !quantumRoom || eliteKind(k) && !eliteRoom) {
                w[k] = 0;
            }
        }
        return w;
    }

    /** Ф3, ПС-8 (pure): the place of a column - in a Dead Node's zone, else the canyons or the plateaus by its biome. */
    public static int placeOf(boolean inNodeZone, boolean canyonBiome) {
        return inNodeZone ? PLACE_NODE : canyonBiome ? PLACE_CANYON : PLACE_PLATEAU;
    }

    /**
     * Ф4 (ПС-41, pure): the place of a column - in a Dead Node's zone, else by its biome kind (BiomeSubstrateSC.PLATEAUS ..
     * RIFT; an unknown kind - the plateaus).
     */
    public static int placeOf(boolean inNodeZone, int biomeKind) {
        if (inNodeZone) {
            return PLACE_NODE;
        }
        switch (biomeKind) {
            case BiomeSubstrateSC.CANYONS:
                return PLACE_CANYON;
            case BiomeSubstrateSC.RIDGES:
                return PLACE_RIDGES;
            case BiomeSubstrateSC.OXIDE:
                return PLACE_OXIDE;
            case BiomeSubstrateSC.RIFT:
                return PLACE_RIFT;
            default:
                return PLACE_PLATEAU;
        }
    }

    /**
     * Ф4 (§7.4, ПС-34; pure): «Затмение»'s roll at a Rift spot in % - ECLIPSE_PCT, x3 in the Emission; 0 while its record (state
     * RareRegistrySC.NONE / ALIVE / DEAD, died at `diedAt`) can't have it back at `now`, or the elite cap round the spot is full.
     */
    public static int eclipseChancePct(int state, long diedAt, long now, boolean emission, boolean eliteRoom) {
        if (!eliteRoom || !RareRegistrySC.mayRespawn(state, diedAt, now, ECLIPSE_RESPAWN_TICKS)) {
            return 0;
        }
        return Math.round(ECLIPSE_PCT * (emission ? StormRulesSC.EMISSION_ECLIPSE_MUL : 1F));
    }

    /** Ф4 (ПС-41; pure): a group of `group` peaceful natives (the Wafer Crabs) may come with `near` of them round the player, `inWorld` in all. */
    public static boolean peacefulMaySpawn(int near, int inWorld, int group) {
        return group > 0 && near + group <= PEACEFUL_NEAR_MAX && inWorld + group <= PEACEFUL_WORLD_MAX;
    }

    /** Ф4 (ПС-41; pure): the largest peaceful group from `want` down to 1 that the caps allow, 0 if none. */
    public static int fitPeaceful(int near, int inWorld, int want) {
        for (int g = want; g >= 1; g--) {
            if (peacefulMaySpawn(near, inWorld, g)) {
                return g;
            }
        }
        return 0;
    }

    /** Ф4 (ПС-41; pure): the peaceful branch's places - the plateaus and the Ridges. */
    public static boolean peacefulPlace(int place) {
        return place == PLACE_PLATEAU || place == PLACE_RIDGES;
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
