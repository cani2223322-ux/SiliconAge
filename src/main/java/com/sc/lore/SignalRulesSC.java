package com.sc.lore;

import com.sc.arena.ArenaLayoutSC;
import com.sc.dim.SubstrateTerrainSC;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §7, rules ПЛ-5..ПЛ-8; proposal §9 событие 5 «Сигнал Литографов. Редко в одном из
 * Мёртвых Узлов на 10 мин оживает маяк, на горизонте встаёт вертикальный луч. Там тайник (чипы, Крупица в пределах лимита, Журнал),
 * но рядом появляется патруль Оболочек»): the Signal's numbers and pure decisions - written by the coordinator; package J may retune
 * the TODO(balance) numbers, not the contract.
 * <ul>
 * <li>one roll a game day (the day's first check with a player in the Substrate - never twice: SignalDataSC keeps the day), CHANCE_PCT;
 * the start - START_MIN..START_MAX ticks into the day (any hour: the beam shows by day too), only if a player is in the Substrate
 * then (else the day's signal is gone - nothing simulated without a player);</li>
 * <li>the Node: a crossing DIST_MIN..DIST_MAX blocks from a random player there, not within SPAWN_RING lines of the spawn's, never an
 * arena's crossing (ArenaLayoutSC) - by the hash of the day;</li>
 * <li>it lasts DURATION_TICKS; a player coming within CACHE_REACH of the crossing while it is on - the cache (a chest at the plaza's
 * middle: Memory Chips, Controllers, a Sealed Crumb - the daily cap holds at its opening, a Journal №11) and a patrol of
 * PATROL_SHELLS + one a player beyond the first Hollow Shells (born in Si-14) - once a signal;</li>
 * <li>the beam - the clients draw it from the plaza's floor to BEAM_TOP, seen from BEAM_VIEW blocks (past the render distance).</li>
 * </ul>
 */
public final class SignalRulesSC {

    /** ПЛ-5: the day's chance, the start's window in the day, the duration (10 min). TODO(balance) */
    public static final int CHANCE_PCT = 15, START_MIN = 1000, START_MAX = 20000, DURATION_TICKS = 12000;
    /** ПЛ-6: how far from the player the Node is; the spawn's ring kept clear; tries for a crossing. TODO(balance) */
    public static final int DIST_MIN = 300, DIST_MAX = 800, SPAWN_RING = 1, TRIES = 16;
    /** ПЛ-7: the cache's reach, the patrol. TODO(balance) */
    public static final int CACHE_REACH = 24, PATROL_SHELLS = 3;
    /** ПЛ-7: the cache's contents (Memory Chips, Controllers ranges; one Sealed Crumb; one Journal №11). TODO(balance) */
    public static final int CHIPS_MIN = 1, CHIPS_MAX = 3, CONTROLLERS_MIN = 1, CONTROLLERS_MAX = 2;
    /** ПЛ-8: the beam's top and how far it is seen. */
    public static final int BEAM_TOP = 256, BEAM_VIEW = 1024;
    /** The day's check runs this often (ticks). */
    public static final int CHECK_TICKS = 100;

    private static final long SALT = 0x516A1A15L;

    private SignalRulesSC() {
    }

    /** ПЛ-5 (pure): game day `day` has a Signal. */
    public static boolean rolls(long seed, long day) {
        return pick(mix(seed ^ SALT, day, 0), 100) < CHANCE_PCT;
    }

    /** ПЛ-5 (pure): when in day `day` (ticks into it) its Signal begins. */
    public static int startOf(long seed, long day) {
        return START_MIN + pick(mix(seed ^ SALT, day, 1), START_MAX - START_MIN + 1);
    }

    /** ПЛ-6 (pure): the crossing {i, j} for a player at (px, pz) on day `day`, or null (none fits). */
    public static int[] crossingFor(long seed, long day, int px, int pz) {
        long h = mix(seed ^ SALT, day, 2);
        for (int t = 0; t < TRIES; t++) {
            long ht = mix(h, t, 5);
            double a = (ht >>> 11) * (2D * Math.PI / (1L << 53));
            double d = DIST_MIN + pick(mix(ht, 1, 1), DIST_MAX - DIST_MIN + 1);
            int i = SubstrateTerrainSC.lineIndex((int) Math.floor(px + Math.cos(a) * d));
            int j = SubstrateTerrainSC.lineIndex((int) Math.floor(pz + Math.sin(a) * d));
            if (Math.abs(i) <= SPAWN_RING && Math.abs(j) <= SPAWN_RING || ArenaLayoutSC.isArena(seed, i, j)) {
                continue;
            }
            return new int[]{i, j};
        }
        return null;
    }

    /** ПЛ-7 (pure): the patrol for `players` players. */
    public static int patrolSize(int players) {
        return PATROL_SHELLS + Math.max(0, players - 1);
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
