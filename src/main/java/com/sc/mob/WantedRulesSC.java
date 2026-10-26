package com.sc.mob;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск: именные Отступники», ★ A): the wanted Renegade's numbers and pure decisions - the
 * coordinator's names and signatures, ПД-4's bodies and TODO(balance) numbers.
 * <ul>
 * <li>once a game week (SettlementSC.week) a new one, while the rule dimension.wanted is on and a City exists: posted by the City's
 * Captain (a chat line to everyone in the Substrate; the Captain's Shift + right click repeats it);</li>
 * <li>it roams RANGE_MIN..RANGE_MAX from its City, moving ROAM_STEP a ROAM_EVERY while unloaded (simulated on its record), never into a
 * dome's belt or the Rift; a player within SPAWN_NEAR of its spot brings it (and ESCORT Troopers) into the world;</li>
 * <li>Quantum (QUANTUM_PCT) or Exo armour, two elite modifiers from PAIRS only (no pair that spirals - it never grows stronger by killing
 * players);</li>
 * <li>killed: every player with REWARD_SHARE of the damage - REWARD_CHIPS Credit Chips, REWARD_REP «Доверия» (ReputationSC.SRC_WANTED),
 * one Wanted Token; TOKENS_PER_RESTORE tokens pay an Armourer's restoring instead of its chips; the Cartographer's word on where it was
 * seen last: HINT_CHIPS (TradeOfferSC.OUT_WANTED_HINT), within HINT_SPREAD of the truth.</li>
 * </ul>
 */
public final class WantedRulesSC {

    /** The roaming ring round its City, the walk while unloaded, the spawn distance, the escort. TODO(balance) */
    public static final int RANGE_MIN = 600, RANGE_MAX = 1500, ROAM_STEP = 48, ROAM_EVERY = 1200, SPAWN_NEAR = 96, ESCORT = 3;
    /** Quantum armour this often, else Exo. */
    public static final int QUANTUM_PCT = 60;
    /** The allowed modifier pairs (EliteModSC ordinals; the Avenger and the Repairer never - no spiral, no endless fight). TODO(balance) */
    public static final int[][] PAIRS = {{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}, {2, 3}};
    /** The rewards. TODO(balance) */
    public static final float REWARD_SHARE = 0.10F;
    public static final int REWARD_CHIPS = 12, REWARD_REP = 40, TOKENS_PER_RESTORE = 3;
    /** The Cartographer's word: its price, how far off the truth it may be. */
    public static final int HINT_CHIPS = 4, HINT_SPREAD = 64;
    /** The name's parts (lang sc.wanted.name.<i>, sc.wanted.alias.<i>): how many of each. */
    public static final int NAMES = 12, ALIASES = 12;

    private WantedRulesSC() {
    }

    /** Pure: the clients' look (EntityWantedSC.DW_WANTED): the name's code, the alias's code, the second modifier's code (0 none). */
    public static int packLook(int name, int alias, int mod2) {
        return (name & 0xFF) | (alias & 0xFF) << 8 | (mod2 & 0xFF) << 16;
    }

    public static int nameOf(int look) {
        return look & 0xFF;
    }

    public static int aliasOf(int look) {
        return look >> 8 & 0xFF;
    }

    public static int mod2Of(int look) {
        return look >> 16 & 0xFF;
    }

    /** ПД-4: a wanted one's leave - no real player within LEAVE_REACH for LEAVE_TICKS: it and its escort leave the world. TODO(balance) */
    public static final int LEAVE_REACH = 128, LEAVE_TICKS = 600;
    /** ПД-4: the tries for a spot outside the bans (a dome's belt, the Rift, an arena's crossing). */
    public static final int SPOT_TRIES = 16;

    private static final long SALT = 0x0BAD5EEDL;

    /** Pure: the week's own hash `k` (0..) of world seed `seed`, week `week`. */
    public static long hash(long seed, long week, int k) {
        long h = seed ^ SALT ^ week * 0x9E3779B97F4A7C15L ^ (long) k * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }

    /** Pure: which of `cities` Cities posts week `week`'s wanted one (-1 none). */
    public static int city(long seed, long week, int cities) {
        return cities <= 0 ? -1 : (int) (hash(seed, week, 1) % cities);
    }

    /** Pure: week `week`'s wanted one wears Quantum (QUANTUM_PCT), else Exo. */
    public static boolean quantum(long seed, long week) {
        return hash(seed, week, 2) % 100 < QUANTUM_PCT;
    }

    /** Pure: week `week`'s pair (EliteModSC ordinals, the first one its own modifier), one of PAIRS either way round. */
    public static int[] pair(long seed, long week) {
        int[] p = PAIRS[(int) (hash(seed, week, 3) % PAIRS.length)];
        return hash(seed, week, 4) % 2 == 0 ? new int[]{p[0], p[1]} : new int[]{p[1], p[0]};
    }

    /** Pure: the name's and the alias's index (lang sc.wanted.name.<i> / sc.wanted.alias.<i>). */
    public static int name(long seed, long week) {
        return (int) (hash(seed, week, 5) % NAMES);
    }

    public static int alias(long seed, long week) {
        return (int) (hash(seed, week, 6) % ALIASES);
    }

    /**
     * Pure: the try `attempt` of week `week`'s first spot round a City's core (cx, cz) - an angle and a distance RANGE_MIN + 1..RANGE_MAX - 1
     * ({x, z}; a block in from both edges: the rounded column stays in the ring). WantedSC tries again while a ban says no.
     */
    public static int[] spot(long seed, long week, int cx, int cz, int attempt) {
        double a = hash(seed, week, 100 + attempt * 2) % 3600 / 3600D * Math.PI * 2D;
        int d = RANGE_MIN + 1 + (int) (hash(seed, week, 101 + attempt * 2) % (RANGE_MAX - RANGE_MIN - 1));
        return new int[]{cx + (int) Math.round(Math.cos(a) * d), cz + (int) Math.round(Math.sin(a) * d)};
    }

    /** Pure: (x, z) is within the roaming ring of a City at (cx, cz). */
    public static boolean inRing(int x, int z, int cx, int cz) {
        double dx = x - cx, dz = z - cz, d2 = dx * dx + dz * dz;
        return d2 >= (double) RANGE_MIN * RANGE_MIN && d2 <= (double) RANGE_MAX * RANGE_MAX;
    }

    /** Pure: the Cartographer's word on a spot seen at world time `seenAt` and asked at `now` - minutes ago (at least 0). */
    public static int minutesAgo(long seenAt, long now) {
        return (int) Math.max(0L, (now - seenAt) / 1200L);
    }

    /** Pure: `a` and `b` (EliteModSC) may go on one Renegade - a PAIRS pair either way round. */
    public static boolean allowedPair(EliteModSC a, EliteModSC b) {
        if (a == null || b == null || a == b) {
            return false;
        }
        for (int[] p : PAIRS) {
            if (p[0] == a.ordinal() && p[1] == b.ordinal() || p[1] == a.ordinal() && p[0] == b.ordinal()) {
                return true;
            }
        }
        return false;
    }
}
